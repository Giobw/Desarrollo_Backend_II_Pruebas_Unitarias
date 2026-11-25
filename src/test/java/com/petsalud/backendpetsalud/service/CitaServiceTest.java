package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Cita;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.CitaRepository;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CitaService: gestión de citas veterinarias")
class CitaServiceTest {

    @Mock
    private CitaRepository citaRepository;
    @Mock
    private MascotaRepository mascotaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CitaServiceImpl citaService;

    private Mascota mascota;
    private Usuario veterinario;
    private LocalDateTime futuro;

    @BeforeEach
    void setUp() {
        Usuario dueno = Usuario.builder().id(1L).email("dueno@correo.cl").rol(Rol.CLIENTE).build();
        mascota = Mascota.builder().id(10L).nombre("Firulais").especie("Perro").propietario(dueno).build();
        veterinario = Usuario.builder().id(20L).email("vet@petsalud.cl").rol(Rol.EMPLEADO).build();
        futuro = LocalDateTime.now().plusDays(3);

        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(mascota));
        when(usuarioRepository.findById(20L)).thenReturn(Optional.of(veterinario));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- agendarCita ----------

    @Test
    @DisplayName("Agenda una cita con datos válidos en estado Agendada")
    void agendarCita_datosValidos_creaCitaAgendada() {
        Cita cita = citaService.agendarCita(10L, 20L, futuro, "Control anual");

        assertEquals("Agendada", cita.getEstado());
        assertEquals(futuro, cita.getFechaHora());
        assertEquals("Control anual", cita.getMotivo());
        verify(citaRepository, times(1)).save(any(Cita.class));
    }

    @Test
    @DisplayName("La cita queda relacionada con la mascota y con el veterinario")
    void agendarCita_relacionaMascotaYVeterinario() {
        citaService.agendarCita(10L, 20L, futuro, "Vacuna");

        ArgumentCaptor<Cita> captor = ArgumentCaptor.forClass(Cita.class);
        verify(citaRepository).save(captor.capture());
        assertSame(mascota, captor.getValue().getMascota());
        assertSame(veterinario, captor.getValue().getVeterinario());
    }

