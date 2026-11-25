package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UsuarioService: registro seguro y gestión de usuarios")
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "HASH(" + inv.getArgument(0) + ")");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Usuario nuevoUsuario() {
        return Usuario.builder().email("nuevo@correo.cl").password("Clave12345")
                .nombre("Ana").apellido("Pérez").build();
    }

    @Test
    @DisplayName("Autenticación: la contraseña se guarda cifrada y nunca en texto plano")
    void registrarUsuario_cifraLaContrasena() {
        Usuario guardado = usuarioService.registrarUsuario(nuevoUsuario());

        assertEquals("HASH(Clave12345)", guardado.getPassword());
        assertNotEquals("Clave12345", guardado.getPassword());
        verify(passwordEncoder).encode("Clave12345");
    }

    @Test
    @DisplayName("Autorización: un usuario sin rol queda como CLIENTE")
    void registrarUsuario_sinRol_quedaComoCliente() {
        assertEquals(Rol.CLIENTE, usuarioService.registrarUsuario(nuevoUsuario()).getRol());
    }

    @Test
    @DisplayName("Un rol explícito (alta hecha por el gerente) se conserva")
    void registrarUsuario_conRolExplicito_loConserva() {
        Usuario empleado = nuevoUsuario();
        empleado.setRol(Rol.EMPLEADO);

        assertEquals(Rol.EMPLEADO, usuarioService.registrarUsuario(empleado).getRol());
    }

    @Test
    @DisplayName("XSS: los campos de texto se guardan con HTML escapado")
    void registrarUsuario_escapaHtml() {
        Usuario usuario = nuevoUsuario();
        usuario.setNombre("<script>alert(1)</script>");

        Usuario guardado = usuarioService.registrarUsuario(usuario);

        assertFalse(guardado.getNombre().contains("<script>"));
        assertTrue(guardado.getNombre().contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("Falla si el email ya está registrado")
    void registrarUsuario_emailDuplicado_lanzaExcepcion() {
        when(usuarioRepository.findByEmail("nuevo@correo.cl")).thenReturn(Optional.of(new Usuario()));

        assertThrows(IllegalArgumentException.class, () -> usuarioService.registrarUsuario(nuevoUsuario()));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Actualizar un usuario no modifica su email ni su contraseña")
    void actualizarUsuario_noCambiaEmailNiPassword() {
        Usuario existente = Usuario.builder().id(1L).email("a@correo.cl").password("HASH").nombre("Ana").build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existente));
        Usuario cambios = Usuario.builder().email("otro@correo.cl").password("otraClave").nombre("Ana María").build();

        Usuario actualizado = usuarioService.actualizarUsuario(1L, cambios);

        assertEquals("Ana María", actualizado.getNombre());
        assertEquals("a@correo.cl", actualizado.getEmail());
        assertEquals("HASH", actualizado.getPassword());
    }

    @Test
    @DisplayName("Falla al actualizar un usuario que no existe")
    void actualizarUsuario_inexistente_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> usuarioService.actualizarUsuario(404L, new Usuario()));
    }

    @Test
    @DisplayName("Consulta por id, por email y listado")
    void consultas_delegaEnElRepositorio() {
        Usuario u = Usuario.builder().id(1L).email("a@correo.cl").build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(u));
        when(usuarioRepository.findByEmail("a@correo.cl")).thenReturn(Optional.of(u));
        when(usuarioRepository.findAll()).thenReturn(List.of(u));

        assertSame(u, usuarioService.obtenerUsuarioPorId(1L).orElseThrow());
        assertSame(u, usuarioService.obtenerUsuarioPorEmail("a@correo.cl").orElseThrow());
        assertEquals(1, usuarioService.listarUsuarios().size());
    }

    @Test
    @DisplayName("Elimina un usuario por id")
    void eliminarUsuario_invocaAlRepositorio() {
        usuarioService.eliminarUsuario(1L);
        verify(usuarioRepository).deleteById(1L);
    }
}
