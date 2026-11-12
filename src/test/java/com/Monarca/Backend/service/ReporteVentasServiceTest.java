package com.Monarca.Backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReporteVentasServiceTest {
    private final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
    private final ReporteVentasService reportes=new ReporteVentasService(jdbc);

    @Test void rechazaRangoInvertido() {
        var ex=assertThrows(ResponseStatusException.class,()->
                reportes.consultar(LocalDate.parse("2026-11-12"),LocalDate.parse("2026-11-01")));
        assertEquals(HttpStatus.BAD_REQUEST,ex.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test void limitaRangosMayoresA366Dias() {
        var ex=assertThrows(ResponseStatusException.class,()->
                reportes.consultar(LocalDate.parse("2024-01-01"),LocalDate.parse("2026-01-01")));
        assertEquals(HttpStatus.BAD_REQUEST,ex.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test void requiereFechasNoNulas() {
        assertThrows(ResponseStatusException.class,()->
                reportes.consultar(null,LocalDate.now()));
        verifyNoInteractions(jdbc);
    }
}
