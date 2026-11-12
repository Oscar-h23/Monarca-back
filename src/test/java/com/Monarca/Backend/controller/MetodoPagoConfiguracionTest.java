package com.Monarca.Backend.controller;

import com.Monarca.Backend.model.MetodoPago;
import com.Monarca.Backend.repository.MetodoPagoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetodoPagoConfiguracionTest {
    private final MetodoPagoRepository repo=mock(MetodoPagoRepository.class);
    private final MetodoPagoController controller=new MetodoPagoController(repo);

    private MetodoPago metodo(String codigo) {
        MetodoPago m=new MetodoPago();m.setIdMetodoPago(5L);m.setCodigo(codigo);m.setNombre(codigo);
        m.setActivo(true);m.setRequiereComprobante(true);return m;
    }

    @Test void yapePuedeGuardarTelefonoYQrSoloDesdeStorageDeLaTienda() {
        MetodoPago m=metodo("YAPE");
        when(repo.findById(5L)).thenReturn(Optional.of(m));
        when(repo.save(m)).thenReturn(m);
        String qr="https://ldjuyocsuvvvbllgdclv.supabase.co/storage/v1/object/public/monarca-productos/productos/123e4567-e89b-12d3-a456-426614174000.png";
        var response=controller.configurar(5L,new MetodoPagoController.DatosPago(
                true,"987654321",qr,"Tienda Nayeli",null,null,null));
        assertTrue(response.configurado());
        assertEquals("987654321",response.telefonoYape());
        assertEquals(qr,response.qrUrl());
        verify(repo).save(m);
    }

    @Test void yapeNoAceptaQrExternoNiTelefonosInvalidos() {
        MetodoPago m=metodo("YAPE");when(repo.findById(5L)).thenReturn(Optional.of(m));
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->
                controller.configurar(5L,new MetodoPagoController.DatosPago(
                        true,"123456789",null,null,null,null,null))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->
                controller.configurar(5L,new MetodoPagoController.DatosPago(
                        true,"987654321","https://evil.example/qr.png",null,null,null,null))).getStatusCode());
        verify(repo,never()).save(any());
    }

    @Test void transferenciaExigeBancoCuentaYCciParaEstarLista() {
        MetodoPago m=metodo("TRANSFERENCIA");
        when(repo.findById(5L)).thenReturn(Optional.of(m));
        when(repo.save(m)).thenReturn(m);
        var respuesta=controller.configurar(5L,new MetodoPagoController.DatosPago(
                true,null,null,"Titular","BCP","194123456789", "00219412345678901234"));
        assertTrue(respuesta.configurado());
        assertEquals("BCP",respuesta.banco());
        assertEquals("00219412345678901234",respuesta.cci());
    }

    @Test void transferenciaConCciInvalidoNoGuarda() {
        MetodoPago m=metodo("TRANSFERENCIA");when(repo.findById(5L)).thenReturn(Optional.of(m));
        assertThrows(ResponseStatusException.class,()->
                controller.configurar(5L,new MetodoPagoController.DatosPago(
                        true,null,null,"Titular","BCP","194123456789","123")));
        verify(repo,never()).save(any());
    }

    @Test void publicacionOmiteMetodosDesactivadosYNoInventanDatos() {
        MetodoPago yape=metodo("YAPE");
        MetodoPago apagado=metodo("TRANSFERENCIA");apagado.setActivo(false);
        when(repo.findAll()).thenReturn(List.of(yape,apagado));
        var lista=controller.listar();
        assertEquals(1,lista.size());
        assertFalse(lista.getFirst().configurado());
        assertNull(lista.getFirst().telefonoYape());
    }
}
