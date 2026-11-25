package com.petsalud.backendpetsalud.security;

import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SeguridadService: reglas de propiedad (evita acceso a datos de otros clientes)")
class SeguridadServiceTest {

    @Mock
    private MascotaRepository mascotaRepository;

    private SeguridadService seguridad;
    private Authentication authCliente;

    @BeforeEach
    void setUp() {
        seguridad = new SeguridadService(mascotaRepository);
        Usuario cliente = Usuario.builder().id(3L).email("cliente@petsalud.cl").password("x").rol(Rol.CLIENTE).build();
        UsuarioDetails details = new UsuarioDetails(cliente);
        authCliente = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    @DisplayName("esUsuario: true cuando el id coincide con el usuario autenticado")
    void esUsuario_mismoId_true() {
        assertTrue(seguridad.esUsuario(authCliente, 3L));
    }

    @Test
    @DisplayName("esUsuario: false cuando el id es de otro usuario")
    void esUsuario_otroId_false() {
        assertFalse(seguridad.esUsuario(authCliente, 4L));
    }

    @Test
    @DisplayName("esUsuario: false sin autenticación")
    void esUsuario_sinAutenticacion_false() {
        assertFalse(seguridad.esUsuario(null, 3L));
    }

    @Test
    @DisplayName("esUsuario: false si el principal no es un usuario del sistema")
    void esUsuario_principalDesconocido_false() {
        Authentication anonimo = new UsernamePasswordAuthenticationToken("anonymousUser", null);
        assertFalse(seguridad.esUsuario(anonimo, 3L));
    }

    @Test
    @DisplayName("esPropietarioMascota: true si la mascota pertenece al usuario autenticado")
    void esPropietarioMascota_propia_true() {
        Usuario dueno = Usuario.builder().id(3L).build();
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(Mascota.builder().id(10L).propietario(dueno).build()));

        assertTrue(seguridad.esPropietarioMascota(authCliente, 10L));
    }

    @Test
    @DisplayName("esPropietarioMascota: false si la mascota es de otro propietario")
    void esPropietarioMascota_ajena_false() {
        Usuario otro = Usuario.builder().id(99L).build();
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(Mascota.builder().id(10L).propietario(otro).build()));

        assertFalse(seguridad.esPropietarioMascota(authCliente, 10L));
    }

    @Test
    @DisplayName("esPropietarioMascota: false si la mascota no existe")
    void esPropietarioMascota_inexistente_false() {
        assertFalse(seguridad.esPropietarioMascota(authCliente, 404L));
    }

    @Test
    @DisplayName("esPropietarioMascota: false si la mascota no tiene propietario")
    void esPropietarioMascota_sinPropietario_false() {
        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(Mascota.builder().id(10L).build()));

        assertFalse(seguridad.esPropietarioMascota(authCliente, 10L));
    }

    @Test
    @DisplayName("esPropietarioMascota: false sin autenticación")
    void esPropietarioMascota_sinAutenticacion_false() {
        assertFalse(seguridad.esPropietarioMascota(null, 10L));
    }
}
