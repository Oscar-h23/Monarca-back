package com.Monarca.Backend.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {
    @Test
    void rutaPublicaMinimaParaRailway() {
        assertEquals("UP", new HealthController().health().get("status"));
    }
}
