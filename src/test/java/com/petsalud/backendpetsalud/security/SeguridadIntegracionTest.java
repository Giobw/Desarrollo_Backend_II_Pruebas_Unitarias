package com.petsalud.backendpetsalud.security;

import com.jayway.jsonpath.JsonPath;
import com.petsalud.backendpetsalud.entity.Mascota;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.repository.MascotaRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Valida de punta a punta la autenticación (login + JWT) y la autorización por rol,
 * usando MockMvc y una base H2 en memoria (perfil "test"): no requiere MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Seguridad: autenticación JWT y autorización por rol (MockMvc + H2)")
class SeguridadIntegracionTest {

    private static final String CLAVE = "Clave12345";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private MascotaRepository mascotaRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario gerente;
    private Usuario empleado;
    private Usuario cliente;
    private Usuario otroCliente;
    private Mascota mascotaDelCliente;

    @BeforeEach
    void cargarDatos() {
        mascotaRepository.deleteAll();
        usuarioRepository.deleteAll();
        gerente = crearUsuario("gerente@test.cl", Rol.GERENTE);
        empleado = crearUsuario("empleado@test.cl", Rol.EMPLEADO);
        cliente = crearUsuario("cliente@test.cl", Rol.CLIENTE);
        otroCliente = crearUsuario("otro@test.cl", Rol.CLIENTE);
        mascotaDelCliente = mascotaRepository.save(
                Mascota.builder().nombre("Firulais").especie("Perro").propietario(cliente).build());
    }

    private Usuario crearUsuario(String email, Rol rol) {
        return usuarioRepository.save(Usuario.builder().email(email).password(passwordEncoder.encode(CLAVE))
                .nombre("Nombre").apellido("Apellido").rol(rol).build());
    }

