package com.Monarca.Backend.service;

import com.Monarca.Backend.model.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BoletaPruebaPdfServiceTest {
    @Test void creaPDFConAdvertenciaNoSunatYValoresRealesDelPedido() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNombres("Ana");
        usuario.setApellidos("Cliente");
        Pedido pedido = new Pedido();
        pedido.setCodigoPedido("MON-PRUEBA-123");
        pedido.setUsuario(usuario);
        pedido.setFechaPedido(OffsetDateTime.parse("2026-11-12T17:00:00-05:00"));
        pedido.setSubtotal(new BigDecimal("79.80"));
        pedido.setDescuento(BigDecimal.ZERO);
        pedido.setCostoEnvio(BigDecimal.ZERO);
        pedido.setTotal(new BigDecimal("79.80"));
        DetallePedido detalle = new DetallePedido();
        detalle.setNombreProducto("Blusa crema");
        detalle.setTalla("M");
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(new BigDecimal("39.90"));
        detalle.setSubtotal(new BigDecimal("79.80"));
        byte[] archivo = new BoletaPruebaPdfService().generar(pedido,List.of(detalle));
        assertEquals("%PDF",new String(archivo,0,4,java.nio.charset.StandardCharsets.US_ASCII));
        try(var documento = Loader.loadPDF(archivo)) {
            String texto = new PDFTextStripper().getText(documento);
            assertTrue(texto.contains("NO SUNAT"));
            assertTrue(texto.contains("SIN VALIDEZ TRIBUTARIA"));
            assertTrue(texto.contains("MON-PRUEBA-123"));
            assertTrue(texto.contains("79.80"));
        }
    }
}
