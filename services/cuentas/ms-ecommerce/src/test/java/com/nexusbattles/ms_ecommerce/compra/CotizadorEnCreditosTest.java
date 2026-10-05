package com.nexusbattles.ms_ecommerce.compra;

import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.catalogo.PromocionDelCatalogo;
import com.nexusbattles.ms_ecommerce.compra.CotizacionEnCreditos.Motivo;
import com.nexusbattles.ms_ecommerce.service.CarritoLeido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-44 (auditoria del 4-oct, cambio autorizado n.º 5): lo que costaria pagar el
 * carrito con creditos del juego, calculado en el servidor. La interfaz ensena
 * esta cotizacion —saldo actual, precio, saldo despues— y no suma ni resta
 * nada por su cuenta.
 */
@DisplayName("Cotizacion en creditos: precio del catalogo, total y saldo despues")
class CotizadorEnCreditosTest {

    private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");

    private static ProductoDelCatalogo producto(String id, Integer precioCreditos, boolean premium, Integer tiraje,
                                                String estado, PromocionDelCatalogo promocion) {
        return new ProductoDelCatalogo(id, "Producto " + id, null, null, "ARMA", tiraje, precioCreditos,
                new BigDecimal("6000"), premium, estado, null, promocion);
    }

    private static ProductoDelCatalogo enCreditos(String id, int precioCreditos) {
        return producto(id, precioCreditos, false, -1, "ACTIVO", null);
    }

    private static CarritoLeido carrito(Object... refYCantidad) {
        List<CarritoLeido.Linea> lineas = new java.util.ArrayList<>();
        for (int i = 0; i < refYCantidad.length; i += 2) {
            lineas.add(new CarritoLeido.Linea((long) i, (String) refYCantidad[i], "Nombre guardado " + i,
                    (Integer) refYCantidad[i + 1], new BigDecimal("6000"), "COP"));
        }
        return new CarritoLeido(1L, "jugador", lineas);
    }

    private static Map<String, ProductoDelCatalogo> catalogo(ProductoDelCatalogo... productos) {
        return Arrays.stream(productos).collect(Collectors.toMap(ProductoDelCatalogo::id, Function.identity()));
    }

    @Test
    @DisplayName("todo con precio en creditos: total, saldo actual y saldo despues, calculados aqui")
    void pagable() {
        CotizacionEnCreditos cotizacion = CotizadorEnCreditos.cotizar(carrito("hacha", 2, "anillo", 1),
                catalogo(enCreditos("hacha", 300), enCreditos("anillo", 150)), OptionalLong.of(1000), AHORA);

        assertThat(cotizacion.pagable()).isTrue();
        assertThat(cotizacion.motivo()).isNull();
        assertThat(cotizacion.lineas()).extracting(CotizacionEnCreditos.Linea::productoId)
                .containsExactly("hacha", "anillo");
        assertThat(cotizacion.lineas().get(0).precioCreditos()).isEqualTo(300L);
        assertThat(cotizacion.lineas().get(0).subtotalCreditos()).isEqualTo(600L);
        assertThat(cotizacion.lineas().get(0).nombre()).as("el nombre vigente del catalogo").isEqualTo("Producto hacha");
        assertThat(cotizacion.totalCreditos()).isEqualTo(750L);
        assertThat(cotizacion.saldoDisponible()).isEqualTo(1000L);
        assertThat(cotizacion.saldoDespues()).isEqualTo(250L);
        assertThat(cotizacion.alcanza()).isTrue();
    }

    @Test
    @DisplayName("el saldo no alcanza: saldo despues negativo y alcanza false (el cobro lo vuelve a mirar)")
    void noAlcanza() {
        CotizacionEnCreditos cotizacion = CotizadorEnCreditos.cotizar(carrito("hacha", 2),
                catalogo(enCreditos("hacha", 300)), OptionalLong.of(500), AHORA);

        assertThat(cotizacion.pagable()).isTrue();
        assertThat(cotizacion.saldoDespues()).isEqualTo(-100L);
        assertThat(cotizacion.alcanza()).isFalse();
    }

    @Test
    @DisplayName("justo el saldo: alcanza y queda en cero")
    void justo() {
        CotizacionEnCreditos cotizacion = CotizadorEnCreditos.cotizar(carrito("hacha", 1),
                catalogo(enCreditos("hacha", 500)), OptionalLong.of(500), AHORA);

        assertThat(cotizacion.saldoDespues()).isZero();
        assertThat(cotizacion.alcanza()).isTrue();
    }

