package com.Monarca.Backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class MlTallasEntrenador {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @Value("${monarca.ml.url:}") private String mlUrl;
    @Value("${monarca.ml.internal-token:}") private String mlToken;
    private static final String COLUMNAS="categoria,talla_comprada,ajuste,busto_cm,cintura_cm,cadera_cm,prenda_pecho_cm,prenda_cintura_cm,prenda_cadera_cm";
    private static final String FILTRO_REAL="""
        consentimiento_ml IS TRUE AND CASE WHEN categoria='Tops'
          THEN busto_cm IS NOT NULL AND cintura_cm IS NOT NULL AND prenda_pecho_cm IS NOT NULL AND prenda_cintura_cm IS NOT NULL
        WHEN categoria='Bottoms' THEN cintura_cm IS NOT NULL AND cadera_cm IS NOT NULL AND prenda_cintura_cm IS NOT NULL AND prenda_cadera_cm IS NOT NULL
        WHEN categoria='Vestidos' THEN busto_cm IS NOT NULL AND cintura_cm IS NOT NULL AND cadera_cm IS NOT NULL AND prenda_pecho_cm IS NOT NULL AND prenda_cintura_cm IS NOT NULL AND prenda_cadera_cm IS NOT NULL
        ELSE FALSE END
        """;
    public record Dataset(String csv,int registros) {}
    public MlTallasEntrenador(JdbcTemplate db,ObjectMapper mapper){this.db=db;this.mapper=mapper;}
    public Dataset dataset(String fuente){
        if(!Set.of("REALES","PRUEBA").contains(fuente))throw new IllegalArgumentException("Fuente inválida");
        String sql="SELECT "+COLUMNAS+("PRUEBA".equals(fuente)?" FROM monarca.ml_tallas_prueba ORDER BY id_ejemplo":" FROM monarca.valoraciones_talla WHERE "+FILTRO_REAL+" ORDER BY fecha_valoracion,id_valoracion");
        StringBuilder csv=new StringBuilder(COLUMNAS+"\n");
        int[] n={0};
        db.query(sql,rs->{
            n[0]++;
            for(int i=1;i<=9;i++){
                if(i>1)csv.append(',');
                String valor=rs.getString(i);
                if(valor!=null)csv.append('"').append(valor.replace("\"","\"\"")).append('"');
            }
            csv.append('\n');
        });
        return new Dataset(csv.toString(),n[0]);
    }
    public Map<String,Object> entrenar(String fuente,Dataset dataset){
        if(!Set.of("REALES","PRUEBA").contains(fuente))throw new IllegalArgumentException("Fuente inválida");
        if(mlUrl==null||mlUrl.isBlank()||mlToken==null||mlToken.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"El servicio ML no está configurado");
        if(dataset.registros()<30||dataset.csv().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>300000)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Se necesitan al menos 30 registros válidos y un dataset dentro del límite");
        try{
            String body=mapper.writeValueAsString(Map.of("fuente",fuente,"dataset_csv",dataset.csv()));
            HttpRequest req=HttpRequest.newBuilder(URI.create(mlUrl.replaceAll("/+$","")+"/internal/v1/tallas/entrenar"))
                .timeout(Duration.ofSeconds(45)).header("Content-Type","application/json").header("X-ML-Token",mlToken)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=http.send(req,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==422)throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Dataset insuficiente o inválido para entrenar las tres clases");
            if(response.statusCode()!=200)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"El servicio ML rechazó el entrenamiento");
            JsonNode result=mapper.readTree(response.body());
            String modelo=result.path("modelo_base64").asText();
            if(!fuente.equals(result.path("fuente").asText())||!Set.of("knn","arbol").contains(result.path("algoritmo").asText())||modelo.isBlank()||modelo.length()>2000000)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Resultado de entrenamiento inválido");
            Long id=db.queryForObject("""
                INSERT INTO monarca.ml_modelos_talla(fuente,algoritmo,modelo_base64,metricas)
                VALUES (?,?,?,CAST(? AS jsonb)) RETURNING id_modelo
                """,Long.class,fuente,result.path("algoritmo").asText(),modelo,result.path("metricas").toString());
            return Map.of("idModelo",id,"fuente",fuente,"algoritmo",result.path("algoritmo").asText(),"metricas",mapper.convertValue(result.path("metricas"),Map.class),"activo",false);
        }catch(ResponseStatusException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Entrenamiento interrumpido");}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo completar el entrenamiento. Verifica el servicio ML.");}
    }
}
