package com.Monarca.Backend.service;

import com.Monarca.Backend.model.Pedido;
import com.Monarca.Backend.model.Usuario;
import com.Monarca.Backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Prueba el evento real de Spring y el AFTER_COMMIT (no solo un mock del publisher).
 * No usa una base de datos ni establece conexiones SMTP.
 */
class NotificacionesPedidoAfterCommitTest {
    static class TxSinBaseDatos extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }

    private Pedido pedido() {
        Usuario usuario = new Usuario();
        usuario.setCorreo("compradora@example.test");
        usuario.setNombres("Ana");
        Pedido pedido = new Pedido();
        pedido.setIdPedido(21L);
        pedido.setUsuario(usuario);
        pedido.setCodigoPedido("MON-PRUEBA-21");
        pedido.setTotal(new BigDecimal("39.90"));
        return pedido;
    }

    @Test void entregaSoloDespuesDeCommitYNoDespuesDeRollback() {
        JavaMailSender mail = mock(JavaMailSender.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(mail);
        try (AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext()) {
            contexto.registerBean("transactionManager",PlatformTransactionManager.class,TxSinBaseDatos::new);
            contexto.registerBean(NotificacionesPedidoService.class,()->new NotificacionesPedidoService(
                    contexto,usuarios,proveedor,"smtp.resend.com","resend",
                    "re_test_sin_validez","Nayeli's Moda <avisos@example.test>",
                    "admin@example.test","http://localhost:4200"));
            contexto.refresh();

            TransactionTemplate tx = new TransactionTemplate(contexto.getBean(PlatformTransactionManager.class));
            NotificacionesPedidoService notificaciones = contexto.getBean(NotificacionesPedidoService.class);

            tx.executeWithoutResult(estado -> {
                notificaciones.publicar(pedido(),NotificacionesPedidoService.Tipo.NUEVO_PEDIDO,null);
                verifyNoInteractions(mail);
                assertEquals(0,notificaciones.estado().eventosConfirmados());
            });

            verify(mail,timeout(2500).times(2)).send(any(SimpleMailMessage.class));
            assertEquals(1,notificaciones.estado().eventosConfirmados());
            assertEquals(2,notificaciones.estado().mensajesAceptadosSmtp());

            clearInvocations(mail);
            tx.executeWithoutResult(estado -> {
                notificaciones.publicar(pedido(),NotificacionesPedidoService.Tipo.NUEVO_PEDIDO,null);
                estado.setRollbackOnly();
            });
            verify(mail,after(350).never()).send(any(SimpleMailMessage.class));
            assertEquals(1,notificaciones.estado().eventosConfirmados());
        }
    }
}
