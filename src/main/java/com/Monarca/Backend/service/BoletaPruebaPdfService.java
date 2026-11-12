package com.Monarca.Backend.service;

import com.Monarca.Backend.model.DetallePedido;
import com.Monarca.Backend.model.Pedido;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Genera un documento demostrativo. No asigna serie fiscal, no firma ni comunica a SUNAT. */
@Service
public class BoletaPruebaPdfService {
    private static final PDType1Font NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));

    public byte[] generar(Pedido pedido, List<DetallePedido> items) {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream dibujo = new PDPageContentStream(documento, pagina);
            float y = cabecera(dibujo, pedido);
            for (DetallePedido item : items) {
                if (y < 135) {
                    dibujo.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    dibujo = new PDPageContentStream(documento, pagina);
                    y = cabecera(dibujo, pedido);
                }
                linea(dibujo, abreviar(item.getNombreProducto(), 44), 9, 48, y, NORMAL);
                linea(dibujo, item.getTalla() == null ? "-" : abreviar(item.getTalla(), 6), 9, 328, y, NORMAL);
                linea(dibujo, String.valueOf(item.getCantidad()), 9, 382, y, NORMAL);
                linea(dibujo, dinero(item.getPrecioUnitario()), 9, 415, y, NORMAL);
                linea(dibujo, dinero(item.getSubtotal()), 9, 500, y, NORMAL);
                y -= 24;
            }
            if (y < 130) {
                dibujo.close();
                pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                dibujo = new PDPageContentStream(documento, pagina);
                y = cabecera(dibujo, pedido);
            }
            y -= 14;
            linea(dibujo, "Subtotal: " + dinero(pedido.getSubtotal()), 11, 380, y, NORMAL);
            y -= 20;
            linea(dibujo, "Descuento: " + dinero(pedido.getDescuento()), 10, 380, y, NORMAL);
            y -= 20;
            linea(dibujo, "Envio: " + dinero(pedido.getCostoEnvio()), 10, 380, y, NORMAL);
            y -= 27;
            linea(dibujo, "TOTAL: " + dinero(pedido.getTotal()), 13, 380, y, BOLD);
            y -= 46;
            linea(dibujo, "Pago verificado manualmente por la tienda.", 10, 48, y, NORMAL);
            y -= 20;
            linea(dibujo, "Este PDF es solo para demostracion y pruebas internas.", 10, 48, y, NORMAL);
            dibujo.close();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el PDF de prueba.", ex);
        }
    }

    private static float cabecera(PDPageContentStream dibujo, Pedido pedido) throws IOException {
        float y = 790;
        linea(dibujo, "NAYELI'S MODA", 21, 48, y, BOLD);
        y -= 27;
        linea(dibujo, "BOLETA SIMULADA DE COMPRA", 16, 48, y, BOLD);
        y -= 23;
        linea(dibujo, "DOCUMENTO DE PRUEBA - SIN VALIDEZ TRIBUTARIA - NO SUNAT", 11, 48, y, BOLD);
        y -= 18;
        linea(dibujo, "NO ES BOLETA ELECTRONICA NI COMPROBANTE FISCAL", 10, 48, y, BOLD);
        y -= 38;
        linea(dibujo, "Codigo interno de pedido: " + seguro(pedido.getCodigoPedido()), 10, 48, y, NORMAL);
        y -= 19;
        linea(dibujo, "Fecha de compra: " + FECHA.format(pedido.getFechaPedido()), 10, 48, y, NORMAL);
        y -= 19;
        String cliente = pedido.getUsuario().getNombres() + " " + pedido.getUsuario().getApellidos();
        linea(dibujo, "Cliente: " + abreviar(cliente, 77), 10, 48, y, NORMAL);
        y -= 22;
        linea(dibujo, "Estado de pago: CONFIRMADO MANUALMENTE", 10, 48, y, NORMAL);
        y -= 35;
        linea(dibujo, "PRODUCTO", 10, 48, y, BOLD);
        linea(dibujo, "TALLA", 10, 328, y, BOLD);
        linea(dibujo, "CANT.", 10, 382, y, BOLD);
        linea(dibujo, "P.U.", 10, 415, y, BOLD);
        linea(dibujo, "IMPORTE", 10, 500, y, BOLD);
        return y - 28;
    }

    private static String dinero(BigDecimal monto) {
        return "S/ " + (monto == null ? "0.00" : monto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    private static String seguro(String entrada) {
        if (entrada == null) return "-";
        return Normalizer.normalize(entrada, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static String abreviar(String texto, int max) {
        String limpio = seguro(texto);
        return limpio.length() <= max ? limpio : limpio.substring(0, max - 3) + "...";
    }

    private static void linea(PDPageContentStream dibujo, String texto, int tam,
                              int x, float y, PDType1Font fuente) throws IOException {
        dibujo.beginText();
        dibujo.setFont(fuente, tam);
        dibujo.newLineAtOffset(x, y);
        dibujo.showText(seguro(texto));
        dibujo.endText();
    }
}
