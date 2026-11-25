package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.ExpedienteMedico;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.ExpedienteMedicoRepository;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ExpedienteMedicoServiceImpl implements ExpedienteMedicoService {

    @Autowired
    private ExpedienteMedicoRepository expedienteMedicoRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public ExpedienteMedico agregarRegistro(Long mascotaId, Long veterinarioId, ExpedienteMedico registro) {
        // Registro completo: descripción y diagnóstico son obligatorios
        if (registro == null) {
            throw new IllegalArgumentException("El registro médico es obligatorio");
        }
        if (registro.getDescripcion() == null || registro.getDescripcion().isBlank()) {
            throw new IllegalArgumentException("La descripción del registro es obligatoria");
        }
        if (registro.getDiagnostico() == null || registro.getDiagnostico().isBlank()) {
            throw new IllegalArgumentException("El diagnóstico del registro es obligatorio");
        }

        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        Usuario veterinario = usuarioRepository.findById(veterinarioId)
                .orElseThrow(() -> new IllegalArgumentException("Veterinario no encontrado"));

        // Solo un veterinario (rol EMPLEADO) puede escribir en el expediente clínico
        if (veterinario.getRol() != Rol.EMPLEADO) {
            throw new IllegalArgumentException("Solo un veterinario puede registrar en el expediente");
        }

        registro.setMascota(mascota);
        registro.setVeterinario(veterinario);
        registro.setFecha(LocalDateTime.now());

        return expedienteMedicoRepository.save(registro);
    }

    @Override
    public Optional<ExpedienteMedico> obtenerRegistroPorId(Long id) {
        return expedienteMedicoRepository.findById(id);
    }

    @Override
    public List<ExpedienteMedico> listarRegistrosPorMascota(Long mascotaId) {
        return expedienteMedicoRepository.findByMascotaId(mascotaId);
    }

    @Override
    public void eliminarRegistro(Long id) {
        expedienteMedicoRepository.deleteById(id);
    }
}
