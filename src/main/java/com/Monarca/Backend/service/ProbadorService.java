package com.Monarca.Backend.service;

import com.Monarca.Backend.dto.ProductoResponseDto;
import com.Monarca.Backend.model.FotoProbador;
import com.Monarca.Backend.repository.FotoProbadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Una prueba envía la foto y la prenda al proveedor seleccionado solo tras confirmación del cliente. */
@Service
public class ProbadorService {
 private static final String ORIGEN="https://api.tryonlabs.ai";
 private final String token;
 private final AgalazProbadorService agalaz;
 @Value("${monarca.tryon.provider:agalaz}") private String provider="agalaz";
 // Ajustes de prueba: modificables sin exponer la clave en Angular.
 @Value("${monarca.tryon.resolution:1k}") private String resolution="1k";
 @Value("${monarca.tryon.generation-mode:fast}") private String generationMode="fast";
 private final PerfilService perfiles;
 private final FotoProbadorRepository fotos;
 private final ProductoService productos;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
   .followRedirects(HttpClient.Redirect.NEVER).build();
 private final ObjectMapper json=new ObjectMapper();

 public ProbadorService(@Value("${monarca.tryon.token:}") String token,
     PerfilService perfiles,FotoProbadorRepository fotos,ProductoService productos,
     AgalazProbadorService agalaz){
   this.token=token;this.perfiles=perfiles;this.fotos=fotos;this.productos=productos;this.agalaz=agalaz;
 }
 public String proveedorActual(){
   return "agalaz".equalsIgnoreCase(provider) ? "Agalaz" :
          "tryonlabs".equalsIgnoreCase(provider) ? "TryOn Labs" : "no configurado";
 }
 public byte[] probar(String correo,Long productoId,boolean consentimiento){
   if(!consentimiento)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Debes aceptar enviar tu foto para esta prueba.");
   if(!"agalaz".equalsIgnoreCase(provider)&&!"tryonlabs".equalsIgnoreCase(provider))
     throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
       "Proveedor de probador no admitido. Configura TRYON_PROVIDER como agalaz o tryonlabs.");
   if("tryonlabs".equalsIgnoreCase(provider)&&(token==null||token.isBlank()))
     throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
       "El probador TryOn Labs necesita configurar TRYONLABS_TOKEN en Monarca-back.");
   Long uid=perfiles.usuario(correo).getIdUsuario();
   FotoProbador persona=fotos.findById(uid).orElseThrow(()->new ResponseStatusException(
     HttpStatus.BAD_REQUEST,"Primero sube tu foto desde Mi perfil."));
   ProductoResponseDto producto=productos.buscarCatalogoPorId(productoId)
     .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto no disponible."));
   if(!("Tops".equalsIgnoreCase(producto.getCategoria())
       || "Bottoms".equalsIgnoreCase(producto.getCategoria())))
     throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El probador de prueba solo admite prendas superiores e inferiores.");
   String url=producto.getImagen();
   if(url==null||url.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
     "Esta prenda no tiene una imagen para el probador.");
   try {
     byte[] prenda=descargarPrenda(url);
     String prendaMime=PerfilService.detectarMime(prenda);
     if(prendaMime==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La imagen de la prenda no es JPG, PNG ni WebP.");
     if("agalaz".equalsIgnoreCase(provider))
       return agalaz.generar(persona.getDatos(),prenda);
     String boundary="monarca-"+UUID.randomUUID();
     byte[] body=multipart(boundary,persona,prenda,prendaMime);
     HttpRequest start=HttpRequest.newBuilder(URI.create(ORIGEN+"/api/v1/vton/generate/"))
       .timeout(Duration.ofSeconds(120)).header("Authorization","Bearer "+token)
       .header("Content-Type","multipart/form-data; boundary="+boundary)
       .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
     HttpResponse<byte[]> created=http.send(start,HttpResponse.BodyHandlers.ofByteArray());
     if(created.statusCode()!=200)throw falloProveedor(created.statusCode(), "generación",created.body());
     JsonNode data=json.readTree(created.body());
     String id=data.path("id").asText("");
     if(id.isBlank())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"TryOn Labs respondió sin identificador de resultado. Revisa el formato de respuesta de la API; no repitas la generación hasta comprobar los créditos.");
     String query=URLEncoder.encode(id,StandardCharsets.UTF_8);
     HttpRequest download=HttpRequest.newBuilder(
         URI.create(ORIGEN+"/api/v1/vton/download/?experiment_id="+query))
       .timeout(Duration.ofSeconds(45)).header("Authorization","Bearer "+token).GET().build();
     HttpResponse<byte[]> result=http.send(download,HttpResponse.BodyHandlers.ofByteArray());
     if(result.statusCode()!=200)throw falloProveedor(result.statusCode(), "descarga",result.body());
     if(result.body().length>12_000_000||PerfilService.detectarMime(result.body())==null)
       throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"TryOn Labs devolvió una imagen de resultado no válida o demasiado grande.");
     return result.body();
   }catch(ResponseStatusException e){throw e;}
   catch(InterruptedException e){Thread.currentThread().interrupt();
     throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"La generación fue interrumpida.",e);}
   catch(IOException|IllegalArgumentException e){
     throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"No se pudo conectar o interpretar la respuesta de TryOn Labs. Revisa el registro de Spring Boot.",e);
   }
 }

 private byte[] descargarPrenda(String url)throws IOException,InterruptedException {
   URI uri;
   try{uri=URI.create(url);}catch(IllegalArgumentException e){throw new IOException("URL inválida",e);}
   if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null
        ||uri.getUserInfo()!=null||uri.getPort()>0)
     throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La prenda requiere imagen HTTPS pública.");
   // No acceder a localhost, redes privadas ni enlaces de metadatos de nube.
   for(InetAddress ip:InetAddress.getAllByName(uri.getHost())){
     byte[] raw=ip.getAddress();
     if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isSiteLocalAddress()
       ||ip.isLinkLocalAddress()||ip.isMulticastAddress()
       ||(raw.length==16&&(raw[0]&0xfe)==0xfc))
       throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La imagen debe estar alojada en un servidor público.");
   }
   HttpRequest req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).GET().build();
   HttpResponse<byte[]> result=http.send(req,HttpResponse.BodyHandlers.ofByteArray());
   if(result.statusCode()!=200||result.body().length>5_000_000)
     throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No fue posible descargar la imagen de la prenda (máx. 5 MB).");
   return result.body();
 }
 private byte[] multipart(String b,FotoProbador person,byte[] garment,String garmentMime)throws IOException{
   ByteArrayOutputStream out=new ByteArrayOutputStream();
   parte(out,b,"person_image","persona."+extension(person.getMimeTipo()),person.getMimeTipo(),person.getDatos());
   parte(out,b,"garment_image","prenda."+extension(garmentMime),garmentMime,garment);
   campo(out,b,"resolution",resolution);
   campo(out,b,"generation_mode",generationMode);
   out.write(("--"+b+"--\r\n").getBytes(StandardCharsets.UTF_8));
   return out.toByteArray();
 }
 private void parte(ByteArrayOutputStream out,String b,String field,String filename,String mime,byte[] data)throws IOException {
   out.write(("--"+b+"\r\nContent-Disposition: form-data; name=\""+field+"\"; filename=\""
      +filename+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
   out.write(data);out.write("\r\n".getBytes(StandardCharsets.UTF_8));
 }
 private void campo(ByteArrayOutputStream out,String b,String field,String value)throws IOException {
   out.write(("--"+b+"\r\nContent-Disposition: form-data; name=\""+field+
     "\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));
 }
 private static String extension(String mime) {
   return "image/png".equals(mime) ? "png" : "image/webp".equals(mime) ? "webp" : "jpg";
 }

 /**
  * Solo presenta detalles de validación de campos conocidos. Nunca devuelve imágenes,
  * tokens, URLs ni el cuerpo completo de la respuesta del proveedor.
  */
 private String erroresValidacion(byte[] respuesta) {
   if(respuesta==null || respuesta.length==0 || respuesta.length>16_384)return "";
   try {
     JsonNode root=json.readTree(respuesta);
     if(root==null || !root.isObject())return "";
     String[] campos={"person_image","person_image_id","garment_image","garment_image_id",
       "resolution","generation_mode","num_samples","category","detail","non_field_errors","error","message"};
     java.util.List<String> errores=new java.util.ArrayList<>();
     for(String campo:campos){
       JsonNode node=root.path(campo);
       if(node.isMissingNode()||node.isNull())continue;
       if(node.isArray() && !node.isEmpty())node=node.get(0);
       if(!node.isTextual())continue;
       String valor=node.asText().replaceAll("[\\r\\n\\t]+"," ");
       valor=valor.replaceAll("(?i)Bearer\\s+[^\\s,;]+","[credencial]")
         .replaceAll("https?://[^\\s,;]+","[URL]")
         .replaceAll("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}","[correo]")
         .replaceAll("[A-Za-z0-9_./+=-]{65,}","[dato]");
       if(valor.length()>130)valor=valor.substring(0,130)+"…";
       if(!valor.isBlank())errores.add(campo+": "+valor);
       if(errores.size()>=3)break;
     }
     return String.join("; ",errores);
   }catch(Exception ex){return "";}
 }

 private ResponseStatusException falloProveedor(int code,String etapa,byte[] respuesta){
   // El endpoint de generación solo cobra créditos tras una generación exitosa,
   // pero un error en la descarga podría ocurrir después de cobrarla.
   if(code==400||code==422){
     String detalle=erroresValidacion(respuesta);
     return new ResponseStatusException(HttpStatus.BAD_REQUEST,
       "TryOn Labs rechazó los datos de "+etapa+" (HTTP "+code+"). "
       +(detalle.isBlank()?"Revisa la respuesta original del proveedor.":detalle));
   }
   if(code==402||code==429)
     return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
       "TryOn Labs indicó créditos insuficientes o límite de solicitudes (HTTP "+code+").");
   if(code==401||code==403)
     return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
       "TryOn Labs rechazó el token de acceso (HTTP "+code+").");
   return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
     "TryOn Labs devolvió HTTP "+code+" durante la "+etapa+". No se puede determinar si se descontaron créditos.");
 }
}
