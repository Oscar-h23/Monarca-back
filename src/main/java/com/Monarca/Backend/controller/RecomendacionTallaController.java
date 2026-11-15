package com.Monarca.Backend.controller;

import com.Monarca.Backend.model.Producto;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.model.VarianteProducto;
import com.Monarca.Backend.repository.ProductoRepository;
import com.Monarca.Backend.repository.UsuarioRepository;
import com.Monarca.Backend.repository.VarianteProductoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.time.Duration;
import java.util.*;

/**
 * Accede a los datos actuales de Supabase por los repositorios JPA existentes.
 * No admite medidas ni variantes suministradas por el navegador.
 */
@RestController
@RequestMapping("/api/perfil/tallas")
public class RecomendacionTallaController {
    @Autowired private JdbcTemplate db;
    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final VarianteProductoRepository variantes;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    @Value("${monarca.ml.url:}") private String mlUrl;
    @Value("${monarca.ml.internal-token:}") private String mlToken;

    public RecomendacionTallaController(UsuarioRepository usuarios,
                                       ProductoRepository productos,
                                       VarianteProductoRepository variantes,
                                       ObjectMapper json) {
        this.usuarios=usuarios;this.productos=productos;this.variantes=variantes;this.json=json;
    }

    @GetMapping("/{idProducto}")
    @Transactional(readOnly=true)
    public ResponseEntity<Map<String,Object>> recomendar(@PathVariable Long idProducto,
                                                           Principal principal) {
        if (principal == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión.");
        Usuario u=usuarios.findByCorreoIgnoreCase(principal.getName())
                .filter(x->Boolean.TRUE.equals(x.getActivo()))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Cuenta no disponible."));
        Producto p=productos.findById(idProducto)
                .filter(x->Boolean.TRUE.equals(x.getActivo())
                        && Boolean.TRUE.equals(x.getCategoria().getActivo()))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto no disponible."));
        String categoria=p.getCategoria().getNombre();
        List<String> requeridas=switch(categoria) {
            case "Tops" -> List.of("pecho","cintura");
            case "Bottoms" -> List.of("cintura","cadera");
            case "Vestidos" -> List.of("pecho","cintura","cadera");
            default -> List.of();
        };
        if(requeridas.isEmpty())
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("recomendacion",Map.of(),"disponible",false,"motivo","Categoría no compatible."));
        Map<String,BigDecimal> medidas=new LinkedHashMap<>();
        medidas.put("pecho",u.getBustoCm());
        medidas.put("cintura",u.getCinturaCm());
        medidas.put("cadera",u.getCaderaCm());
        if(requeridas.stream().anyMatch(k->medidas.get(k)==null))
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("recomendacion",Map.of(),"disponible",false,"motivo","Completa tus medidas en el perfil."));
        List<Map<String,Object>> opciones=new ArrayList<>();
        for(VarianteProducto v:variantes.findByProducto_IdProductoAndActivoTrue(idProducto)){
            if(v.getStock()==null||v.getStock()<=0)continue;
            Map<String,Object> fila=new LinkedHashMap<>();
            fila.put("id_variante",v.getIdVariante());fila.put("talla",v.getTalla());
            fila.put("stock",v.getStock());fila.put("pecho",v.getPechoCm());
            fila.put("cintura",v.getCinturaCm());fila.put("cadera",v.getCaderaCm());
            opciones.add(fila);
        }
        if(mlUrl==null||mlUrl.isBlank()||mlToken==null||mlToken.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"El servicio de tallas no está configurado.");
        try {
            String url=mlUrl.stripTrailing().replaceAll("/+$","")
                    + "/internal/v1/tallas/recomendar";
            Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("categoria",categoria);
            payload.put("medidas",medidas);
            payload.put("variantes",opciones);
            List<Map<String,String>> activo=db.query(
                "SELECT fuente,modelo_base64 FROM monarca.ml_modelos_talla WHERE activo=true LIMIT 1",
                (rs,n)->Map.of("fuente",rs.getString(1),"modelo_base64",rs.getString(2)));
            boolean experimental=!activo.isEmpty();
            payload.put("metodo",experimental?"supervisado":"reglas");
            if(experimental) payload.put("modelo_base64",activo.get(0).get("modelo_base64"));
            HttpRequest request=HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type","application/json")
                    .header("X-ML-Token",mlToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"No se pudo consultar el recomendador.");
            JsonNode root=json.readTree(response.body());
            JsonNode resultado=root.path("recomendacion");
            if(resultado.isMissingNode()||resultado.isNull())
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(Map.of("recomendacion",Map.of(),"disponible",false,
                                "experimental",experimental,
                                "motivo",root.path("motivo").asText("No hay talla compatible.")));
            long id=resultado.path("variante_id").asLong(-1);
            boolean valida=opciones.stream().anyMatch(v->((Long)v.get("id_variante"))==id);
            if(!valida)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Respuesta ML inválida.");
            Map<String,Object> salida=new LinkedHashMap<>();
            salida.put("disponible",true);
            salida.put("idVariante",id);
            salida.put("talla",resultado.path("talla").asText());
            salida.put("metodo",resultado.path("metodo").asText());
            salida.put("holgurasCm",json.convertValue(resultado.path("holguras_cm"),Map.class));
            salida.put("experimental",experimental);
            salida.put("fuenteModelo",experimental?activo.get(0).get("fuente"):"REGLAS");
            salida.put("aviso",experimental
                ? "Predicción experimental; no validada con compradores reales."
                : "Sugerencia orientativa basada en medidas de la prenda.");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(salida);
        } catch(ResponseStatusException e) {throw e;}
          catch(Exception e) {
            // No filtrar a clientes URL, credenciales, stacktraces ni datos del perfil.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El recomendador no está disponible por el momento.");
        }
    }
}
