package com.Monarca.Backend.service;

import com.Monarca.Backend.model.VarianteProducto;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MedidasServiceTest {
    @Test void aceptaMedidasOpcionales() {
        VarianteProducto v = new VarianteProducto();
        MedidasService.variante(v, null, new BigDecimal("75.5"), null, null);
        assertEquals(new BigDecimal("75.5"), v.getCinturaCm());
        assertNull(v.getPechoCm());
    }
    @Test void impideMedidasNoRealistasYMasDeUnDecimal() {
        assertThrows(IllegalArgumentException.class,
            () -> MedidasService.validar(new BigDecimal("-2"), "Cintura", 30, 300));
        assertThrows(IllegalArgumentException.class,
            () -> MedidasService.validar(new BigDecimal("80.25"), "Cintura", 30, 300));
    }
}
