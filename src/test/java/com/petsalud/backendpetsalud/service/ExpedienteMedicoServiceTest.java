package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.ExpedienteMedico;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.ExpedienteMedicoRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExpedienteMedicoService: historial clínico de las mascotas")
class ExpedienteMedicoServiceTest {

    @Mock
    private ExpedienteMedicoRepository expedienteMedicoRepository;
    @Mock
    private MascotaRepository mascotaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ExpedienteMedicoServiceImpl expedienteService;

    private Mascota mascota;
    private Usuario veterinario;

    @BeforeEach
    void setUp() {
        mascota = Mascota.builder().id(10L).nombre("Luna").especie("Gato").build();
        veterinario = Usuario.builder().id(20L).email("vet@petsalud.cl").rol(Rol.EMPLEADO).build();

        when(mascotaRepository.findById(10L)).thenReturn(Optional.of(mascota));
        when(usuarioRepository.findById(20L)).thenReturn(Optional.of(veterinario));
        when(expedienteMedicoRepository.save(any(ExpedienteMedico.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ExpedienteMedico registroCompleto() {
        return ExpedienteMedico.builder()
                .descripcion("Control anual")
                .diagnostico("Sano")
                .tratamiento("Vacuna antirrábica")
                .build();
    }

    @Test
    @DisplayName("Agrega un registro completo asociado a la mascota y al veterinario")
    void agregarRegistro_registroCompleto_guardaConRelaciones() {
        ExpedienteMedico guardado = expedienteService.agregarRegistro(10L, 20L, registroCompleto());

        assertSame(mascota, guardado.getMascota());
        assertSame(veterinario, guardado.getVeterinario());
        assertNotNull(guardado.getFecha());
        assertEquals("Sano", guardado.getDiagnostico());
        verify(expedienteMedicoRepository, times(1)).save(any(ExpedienteMedico.class));
    }

    @Test
    @DisplayName("Falla si la mascota no existe")
    void agregarRegistro_mascotaInexistente_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> expedienteService.agregarRegistro(99L, 20L, registroCompleto()));
        assertEquals("Mascota no encontrada", ex.getMessage());
    }

    @Test
    @DisplayName("Falla si el veterinario no existe")
    void agregarRegistro_veterinarioInexistente_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> expedienteService.agregarRegistro(10L, 99L, registroCompleto()));
        assertEquals("Veterinario no encontrado", ex.getMessage());
    }

    @Test
    @DisplayName("Seguridad: un CLIENTE no puede escribir en el expediente clínico")
    void agregarRegistro_usuarioSinRolVeterinario_lanzaExcepcion() {
        Usuario cliente = Usuario.builder().id(30L).email("cliente@correo.cl").rol(Rol.CLIENTE).build();
        when(usuarioRepository.findById(30L)).thenReturn(Optional.of(cliente));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> expedienteService.agregarRegistro(10L, 30L, registroCompleto()));
        assertEquals("Solo un veterinario puede registrar en el expediente", ex.getMessage());
        verify(expedienteMedicoRepository, never()).save(any(ExpedienteMedico.class));
    }

    @Test
    @DisplayName("Caso límite: la descripción es obligatoria")
    void agregarRegistro_descripcionVacia_lanzaExcepcion() {
        ExpedienteMedico registro = ExpedienteMedico.builder().descripcion("  ").diagnostico("Sano").build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> expedienteService.agregarRegistro(10L, 20L, registro));
        assertEquals("La descripción del registro es obligatoria", ex.getMessage());
        verify(expedienteMedicoRepository, never()).save(any(ExpedienteMedico.class));
    }

    @Test
    @DisplayName("Caso límite: el diagnóstico es obligatorio")
    void agregarRegistro_diagnosticoNulo_lanzaExcepcion() {
        ExpedienteMedico registro = ExpedienteMedico.builder().descripcion("Control").build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> expedienteService.agregarRegistro(10L, 20L, registro));
        assertEquals("El diagnóstico del registro es obligatorio", ex.getMessage());
    }

    @Test
    @DisplayName("Caso límite: el registro no puede ser nulo")
    void agregarRegistro_registroNulo_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> expedienteService.agregarRegistro(10L, 20L, null));
        assertEquals("El registro médico es obligatorio", ex.getMessage());
    }

    @Test
    @DisplayName("Obtiene un registro existente por id")
    void obtenerRegistroPorId_existente_devuelveRegistro() {
        ExpedienteMedico registro = registroCompleto();
        when(expedienteMedicoRepository.findById(7L)).thenReturn(Optional.of(registro));

        Optional<ExpedienteMedico> resultado = expedienteService.obtenerRegistroPorId(7L);

        assertTrue(resultado.isPresent());
        assertSame(registro, resultado.get());
    }

    @Test
    @DisplayName("Devuelve vacío si el registro no existe")
    void obtenerRegistroPorId_inexistente_devuelveVacio() {
        assertTrue(expedienteService.obtenerRegistroPorId(404L).isEmpty());
    }

    @Test
    @DisplayName("Lista el historial completo de una mascota")
    void listarRegistrosPorMascota_devuelveHistorial() {
        when(expedienteMedicoRepository.findByMascotaId(10L))
                .thenReturn(List.of(registroCompleto(), registroCompleto()));

        assertEquals(2, expedienteService.listarRegistrosPorMascota(10L).size());
    }

    @Test
    @DisplayName("Una mascota sin historial devuelve una lista vacía")
    void listarRegistrosPorMascota_sinHistorial_devuelveListaVacia() {
        assertTrue(expedienteService.listarRegistrosPorMascota(10L).isEmpty());
    }

    @Test
    @DisplayName("Elimina un registro por id")
    void eliminarRegistro_invocaAlRepositorio() {
        expedienteService.eliminarRegistro(7L);
        verify(expedienteMedicoRepository, times(1)).deleteById(7L);
    }
}
