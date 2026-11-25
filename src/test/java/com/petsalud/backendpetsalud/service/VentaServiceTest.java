package com.petsalud.backendpetsalud.service;

import com.petsalud.backendpetsalud.entity.Producto;
import com.petsalud.backendpetsalud.entity.Rol;
import com.petsalud.backendpetsalud.entity.Usuario;
import com.petsalud.backendpetsalud.entity.Venta;
import com.petsalud.backendpetsalud.repository.ProductoRepository;
import com.petsalud.backendpetsalud.repository.UsuarioRepository;
import com.petsalud.backendpetsalud.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VentaService: transacciones, productos y control de stock")
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private VentaServiceImpl ventaService;

    private Usuario cliente;

    @BeforeEach
    void setUp() {
        cliente = Usuario.builder().id(1L).email("cliente@correo.cl").rol(Rol.CLIENTE).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Producto producto(Long id, String nombre, String precio, Integer stock) {
        return Producto.builder().id(id).nombre(nombre).precio(precio == null ? null : new BigDecimal(precio))
                .stock(stock).categoria("Accesorios").build();
    }

    @Test
    @DisplayName("Realiza una venta, calcula el total y la vincula al cliente y a los productos")
    void realizarVenta_productosConStock_calculaTotalYVincula() {
        Producto a = producto(1L, "Shampoo", "5000", 3);
        Producto b = producto(2L, "Collar", "7000", 2);
        List<Long> ids = List.of(1L, 2L);
        when(productoRepository.findAllById(ids)).thenReturn(Arrays.asList(a, b));

        Venta venta = ventaService.realizarVenta(1L, ids);

        assertEquals(0, new BigDecimal("12000").compareTo(venta.getTotal()));
        assertSame(cliente, venta.getUsuario());
        assertEquals(2, venta.getProductos().size());
        assertNotNull(venta.getFecha());
        verify(ventaRepository, times(1)).save(any(Venta.class));
    }

    @Test
    @DisplayName("Control de stock: descuenta una unidad de cada producto vendido")
    void realizarVenta_descuentaStock() {
        Producto a = producto(1L, "Shampoo", "5000", 3);
        Producto b = producto(2L, "Collar", "7000", 2);
        List<Long> ids = List.of(1L, 2L);
        when(productoRepository.findAllById(ids)).thenReturn(Arrays.asList(a, b));

        ventaService.realizarVenta(1L, ids);

        assertEquals(2, a.getStock());
        assertEquals(1, b.getStock());
        verify(productoRepository, times(2)).save(any(Producto.class));
    }

    @Test
    @DisplayName("Caso límite: con exactamente 1 unidad de stock la venta es posible y deja stock en 0")
    void realizarVenta_stockExactoUno_dejaStockEnCero() {
        Producto a = producto(1L, "Vacuna", "15000", 1);
        List<Long> ids = List.of(1L);
        when(productoRepository.findAllById(ids)).thenReturn(List.of(a));

        Venta venta = ventaService.realizarVenta(1L, ids);

        assertEquals(0, a.getStock());
        assertEquals(0, new BigDecimal("15000").compareTo(venta.getTotal()));
    }

    @Test
    @DisplayName("Falla si el usuario no existe")
    void realizarVenta_usuarioInexistente_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(99L, List.of(1L)));
        assertEquals("Usuario no encontrado", ex.getMessage());
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    @DisplayName("Falla si algún producto no existe")
    void realizarVenta_productoInexistente_lanzaExcepcion() {
        List<Long> ids = List.of(1L, 2L);
        when(productoRepository.findAllById(ids)).thenReturn(List.of(producto(1L, "Shampoo", "5000", 3)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(1L, ids));
        assertEquals("Algunos productos no fueron encontrados", ex.getMessage());
    }

    @Test
    @DisplayName("Falla si un producto no tiene stock")
    void realizarVenta_productoSinStock_lanzaExcepcion() {
        List<Long> ids = List.of(1L);
        when(productoRepository.findAllById(ids)).thenReturn(List.of(producto(1L, "Shampoo", "5000", 0)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(1L, ids));
        assertTrue(ex.getMessage().contains("Shampoo"));
        assertTrue(ex.getMessage().contains("no tiene stock disponible"));
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    @DisplayName("Caso límite: un producto con stock nulo se trata como sin stock")
    void realizarVenta_stockNulo_lanzaExcepcionControlada() {
        List<Long> ids = List.of(1L);
        when(productoRepository.findAllById(ids)).thenReturn(List.of(producto(1L, "Shampoo", "5000", null)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(1L, ids));
        assertTrue(ex.getMessage().contains("no tiene stock disponible"));
    }

    @Test
    @DisplayName("Caso límite: un producto sin precio no puede venderse")
    void realizarVenta_precioNulo_lanzaExcepcionControlada() {
        List<Long> ids = List.of(1L);
        when(productoRepository.findAllById(ids)).thenReturn(List.of(producto(1L, "Shampoo", null, 3)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(1L, ids));
        assertTrue(ex.getMessage().contains("precio"));
    }

    @Test
    @DisplayName("Integridad: si un producto no tiene stock, no se descuenta el stock de los demás")
    void realizarVenta_unProductoSinStock_noModificaElStockDeLosOtros() {
        Producto conStock = producto(1L, "Shampoo", "5000", 5);
        Producto sinStock = producto(2L, "Collar", "7000", 0);
        List<Long> ids = List.of(1L, 2L);
        when(productoRepository.findAllById(ids)).thenReturn(Arrays.asList(conStock, sinStock));

        assertThrows(IllegalArgumentException.class, () -> ventaService.realizarVenta(1L, ids));

        assertEquals(5, conStock.getStock());
        verify(productoRepository, never()).save(any(Producto.class));
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    @DisplayName("Caso límite: una venta sin productos no es válida")
    void realizarVenta_listaVacia_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(1L, new ArrayList<>()));
        assertEquals("Debe seleccionar al menos un producto", ex.getMessage());
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    @DisplayName("Caso límite: la lista de productos nula no es válida")
    void realizarVenta_listaNula_lanzaExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ventaService.realizarVenta(1L, null));
        assertEquals("Debe seleccionar al menos un producto", ex.getMessage());
    }

    @Test
    @DisplayName("Obtiene una venta existente por id")
    void obtenerVentaPorId_existente_devuelveVenta() {
        Venta venta = Venta.builder().id(3L).total(new BigDecimal("1000")).build();
        when(ventaRepository.findById(3L)).thenReturn(Optional.of(venta));

        assertSame(venta, ventaService.obtenerVentaPorId(3L).orElseThrow());
    }

    @Test
    @DisplayName("Devuelve vacío si la venta no existe")
    void obtenerVentaPorId_inexistente_devuelveVacio() {
        assertTrue(ventaService.obtenerVentaPorId(404L).isEmpty());
    }

    @Test
    @DisplayName("Lista las ventas de un usuario")
    void listarVentasPorUsuario_devuelveLasVentas() {
        when(ventaRepository.findByUsuarioId(1L)).thenReturn(List.of(new Venta(), new Venta()));
        assertEquals(2, ventaService.listarVentasPorUsuario(1L).size());
    }

    @Test
    @DisplayName("Lista todas las ventas")
    void listarTodasLasVentas_devuelveTodas() {
        when(ventaRepository.findAll()).thenReturn(List.of(new Venta()));
        assertEquals(1, ventaService.listarTodasLasVentas().size());
    }
}
