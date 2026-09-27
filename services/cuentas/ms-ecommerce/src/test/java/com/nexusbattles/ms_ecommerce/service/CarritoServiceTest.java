package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestro;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.catalogo.CopiaDelCatalogo;
import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.catalogo.PromocionDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.dto.ItemCarritoDto;
import com.nexusbattles.ms_ecommerce.dto.MotivoDeLinea;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.model.ItemCarrito;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import com.nexusbattles.ms_ecommerce.precios.MonedaNoDisponibleException;
import com.nexusbattles.ms_ecommerce.precios.Tarifa;
import com.nexusbattles.ms_ecommerce.precios.TasasDeCambio;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El carrito sobre el catalogo maestro (R16) con precio del servidor (B5): cada
 * producto se resuelve contra el catalogo, la linea guarda una instantanea y
 * la respuesta se recalcula con el catalogo del momento.
 *
 * <p>El gestor de transacciones es un doble: aqui interesa donde empieza y
 * termina la transaccion y que reglas se aplican, no la base de datos (eso lo
 * prueban los IT con PostgreSQL de verdad).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CarritoServiceTest {

    private static final String USUARIO = "usr_123";

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private CatalogoMaestro catalogo;

    @Mock
    private CopiaDelCatalogo copia;

    @Mock
    private TasasDeCambio tasas;

    @Mock
    private PlatformTransactionManager transacciones;

    private RelojAjustable reloj;
    private CarritoService carritoService;

    @BeforeEach
    void preparar() {
        reloj = new RelojAjustable(Instant.parse("2026-09-25T12:00:00Z"));
        carritoService = new CarritoService(carritoRepository, catalogo, copia, tasas, new CotizadorDelCarrito(), reloj,
                transacciones);
        when(tasas.tarifa(Moneda.COP)).thenReturn(Tarifa.enPesos());
        when(copia.siDisponible()).thenReturn(Optional.of(Map.of()));
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
        when(carritoRepository.bloquear(USUARIO)).thenReturn(Optional.of(carrito));
        return carrito;
    }

    private void guardarDevuelveLoMismo() {
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    private void catalogoEnCopia(ProductoDelCatalogo... productos) {
        Map<String, ProductoDelCatalogo> porId = new java.util.HashMap<>();
        for (ProductoDelCatalogo producto : productos) {
            porId.put(producto.id(), producto);
        }
        when(copia.siDisponible()).thenReturn(Optional.of(porId));
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
    @DisplayName("agregar un producto nuevo crea la linea y calcula subtotal y total")
    void agregarNuevo() {
        carritoExistente();
        guardarDevuelveLoMismo();
        when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA, "ARMA", "10000")));

        CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 2));

        assertThat(resultado.items()).singleElement().satisfies(item -> {
            assertThat(item.cantidad()).isEqualTo(2);
            assertThat(item.subtotal()).isEqualByComparingTo("20000");
            assertThat(item.disponible()).isTrue();
            assertThat(item.maximo()).isEqualTo(20);
        });
        assertThat(resultado.total()).isEqualByComparingTo("20000");
        assertThat(resultado.unidades()).isEqualTo(2);
        assertThat(resultado.preciosVigentes()).isTrue();
    }

    @Nested
    @DisplayName("reglas de venta al agregar")
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
        @DisplayName("tiraje 0: agotado (RF-CAR-007)")
        void agotado() {
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conTiraje(enVenta(ESPADA), 0)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.AGOTADO));
            verifyNoInteractions(carritoRepository);
        }

        @Test
        @DisplayName("sin precio en moneda real, o a 0: no se compra en la tienda (RF-CAR-002)")
        void sinPrecioEnMonedaReal() {
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(soloEnCreditos(enVenta(ESPADA), 300)));
            when(catalogo.producto(ESCUDO)).thenReturn(Optional.of(conPrecio(enVenta(ESCUDO), BigDecimal.ZERO)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.SIN_PRECIO_EN_MONEDA_REAL));
            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESCUDO, 1)))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.SIN_PRECIO_EN_MONEDA_REAL));
            verifyNoInteractions(carritoRepository);
        }

        @Test
        @DisplayName("catalogo caido: el fallo sale, sin transaccion ni base")
        void catalogoCaido() {
            when(catalogo.producto(ESPADA)).thenThrow(new CatalogoNoDisponibleException("caido"));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOf(CatalogoNoDisponibleException.class);
            verifyNoInteractions(carritoRepository, transacciones);
        }

        @Test
        @DisplayName("una moneda sin tasa se rechaza antes de mirar el catalogo")
        void monedaSinTasa() {
            when(tasas.tarifa(Moneda.EUR)).thenThrow(new MonedaNoDisponibleException(Moneda.EUR, Set.of(Moneda.COP)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1), Moneda.EUR))
                    .isInstanceOf(MonedaNoDisponibleException.class);
            verifyNoInteractions(catalogo, carritoRepository);
        }
    }

    @Nested
    @DisplayName("cantidades: 1..20 por linea y nunca mas que el tiraje")
    class Cantidades {

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {0, -1, 21, 100})
        @DisplayName("agregar fuera de 1..20 es cantidad fuera de rango, sin consultar nada")
        void agregarFueraDeRango(int cantidad) {
            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, cantidad)))
                    .isInstanceOfSatisfying(CantidadNoPermitidaException.class,
                            e -> assertThat(e.motivo()).isEqualTo(CantidadNoPermitidaException.Motivo.FUERA_DE_RANGO));
            verifyNoInteractions(catalogo, carritoRepository);
        }

        @Test
        @DisplayName("sumar a una linea que ya tiene 18 dos mas llega a 20; una mas ya no cabe")
        void maximoPorLinea() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 18, "6000");
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA)));

            assertThat(carritoService.agregarProducto(USUARIO, pedir(ESPADA, 2)).items().get(0).cantidad()).isEqualTo(20);
            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)))
                    .isInstanceOfSatisfying(CantidadNoPermitidaException.class,
                            e -> assertThat(e.motivo()).isEqualTo(CantidadNoPermitidaException.Motivo.MAXIMA_POR_LINEA));
            assertThat(carrito.getItems().get(0).getCantidad()).isEqualTo(20);
        }

        @Test
        @DisplayName("con tiraje 3, la linea no pasa de 3 unidades y el rechazo dice cuantas quedan")
        void tirajeLimitado() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 2, "6000");
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conTiraje(enVenta(ESPADA), 3)));

            assertThatThrownBy(() -> carritoService.agregarProducto(USUARIO, pedir(ESPADA, 2)))
                    .isInstanceOfSatisfying(CantidadNoPermitidaException.class, e -> {
                        assertThat(e.motivo()).isEqualTo(CantidadNoPermitidaException.Motivo.TIRAJE_INSUFICIENTE);
                        assertThat(e.disponibles()).isEqualTo(3);
                    });
            verify(carritoRepository, never()).save(any());
        }

        @Test
        @DisplayName("cambiar la cantidad fija la de la linea, contra el catalogo del momento")
        void cambiarCantidad() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 1, "5000");
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conTiraje(enVenta(ESPADA, "ARMA", "6000"), 9)));

            CarritoDto resultado = carritoService.cambiarCantidad(USUARIO, "10", 4, Moneda.COP);

            ItemCarritoDto item = resultado.items().get(0);
            assertThat(item.cantidad()).isEqualTo(4);
            assertThat(item.precioUnitario()).isEqualByComparingTo("6000");
            assertThat(item.subtotal()).isEqualByComparingTo("24000");
            assertThat(item.maximo()).isEqualTo(9);
            assertThat(carrito.getItems().get(0).getPrecioUnitario()).isEqualByComparingTo("6000");
        }

        @Test
        @DisplayName("cambiar la cantidad por encima del tiraje: tiraje insuficiente")
        void cambiarPorEncimaDelTiraje() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 1, "6000");
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conTiraje(enVenta(ESPADA), 2)));

            assertThatThrownBy(() -> carritoService.cambiarCantidad(USUARIO, "10", 3, Moneda.COP))
                    .isInstanceOfSatisfying(CantidadNoPermitidaException.class,
                            e -> assertThat(e.disponibles()).isEqualTo(2));
        }

        @Test
        @DisplayName("cambiar la cantidad de una linea que no es del jugador, o que no es un numero: inexistente")
        void lineaInexistente() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 1, "6000");

            assertThatThrownBy(() -> carritoService.cambiarCantidad(USUARIO, "99", 2, Moneda.COP))
                    .isInstanceOf(LineaInexistenteException.class);
            assertThatThrownBy(() -> carritoService.cambiarCantidad(USUARIO, "diez", 2, Moneda.COP))
                    .isInstanceOf(LineaInexistenteException.class);
            verifyNoInteractions(catalogo);
        }

        @Test
        @DisplayName("una linea legada (sin producto del catalogo) no se cambia: no disponible")
        void lineaLegada() {
            Carrito carrito = carritoExistente();
            linea(carrito, 5L, null, 1, "25000");

            assertThatThrownBy(() -> carritoService.cambiarCantidad(USUARIO, "5", 2, Moneda.COP))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.NO_DISPONIBLE));
        }

        @Test
        @DisplayName("cambiar la cantidad de un producto que se suspendio: no disponible")
        void cambiarSuspendido() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 1, "6000");
            when(catalogo.producto(ESPADA)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> carritoService.cambiarCantidad(USUARIO, "10", 2, Moneda.COP))
                    .isInstanceOfSatisfying(ProductoNoAgregableException.class,
                            e -> assertThat(e.motivo()).isEqualTo(Motivo.NO_DISPONIBLE));
        }
    }

    @Nested
    @DisplayName("la linea, su instantanea y el precio del servidor")
    class Instantanea {

        @Test
        @DisplayName("guarda referencia, nombre, precio en COP y moneda")
        void guardaLaInstantanea() {
            Carrito carrito = carritoExistente();
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA))
                    .thenReturn(Optional.of(conNombre(enVenta(ESPADA, "ARMA", "6000.00"), "Espada de fuego")));

            CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 3));

            ItemCarrito guardada = carrito.getItems().get(0);
            assertThat(guardada.getProductoRef()).isEqualTo(ESPADA);
            assertThat(guardada.getProductoNombre()).isEqualTo("Espada de fuego");
            assertThat(guardada.getPrecioUnitario()).isEqualByComparingTo("6000");
            assertThat(guardada.getMoneda()).isEqualTo("COP");
            assertThat(guardada.getCarrito()).isSameAs(carrito);
            assertThat(resultado.items().get(0).producto().nombre()).isEqualTo("Espada de fuego");
            assertThat(resultado.total()).isEqualByComparingTo("18000");
            assertThat(resultado.moneda()).isEqualTo("COP");
            assertThat(resultado.usuarioId()).isEqualTo(USUARIO);
        }

        @Test
        @DisplayName("con una promocion vigente, la instantanea guarda el precio rebajado")
        void promocionEnLaInstantanea() {
            Carrito carrito = carritoExistente();
            guardarDevuelveLoMismo();
            ProductoDelCatalogo conPromo = new ProductoDelCatalogo(ESPADA, "Espada", null, null, "ARMA", -1, null,
                    new BigDecimal("10000"), true, "ACTIVO", null,
                    new PromocionDelCatalogo(25, Instant.parse("2026-09-01T00:00:00Z"),
                            Instant.parse("2026-10-01T00:00:00Z"), true));
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(conPromo));

            ItemCarritoDto item = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1)).items().get(0);

            assertThat(carrito.getItems().get(0).getPrecioUnitario()).isEqualByComparingTo("7500");
            assertThat(item.precioUnitario()).isEqualByComparingTo("7500");
            assertThat(item.precioOriginal()).isEqualByComparingTo("10000");
            assertThat(item.porcentajeDescuento()).isEqualTo(25);
        }

        @Test
        @DisplayName("una linea legada (sin referencia) nunca se confunde con un producto nuevo ni suma al total")
        void lineaLegadaNoSeMezcla() {
            Carrito carrito = carritoExistente();
            linea(carrito, 5L, null, 1, "25000");
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA)));

            CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1));

            assertThat(resultado.items()).hasSize(2);
            assertThat(resultado.items().get(0).producto().id()).isNull();
            assertThat(resultado.items().get(0).disponible()).isFalse();
            assertThat(resultado.items().get(0).motivo()).isEqualTo(MotivoDeLinea.NO_DISPONIBLE);
            assertThat(resultado.total()).isEqualByComparingTo("6000");
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
        @DisplayName("si el jugador no tenia carrito, se crea (sin duplicarlo: INSERT ... ON CONFLICT)")
        void creaElCarrito() {
            Carrito nuevo = new Carrito();
            nuevo.setId(3L);
            nuevo.setUsuarioId(USUARIO);
            nuevo.setItems(new ArrayList<>());
            when(carritoRepository.bloquear(USUARIO)).thenReturn(Optional.empty()).thenReturn(Optional.of(nuevo));
            guardarDevuelveLoMismo();
            when(catalogo.producto(ESPADA)).thenReturn(Optional.of(enVenta(ESPADA)));

            CarritoDto resultado = carritoService.agregarProducto(USUARIO, pedir(ESPADA, 1));

            verify(carritoRepository).crearSiNoExiste(USUARIO);
            assertThat(resultado.usuarioId()).isEqualTo(USUARIO);
            assertThat(resultado.items()).hasSize(1);
        }

        @Test
        @DisplayName("la escritura va en una transaccion; si falla, se deshace")
        void transaccion() {
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
    @DisplayName("obtener, eliminar y retirar lo comprado")
    class ObtenerYEliminar {

        @Test
        @DisplayName("obtener recalcula con el precio del catalogo de ahora, no el de la instantanea")
        void obtenerRecalcula() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 2, "5000");
            catalogoEnCopia(enVenta(ESPADA, "ARMA", "6000"));

            CarritoDto resultado = carritoService.obtener(USUARIO, Moneda.COP);

            assertThat(resultado.items().get(0).precioUnitario()).isEqualByComparingTo("6000");
            assertThat(resultado.total()).isEqualByComparingTo("12000");
            assertThat(resultado.preciosVigentes()).isTrue();
            verify(carritoRepository, never()).save(any());
        }

        @Test
        @DisplayName("con el catalogo caido, la ultima instantanea y preciosVigentes false")
        void obtenerSinCatalogo() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 2, "5000");
            when(copia.siDisponible()).thenReturn(Optional.empty());

            CarritoDto resultado = carritoService.obtener(USUARIO, Moneda.COP);

            assertThat(resultado.items().get(0).precioUnitario()).isEqualByComparingTo("5000");
            assertThat(resultado.total()).isEqualByComparingTo("10000");
            assertThat(resultado.preciosVigentes()).isFalse();
        }

        @Test
        @DisplayName("sin carrito se crea uno vacio: total 0 y sin moneda")
        void obtenerCreaVacio() {
            Carrito nuevo = new Carrito();
            nuevo.setId(3L);
            nuevo.setUsuarioId(USUARIO);
            nuevo.setItems(new ArrayList<>());
            when(carritoRepository.findByUsuarioId(USUARIO)).thenReturn(Optional.empty()).thenReturn(Optional.of(nuevo));

            CarritoDto resultado = carritoService.obtenerOCrearCarrito(USUARIO);

            verify(carritoRepository).crearSiNoExiste(USUARIO);
            assertThat(resultado.items()).isEmpty();
            assertThat(resultado.total()).isEqualByComparingTo("0");
            assertThat(resultado.moneda()).isNull();
            verifyNoInteractions(catalogo);
        }

        @Test
        @DisplayName("eliminar quita la linea y recalcula; una que no esta no cambia nada")
        void eliminar() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 1, "6000");
            linea(carrito, 11L, ESCUDO, 2, "4000");
            guardarDevuelveLoMismo();
            when(copia.siDisponible()).thenReturn(Optional.empty());

            CarritoDto resultado = carritoService.eliminarItem(USUARIO, 10L);
            assertThat(resultado.items()).extracting(ItemCarritoDto::id).containsExactly(11L);
            assertThat(resultado.total()).isEqualByComparingTo("8000");

            assertThat(carritoService.eliminarItem(USUARIO, 99L).items()).hasSize(1);
        }

        @Test
        @DisplayName("retirar lo comprado resta unidades; la linea sin unidades desaparece y lo demas se queda")
        void retirarComprado() {
            Carrito carrito = carritoExistente();
            linea(carrito, 10L, ESPADA, 3, "6000");
            linea(carrito, 11L, ESCUDO, 1, "4000");
            linea(carrito, 12L, "otro", 2, "1000");

            carritoService.retirarComprado(USUARIO, Map.of(ESPADA, 2, ESCUDO, 1));

            assertThat(carrito.getItems()).extracting(ItemCarrito::getProductoRef).containsExactly(ESPADA, "otro");
            assertThat(carrito.getItems().get(0).getCantidad()).isEqualTo(1);
            assertThat(carrito.getTotal()).isEqualByComparingTo("8000");
            verify(carritoRepository).save(carrito);
        }

        @Test
        @DisplayName("retirar de un jugador sin carrito no hace nada")
        void retirarSinCarrito() {
            when(carritoRepository.bloquear(USUARIO)).thenReturn(Optional.empty());

            carritoService.retirarComprado(USUARIO, Map.of(ESPADA, 1));

            verify(carritoRepository, never()).save(any());
        }
    }
}
