package com.Monarca.Backend.controller;

import com.Monarca.Backend.dto.PedidoRequestDto;
import com.Monarca.Backend.model.Pedido;
import com.Monarca.Backend.model.Pago;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.PedidoRepository;
import com.Monarca.Backend.repository.PagoRepository;
import com.Monarca.Backend.repository.UsuarioRepository;
import com.Monarca.Backend.service.PedidoService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PagoRepository pagoRepository;


    // =========================================================
    // LISTAR PEDIDOS
    // =========================================================

    @GetMapping
    public ResponseEntity<?> listarTodosLosPedidos() {
        List<Pedido> lista = pedidoRepository.findAll();
        List<Long> ids = lista.stream().map(Pedido::getIdPedido).toList();
        Map<Long, Pago> pagosPorPedido = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Pago pago : pagoRepository.findByPedido_IdPedidoIn(ids)) {
                pagosPorPedido.merge(pago.getPedido().getIdPedido(), pago,
                        (anterior, siguiente) -> anterior.getIdPago() > siguiente.getIdPago() ? anterior : siguiente);
            }
        }
        List<Map<String, Object>> respuesta = lista.stream().map(pedido -> {
            Pago pago = pagosPorPedido.get(pedido.getIdPedido());
            Map<String, Object> fila = new HashMap<>();
            fila.put("idPedido", pedido.getIdPedido());
            fila.put("codigoPedido", pedido.getCodigoPedido());
            fila.put("estado", pedido.getEstado());
            fila.put("subtotal", pedido.getSubtotal());
            fila.put("total", pedido.getTotal());
            fila.put("cliente", pedido.getUsuario().getCorreo());
            fila.put("fechaPedido", pedido.getFechaPedido());
            fila.put("estadoPago", pago != null ? pago.getEstado() : "PENDIENTE");
            fila.put("evidenciaDisponible", pago != null && pago.getComprobanteUrl() != null && !pago.getComprobanteUrl().isBlank());
            fila.put("motivoRechazo", pago != null && "RECHAZADO".equals(pago.getEstado()) ? pago.getObservacion() : null);
            fila.put("boletaDisponible", pago != null && "PAGADO".equals(pago.getEstado()));
            return fila;
        }).toList();
        return ResponseEntity.ok(respuesta);
    }

    // =========================================================
    // =========================================================
    // CREAR PEDIDO
    // =========================================================

    @PostMapping("/procesar")
    public ResponseEntity<?> crearPedido(
            @RequestBody PedidoRequestDto pedidoRequest,
            Authentication authentication
    ) {

        try {

            if (authentication == null
                    || !authentication.isAuthenticated()) {

                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(
                                Map.of(
                                        "error",
                                        "Usuario no autenticado"
                                )
                        );
            }


            // Usuario obtenido del JWT
            String correoCliente =
                    authentication.getName();


            Usuario usuario =
                    usuarioRepository
                            .findByCorreoIgnoreCase(
                                    correoCliente
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Cliente no encontrado"
                                    )
                            );


            // Nunca confiamos en el idUsuario
            // enviado por el navegador.
            pedidoRequest.setIdUsuario(
                    usuario.getIdUsuario()
            );


            Pedido pedidoGuardado =
                    pedidoService.procesarCompra(
                            pedidoRequest
                    );


            Map<String, Object> response =
                    Map.of(
                            "mensaje",
                            "Pedido registrado correctamente",

                            "idPedido",
                            pedidoGuardado.getIdPedido(),

                            "codigoPedido",
                            pedidoGuardado.getCodigoPedido(),

                            "estado",
                            pedidoGuardado.getEstado(),

                            "total",
                            pedidoGuardado.getTotal()
                    );


            return ResponseEntity.ok(
                    response
            );


        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }
}