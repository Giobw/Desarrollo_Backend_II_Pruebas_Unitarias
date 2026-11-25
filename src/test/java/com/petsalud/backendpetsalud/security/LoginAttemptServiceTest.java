package com.petsalud.backendpetsalud.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoginAttemptService: bloqueo temporal ante intentos fallidos (fuerza bruta)")
class LoginAttemptServiceTest {

    private static final String EMAIL = "cliente@petsalud.cl";
    private static final String IP = "10.0.0.1";

    @Test
    @DisplayName("Sin intentos fallidos no hay bloqueo")
    void sinIntentos_noEstaBloqueado() {
        assertFalse(new LoginAttemptService(3, 15).estaBloqueado(EMAIL, IP));
    }

    @Test
    @DisplayName("Caso límite: con un intento menos que el máximo aún no se bloquea")
    void bajoElMaximo_noEstaBloqueado() {
        LoginAttemptService servicio = new LoginAttemptService(3, 15);
        servicio.registrarFallo(EMAIL, IP);
        servicio.registrarFallo(EMAIL, IP);

        assertFalse(servicio.estaBloqueado(EMAIL, IP));
    }

    @Test
    @DisplayName("Al alcanzar el máximo de intentos fallidos se bloquea")
    void alcanzaElMaximo_seBloquea() {
        LoginAttemptService servicio = new LoginAttemptService(3, 15);
        for (int i = 0; i < 3; i++) {
            servicio.registrarFallo(EMAIL, IP);
        }

        assertTrue(servicio.estaBloqueado(EMAIL, IP));
    }

    @Test
    @DisplayName("Un login exitoso reinicia el contador de intentos")
    void loginExitoso_reiniciaContador() {
        LoginAttemptService servicio = new LoginAttemptService(3, 15);
        servicio.registrarFallo(EMAIL, IP);
        servicio.registrarFallo(EMAIL, IP);
        servicio.registrarExito(EMAIL, IP);
        servicio.registrarFallo(EMAIL, IP);

        assertFalse(servicio.estaBloqueado(EMAIL, IP));
    }

    @Test
    @DisplayName("El bloqueo es por combinación de email e IP")
    void bloqueo_esPorEmailEIp() {
        LoginAttemptService servicio = new LoginAttemptService(2, 15);
        servicio.registrarFallo(EMAIL, IP);
        servicio.registrarFallo(EMAIL, IP);

        assertTrue(servicio.estaBloqueado(EMAIL, IP));
        assertFalse(servicio.estaBloqueado(EMAIL, "10.0.0.2"));
        assertFalse(servicio.estaBloqueado("otro@petsalud.cl", IP));
    }

    @Test
    @DisplayName("El email se compara sin distinguir mayúsculas")
    void bloqueo_ignoraMayusculas() {
        LoginAttemptService servicio = new LoginAttemptService(2, 15);
        servicio.registrarFallo("CLIENTE@PETSALUD.CL", IP);
        servicio.registrarFallo("cliente@petsalud.cl", IP);

        assertTrue(servicio.estaBloqueado(EMAIL, IP));
    }
}
