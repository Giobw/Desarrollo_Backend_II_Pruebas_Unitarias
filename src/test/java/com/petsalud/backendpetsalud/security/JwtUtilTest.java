package com.petsalud.backendpetsalud.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil: generación y validación de tokens")
class JwtUtilTest {

    private static String secreto(String texto) {
        return Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static final String SECRETO_A = secreto("clave-secreta-de-prueba-A-0123456789-abcdef");
    private static final String SECRETO_B = secreto("clave-secreta-de-prueba-B-0123456789-abcdef");

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRETO_A, 3_600_000L);
    }

    @Test
    @DisplayName("Un token generado contiene el email y el rol del usuario")
    void generarToken_incluyeEmailYRol() {
        String token = jwtUtil.generarToken("gerente@petsalud.cl", "GERENTE");

        Claims claims = jwtUtil.validarYObtenerClaims(token);

        assertEquals("gerente@petsalud.cl", claims.getSubject());
        assertEquals("GERENTE", claims.get("rol", String.class));
        assertEquals("gerente@petsalud.cl", jwtUtil.obtenerEmail(token));
    }

    @Test
    @DisplayName("El token tiene fecha de emisión y expiración posterior")
    void generarToken_tieneExpiracion() {
        Claims claims = jwtUtil.validarYObtenerClaims(jwtUtil.generarToken("a@petsalud.cl", "CLIENTE"));

        assertNotNull(claims.getIssuedAt());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
        assertEquals(3_600_000L, jwtUtil.getExpirationMs());
    }

    @Test
    @DisplayName("Seguridad: un token con la firma alterada es rechazado")
    void validar_firmaAlterada_lanzaExcepcion() {
        String token = jwtUtil.generarToken("a@petsalud.cl", "CLIENTE");
        String falso = token.substring(0, token.lastIndexOf('.') + 1) + "A".repeat(43);

        assertThrows(JwtException.class, () -> jwtUtil.validarYObtenerClaims(falso));
    }

    @Test
    @DisplayName("Seguridad: un token firmado con otra clave es rechazado")
    void validar_tokenDeOtraClave_lanzaExcepcion() {
        String tokenAjeno = new JwtUtil(SECRETO_B, 3_600_000L).generarToken("a@petsalud.cl", "GERENTE");

        assertThrows(JwtException.class, () -> jwtUtil.validarYObtenerClaims(tokenAjeno));
    }

    @Test
    @DisplayName("Seguridad: un token expirado es rechazado")
    void validar_tokenExpirado_lanzaExcepcion() {
        JwtUtil expirado = new JwtUtil(SECRETO_A, -1_000L);
        String token = expirado.generarToken("a@petsalud.cl", "CLIENTE");

        assertThrows(JwtException.class, () -> jwtUtil.validarYObtenerClaims(token));
    }

    @Test
    @DisplayName("Caso límite: un texto que no es un JWT es rechazado")
    void validar_tokenMalformado_lanzaExcepcion() {
        assertThrows(JwtException.class, () -> jwtUtil.validarYObtenerClaims("esto-no-es-un-jwt"));
    }

    @Test
    @DisplayName("Caso límite: un secreto demasiado corto no se acepta")
    void constructor_secretoCorto_lanzaExcepcion() {
        String corto = secreto("corta");

        assertThrows(RuntimeException.class, () -> new JwtUtil(corto, 1000L));
    }
}
