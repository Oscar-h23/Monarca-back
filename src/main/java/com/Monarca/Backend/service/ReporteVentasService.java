package com.Monarca.Backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Indicadores basados en pagos confirmados, no en pedidos meramente registrados.
 * El filtro por fechas utiliza la fecha de creación del pedido en horario de Lima.
 */
@Service
public class ReporteVentasService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final NamedParameterJdbcTemplate db;

    public ReporteVentasService(NamedParameterJdbcTemplate db) { this.db = db; }

    public record Kpis(BigDecimal ventasConfirmadas, long pedidosConfirmados,
                       long pedidosRegistrados, long pagosEnRevision, long unidadesVendidas,
                       BigDecimal ticketPromedio, BigDecimal costoMercaderia,
                       BigDecimal gananciaVentaMenosCompra, long partidasSinCosto) {}
    public record VentaDia(String fecha, BigDecimal importe, long pedidos) {}
    public record FormaPago(String nombre, BigDecimal importe, long pedidos) {}
    public record Estado(String estado, long cantidad) {}
    public record ProductoTop(String nombre, long unidades, BigDecimal importe) {}
    public record Resumen(String desde, String hasta, Kpis kpis, List<VentaDia> ventasDiarias,
                          List<FormaPago> mediosPago, List<Estado> estados, List<ProductoTop> topProductos) {}

    public Resumen consultar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta)
                || ChronoUnit.DAYS.between(desde, hasta) > 365)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selecciona fechas válidas, con un máximo de 366 días.");
        OffsetDateTime inicio = desde.atStartOfDay(LIMA).toOffsetDateTime();
        OffsetDateTime fin = hasta.plusDays(1).atStartOfDay(LIMA).toOffsetDateTime();
        Map<String, Object> parametros = Map.of("inicio", inicio, "fin", fin);
        String filtro = "p.fecha_pedido >= :inicio AND p.fecha_pedido < :fin";
        String pagado = "EXISTS (SELECT 1 FROM monarca.pagos pg WHERE pg.id_pedido=p.id_pedido"
                + " AND pg.estado='PAGADO')";
        String revision = "EXISTS (SELECT 1 FROM monarca.pagos pg WHERE pg.id_pedido=p.id_pedido AND pg.estado='EN_REVISION')";
        String totales = "SELECT COUNT(*) AS registrados,"
                + " COUNT(*) FILTER (WHERE " + pagado + ") AS confirmados,"
                + " COUNT(*) FILTER (WHERE " + revision + ") AS revision,"
                + " COALESCE(SUM(p.total) FILTER (WHERE " + pagado + "),0) AS ventas"
                + " FROM monarca.pedidos p WHERE " + filtro;
        Kpis parciales = db.queryForObject(totales, parametros, (rs,i) -> {
            long pagados = rs.getLong("confirmados");
            BigDecimal venta = rs.getBigDecimal("ventas");
            return new Kpis(venta, pagados, rs.getLong("registrados"), rs.getLong("revision"),
                    0, pagados == 0 ? BigDecimal.ZERO
                            : venta.divide(BigDecimal.valueOf(pagados),2,RoundingMode.HALF_UP),
                    BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        });
        if (parciales == null) throw new IllegalStateException("No se pudieron calcular métricas.");

        Long unidades = db.queryForObject(
                "SELECT COALESCE(SUM(d.cantidad),0) FROM monarca.detalles_pedido d"
                + " JOIN monarca.pedidos p ON p.id_pedido=d.id_pedido WHERE " + filtro
                + " AND " + pagado,
                parametros, Long.class);

        // El costo de compra registrado actualmente es un valor estimado y no un
        // histórico congelado al momento de la venta. No duplicar pedidos por pagos.
        // Se restringe a las mismas ventas con pago confirmado del reporte.
        record CostoVentas(BigDecimal importe, long sinCosto) {}
        CostoVentas costoVentas = db.queryForObject(
                "SELECT COALESCE(SUM(d.cantidad * pr.costo_compra),0) AS importe,"
                + " COUNT(*) FILTER (WHERE pr.costo_compra IS NULL) AS sin_costo"
                + " FROM monarca.detalles_pedido d"
                + " JOIN monarca.pedidos p ON p.id_pedido=d.id_pedido"
                + " JOIN monarca.variantes_producto v ON v.id_variante=d.id_variante"
                + " JOIN monarca.productos pr ON pr.id_producto=v.id_producto"
                + " WHERE " + filtro + " AND " + pagado,
                parametros, (rs,i) -> new CostoVentas(rs.getBigDecimal("importe"),rs.getLong("sin_costo")));
        if (costoVentas == null) throw new IllegalStateException("No se pudieron consultar los costos.");
        BigDecimal ganancia = costoVentas.sinCosto() == 0
                ? parciales.ventasConfirmadas().subtract(costoVentas.importe()) : null;

        Kpis kpis = new Kpis(parciales.ventasConfirmadas(),parciales.pedidosConfirmados(),
                parciales.pedidosRegistrados(),parciales.pagosEnRevision(),
                unidades == null ? 0 : unidades,parciales.ticketPromedio(),
                costoVentas.importe(),ganancia,costoVentas.sinCosto());

        List<VentaDia> presentes = db.query(
                "SELECT to_char((p.fecha_pedido AT TIME ZONE 'America/Lima')::date,'YYYY-MM-DD') AS dia,"
                + " COALESCE(SUM(p.total) FILTER (WHERE " + pagado + "),0) AS venta,"
                + " COUNT(*) FILTER (WHERE " + pagado + ") AS pedidos"
                + " FROM monarca.pedidos p WHERE " + filtro + " GROUP BY dia ORDER BY dia",
                parametros, (rs,i) -> new VentaDia(rs.getString("dia"),rs.getBigDecimal("venta"),rs.getLong("pedidos")));
        Map<String,VentaDia> porFecha = new HashMap<>();
        for (VentaDia d : presentes) porFecha.put(d.fecha(),d);
        List<VentaDia> dias = desde.datesUntil(hasta.plusDays(1)).map(d ->
                porFecha.getOrDefault(d.toString(),new VentaDia(d.toString(),BigDecimal.ZERO,0))).toList();

        List<FormaPago> medios = db.query(
                "SELECT m.nombre AS nombre,COALESCE(SUM(p.total),0) AS ventas,COUNT(DISTINCT p.id_pedido) AS pedidos"
                + " FROM monarca.pagos pg JOIN monarca.pedidos p ON p.id_pedido=pg.id_pedido"
                + " JOIN monarca.metodos_pago m ON m.id_metodo_pago=pg.id_metodo_pago"
                + " WHERE " + filtro + " AND pg.estado='PAGADO'"
                + " GROUP BY m.nombre ORDER BY ventas DESC",
                parametros, (rs,i) -> new FormaPago(rs.getString("nombre"),rs.getBigDecimal("ventas"),rs.getLong("pedidos")));

        List<Estado> estados = db.query(
                "SELECT p.estado,COUNT(*) AS cantidad FROM monarca.pedidos p WHERE " + filtro
                + " GROUP BY p.estado ORDER BY cantidad DESC",
                parametros, (rs,i) -> new Estado(rs.getString("estado"),rs.getLong("cantidad")));

        List<ProductoTop> top = db.query(
                "SELECT d.nombre_producto AS nombre,SUM(d.cantidad) AS unidades,"
                + " COALESCE(SUM(d.subtotal),0) AS importe"
                + " FROM monarca.detalles_pedido d JOIN monarca.pedidos p ON p.id_pedido=d.id_pedido"
                + " WHERE " + filtro + " AND " + pagado
                + " GROUP BY d.nombre_producto ORDER BY unidades DESC, importe DESC LIMIT 6",
                parametros, (rs,i) -> new ProductoTop(rs.getString("nombre"),rs.getLong("unidades"),
                        rs.getBigDecimal("importe")));
        return new Resumen(desde.toString(),hasta.toString(),kpis,dias,medios,estados,top);
    }
}
