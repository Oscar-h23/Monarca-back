package com.Monarca.Backend.service;

import com.Monarca.Backend.model.Pedido;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificacionesPedidoServiceTest {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    private NotificacionesPedidoService servicio;

    private NotificacionesPedidoService construir(String adminTo, boolean smtp) {
        if (smtp) when(provider.getIfAvailable()).thenReturn(mail);
        servicio = new NotificacionesPedidoService(publisher, usuarios, provider,
                smtp ? "smtp.resend.com" : "",
                smtp ? "resend" : "",
                smtp ? "re_clave_de_prueba" : "",
                smtp ? "avisos@example.test" : "",
                adminTo, "http://localhost:4200");
        return servicio;
    }

    @AfterEach void cerrar() {
        if (servicio != null) servicio.cerrar();
    }

    private NotificacionesPedidoService.Aviso aviso(NotificacionesPedidoService.Tipo tipo, String motivo) {
        return new NotificacionesPedidoService.Aviso(
                tipo, 19L, "MON-19-PRUEBA", "compradora@example.test", "Ana",
                new BigDecimal("39.90"), motivo);
    }

    @Test void publicaEventoSinEnviarAntesDelCommit() {
        NotificacionesPedidoService servicio = construir("admin@example.test", true);
        Usuario user = new Usuario();
        user.setCorreo("compradora@example.test");
        user.setNombres("Ana");
        Pedido p = new Pedido();
        p.setIdPedido(19L);
        p.setCodigoPedido("MON-19-PRUEBA");
        p.setTotal(new BigDecimal("39.90"));
        p.setUsuario(user);

        servicio.publicar(p, NotificacionesPedidoService.Tipo.NUEVO_PEDIDO, null);
        ArgumentCaptor<NotificacionesPedidoService.Aviso> captor =
                ArgumentCaptor.forClass(NotificacionesPedidoService.Aviso.class);
        verify(publisher).publishEvent(captor.capture());
        assertEquals("MON-19-PRUEBA",captor.getValue().codigoPedido());
        assertEquals(NotificacionesPedidoService.Tipo.NUEVO_PEDIDO,captor.getValue().tipo());
        verifyNoInteractions(mail,usuarios);
    }

    @Test void listenerSoloSeEjecutaDespuesDelCommit() throws Exception {
        Method m = NotificacionesPedidoService.class.getMethod(
                "alConfirmarTransaccion",NotificacionesPedidoService.Aviso.class);
        TransactionalEventListener annotation = m.getAnnotation(TransactionalEventListener.class);
        assertNotNull(annotation);
        assertEquals(TransactionPhase.AFTER_COMMIT,annotation.phase());
    }

    @Test void nuevoPedidoNotificaAlClienteYAlAdminConfigurado() {
        NotificacionesPedidoService servicio = construir("admin@example.test", true);
        servicio.entregar(aviso(NotificacionesPedidoService.Tipo.NUEVO_PEDIDO,null));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail,times(2)).send(captor.capture());
        List<SimpleMailMessage> mails = captor.getAllValues();
        assertEquals("compradora@example.test", mails.get(0).getTo()[0]);
        assertEquals("admin@example.test", mails.get(1).getTo()[0]);
        assertTrue(mails.get(0).getText().contains("pendiente de pago"));
        assertTrue(mails.get(0).getText().contains("/mis-pedidos"));
        assertTrue(mails.get(1).getText().contains("S/ 39.90"));
        assertTrue(mails.get(1).getText().contains("/admin"));
        verifyNoInteractions(usuarios); // la configuración explícita prevalece
    }

    @Test void recibeEvidenciaNotificaAlAdminYAlClienteSinAprobar() {
        NotificacionesPedidoService servicio = construir("admin@example.test", true);
        servicio.entregar(aviso(NotificacionesPedidoService.Tipo.EVIDENCIA_RECIBIDA,null));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail,times(2)).send(captor.capture());
        assertTrue(captor.getAllValues().get(0).getText().contains("NO está confirmado"));
        assertTrue(captor.getAllValues().get(1).getText().contains("requiere validación manual"));
    }

    @Test void estadosDelClienteNoNotificanAlAdmin() {
        NotificacionesPedidoService servicio = construir("admin@example.test", true);
        for (NotificacionesPedidoService.Tipo tipo : List.of(
                NotificacionesPedidoService.Tipo.PAGO_APROBADO,
                NotificacionesPedidoService.Tipo.PAGO_RECHAZADO,
                NotificacionesPedidoService.Tipo.EN_PREPARACION,
                NotificacionesPedidoService.Tipo.EN_CAMINO,
                NotificacionesPedidoService.Tipo.FINALIZADO,
                NotificacionesPedidoService.Tipo.CANCELADO)) {
            servicio.entregar(aviso(tipo, "Transferencia no encontrada"));
        }
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail,times(6)).send(captor.capture());
        for (SimpleMailMessage e : captor.getAllValues()) {
            assertEquals("compradora@example.test",e.getTo()[0]);
            assertTrue(e.getText().contains("MON-19-PRUEBA"));
        }
        assertTrue(captor.getAllValues().get(0).getText().contains("DE PRUEBA"));
        assertTrue(captor.getAllValues().get(1).getText().contains("Transferencia no encontrada"));
    }

    @Test void sinAdminOverrideNotificaCuentasAdminActivas() {
        NotificacionesPedidoService servicio = construir("", true);
        Usuario admin = new Usuario(); admin.setCorreo("admin-real@example.test");
        when(usuarios.findByRol_NombreIgnoreCaseAndActivoTrue("ADMIN"))
                .thenReturn(List.of(admin));
        servicio.entregar(aviso(NotificacionesPedidoService.Tipo.NUEVO_PEDIDO,null));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail,times(2)).send(captor.capture());
        assertEquals("admin-real@example.test",captor.getAllValues().get(1).getTo()[0]);
    }

    @Test void sinSmtpNoEnviaPeroNoFallaCompra() {
        NotificacionesPedidoService servicio = construir("admin@example.test", false);
        assertDoesNotThrow(()->servicio.alConfirmarTransaccion(
                aviso(NotificacionesPedidoService.Tipo.NUEVO_PEDIDO,null)));
        verifyNoInteractions(mail,usuarios);
    }

    @Test void diagnosticoIndicaVariablesFaltantesSinMostrarSecretos() {
        NotificacionesPedidoService servicio = construir("", false);
        var estado = servicio.estado();
        assertFalse(estado.configurado());
        assertFalse(estado.claveSmtp());
        assertFalse(estado.remitenteValido());
        assertFalse(estado.servicioCorreo());
        assertFalse(estado.mensaje().contains("re_clave_de_prueba"));
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> servicio.enviarPruebaAdministrador("admin@example.test"));
        assertEquals(503, error.getStatusCode().value());
        verifyNoInteractions(mail);
    }

    @Test void pruebaRealSoloSeEnviaAAdminVerificadoPorControlador() {
        NotificacionesPedidoService servicio = construir("admin@example.test", true);
        assertTrue(servicio.estado().configurado());
        assertDoesNotThrow(() -> servicio.enviarPruebaAdministrador("admin@example.test"));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        assertEquals("admin@example.test", captor.getValue().getTo()[0]);
        assertTrue(captor.getValue().getSubject().contains("Prueba"));
    }

    @Test void pruebaDevuelveErrorSiResendRechazaAutenticacion() {
        NotificacionesPedidoService servicio = construir("admin@example.test", true);
        doThrow(new org.springframework.mail.MailAuthenticationException("clave inválida de prueba"))
                .when(mail).send(any(SimpleMailMessage.class));
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> servicio.enviarPruebaAdministrador("admin@example.test"));
        assertEquals(502, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Resend rechazó la autenticación"));
        assertFalse(error.getReason().contains("clave inválida de prueba"));
    }

    @Test void aceptaRemitenteConNombreQueTambienSirveParaRecuperarContrasena() {
        when(provider.getIfAvailable()).thenReturn(mail);
        servicio = new NotificacionesPedidoService(publisher, usuarios, provider,
                "smtp.resend.com", "resend", "re_clave_de_prueba",
                "Nayeli's Moda <avisos@example.test>",
                "admin@example.test", "http://localhost:4200");
        assertTrue(servicio.estado().configurado(),
                "El formato con nombre visible no debe desactivar notificaciones.");
    }

    @Test void contadorDistingueEventoPublicadoDeEventoConfirmado() {
        NotificacionesPedidoService servicio=construir("admin@example.test", true);
        Usuario cliente=new Usuario();
        cliente.setCorreo("compradora@example.test");
        cliente.setNombres("Ana");
        Pedido pedido=new Pedido();
        pedido.setIdPedido(19L);pedido.setUsuario(cliente);
        pedido.setCodigoPedido("MON-19-PRUEBA");
        pedido.setTotal(new BigDecimal("39.90"));
        servicio.publicar(pedido,NotificacionesPedidoService.Tipo.NUEVO_PEDIDO,null);
        assertEquals(1,servicio.estado().eventosPublicados());
        assertEquals(0,servicio.estado().eventosConfirmados());
        assertEquals(1,servicio.estado().eventosSinTransaccion());
        verifyNoInteractions(mail);
    }

    @Test void falloSmtpNoFallaLaOperacionNiDetieneOtroAviso() {
        NotificacionesPedidoService servicio = construir("admin@example.test", true);
        doThrow(new org.springframework.mail.MailSendException("SMTP fuera de servicio"))
                .doNothing().when(mail).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(()->servicio.entregar(aviso(
                NotificacionesPedidoService.Tipo.NUEVO_PEDIDO,null)));
        verify(mail,times(2)).send(any(SimpleMailMessage.class));
    }
}
