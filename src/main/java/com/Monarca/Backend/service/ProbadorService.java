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

/** Una prueba envía la foto y la prenda a TryOn Labs solo tras confirmación del cliente. */
@Service
public class ProbadorService {
 private static final String ORIGEN="https://api.tryonlabs.ai";
 private final String token;
 private final PerfilService perfiles;
 private final FotoProbadorRepository fotos;
 private final ProductoService productos;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
   .followRedirects(HttpClient.Redirect.NEVER).build();
 private final ObjectMapper json=new ObjectMapper();

 public ProbadorService(@Value("${monarca.tryon.token:}") String token,
     PerfilService perfiles,FotoProbadorRepository fotos,ProductoService productos){
   this.token=token;this.perfiles=perfiles;this.fotos=fotos;this.productos=productos;
 }
 public byte[] probar(String correo,Long productoId,boolean consentimiento){
   if(!consentimiento)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Debes aceptar enviar tu foto para esta prueba.");
   if(token==null||token.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
     "El probador aún no tiene token. Configura TRYONLABS_TOKEN en Monarca-back.");
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
     String boundary="monarca-"+UUID.randomUUID();
     byte[] body=multipart(boundary,persona,prenda,prendaMime);
     HttpRequest start=HttpRequest.newBuilder(URI.create(ORIGEN+"/api/v1/vton/generate/"))
       .timeout(Duration.ofSeconds(120)).header("Authorization","Bearer "+token)
       .header("Content-Type","multipart/form-data; boundary="+boundary)
       .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
     HttpResponse<byte[]> created=http.send(start,HttpResponse.BodyHandlers.ofByteArray());
     if(created.statusCode()!=200)throw falloProveedor(created.statusCode());
     JsonNode data=json.readTree(created.body());
     String id=data.path("id").asText("");
     if(id.isBlank())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"El proveedor no devolvió un identificador de resultado.");
     String query=URLEncoder.encode(id,StandardCharsets.UTF_8);
     HttpRequest download=HttpRequest.newBuilder(
         URI.create(ORIGEN+"/api/v1/vton/download/?experiment_id="+query))
       .timeout(Duration.ofSeconds(45)).header("Authorization","Bearer "+token).GET().build();
     HttpResponse<byte[]> result=http.send(download,HttpResponse.BodyHandlers.ofByteArray());
     if(result.statusCode()!=200)throw falloProveedor(result.statusCode());
     if(result.body().length>12_000_000||PerfilService.detectarMime(result.body())==null)
       throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"La API devolvió un formato de imagen no admitido.");
     return result.body();
   }catch(ResponseStatusException e){throw e;}
   catch(InterruptedException e){Thread.currentThread().interrupt();
     throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"La generación fue interrumpida.",e);}
   catch(IOException|IllegalArgumentException e){
     throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"No se pudo procesar el probador virtual.",e);
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
   parte(out,b,"person_image","persona.jpg",person.getMimeTipo(),person.getDatos());
   parte(out,b,"garment_image","prenda.jpg",garmentMime,garment);
   campo(out,b,"resolution","2k");
   campo(out,b,"generation_mode","quality");
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
 private ResponseStatusException falloProveedor(int code){
   if(code==400||code==402||code==429)
     return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
       "No hay créditos suficientes o se alcanzó el límite de pruebas del proveedor.");
   if(code==401||code==403)
     return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
       "El token del probador virtual no es válido o expiró.");
   return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
       "El proveedor del probador no está disponible (HTTP "+code+").");
 }
}
