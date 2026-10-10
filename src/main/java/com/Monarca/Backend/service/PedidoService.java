package com.Monarca.Backend.service;

import com.Monarca.Backend.dto.ItemCarritoDto;
import com.Monarca.Backend.dto.PedidoRequestDto;
import com.Monarca.Backend.model.DetallePedido;
import com.Monarca.Backend.model.MetodoPago;
import com.Monarca.Backend.model.Pago;
import com.Monarca.Backend.model.Pedido;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.model.VarianteProducto;
import com.Monarca.Backend.repository.DetallePedidoRepository;
import com.Monarca.Backend.repository.MetodoPagoRepository;
import com.Monarca.Backend.repository.PagoRepository;
import com.Monarca.Backend.repository.PedidoRepository;
import com.Monarca.Backend.repository.UsuarioRepository;
import com.Monarca.Backend.repository.VarianteProductoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    @Autowired
    private VarianteProductoRepository varianteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MetodoPagoRepository metodoPagoRepository;

    @Autowired
    private PagoRepository pagoRepository;


    @Transactional
    public Pedido procesarCompra(
            PedidoRequestDto pedidoDto
    ) {

        validarPedido(pedidoDto);


        // =====================================================
        // 1. USUARIO
        // =====================================================

        Usuario usuario =
                usuarioRepository
                        .findById(
                                pedidoDto.getIdUsuario()
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Usuario no encontrado"
                                )
                        );


        // =====================================================
        // 2. MÉTODO DE PAGO
        // =====================================================

        MetodoPago metodoPago =
                metodoPagoRepository
                        .findById(
                                pedidoDto.getIdMetodoPago()
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Método de pago no encontrado"
                                )
                        );


        if (!Boolean.TRUE.equals(
                metodoPago.getActivo()
        )) {

            throw new RuntimeException(
                    "El método de pago no está disponible"
            );
        }


        // =====================================================
        // 3. PROCESAR ITEMS
        // =====================================================

        BigDecimal subtotalPedido =
                BigDecimal.ZERO;

        List<LineaTemporal> lineas =
                new ArrayList<>();


        for (ItemCarritoDto item : pedidoDto.getItems()) {
            if (item == null || item.getIdVariante() == null || item.getIdVariante() <= 0)
                throw new IllegalArgumentException("El item debe contener idVariante válido");
        }
        List<ItemCarritoDto> itemsOrdenados = pedidoDto.getItems().stream()
                .sorted(java.util.Comparator.comparing(ItemCarritoDto::getIdVariante)).toList();
        for (ItemCarritoDto item : itemsOrdenados) {

            if (item.getCantidad() == null
                    || item.getCantidad() <= 0) {

                throw new RuntimeException(
                        "Cantidad inválida en el carrito"
                );
            }


            VarianteProducto variante =
                    obtenerVariante(item);


            if (!Boolean.TRUE.equals(
                    variante.getActivo()
            ) || !Boolean.TRUE.equals(variante.getProducto().getActivo())) {

                throw new RuntimeException(
                        "La variante "
                                + variante.getSku()
                                + " no está disponible"
                );
            }


            if (variante.getStock()
                    < item.getCantidad()) {

                throw new RuntimeException(
                        "Stock insuficiente para: "
                                + variante
                                .getProducto()
                                .getNombre()
                                + " - "
                                + variante.getTalla()
                                + " / "
                                + variante.getColor()
                );
            }


            BigDecimal precioUnitario =
                    variante.getPrecio();


            BigDecimal subtotalLinea =
                    precioUnitario.multiply(
                            BigDecimal.valueOf(
                                    item.getCantidad()
                            )
                    );


            // Descontar stock
            variante.setStock(
                    variante.getStock()
                            - item.getCantidad()
            );

            varianteRepository.save(variante);


            subtotalPedido =
                    subtotalPedido.add(
                            subtotalLinea
                    );


            lineas.add(
                    new LineaTemporal(
                            variante,
                            item.getCantidad(),
                            precioUnitario,
                            subtotalLinea
                    )
            );
        }


        // =====================================================
        // 4. CREAR PEDIDO
        // =====================================================

        Pedido pedido = new Pedido();

        pedido.setUsuario(usuario);
        pedido.setObservacion(pedidoDto.getObservacion());

        pedido.setCodigoPedido(
                generarCodigoPedido()
        );

        pedido.setEstado(
                "PENDIENTE_PAGO"
        );

        pedido.setSubtotal(
                subtotalPedido
        );

        pedido.setDescuento(
                BigDecimal.ZERO
        );

        pedido.setCostoEnvio(
                BigDecimal.ZERO
        );

        pedido.setTotal(
                subtotalPedido
        );


        pedido =
                pedidoRepository.save(pedido);


        // =====================================================
        // 5. DETALLES DEL PEDIDO
        // =====================================================

        for (LineaTemporal linea : lineas) {

            VarianteProducto variante =
                    linea.variante();

            DetallePedido detalle =
                    new DetallePedido();

            detalle.setPedido(pedido);

            detalle.setVariante(
                    variante
            );

            detalle.setCantidad(
                    linea.cantidad()
            );

            detalle.setPrecioUnitario(
                    linea.precioUnitario()
            );

            detalle.setSubtotal(
                    linea.subtotal()
            );

            // Snapshot
            detalle.setNombreProducto(
                    variante
                            .getProducto()
                            .getNombre()
            );

            detalle.setSku(
                    variante.getSku()
            );

            detalle.setTalla(
                    variante.getTalla()
            );

            detalle.setColor(
                    variante.getColor()
            );


            detallePedidoRepository.save(
                    detalle
            );
        }


        // =====================================================
        // 6. REGISTRO DEL PAGO
        // =====================================================

        Pago pago = new Pago();

        pago.setPedido(pedido);

        pago.setMetodoPago(
                metodoPago
        );

        pago.setMonto(
                pedido.getTotal()
        );

        pago.setEstado(
                "PENDIENTE"
        );


        pagoRepository.save(pago);


        return pedido;
    }


    private VarianteProducto obtenerVariante(ItemCarritoDto item) {
        return varianteRepository.findByIdForUpdate(item.getIdVariante())
                .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada: " + item.getIdVariante()));
    }

    private void validarPedido(
            PedidoRequestDto dto
    ) {

        if (dto == null) {
            throw new RuntimeException(
                    "Pedido inválido"
            );
        }


        if (dto.getObservacion() != null && dto.getObservacion().length() > 500) {
            throw new IllegalArgumentException("Los datos de entrega exceden 500 caracteres");
        }
        if (dto.getIdUsuario() == null) {

            throw new RuntimeException(
                    "Usuario requerido"
            );
        }


        if (dto.getIdMetodoPago() == null) {

            throw new RuntimeException(
                    "Método de pago requerido"
            );
        }


        if (dto.getItems() == null
                || dto.getItems().isEmpty()) {

            throw new RuntimeException(
                    "El carrito está vacío"
            );
        }
    }


    private String generarCodigoPedido() {

        String aleatorio =
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();

        return "MON-"
                + System.currentTimeMillis()
                + "-"
                + aleatorio;
    }


    private record LineaTemporal(
            VarianteProducto variante,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal
    ) {
    }
}