    private String login(String email, String clave) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + clave + "\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    private String token(Usuario usuario) throws Exception {
        String json = login(usuario.getEmail(), CLAVE);
        String token = JsonPath.read(json, "$.token");
        return "Bearer " + token;
    }

    private String productoJson(String nombre) {
        return "{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Prueba\",\"precio\":5990,\"stock\":10,\"categoria\":\"Accesorios\"}";
    }

    // ---------------- Autenticación ----------------

    @Test
    @DisplayName("Login válido devuelve 200, un token Bearer y el rol del usuario")
    void login_credencialesValidas_devuelveToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gerente@test.cl\",\"password\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.rol").value("GERENTE"));
    }

    @Test
    @DisplayName("Login con contraseña incorrecta devuelve 401 con mensaje genérico")
    void login_credencialesInvalidas_devuelve401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gerente@test.cl\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas"));
    }

    @Test
    @DisplayName("Login con un email inexistente devuelve el mismo 401 (no revela si el usuario existe)")
    void login_emailInexistente_devuelve401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@test.cl\",\"password\":\"" + CLAVE + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas"));
    }

    @Test
    @DisplayName("Caso límite: un login con email mal formado es rechazado por validación (400)")
    void login_emailMalFormado_devuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"' OR '1'='1\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Fuerza bruta: tras 5 intentos fallidos el login se bloquea (429), incluso con la clave correcta")
    void login_intentosFallidos_bloqueaCuenta() throws Exception {
        String email = "bloqueo@test.cl";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"mala\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + CLAVE + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("El registro público siempre crea un CLIENTE, aunque se envíe otro rol")
    void registro_ignoraElRolEnviado() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nuevo@test.cl\",\"password\":\"" + CLAVE
                                + "\",\"nombre\":\"Nuevo\",\"apellido\":\"Cliente\",\"rol\":\"GERENTE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    @DisplayName("El registro no devuelve la contraseña")
    void registro_noDevuelvePassword() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sinpass@test.cl\",\"password\":\"" + CLAVE
                                + "\",\"nombre\":\"Ana\",\"apellido\":\"Perez\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("XSS: el nombre con una etiqueta script se devuelve escapado")
    void registro_nombreConScript_seEscapa() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"xss@test.cl\",\"password\":\"" + CLAVE
                                + "\",\"nombre\":\"<script>alert(1)</script>\",\"apellido\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    @DisplayName("Registro con una contraseña corta es rechazado (400)")
    void registro_passwordCorta_devuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"corta@test.cl\",\"password\":\"123\",\"nombre\":\"A\",\"apellido\":\"B\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------- Token ----------------

    @Test
    @DisplayName("Sin token, un endpoint protegido responde 401")
    void sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token alterado responde 401")
    void tokenAlterado_devuelve401() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un endpoint exige el esquema Bearer: otro esquema responde 401")
    void esquemaIncorrecto_devuelve401() throws Exception {
        String token = token(gerente).replace("Bearer ", "");
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Token " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF: un POST sin token (como el de un formulario externo) responde 401")
    void postSinToken_devuelve401() throws Exception {
        mockMvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(productoJson("X")))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- Autorización por rol ----------------

    @Test
    @DisplayName("Listar usuarios: CLIENTE y EMPLEADO reciben 403, GERENTE recibe 200")
    void listarUsuarios_soloGerente() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", token(cliente)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios").header("Authorization", token(empleado)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios").header("Authorization", token(gerente)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("IDOR: un CLIENTE ve su propio usuario (200) pero no el de otro cliente (403)")
    void verUsuario_clienteSoloElPropio() throws Exception {
        mockMvc.perform(get("/api/usuarios/" + cliente.getId()).header("Authorization", token(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@test.cl"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get("/api/usuarios/" + otroCliente.getId()).header("Authorization", token(cliente)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un EMPLEADO puede consultar a cualquier usuario")
    void verUsuario_empleadoPuedeConsultarCualquiera() throws Exception {
        mockMvc.perform(get("/api/usuarios/" + cliente.getId()).header("Authorization", token(empleado)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Productos: CLIENTE no crea (403), EMPLEADO crea (200)")
    void crearProducto_soloPersonal() throws Exception {
        String nombre = "Collar-" + System.nanoTime();
        mockMvc.perform(post("/api/productos").header("Authorization", token(cliente))
                        .contentType(MediaType.APPLICATION_JSON).content(productoJson(nombre)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/productos").header("Authorization", token(empleado))
                        .contentType(MediaType.APPLICATION_JSON).content(productoJson(nombre)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(nombre));
    }

    @Test
    @DisplayName("Productos: EMPLEADO no elimina (403), GERENTE elimina (204)")
    void eliminarProducto_soloGerente() throws Exception {
        MvcResult creado = mockMvc.perform(post("/api/productos").header("Authorization", token(empleado))
                        .contentType(MediaType.APPLICATION_JSON).content(productoJson("Eliminable-" + System.nanoTime())))
                .andExpect(status().isOk()).andReturn();
        Number id = JsonPath.read(creado.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/productos/" + id.longValue()).header("Authorization", token(empleado)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/productos/" + id.longValue()).header("Authorization", token(gerente)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Productos: cualquier usuario autenticado puede consultar el catálogo")
    void listarProductos_cualquierAutenticado() throws Exception {
        mockMvc.perform(get("/api/productos").header("Authorization", token(cliente)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ventas globales: CLIENTE recibe 403 y GERENTE recibe 200")
    void listarVentas_soloGerente() throws Exception {
        mockMvc.perform(get("/api/ventas").header("Authorization", token(cliente)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ventas").header("Authorization", token(gerente)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ventas por usuario: el cliente ve las suyas (200) y no las de otro (403)")
    void ventasPorUsuario_soloLasPropias() throws Exception {
        mockMvc.perform(get("/api/ventas/usuario/" + cliente.getId()).header("Authorization", token(cliente)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ventas/usuario/" + otroCliente.getId()).header("Authorization", token(cliente)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Mascotas: el dueño lista las suyas (200) y otro cliente recibe 403")
    void mascotasPorPropietario_soloElDueno() throws Exception {
        mockMvc.perform(get("/api/mascotas/propietario/" + cliente.getId()).header("Authorization", token(cliente)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/mascotas/propietario/" + cliente.getId()).header("Authorization", token(otroCliente)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Mascota por id: el dueño accede (200) y otro cliente recibe 403")
    void mascotaPorId_soloElDueno() throws Exception {
        mockMvc.perform(get("/api/mascotas/" + mascotaDelCliente.getId()).header("Authorization", token(cliente)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/mascotas/" + mascotaDelCliente.getId()).header("Authorization", token(otroCliente)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Expedientes: un CLIENTE no puede crear registros clínicos (403)")
    void crearExpediente_clienteRecibe403() throws Exception {
        mockMvc.perform(post("/api/expedientes").param("mascotaId", "1").param("veterinarioId", "1")
                        .header("Authorization", token(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"x\",\"diagnostico\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Citas: un cliente no puede cambiar el estado de una cita (403)")
    void cambiarEstadoCita_clienteRecibe403() throws Exception {
        mockMvc.perform(put("/api/citas/1/estado").param("estado", "Completada")
                        .header("Authorization", token(cliente)))
                .andExpect(status().isForbidden());
    }
}