    @Test
    @DisplayName("ms-finanzas no respondio: la cotizacion sale igual, sin saldo y sin afirmar si alcanza")
    void sinSaldo() {
        CotizacionEnCreditos cotizacion = CotizadorEnCreditos.cotizar(carrito("hacha", 1),
                catalogo(enCreditos("hacha", 300)), OptionalLong.empty(), AHORA);

        assertThat(cotizacion.pagable()).isTrue();
        assertThat(cotizacion.totalCreditos()).isEqualTo(300L);
        assertThat(cotizacion.saldoDisponible()).isNull();
        assertThat(cotizacion.saldoDespues()).isNull();
        assertThat(cotizacion.alcanza()).isNull();
    }

    @Test
    @DisplayName("la promocion vigente rebaja tambien el precio en creditos, con su porcentaje")
    void promocion() {
        PromocionDelCatalogo veinte = new PromocionDelCatalogo(20, Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-31T00:00:00Z"), true);
        CotizacionEnCreditos cotizacion = CotizadorEnCreditos.cotizar(carrito("escudo", 1),
                catalogo(producto("escudo", 250, false, -1, "ACTIVO", veinte)), OptionalLong.of(1000), AHORA);

        assertThat(cotizacion.lineas().get(0).precioCreditos()).isEqualTo(200L);
        assertThat(cotizacion.lineas().get(0).porcentajeDescuento()).isEqualTo(20);
        assertThat(cotizacion.totalCreditos()).isEqualTo(200L);
    }

    @Test
    @DisplayName("un producto sin precio en creditos (premium o sin precio): no se paga con creditos y se dice cual")
    void sinPrecioEnCreditos() {
        CotizacionEnCreditos cotizacion = CotizadorEnCreditos.cotizar(carrito("hacha", 1, "heroe", 1),
                catalogo(enCreditos("hacha", 300), producto("heroe", null, true, -1, "ACTIVO", null)),
                OptionalLong.of(5000), AHORA);

        assertThat(cotizacion.pagable()).isFalse();
        assertThat(cotizacion.motivo()).isEqualTo(Motivo.SIN_PRECIO_EN_CREDITOS);
        assertThat(cotizacion.totalCreditos()).isNull();
        assertThat(cotizacion.saldoDisponible()).isEqualTo(5000L);
        assertThat(cotizacion.saldoDespues()).isNull();
        assertThat(cotizacion.alcanza()).isNull();
        assertThat(cotizacion.lineas().get(1).precioCreditos()).isNull();
        assertThat(cotizacion.lineas().get(1).subtotalCreditos()).isNull();
        assertThat(cotizacion.lineas().get(1).disponible()).isTrue();
    }

    @Test
    @DisplayName("agotado, suspendido, fuera del catalogo o con mas unidades de las que quedan: no se puede pagar")
    void noDisponible() {
        Map<String, ProductoDelCatalogo> productos = catalogo(
                producto("agotado", 300, false, 0, "ACTIVO", null),
                producto("suspendido", 300, false, -1, "SUSPENDIDO", null),
                producto("escaso", 300, false, 2, "ACTIVO", null));

        assertThat(CotizadorEnCreditos.cotizar(carrito("agotado", 1), productos, OptionalLong.of(1000), AHORA).motivo())
                .isEqualTo(Motivo.PRODUCTO_NO_DISPONIBLE);
        assertThat(CotizadorEnCreditos.cotizar(carrito("suspendido", 1), productos, OptionalLong.of(1000), AHORA)
                .motivo()).isEqualTo(Motivo.PRODUCTO_NO_DISPONIBLE);
        assertThat(CotizadorEnCreditos.cotizar(carrito("fantasma", 1), productos, OptionalLong.of(1000), AHORA)
                .motivo()).isEqualTo(Motivo.PRODUCTO_NO_DISPONIBLE);
        CotizacionEnCreditos escaso = CotizadorEnCreditos.cotizar(carrito("escaso", 3), productos,
                OptionalLong.of(1000), AHORA);
        assertThat(escaso.pagable()).isFalse();
        assertThat(escaso.motivo()).isEqualTo(Motivo.PRODUCTO_NO_DISPONIBLE);
        assertThat(escaso.lineas().get(0).disponible()).isFalse();
        assertThat(escaso.totalCreditos()).isNull();
    }

    @Test
    @DisplayName("carrito vacio (o solo con lineas legadas que no son del catalogo): nada que pagar")
    void vacio() {
        CotizacionEnCreditos vacio = CotizadorEnCreditos.cotizar(new CarritoLeido(1L, "jugador", List.of()),
                Map.of(), OptionalLong.of(100), AHORA);
        CotizacionEnCreditos legado = CotizadorEnCreditos.cotizar(carrito(null, 1), Map.of(), OptionalLong.of(100),
                AHORA);

        assertThat(vacio.pagable()).isFalse();
        assertThat(vacio.motivo()).isEqualTo(Motivo.CARRITO_VACIO);
        assertThat(vacio.lineas()).isEmpty();
        assertThat(vacio.saldoDisponible()).isEqualTo(100L);
        assertThat(legado.motivo()).isEqualTo(Motivo.CARRITO_VACIO);
    }
}
