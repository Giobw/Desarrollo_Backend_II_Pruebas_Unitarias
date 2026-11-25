package com.petsalud.backendpetsalud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de las entidades clave: construcción, relaciones entre ellas y consistencia de datos.
 * Nota: no se usa equals/toString sobre grafos con referencias bidireccionales (evita recursión infinita).
 */
@DisplayName("Entidades del dominio PetSalud")
class EntidadesTest {

    @Test
    @DisplayName("Cita: se construye con mascota, veterinario, fecha, motivo y estado")
    void cita_seConstruyeConSusRelaciones() {
        Mascota mascota = Mascota.builder().id(1L).nombre("Luna").build();
        Usuario vet = Usuario.builder().id(2L).rol(Rol.EMPLEADO).build();
        LocalDateTime fecha = LocalDateTime.now().plusDays(2);

        Cita cita = Cita.builder().id(9L).fechaHora(fecha).estado("Agendada").motivo("Control")
                .mascota(mascota).veterinario(vet).build();

        assertEquals("Agendada", cita.getEstado());
        assertEquals(fecha, cita.getFechaHora());
        assertSame(mascota, cita.getMascota());
        assertSame(vet, cita.getVeterinario());
    }

    @Test
    @DisplayName("Cita: el estado puede cambiar durante su ciclo de vida")
    void cita_cambioDeEstado() {
        Cita cita = Cita.builder().estado("Agendada").build();

        cita.setEstado("Completada");

        assertEquals("Completada", cita.getEstado());
    }

    @Test
    @DisplayName("ExpedienteMedico: registra diagnóstico, tratamiento y veterinario responsable")
    void expediente_registroCompleto() {
        Usuario vet = Usuario.builder().id(2L).rol(Rol.EMPLEADO).build();
        Mascota mascota = Mascota.builder().id(1L).build();

        ExpedienteMedico exp = ExpedienteMedico.builder().descripcion("Control").diagnostico("Sano")
                .tratamiento("Vacuna").fecha(LocalDateTime.now()).mascota(mascota).veterinario(vet).build();

        assertEquals("Sano", exp.getDiagnostico());
        assertEquals("Vacuna", exp.getTratamiento());
        assertSame(vet, exp.getVeterinario());
        assertSame(mascota, exp.getMascota());
        assertNotNull(exp.getFecha());
    }

    @Test
    @DisplayName("Mascota: se asigna a un propietario y conserva su perfil")
    void mascota_asignacionAPropietario() {
        Usuario dueno = Usuario.builder().id(1L).rol(Rol.CLIENTE).build();

        Mascota mascota = Mascota.builder().nombre("Rex").especie("Perro").raza("Labrador")
                .sexo("Macho").fechaNacimiento(LocalDate.of(2020, 3, 1)).propietario(dueno).build();

        assertSame(dueno, mascota.getPropietario());
        assertEquals("Perro", mascota.getEspecie());
        assertEquals(LocalDate.of(2020, 3, 1), mascota.getFechaNacimiento());
    }

    @Test
    @DisplayName("Mascota: puede tener varias citas y varios registros en su ficha")
    void mascota_consistenciaConCitasYFichas() {
        Mascota mascota = Mascota.builder().id(1L).nombre("Luna").build();
        Cita cita1 = Cita.builder().estado("Agendada").mascota(mascota).build();
        Cita cita2 = Cita.builder().estado("Completada").mascota(mascota).build();
        ExpedienteMedico exp = ExpedienteMedico.builder().diagnostico("Sano").mascota(mascota).build();

        mascota.setCitas(new ArrayList<>(List.of(cita1, cita2)));
        mascota.setExpedientesMedicos(new ArrayList<>(List.of(exp)));

        assertEquals(2, mascota.getCitas().size());
        assertEquals(1, mascota.getExpedientesMedicos().size());
        assertSame(mascota, mascota.getCitas().get(0).getMascota());
        assertSame(mascota, mascota.getExpedientesMedicos().get(0).getMascota());
    }

    @Test
    @DisplayName("Usuario: un propietario puede tener múltiples mascotas")
    void usuario_conVariasMascotas() {
        Usuario dueno = Usuario.builder().id(1L).rol(Rol.CLIENTE).build();
        Mascota m1 = Mascota.builder().nombre("Luna").propietario(dueno).build();
        Mascota m2 = Mascota.builder().nombre("Rex").propietario(dueno).build();

        dueno.setMascotas(new ArrayList<>(List.of(m1, m2)));

        assertEquals(2, dueno.getMascotas().size());
        assertSame(dueno, dueno.getMascotas().get(1).getPropietario());
    }

    @Test
    @DisplayName("Venta: vincula cliente y productos, y el total corresponde a la suma de precios")
    void venta_vinculoConProductosYTotal() {
        Usuario cliente = Usuario.builder().id(1L).rol(Rol.CLIENTE).build();
        Producto p1 = Producto.builder().nombre("Shampoo").precio(new BigDecimal("5000")).stock(3).build();
        Producto p2 = Producto.builder().nombre("Collar").precio(new BigDecimal("7000")).stock(2).build();
        BigDecimal total = p1.getPrecio().add(p2.getPrecio());

        Venta venta = Venta.builder().usuario(cliente).productos(List.of(p1, p2)).total(total)
                .fecha(LocalDateTime.now()).build();

        assertSame(cliente, venta.getUsuario());
        assertEquals(2, venta.getProductos().size());
        assertEquals(0, new BigDecimal("12000").compareTo(venta.getTotal()));
    }

    @Test
    @DisplayName("Producto: el stock se actualiza y puede llegar a cero")
    void producto_controlDeStock() {
        Producto producto = Producto.builder().nombre("Vacuna").precio(new BigDecimal("15000"))
                .stock(1).categoria("Medicamentos").build();

        producto.setStock(producto.getStock() - 1);

        assertEquals(0, producto.getStock());
        assertEquals("Medicamentos", producto.getCategoria());
    }

    @Test
    @DisplayName("Rol: el sistema define los tres perfiles del caso")
    void rol_tresPerfiles() {
        assertEquals(3, Rol.values().length);
        assertEquals(Rol.GERENTE, Rol.valueOf("GERENTE"));
    }
}
