package com.Monarca.Backend.service;

import com.Monarca.Backend.dto.ProductoDto;
import com.Monarca.Backend.dto.VarianteProductoInputDto;
import com.Monarca.Backend.model.Categoria;
import com.Monarca.Backend.model.Producto;
import com.Monarca.Backend.model.VarianteProducto;
import com.Monarca.Backend.repository.CategoriaRepository;
import com.Monarca.Backend.repository.ImagenProductoRepository;
import com.Monarca.Backend.repository.ProductoRepository;
import com.Monarca.Backend.repository.VarianteProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoVariantesTest {
    @Mock ProductoRepository productos;
    @Mock CategoriaRepository categorias;
    @Mock VarianteProductoRepository variantes;
    @Mock ImagenProductoRepository imagenes;
    @InjectMocks ProductoService service;

    private ProductoDto producto() {
        ProductoDto dto = new ProductoDto();
        dto.setNombre("Blusa Elizabeth");
        dto.setCategoria("Tops");
        dto.setPrecioBase(new BigDecimal("69.90"));
        return dto;
    }
    private VarianteProductoInputDto variante(String talla, String color, int stock) {
        VarianteProductoInputDto dto = new VarianteProductoInputDto();
        dto.setTalla(talla); dto.setColor(color);
        dto.setPrecio(new BigDecimal("69.90"));
        dto.setStock(stock); dto.setStockMinimo(2);
        return dto;
    }
    private void simularGuardado() {
        Categoria categoria = new Categoria();
        categoria.setNombre("Tops");
        when(categorias.findByNombreIgnoreCase("Tops")).thenReturn(Optional.of(categoria));
        when(productos.save(any())).thenAnswer(i -> {
            Producto guardado = i.getArgument(0);
            guardado.setIdProducto(17L);
            return guardado;
        });
    }
    @Test void creaVariasTallasConStockPropioYSkuDistinto() {
        ProductoDto dto = producto();
        dto.setVariantes(List.of(variante("S", "Negro", 5),
                variante("M", "Negro", 9), variante("L", "Negro", 3)));
        simularGuardado();
        Producto producto = service.guardar(dto);
        ArgumentCaptor<VarianteProducto> captor = ArgumentCaptor.forClass(VarianteProducto.class);
        verify(variantes, times(3)).save(captor.capture());
        List<VarianteProducto> guardadas = captor.getAllValues();
        assertEquals(17L, producto.getIdProducto());
        assertEquals(List.of("S", "M", "L"), guardadas.stream().map(VarianteProducto::getTalla).toList());
        assertEquals(List.of(5, 9, 3), guardadas.stream().map(VarianteProducto::getStock).toList());
        assertEquals(3, guardadas.stream().map(VarianteProducto::getSku).collect(Collectors.toSet()).size());
        assertTrue(guardadas.stream().allMatch(v -> v.getProducto() == producto && v.getActivo()));
        verify(productos).save(any());
    }
    @Test void evitaTallasDuplicadasAntesDeGuardar() {
        ProductoDto dto = producto();
        dto.setVariantes(List.of(variante(" M ", "Negro", 5), variante("m", "NEGRO", 9)));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.guardar(dto));
        assertTrue(ex.getMessage().contains("No repitas"));
        verifyNoInteractions(productos, variantes);
    }
    @Test void sigueAceptandoFormatoAnteriorDeUnaVariante() {
        ProductoDto dto = producto();
        dto.setPrecio(new BigDecimal("68.00"));
        dto.setTalla("ÚNICA"); dto.setColor("Blanco"); dto.setStock(4);
        simularGuardado();
        service.guardar(dto);
        verify(variantes).save(argThat(v -> "ÚNICA".equals(v.getTalla())
                && "Blanco".equals(v.getColor()) && v.getStock() == 4));
    }
    @Test void rechazaListaVaciaEnLugarDeCrearUnaVarianteFantasma() {
        ProductoDto dto = producto(); dto.setVariantes(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.guardar(dto));
        verifyNoInteractions(productos, variantes);
    }
}
