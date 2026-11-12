package com.Monarca.Backend.controller;

import com.Monarca.Backend.dto.PedidoClienteResponseDto;
import com.Monarca.Backend.service.PedidoConsultaClienteService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoClienteController {
    private final PedidoConsultaClienteService consulta;

    public PedidoClienteController(PedidoConsultaClienteService consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/mis-pedidos")
    public List<PedidoClienteResponseDto> misPedidos(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para ver tus pedidos");
        }
        return consulta.listarPorCorreo(autenticacion.getName());
    }
}
