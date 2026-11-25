package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class MascotaServiceImpl implements MascotaService {

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public Mascota registrarMascota(Mascota mascota, Long propietarioId) {
        // Perfil completo: nombre y especie son obligatorios; la fecha de nacimiento no puede ser futura
        if (mascota == null || mascota.getNombre() == null || mascota.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la mascota es obligatorio");
        }
        if (mascota.getEspecie() == null || mascota.getEspecie().isBlank()) {
            throw new IllegalArgumentException("La especie de la mascota es obligatoria");
        }
        if (mascota.getFechaNacimiento() != null && mascota.getFechaNacimiento().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de nacimiento no puede ser futura");
        }

        Usuario propietario = usuarioRepository.findById(propietarioId)
                .orElseThrow(() -> new IllegalArgumentException("Propietario no encontrado"));

        mascota.setPropietario(propietario);
        return mascotaRepository.save(mascota);
    }

    @Override
    public Optional<Mascota> obtenerMascotaPorId(Long id) {
        return mascotaRepository.findById(id);
    }

    @Override
    public Mascota actualizarMascota(Long id, Mascota mascotaActualizada) {
        return mascotaRepository.findById(id).map(mascota -> {
            mascota.setNombre(mascotaActualizada.getNombre());
            mascota.setEspecie(mascotaActualizada.getEspecie());
            mascota.setRaza(mascotaActualizada.getRaza());
            mascota.setFechaNacimiento(mascotaActualizada.getFechaNacimiento());
            mascota.setSexo(mascotaActualizada.getSexo());
            return mascotaRepository.save(mascota);
        }).orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));
    }

    @Override
    public void eliminarMascota(Long id) {
        mascotaRepository.deleteById(id);
    }

    @Override
    public List<Mascota> listarMascotasPorPropietario(Long propietarioId) {
        return mascotaRepository.findByPropietarioId(propietarioId);
    }
}
