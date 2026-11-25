package com.petsalud.backendpetsalud.security;

import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioDetails y UsuarioDetailsService: carga de usuarios para la autenticación")
class UsuarioDetailsTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioDetailsService usuarioDetailsService;

    private Usuario usuario(Rol rol) {
        return Usuario.builder().id(7L).email("usuario@petsalud.cl").password("HASH").rol(rol).build();
    }

    @Test
    @DisplayName("El rol se expone como autoridad con el prefijo ROLE_")
    void authorities_prefijoRole() {
        UserDetails details = new UsuarioDetails(usuario(Rol.GERENTE));

        assertEquals(1, details.getAuthorities().size());
        GrantedAuthority autoridad = details.getAuthorities().iterator().next();
        assertEquals("ROLE_GERENTE", autoridad.getAuthority());
    }

    @Test
    @DisplayName("El username es el email y se expone el hash de la contraseña")
    void usernameYPassword() {
        UsuarioDetails details = new UsuarioDetails(usuario(Rol.CLIENTE));

        assertEquals("usuario@petsalud.cl", details.getUsername());
        assertEquals("HASH", details.getPassword());
        assertEquals(7L, details.getId());
    }

    @Test
    @DisplayName("La cuenta está habilitada y sin vencimientos")
    void cuentaHabilitada() {
        UsuarioDetails details = new UsuarioDetails(usuario(Rol.EMPLEADO));

        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonExpired());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.isCredentialsNonExpired());
    }

    @Test
    @DisplayName("Carga un usuario existente por email")
    void loadUserByUsername_existente() {
        when(usuarioRepository.findByEmail("usuario@petsalud.cl")).thenReturn(Optional.of(usuario(Rol.EMPLEADO)));

        UserDetails details = usuarioDetailsService.loadUserByUsername("usuario@petsalud.cl");

        assertEquals("usuario@petsalud.cl", details.getUsername());
        assertEquals("ROLE_EMPLEADO", details.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("Falla con UsernameNotFoundException si el email no existe")
    void loadUserByUsername_inexistente() {
        when(usuarioRepository.findByEmail("nadie@petsalud.cl")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> usuarioDetailsService.loadUserByUsername("nadie@petsalud.cl"));
    }
}
