package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Cita;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.CitaRepository;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CitaServiceImpl implements CitaService {

    public static final String AGENDADA = "Agendada";
    public static final String COMPLETADA = "Completada";
    public static final String CANCELADA = "Cancelada";
    private static final List<String> ESTADOS_VALIDOS = List.of(AGENDADA, COMPLETADA, CANCELADA);

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public Cita agendarCita(Long mascotaId, Long veterinarioId, LocalDateTime fechaHora, String motivo) {
        if (fechaHora == null || !fechaHora.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("La fecha de la cita debe ser futura");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de la cita es obligatorio");
        }

        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        Usuario veterinario = usuarioRepository.findById(veterinarioId)
                .orElseThrow(() -> new IllegalArgumentException("Veterinario no encontrado"));

        // Solo el personal con rol EMPLEADO (veterinario) puede atender citas
        if (veterinario.getRol() != Rol.EMPLEADO) {
            throw new IllegalArgumentException("El usuario indicado no es un veterinario");
        }

        // Control de horarios: evita conflictos y sobrecarga del veterinario
        if (citaRepository.existsByVeterinarioIdAndFechaHoraAndEstado(veterinarioId, fechaHora, AGENDADA)) {
            throw new IllegalArgumentException("El veterinario ya tiene una cita agendada en ese horario");
        }

        Cita cita = Cita.builder()
                .mascota(mascota)
                .veterinario(veterinario)
                .fechaHora(fechaHora)
                .motivo(motivo)
                .estado(AGENDADA)
                .build();

        return citaRepository.save(cita);
    }

    @Override
    public Optional<Cita> obtenerCitaPorId(Long id) {
        return citaRepository.findById(id);
    }

    @Override
    public Cita actualizarEstadoCita(Long id, String nuevoEstado) {
        if (nuevoEstado == null || !ESTADOS_VALIDOS.contains(nuevoEstado)) {
            throw new IllegalArgumentException("Estado de cita inválido: " + nuevoEstado);
        }
        return citaRepository.findById(id).map(cita -> {
            cita.setEstado(nuevoEstado);
            return citaRepository.save(cita);
        }).orElseThrow(() -> new IllegalArgumentException("Cita no encontrada"));
    }

    @Override
    public void cancelarCita(Long id) {
        Cita cita = citaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada"));
        if (COMPLETADA.equals(cita.getEstado())) {
            throw new IllegalArgumentException("No se puede cancelar una cita completada");
        }
        // Se conserva el historial: la cita pasa a estado Cancelada en lugar de eliminarse
        cita.setEstado(CANCELADA);
        citaRepository.save(cita);
    }

    @Override
    public List<Cita> listarCitasPorMascota(Long mascotaId) {
        return citaRepository.findByMascotaId(mascotaId);
    }

    @Override
    public List<Cita> listarCitasPorVeterinario(Long veterinarioId) {
        return citaRepository.findByVeterinarioId(veterinarioId);
    }

    @Override
    public List<Cita> listarCitasEntreFechas(LocalDateTime inicio, LocalDateTime fin) {
        return citaRepository.findByFechaHoraBetween(inicio, fin);
    }
}