    @Test
    @DisplayName("Falla si la mascota no existe")
    void agendarCita_mascotaInexistente_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(99L, 20L, futuro, "Control"));
        assertEquals("Mascota no encontrada", ex.getMessage());
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    @DisplayName("Falla si el veterinario no existe")
    void agendarCita_veterinarioInexistente_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(10L, 99L, futuro, "Control"));
        assertEquals("Veterinario no encontrado", ex.getMessage());
    }

    @Test
    @DisplayName("Caso límite: no permite agendar en una fecha pasada")
    void agendarCita_fechaPasada_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(10L, 20L, LocalDateTime.now().minusDays(1), "Control"));
        assertEquals("La fecha de la cita debe ser futura", ex.getMessage());
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    @DisplayName("Caso límite: no permite una fecha nula")
    void agendarCita_fechaNula_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(10L, 20L, null, "Control"));
        assertEquals("La fecha de la cita debe ser futura", ex.getMessage());
    }

    @Test
    @DisplayName("Caso límite: el motivo es obligatorio")
    void agendarCita_motivoVacio_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(10L, 20L, futuro, "   "));
        assertEquals("El motivo de la cita es obligatorio", ex.getMessage());
    }

    @Test
    @DisplayName("Seguridad: un usuario con rol CLIENTE no puede ser asignado como veterinario")
    void agendarCita_usuarioSinRolVeterinario_lanzaExcepcion() {
        Usuario cliente = Usuario.builder().id(30L).email("cliente@correo.cl").rol(Rol.CLIENTE).build();
        when(usuarioRepository.findById(30L)).thenReturn(Optional.of(cliente));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(10L, 30L, futuro, "Control"));
        assertEquals("El usuario indicado no es un veterinario", ex.getMessage());
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    @DisplayName("Control de horarios: no permite dos citas del mismo veterinario a la misma hora")
    void agendarCita_conflictoDeHorario_lanzaExcepcion() {
        when(citaRepository.existsByVeterinarioIdAndFechaHoraAndEstado(20L, futuro, "Agendada")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.agendarCita(10L, 20L, futuro, "Control"));
        assertEquals("El veterinario ya tiene una cita agendada en ese horario", ex.getMessage());
        verify(citaRepository, never()).save(any(Cita.class));
    }

    // ---------- consultas ----------

    @Test
    @DisplayName("Obtiene una cita existente por id")
    void obtenerCitaPorId_existente_devuelveCita() {
        Cita cita = Cita.builder().id(5L).estado("Agendada").build();
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));

        Optional<Cita> resultado = citaService.obtenerCitaPorId(5L);

        assertTrue(resultado.isPresent());
        assertSame(cita, resultado.get());
    }

    @Test
    @DisplayName("Devuelve vacío si la cita no existe")
    void obtenerCitaPorId_inexistente_devuelveVacio() {
        assertTrue(citaService.obtenerCitaPorId(404L).isEmpty());
    }

    @Test
    @DisplayName("Lista las citas de una mascota")
    void listarCitasPorMascota_devuelveLasCitas() {
        when(citaRepository.findByMascotaId(10L)).thenReturn(List.of(new Cita(), new Cita()));
        assertEquals(2, citaService.listarCitasPorMascota(10L).size());
    }

    @Test
    @DisplayName("Lista las citas de un veterinario")
    void listarCitasPorVeterinario_devuelveLasCitas() {
        when(citaRepository.findByVeterinarioId(20L)).thenReturn(List.of(new Cita()));
        assertEquals(1, citaService.listarCitasPorVeterinario(20L).size());
    }

    @Test
    @DisplayName("Lista las citas entre dos fechas")
    void listarCitasEntreFechas_devuelveLasCitas() {
        LocalDateTime inicio = futuro;
        LocalDateTime fin = futuro.plusDays(7);
        when(citaRepository.findByFechaHoraBetween(inicio, fin)).thenReturn(List.of(new Cita(), new Cita(), new Cita()));
        assertEquals(3, citaService.listarCitasEntreFechas(inicio, fin).size());
    }

    // ---------- cambio de estado ----------

    @ParameterizedTest(name = "Estado válido: {0}")
    @ValueSource(strings = {"Agendada", "Completada", "Cancelada"})
    @DisplayName("Actualiza la cita a cada estado válido")
    void actualizarEstadoCita_estadoValido_actualiza(String estado) {
        Cita cita = Cita.builder().id(5L).estado("Agendada").build();
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));

        Cita resultado = citaService.actualizarEstadoCita(5L, estado);

        assertEquals(estado, resultado.getEstado());
    }

    @Test
    @DisplayName("Caso límite: rechaza un estado que no existe")
    void actualizarEstadoCita_estadoInvalido_lanzaExcepcion() {
        Cita cita = Cita.builder().id(5L).estado("Agendada").build();
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.actualizarEstadoCita(5L, "Inventado"));
        assertTrue(ex.getMessage().startsWith("Estado de cita"));
        assertEquals("Agendada", cita.getEstado());
    }

    @Test
    @DisplayName("Caso límite: rechaza un estado nulo")
    void actualizarEstadoCita_estadoNulo_lanzaExcepcion() {
        Cita cita = Cita.builder().id(5L).estado("Agendada").build();
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.actualizarEstadoCita(5L, null));
        assertTrue(ex.getMessage().startsWith("Estado de cita"));
    }

    @Test
    @DisplayName("Falla si la cita a actualizar no existe")
    void actualizarEstadoCita_citaInexistente_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.actualizarEstadoCita(404L, "Completada"));
        assertEquals("Cita no encontrada", ex.getMessage());
    }

    // ---------- cancelación ----------

    @Test
    @DisplayName("Cancelar una cita la marca como Cancelada y conserva el historial")
    void cancelarCita_existente_marcaCanceladaSinEliminar() {
        Cita cita = Cita.builder().id(5L).estado("Agendada").build();
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));

        citaService.cancelarCita(5L);

        assertEquals("Cancelada", cita.getEstado());
        verify(citaRepository).save(cita);
        verify(citaRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Falla al cancelar una cita que no existe")
    void cancelarCita_inexistente_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.cancelarCita(404L));
        assertEquals("Cita no encontrada", ex.getMessage());
    }

    @Test
    @DisplayName("Caso límite: no se puede cancelar una cita ya completada")
    void cancelarCita_completada_lanzaExcepcion() {
        Cita cita = Cita.builder().id(5L).estado("Completada").build();
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> citaService.cancelarCita(5L));
        assertEquals("No se puede cancelar una cita completada", ex.getMessage());
        assertEquals("Completada", cita.getEstado());
    }
}
