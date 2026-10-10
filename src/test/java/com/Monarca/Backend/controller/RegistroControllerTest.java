package com.Monarca.Backend.controller;

import com.Monarca.Backend.dto.RegistroDto;
import com.Monarca.Backend.model.Rol;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistroControllerTest {
    @Test void registroSiempreClienteConPasswordHash() {
        AuthController controller = new AuthController();
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        RolRepository roles = mock(RolRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        ReflectionTestUtils.setField(controller, "usuarioRepository", usuarios);
        ReflectionTestUtils.setField(controller, "rolRepository", roles);
        ReflectionTestUtils.setField(controller, "passwordEncoder", encoder);
        Rol cliente = new Rol(); cliente.setNombre("CLIENTE");
        when(roles.findByNombre("CLIENTE")).thenReturn(Optional.of(cliente));
        RegistroDto dto = new RegistroDto(); dto.setNombre(" Ana "); dto.setApellido(" Pérez ");
        dto.setEmail("ANA@EXAMPLE.TEST"); dto.setPassword(UUID.randomUUID().toString());
        assertEquals(201, controller.registrarCliente(dto).getStatusCode().value());
        verify(usuarios).save(argThat(u -> u.getRol() == cliente && u.getCorreo().equals("ana@example.test")
                && u.getNombres().equals("Ana") && encoder.matches(dto.getPassword(), u.getPassword())));
    }
    @Test void registroIncompletoEs400() {
        assertEquals(400, new AuthController().registrarCliente(new RegistroDto()).getStatusCode().value());
    }
}
