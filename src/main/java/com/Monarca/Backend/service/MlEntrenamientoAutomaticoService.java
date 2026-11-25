package com.Monarca.Backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class MlEntrenamientoAutomaticoService {
    private static final Logger log=LoggerFactory.getLogger(MlEntrenamientoAutomaticoService.class);
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private final JdbcTemplate db;
    private final MlTallasEntrenador entrenador;
    public record Config(boolean habilitado,int horas,int hora,int minNuevos,OffsetDateTime proxima,boolean enCurso,int ultimoTotal) {}
    public record Ajustes(boolean habilitado,int horas,int hora,int minNuevos) {}
    public record Evento(long id,OffsetDateTime inicio,OffsetDateTime fin,String estado,int registros,int nuevos,Long idModelo,String detalle,String algoritmo,String accuracy,String macroF1) {}
    public MlEntrenamientoAutomaticoService(JdbcTemplate db,MlTallasEntrenador entrenador){this.db=db;this.entrenador=entrenador;}
    public Config config(){
        return db.queryForObject("""
            SELECT habilitado,horas,hora,min_nuevos,proxima,COALESCE(ocupado_hasta>now(),false) AS en_curso,ultimo_total
            FROM monarca.ml_auto_config WHERE id=1
            """,(rs,i)->new Config(rs.getBoolean("habilitado"),rs.getInt("horas"),rs.getInt("hora"),rs.getInt("min_nuevos"),rs.getObject("proxima",OffsetDateTime.class),rs.getBoolean("en_curso"),rs.getInt("ultimo_total")));
    }
    public static OffsetDateTime primera(OffsetDateTime ahora,int horas,int hora){
        ZonedDateTime local=ahora.atZoneSameInstant(LIMA);
        ZonedDateTime objetivo=local.toLocalDate().atTime(hora,0).atZone(LIMA);
        if(horas==12){if(!objetivo.isAfter(local))objetivo=objetivo.plusHours(12);if(!objetivo.isAfter(local))objetivo=objetivo.plusHours(12);}
        else if(!objetivo.isAfter(local))objetivo=objetivo.plusDays(1);
        return objetivo.toOffsetDateTime();
    }
    public Config guardar(Ajustes a){
        if(a==null||!Set.of(12,24,72,168).contains(a.horas())||a.hora()<0||a.hora()>23||a.minNuevos()<1||a.minNuevos()>1000)
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Configuración inválida");
        OffsetDateTime proxima=a.habilitado()?primera(OffsetDateTime.now(),a.horas(),a.hora()):null;
        int n=db.update("""
            UPDATE monarca.ml_auto_config SET habilitado=?,horas=?,hora=?,min_nuevos=?,proxima=?
            WHERE id=1 AND (ocupado_hasta IS NULL OR ocupado_hasta<=now())
            """,a.habilitado(),a.horas(),a.hora(),a.minNuevos(),proxima);
        if(n!=1)throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Hay un entrenamiento en curso. Intenta de nuevo al finalizar.");
        return config();
    }
    public List<Evento> historial(){
        return db.query("""
            SELECT h.*,m.algoritmo,m.metricas->'test'->>'accuracy' AS accuracy,m.metricas->'test'->>'macro_f1' AS macro_f1
            FROM monarca.ml_auto_historial h LEFT JOIN monarca.ml_modelos_talla m ON m.id_modelo=h.id_modelo
            ORDER BY h.id DESC LIMIT 30
            """,(rs,i)->new Evento(rs.getLong("id"),rs.getObject("inicio",OffsetDateTime.class),rs.getObject("fin",OffsetDateTime.class),rs.getString("estado"),rs.getInt("registros"),rs.getInt("nuevos"),rs.getObject("id_modelo",Long.class),rs.getString("detalle"),rs.getString("algoritmo"),rs.getString("accuracy"),rs.getString("macro_f1")));
    }
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void revisar(){
        try{
            Config c=config();OffsetDateTime ahora=OffsetDateTime.now();
            if(c.enCurso())return;
            db.update("UPDATE monarca.ml_auto_historial SET estado='ERROR',fin=now(),detalle='Ejecución interrumpida por reinicio o vencimiento del bloqueo' WHERE estado='EN_CURSO' AND inicio<now()-interval '5 minutes'");
            if(!c.habilitado()||c.proxima()==null||c.proxima().isAfter(ahora))return;
            long interval=c.horas()*3600L;
            long saltos=Math.max(1,Duration.between(c.proxima(),ahora).getSeconds()/interval+1);
            OffsetDateTime siguiente=c.proxima().plusHours(saltos*c.horas());
            int lock=db.update("""
                UPDATE monarca.ml_auto_config SET ocupado_hasta=now()+interval '5 minutes',proxima=?
                WHERE id=1 AND habilitado AND proxima=? AND proxima<=now() AND (ocupado_hasta IS NULL OR ocupado_hasta<=now())
                """,siguiente,c.proxima());
            if(lock==1)ejecutar(c);
        }catch(Exception e){log.warn("No se pudo revisar la programación ML: {}",e.getClass().getSimpleName());}
    }
    private void ejecutar(Config c){
        Long id=null;int registros=0,nuevos=0;
        try{
            id=db.queryForObject("INSERT INTO monarca.ml_auto_historial(estado,detalle) VALUES('EN_CURSO','Consultando valoraciones reales') RETURNING id",Long.class);
            var dataset=entrenador.dataset("REALES");
            registros=dataset.registros();nuevos=Math.max(0,registros-c.ultimoTotal());
            if(registros<30||nuevos<c.minNuevos()){
                finalizar(id,"OMITIDO",registros,nuevos,null,registros<30?"Se requieren al menos 30 valoraciones válidas":"No hay suficientes registros adicionales");
                return;
            }
            Map<String,Object> resultado=entrenador.entrenar("REALES",dataset);
            long modelo=((Number)resultado.get("idModelo")).longValue();
            finalizar(id,"COMPLETADO",registros,nuevos,modelo,"Modelo candidato guardado; no se activó automáticamente");
            db.update("UPDATE monarca.ml_auto_config SET ultimo_total=? WHERE id=1",registros);
        }catch(Exception e){
            if(id!=null)finalizar(id,"ERROR",registros,nuevos,null,e instanceof ResponseStatusException se?Objects.toString(se.getReason(),"Error de entrenamiento"):"No se pudo completar el entrenamiento");
        }finally{db.update("UPDATE monarca.ml_auto_config SET ocupado_hasta=NULL WHERE id=1");}
    }
    private void finalizar(long id,String estado,int registros,int nuevos,Long modelo,String detalle){
        db.update("UPDATE monarca.ml_auto_historial SET fin=now(),estado=?,registros=?,nuevos=?,id_modelo=?,detalle=? WHERE id=?",estado,registros,nuevos,modelo,detalle,id);
    }
}
