package com.Monarca.Backend.controller;

import com.Monarca.Backend.config.ApplicationConfig;
import com.Monarca.Backend.dto.*;
import com.Monarca.Backend.model.*;
import com.Monarca.Backend.repository.*;
import com.Monarca.Backend.security.*;
import com.Monarca.Backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest({ProductoController.class, CategoriaController.class, PedidoController.class, MetodoPagoController.class, PedidoClienteController.class, GestionPagoController.class, ReporteVentasController.class})
@Import({SecurityConfig.class, ApplicationConfig.class, CustomUserDetailsService.class, JwtUtil.class, ProductoService.class})
class FlujoApiTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired CustomUserDetailsService userDetails;
    @MockBean ProductoRepository productos;
    @MockBean CategoriaRepository categorias;
    @MockBean VarianteProductoRepository variantes;
    @MockBean ImagenProductoRepository imagenes;
    @MockBean UsuarioRepository usuarios;
    @MockBean PedidoRepository pedidos;
    @MockBean PagoRepository pagos;
    @MockBean MetodoPagoRepository metodos;
    @MockBean PedidoService pedidoService;
    @MockBean GestionPagoService gestionPago;
    @MockBean ReporteVentasService reportesVentas;
    @MockBean PedidoConsultaClienteService consultaCliente;
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry registry) {
        String claveEfimera = UUID.randomUUID().toString() + UUID.randomUUID();
        registry.add("jwt.secret", () -> claveEfimera);
    }
    @BeforeEach void preparar() {
        Categoria categoria = new Categoria(); categoria.setIdCategoria(1L); categoria.setNombre("Tops");
        Producto producto = new Producto(); producto.setIdProducto(1L); producto.setNombre("Top Ana");
        producto.setCategoria(categoria); producto.setPrecioBase(new BigDecimal("59.00"));
        VarianteProducto variante = new VarianteProducto(); variante.setIdVariante(10L); variante.setProducto(producto);
        variante.setSku("ANA-S-NEG"); variante.setTalla("S"); variante.setColor("Negro"); variante.setStock(5);
        variante.setPrecio(new BigDecimal("65.00"));
        ImagenProducto imagen = new ImagenProducto(); imagen.setUrl("https://example.supabase.co/storage/v1/object/public/productos/ana.jpg");
        when(productos.findAll()).thenReturn(List.of(producto));
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(variantes.findByProducto_IdProductoAndActivoTrue(1L)).thenReturn(List.of(variante));
        when(imagenes.findFirstByProducto_IdProductoAndPrincipalTrue(1L)).thenReturn(Optional.of(imagen));
        when(categorias.findAll()).thenReturn(List.of(categoria));
    }
    private String token(String rol) {
        Usuario usuario = new Usuario(); usuario.setIdUsuario(2L); usuario.setCorreo("cliente@example.test"); usuario.setPassword(UUID.randomUUID().toString());
        Rol r = new Rol(); r.setNombre(rol); usuario.setRol(r);
        when(usuarios.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        return "Bearer " + jwt.generateToken(userDetails.loadUserByUsername(usuario.getCorreo()));
    }
    @Test void catalogoDetalleYCategoriasPublicosConMismoDto() throws Exception {
        String listado = mvc.perform(get("/api/productos")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variantes[0].idVariante").value(10))
                .andExpect(jsonPath("$[0].categoria").value("Tops")).andReturn().getResponse().getContentAsString();
        String detalle = mvc.perform(get("/api/productos/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.precioBase").value(59)).andExpect(jsonPath("$.variantes[0].precio").value(65))
                .andReturn().getResponse().getContentAsString();
        assertEquals("[" + detalle + "]", listado);
        mvc.perform(get("/api/categorias")).andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Tops"));
        mvc.perform(get("/api/productos/999")).andExpect(status().isNotFound());
    }
    @Test void anonimoNoPuedeComprarNiAdministrar() throws Exception {
        mvc.perform(post("/api/pedidos/procesar").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos")).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/mis-pedidos")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/productos/1")).andExpect(status().isForbidden());
    }
    @Test void clienteNoPuedeAdministrar() throws Exception {
        String token = token("CLIENTE");
        mvc.perform(get("/api/pedidos").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/productos/1").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(put("/api/productos/1").header("Authorization",token).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/productos").header("Authorization",token).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void clienteCompraConJwtYUsuarioDelServidor() throws Exception {
        Pedido pedido = new Pedido(); pedido.setIdPedido(1L); pedido.setCodigoPedido("MON-TEST"); pedido.setTotal(new BigDecimal("130.00"));
        when(pedidoService.procesarCompra(any())).thenReturn(pedido);
        mvc.perform(post("/api/pedidos/procesar").header("Authorization", token("CLIENTE"))
                .contentType("application/json").content("{\"idUsuario\":999,\"idMetodoPago\":3,\"items\":[{\"idVariante\":10,\"cantidad\":2}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(130));
        verify(pedidoService).procesarCompra(argThat(p -> p.getIdUsuario()==2L && p.getItems().getFirst().getIdVariante()==10L));
    }
    @Test void clienteVeSoloSusPedidosDesdeJWT() throws Exception {
        when(consultaCliente.listarPorCorreo("cliente@example.test")).thenReturn(List.of());
        mvc.perform(get("/api/pedidos/mis-pedidos").header("Authorization", token("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(consultaCliente).listarPorCorreo("cliente@example.test");
    }
    @Test void adminListaPedidos() throws Exception {
        mvc.perform(get("/api/pedidos").header("Authorization",token("ADMIN"))).andExpect(status().isOk());
    }
    @Test void clienteNoPuedeRevisarPagosNiVerEvidenciaAjena() throws Exception {
        String jwt = token("CLIENTE");
        mvc.perform(get("/api/admin/pedidos/12/evidencia").header("Authorization",jwt))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/pedidos/12/pago").header("Authorization",jwt)
                .contentType("application/json").content("{\"accion\":\"APROBAR\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/pedidos/12/estado").header("Authorization",jwt)
                .contentType("application/json").content("{\"estado\":\"FINALIZADO\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(gestionPago);
    }

    @Test void administradorPuedeRegistrarRevisionDeEvidencia() throws Exception {
        String jwt = token("ADMIN");
        mvc.perform(put("/api/admin/pedidos/12/pago").header("Authorization",jwt)
                .contentType("application/json").content("{\"accion\":\"RECHAZAR\",\"motivo\":\"No coincide\"}"))
                .andExpect(status().isOk());
        verify(gestionPago).revisarPago(12L,"RECHAZAR","No coincide");
    }

    @Test void servicioStorageNoDisponibleDevuelveMensajeClaroSinClave() throws Exception {
        String jwt = token("CLIENTE");
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Configura SUPABASE_SERVICE_ROLE_KEY en el backend."))
                .when(gestionPago).enviarEvidencia(eq(12L),eq("cliente@example.test"),any());
        MockMultipartFile imagen = new MockMultipartFile(
                "archivo", "comprobante.jpg", "image/jpeg", new byte[]{1,2,3});
        String respuesta = mvc.perform(multipart("/api/pedidos/12/evidencia")
                        .file(imagen).header("Authorization",jwt))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(
                        "El almacenamiento de comprobantes no está disponible. La tienda debe revisar la configuración privada de Supabase Storage."))
                .andReturn().getResponse().getContentAsString();
        assertFalse(respuesta.contains("SUPABASE_SERVICE_ROLE_KEY"));
        verify(gestionPago).enviarEvidencia(eq(12L),eq("cliente@example.test"),any());
    }

    @Test void clienteNoPuedeVerConfiguracionBancariaAdminNiReportesDeVentas() throws Exception {
        String jwt=token("CLIENTE");
        mvc.perform(get("/api/admin/metodos-pago").header("Authorization",jwt))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/metodos-pago/1").header("Authorization",jwt)
                .contentType("application/json").content("{\"activo\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reportes/ventas?desde=2026-11-01&hasta=2026-11-12")
                .header("Authorization",jwt)).andExpect(status().isForbidden());
        verifyNoInteractions(reportesVentas);
    }

    @Test void administradorPuedeConsultarConfiguracionMetodos() throws Exception {
        MetodoPago m=new MetodoPago();m.setIdMetodoPago(3L);m.setCodigo("YAPE");
        m.setNombre("Yape");m.setActivo(true);
        when(metodos.findAll()).thenReturn(List.of(m));
        mvc.perform(get("/api/admin/metodos-pago").header("Authorization",token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("YAPE"))
                .andExpect(jsonPath("$[0].configurado").value(false));
    }

    @Test void metodosPagoUsanIdentificadoresReales() throws Exception {
        MetodoPago metodo = new MetodoPago(); metodo.setIdMetodoPago(37L); metodo.setCodigo("YAPE"); metodo.setNombre("Yape");
        when(metodos.findAll()).thenReturn(List.of(metodo));
        mvc.perform(get("/api/metodos-pago").header("Authorization",token("CLIENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].idMetodoPago").value(37));
    }
}
