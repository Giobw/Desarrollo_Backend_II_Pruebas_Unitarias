package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MascotaService: administración de mascotas y propietarios")
class MascotaServiceTest {

    @Mock
    private MascotaRepository mascotaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private MascotaServiceImpl mascotaService;

    private Usuario propietario;

    @BeforeEach
    void setUp() {
        propietario = Usuario.builder().id(1L).email("dueno@correo.cl").rol(Rol.CLIENTE).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(propietario));
        when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Mascota mascotaValida() {
        return Mascota.builder()
                .nombre("Luna")
                .especie("Gato")
                .raza("Siamés")
                .sexo("Hembra")
                .fechaNacimiento(LocalDate.now().minusYears(2))
                .build();
    }

    @Test
    @DisplayName("Registra una mascota y la asigna a su propietario")
    void registrarMascota_datosValidos_asignaPropietario() {
        Mascota guardada = mascotaService.registrarMascota(mascotaValida(), 1L);

        assertSame(propietario, guardada.getPropietario());
        assertEquals("Luna", guardada.getNombre());
        verify(mascotaRepository, times(1)).save(any(Mascota.class));
    }

    @Test
    @DisplayName("Falla si el propietario no existe")
    void registrarMascota_propietarioInexistente_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mascotaService.registrarMascota(mascotaValida(), 99L));
        assertEquals("Propietario no encontrado", ex.getMessage());
        verify(mascotaRepository, never()).save(any(Mascota.class));
    }

    @Test
    @DisplayName("Caso límite: el nombre no puede estar vacío")
    void registrarMascota_nombreVacio_lanzaExcepcion() {
        Mascota mascota = mascotaValida();
        mascota.setNombre("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mascotaService.registrarMascota(mascota, 1L));
        assertEquals("El nombre de la mascota es obligatorio", ex.getMessage());
        verify(mascotaRepository, never()).save(any(Mascota.class));
    }

    @Test
    @DisplayName("Caso límite: el nombre no puede ser nulo")
    void registrarMascota_nombreNulo_lanzaExcepcion() {
        Mascota mascota = mascotaValida();
        mascota.setNombre(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mascotaService.registrarMascota(mascota, 1L));
        assertEquals("El nombre de la mascota es obligatorio", ex.getMessage());
    }

    @Test
    @DisplayName("Caso límite: la especie es obligatoria")
    void registrarMascota_especieVacia_lanzaExcepcion() {
        Mascota mascota = mascotaValida();
        mascota.setEspecie("");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mascotaService.registrarMascota(mascota, 1L));
        assertEquals("La especie de la mascota es obligatoria", ex.getMessage());
    }

    @Test
    @DisplayName("Caso límite: la fecha de nacimiento no puede ser futura")
    void registrarMascota_fechaNacimientoFutura_lanzaExcepcion() {
        Mascota mascota = mascotaValida();
        mascota.setFechaNacimiento(LocalDate.now().plusDays(1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mascotaService.registrarMascota(mascota, 1L));
        assertEquals("La fecha de nacimiento no puede ser futura", ex.getMessage());
    }

    @Test
    @DisplayName("Caso límite: una mascota nacida hoy es válida")
    void registrarMascota_nacidaHoy_esValida() {
        Mascota mascota = mascotaValida();
        mascota.setFechaNacimiento(LocalDate.now());

        Mascota guardada = mascotaService.registrarMascota(mascota, 1L);

        assertEquals(LocalDate.now(), guardada.getFechaNacimiento());
    }

    @Test
    @DisplayName("Obtiene una mascota existente por id")
    void obtenerMascotaPorId_existente_devuelveMascota() {
        Mascota mascota = mascotaValida();
        when(mascotaRepository.findById(5L)).thenReturn(Optional.of(mascota));

        assertSame(mascota, mascotaService.obtenerMascotaPorId(5L).orElseThrow());
    }

    @Test
    @DisplayName("Devuelve vacío si la mascota no existe")
    void obtenerMascotaPorId_inexistente_devuelveVacio() {
        assertTrue(mascotaService.obtenerMascotaPorId(404L).isEmpty());
    }

    @Test
    @DisplayName("Actualiza los datos de una mascota existente")
    void actualizarMascota_existente_actualizaCampos() {
        Mascota existente = mascotaValida();
        when(mascotaRepository.findById(5L)).thenReturn(Optional.of(existente));
        Mascota cambios = Mascota.builder().nombre("Luna II").especie("Gato").raza("Persa")
                .sexo("Hembra").fechaNacimiento(LocalDate.of(2022, 5, 10)).build();

        Mascota actualizada = mascotaService.actualizarMascota(5L, cambios);

        assertEquals("Luna II", actualizada.getNombre());
        assertEquals("Persa", actualizada.getRaza());
        assertEquals(LocalDate.of(2022, 5, 10), actualizada.getFechaNacimiento());
    }

    @Test
    @DisplayName("Falla al actualizar una mascota que no existe")
    void actualizarMascota_inexistente_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mascotaService.actualizarMascota(404L, mascotaValida()));
        assertEquals("Mascota no encontrada", ex.getMessage());
    }

    @Test
    @DisplayName("Elimina una mascota por id")
    void eliminarMascota_invocaAlRepositorio() {
        mascotaService.eliminarMascota(5L);
        verify(mascotaRepository, times(1)).deleteById(5L);
    }

    @Test
    @DisplayName("Un propietario puede tener múltiples mascotas")
    void listarMascotasPorPropietario_variasMascotas() {
        Mascota otra = Mascota.builder().nombre("Rex").especie("Perro").build();
        when(mascotaRepository.findByPropietarioId(1L)).thenReturn(List.of(mascotaValida(), otra));

        List<Mascota> mascotas = mascotaService.listarMascotasPorPropietario(1L);

        assertEquals(2, mascotas.size());
        assertEquals("Rex", mascotas.get(1).getNombre());
    }

    @Test
    @DisplayName("Un propietario sin mascotas devuelve una lista vacía")
    void listarMascotasPorPropietario_sinMascotas_devuelveListaVacia() {
        assertTrue(mascotaService.listarMascotasPorPropietario(1L).isEmpty());
    }
}
