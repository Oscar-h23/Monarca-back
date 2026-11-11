package com.Monarca.Backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityCorsConfigTest {

    @Test
    void aceptaDominioNuevoVercelYEntornoLocal() {
        SecurityConfig security = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(AuthenticationProvider.class));
        ReflectionTestUtils.setField(security, "corsAllowedOrigins",
                "https://nayelismoda.shop, https://www.nayelismoda.shop, " +
                "https://monarca-front.vercel.app, http://localhost:4200");

        CorsConfiguration cors = security.corsConfigurationSource().getCorsConfiguration(
                new MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/auth/login"));

        assertNotNull(cors);
        assertEquals("https://nayelismoda.shop", cors.checkOrigin("https://nayelismoda.shop"));
        assertEquals("https://www.nayelismoda.shop", cors.checkOrigin("https://www.nayelismoda.shop"));
        assertEquals("https://monarca-front.vercel.app", cors.checkOrigin("https://monarca-front.vercel.app"));
        assertEquals("http://localhost:4200", cors.checkOrigin("http://localhost:4200"));
        assertTrue(cors.getAllowedMethods().contains("OPTIONS"));
        assertTrue(cors.getAllowedHeaders().contains("*"));
    }

    @Test
    void rechazaDominiosNoRegistrados() {
        SecurityConfig security = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(AuthenticationProvider.class));
        ReflectionTestUtils.setField(security, "corsAllowedOrigins",
                "https://nayelismoda.shop,https://monarca-front.vercel.app");

        CorsConfiguration cors = security.corsConfigurationSource().getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/productos"));

        assertNotNull(cors);
        assertNull(cors.checkOrigin("https://otra-tienda.example"));
        assertNull(cors.checkOrigin("http://nayelismoda.shop"));
        assertNull(cors.checkOrigin("https://nayelismoda.shop.evil.example"));
    }
}
