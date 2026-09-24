package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestro;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.dto.ItemCarritoDto;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.model.ItemCarrito;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import com.nexusbattles.ms_ecommerce.service.ProductoNoAgregableException.Motivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESCUDO;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESPADA;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conEstado;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conNombre;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conPrecio;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conTiraje;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.enVenta;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.sinId;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.soloEnCreditos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El carrito sobre el catalogo maestro (R16): cada producto se resuelve contra
 * el catalogo del servicio productos y la linea guarda una instantanea suya.
 *
 * <p>El gestor de transacciones es un doble: aqui interesa donde empieza y
 * termina la transaccion, no la base de datos (eso lo prueba
 * {@code TiendaConCatalogoMaestroIT} con PostgreSQL de verdad).
 */
@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    private static final String USUARIO = "usr_123";

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private CatalogoMaestro catalogo;

    @Mock
    private PlatformTransactionManager transacciones;

    private CarritoService carritoService;

    @BeforeEach
    void preparar() {
        carritoService = new CarritoService(carritoRepository, catalogo, new CarritoMapper(), transacciones);
    }

    private static AgregarItemRequest pedir(String productoId, int cantidad) {
        AgregarItemRequest request = new AgregarItemRequest();
        request.setProductoId(productoId);
        request.setCantidad(cantidad);
        return request;
    }

    private Carrito carritoExistente() {
        Carrito carrito = new Carrito();
        carrito.setId(1L);
        carrito.setUsuarioId(USUARIO);
        carrito.setItems(new ArrayList<>());
        when(carritoRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.of(carrito));
        return carrito;
    }

    private void guardarDevuelveLoMismo() {
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    private static ItemCarrito linea(Carrito carrito, Long id, String referencia, int cantidad, String precio) {
        ItemCarrito linea = new ItemCarrito();
        linea.setId(id);
        linea.setCarrito(carrito);
        linea.setProductoRef(referencia);
        linea.setProductoNombre("Nombre viejo");
        linea.setMoneda(precio == null ? null : "COP");
        linea.setCantidad(cantidad);
        linea.setPrecioUnitario(precio == null ? null : new BigDecimal(precio));
        linea.calcularSubtotal();
        carrito.getItems().add(linea);
        carrito.recalcularTotal();
        return linea;
    }

    @Test
    void agregarProducto_nuevoItem_debeCrearYCalcularSubtotal() {
        // Arrange
        carritoExistente();
        when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA, "ARMA", "10000")));
        // Simulamos que al guardar, retorna el mismo carrito que se le pasó
        guardarDevuelveLoMismo();

        // Act
        CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 2));

        // Assert
        assertEquals(1, resultado.items().size());
        assertEquals(2, resultado.items().get(0).cantidad());
        assertEquals(new BigDecimal("20000"), resultado.total());
    }

    @Nested
    @DisplayName("reglas de venta")
    class ReglasDeVenta {

        @Test
        @DisplayName("un producto que el catalogo no tiene: inexistente, y el carrito ni se toca")
        void inexistente() {
            when(catalogo.producto("1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir("1", 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.INEXISTENTE));
            verifyNoInteractions(carritoRepository, transacciones);
        }

        @ParameterizedTest(name = "estado {0}")
        @NullSource
        @ValueSource(strings = {"SUSPENDIDO", "BORRADOR"})
        @DisplayName("suspendido, o en un estado que no es de venta: no disponible (RN-PRD-004)")
        void noDisponible(String estado) {
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conEstado(enVenta(ESPADA), estado)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.NO_DISPONIBLE));
            verifyNoInteractions(carritoRepository);
        }

        @Test
        @DisplayName("UNICO se vende igual que ACTIVO")
        void unicoSeVende() {
            carritoExistente();
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conEstado(enVenta(ESPADA), "UNICO")));

            assertThat(carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)).items()).hasSize(1);
        }

        @Test
        @DisplayName("tiraje 0: agotado (RF-CAR-007, valida existencias)")
        void agotado() {
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conTiraje(enVenta(ESPADA), 0)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.AGOTADO));
            verifyNoInteractions(carritoRepository);
        }

        @Test
        @DisplayName("sin tiraje no hay existencias que validar: tambien agotado")
        void sinTiraje() {
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conTiraje(enVenta(ESPADA), null)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.AGOTADO));
        }

        @Test
        @DisplayName("sin precio en moneda real: no se puede comprar en la tienda (RF-CAR-002)")
        void sinPrecioEnMonedaReal() {
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(soloEnCreditos(enVenta(ESPADA), 300)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.SIN_PRECIO_EN_MONEDA_REAL));
            verifyNoInteractions(carritoRepository);
        }

        @Test
        @DisplayName("precio en moneda real de 0: tampoco se compra en la tienda")
        void precioCeroEnMonedaReal() {
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conPrecio(enVenta(ESPADA), BigDecimal.ZERO)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.SIN_PRECIO_EN_MONEDA_REAL));
            verifyNoInteractions(carritoRepository);
        }

        @Test
        @DisplayName("las reglas se comprueban en orden: estado, existencias, precio")
        void ordenDeLasReglas() {
            ProductoDelCatalogo todoMal = conPrecio(conTiraje(conEstado(enVenta(ESPADA), "SUSPENDIDO"), 0), null);
            ProductoDelCatalogo agotadoYSinPrecio = conPrecio(conTiraje(enVenta(ESCUDO), 0), null);
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(todoMal));
            when(catalogo.producto(ESCUDO)).thenReturn(Optional.of(agotadoYSinPrecio));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.NO_DISPONIBLE));
            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESCUDO, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.AGOTADO));
        }

        @Test
        @DisplayName("catalogo caido: el fallo sale, y sin haber abierto transaccion ni tocado la base")
        void catalogoCaido() {
            when(catalogo.producto(ESPADA)).thenThrow(new CatalogoNoDisponibleException("caido"));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOf(CatalogoNoDisponibleException.class);
            verifyNoInteractions(carritoRepository, transacciones);
        }
    }

    @Nested
    @DisplayName("la linea y su instantanea")
    class Instantanea {

        @Test
        @DisplayName("guarda la referencia del catalogo, el nombre, el precio en moneda real y COP")
        void guardaLaInstantanea() {
            Carrito carrito = carritoExistente();
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA))
                    .thenReturn(Optional.of(conNombre(enVenta(ESPADA, "ARMA", "6000.00"), "Espada de fuego")));

            CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 3));

            ItemCarrito guardada = carrito.getItems().get(0);
            assertThat(guardada.getProductoRef()).isEqualTo(ESPADA);
            assertThat(guardada.getProductoNombre()).isEqualTo("Espada de fuego");
            assertThat(guardada.getPrecioUnitario()).isEqualByComparingTo("6000.00");
            assertThat(guardada.getMoneda()).isEqualTo("COP");
            assertThat(guardada.getCarrito()).isSameAs(carrito);

            ItemCarritoDto item = resultado.items().get(0);
            assertThat(item.producto().id()).isEqualTo(ESPADA);
            assertThat(item.producto().nombre()).isEqualTo("Espada de fuego");
            assertThat(item.producto().moneda()).isEqualTo("COP");
            assertThat(item.cantidad()).isEqualTo(3);
            assertThat(item.precioUnitario()).isEqualByComparingTo("6000.00");
            assertThat(item.subtotal()).isEqualByComparingTo("18000.00");
            assertThat(resultado.total()).isEqualByComparingTo("18000.00");
            assertThat(resultado.moneda()).isEqualTo("COP");
            assertThat(resultado.usuarioId()).isEqualTo(USUARIO);
        }

        @Test
        @DisplayName("el mismo producto suma cantidad en su linea y toma el precio vigente del catalogo")
        void mismoProductoSumaCantidad() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 1, "5000");
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA, "ARMA", "6000")));

            CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 2));

            assertThat(resultado.items()).singleElement().satisfies(item -> {
                assertThat(item.id()).isEqualTo(10L);
                assertThat(item.cantidad()).isEqualTo(3);
                assertThat(item.precioUnitario()).isEqualByComparingTo("6000");
                assertThat(item.subtotal()).isEqualByComparingTo("18000");
                assertThat(item.producto().nombre()).isEqualTo("Producto " + ESPADA);
            });
            assertThat(resultado.total()).isEqualByComparingTo("18000");
        }

        @Test
        @DisplayName("otro producto es otra linea, y el total las suma")
        void otroProductoEsOtraLinea() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 1, "6000");
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESCUDO)).thenReturn(Optional.of(enVenta(ESCUDO, "ARMADURA", "4000")));

            CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESCUDO, 2));

            assertThat(resultado.items()).extracting(item -> item.producto().id()).containsExactly(ESPADA, ESCUDO);
            assertThat(resultado.total()).isEqualByComparingTo("14000");
        }

        @Test
        @DisplayName("una linea legada (sin referencia del catalogo) nunca se confunde con un producto nuevo")
        void lineaLegadaNoSeMezcla() {
            Carrito carrito = carritoExistente();
            linea(carrito, 5L, null, 1, "25000");
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA)));

            CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1));

            assertThat(resultado.items()).hasSize(2);
            assertThat(resultado.items().get(0).producto().id()).isNull();
            assertThat(resultado.total()).isEqualByComparingTo("31000");
        }

        @Test
        @DisplayName("la referencia es el id que devuelve el catalogo; si no trae id, el pedido")
        void referenciaDelCatalogo() {
            Carrito carrito = carritoExistente();
            guardarDevuelveLoMismo();
            when(catalogo.producto("p-heroe-e2e")).thenReturn(Optional.of(sinId(enVenta("p-heroe-e2e"))));

            carritoService.agregarProducto(USUARIO, pedir("p-heroe-e2e", 1));

            assertThat(carrito.getItems().get(0).getProductoRef()).isEqualTo("p-heroe-e2e");
        }

        @Test
        @DisplayName("si el jugador no tenia carrito, se crea")
        void creaElCarrito() {
            when(carritoRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.empty());
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA)));

            CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1));

            assertThat(resultado.usuarioId()).isEqualTo(USUARIO);
            assertThat(resultado.items()).hasSize(1);
        }

        @Test
        @DisplayName("la escritura va en una transaccion, que se confirma")
        void escrituraEnTransaccion() {
            carritoExistente();
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA)));

            carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1));

            verify(transacciones).getTransaction(any());
            verify(transacciones).commit(any());
        }

        @Test
        @DisplayName("si la escritura falla, la transaccion se deshace")
        void escrituraFallidaSeDeshace() {
            carritoExistente();
            when(carritoRepository.save(any(Carrito.class))).thenThrow(new IllegalStateException("base caida"));
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOf(IllegalStateException.class);
            verify(transacciones).rollback(any());
            verify(transacciones, never()).commit(any());
        }
    }

    @Nested
    @DisplayName("obtener y eliminar")
    class ObtenerYEliminar {

        @Test
        @DisplayName("obtener devuelve el DTO del carrito existente")
        void obtenerExistente() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 2, "6000");

            CarritoDto resultado = carritoService.obtenerOCrearCarrito(USUARIO);

            assertThat(resultado.id()).isEqualTo(1L);
            assertThat(resultado.items()).hasSize(1);
            assertThat(resultado.total()).isEqualByComparingTo("12000");
            assertThat(resultado.moneda()).isEqualTo("COP");
            verify(carritoRepository, never()).save(any());
        }

        @Test
        @DisplayName("sin carrito se crea uno vacio: total 0 y sin moneda")
        void obtenerCreaVacio() {
            when(carritoRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.empty());
            guardarDevuelveLoMismo();

            CarritoDto resultado = carritoService.obtenerOCrearCarrito(USUARIO);

            assertThat(resultado.usuarioId()).isEqualTo(USUARIO);
            assertThat(resultado.items()).isEmpty();
            assertThat(resultado.total()).isEqualByComparingTo("0");
            assertThat(resultado.moneda()).isNull();
            verifyNoInteractions(catalogo);
        }

        @Test
        @DisplayName("eliminar quita la linea y recalcula el total")
        void eliminarQuitaLaLinea() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 1, "6000");
            linea(carrito, 11L, ESCUDO, 2, "4000");
            guardarDevuelveLoMismo();

            CarritoDto resultado = carritoService.eliminarItem(USUARIO, 10L);

            assertThat(resultado.items()).extracting(ItemCarritoDto::id).containsExactly(11L);
            assertThat(resultado.total()).isEqualByComparingTo("8000");
        }

        @Test
        @DisplayName("eliminar una linea que no esta no cambia nada, ni falla con lineas aun sin id")
        void eliminarInexistente() {
            Carrito carrito = carritoExistente();
            linea(carrito, null, ESPADA, 1, "6000");
            guardarDevuelveLoMismo();

            CarritoDto resultado = carritoService.eliminarItem(USUARIO, 99L);

            assertThat(resultado.items()).hasSize(1);
            assertThat(resultado.total()).isEqualByComparingTo("6000");
        }
    }
}
