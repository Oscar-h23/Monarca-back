package com.Monarca.Backend.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbadorCloudinaryTest {
  @Test
  void convierteImagenPublicaWebpEnJpeg() {
    String anterior="https://res.cloudinary.com/cbcl4rt8/image/upload/v1792260872/top_verde_ns9xoz.webp";
    assertEquals("https://res.cloudinary.com/cbcl4rt8/image/upload/f_jpg/v1792260872/top_verde_ns9xoz.jpg",
       ProbadorService.urlCloudinaryJpg(anterior));
  }
  @Test
  void noReescribeDominiosAjenosNiJpg() {
    String ajeno="https://ejemplo.net/image/upload/v1/prenda.webp";
    assertEquals(ajeno, ProbadorService.urlCloudinaryJpg(ajeno));
    String jpg="https://res.cloudinary.com/cbcl4rt8/image/upload/v1/prenda.jpg";
    assertEquals(jpg, ProbadorService.urlCloudinaryJpg(jpg));
  }
}
