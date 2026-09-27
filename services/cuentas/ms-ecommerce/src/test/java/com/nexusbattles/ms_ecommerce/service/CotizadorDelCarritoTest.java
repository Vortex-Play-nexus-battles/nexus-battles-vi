package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.dto.ItemCarritoDto;
import com.nexusbattles.ms_ecommerce.dto.MotivoDeLinea;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import com.nexusbattles.ms_ecommerce.precios.Tarifa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESCUDO;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESPADA;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.POCION;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conEstado;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conTiraje;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.enVenta;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.soloEnCreditos;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("El carrito con precio: total del servidor y disponibilidad por linea")
class CotizadorDelCarritoTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private final CotizadorDelCarrito cotizador = new CotizadorDelCarrito();

    private static CarritoLeido carrito(CarritoLeido.Linea... lineas) {
        return new CarritoLeido(1L, "uid", List.of(lineas));
    }

    private static CarritoLeido.Linea linea(long id, String referencia, int cantidad, String instantanea) {
        return new CarritoLeido.Linea(id, referencia, "Nombre guardado", cantidad,
                instantanea == null ? null : new BigDecimal(instantanea), instantanea == null ? null : "COP");
    }

    private static Optional<Map<String, ProductoDelCatalogo>> catalogo(ProductoDelCatalogo... productos) {
        java.util.Map<String, ProductoDelCatalogo> porId = new java.util.HashMap<>();
        for (ProductoDelCatalogo producto : productos) {
            porId.put(producto.id(), producto);
        }
        return Optional.of(porId);
    }

    @Test
    @DisplayName("precio del catalogo de ahora, subtotal = unitario x cantidad y total de lo disponible")
    void total() {
        CarritoDto dto = cotizador.cotizar(carrito(linea(10, ESPADA, 2, "5000"), linea(11, ESCUDO, 1, "4000")),
                Tarifa.enPesos(), catalogo(enVenta(ESPADA, "ARMA", "6000"), enVenta(ESCUDO, "ARMADURA", "4000")), AHORA);

        assertThat(dto.items()).extracting(ItemCarritoDto::subtotal)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("12000"), new BigDecimal("4000"));
        assertThat(dto.total()).isEqualByComparingTo("16000");
        assertThat(dto.unidades()).isEqualTo(3);
        assertThat(dto.moneda()).isEqualTo("COP");
        assertThat(dto.preciosVigentes()).isTrue();
    }

    @Test
    @DisplayName("agotado, suspendido, retirado o sin precio: sigue en el carrito, no suma y dice por que")
    void noDisponibles() {
        CarritoDto dto = cotizador.cotizar(carrito(
                        linea(1, ESPADA, 1, "6000"),
                        linea(2, ESCUDO, 1, "4000"),
                        linea(3, POCION, 1, "3000"),
                        linea(4, "retirado", 1, "1000"),
                        linea(5, "creditos", 1, "2000")),
                Tarifa.enPesos(),
                catalogo(conTiraje(enVenta(ESPADA), 0), conEstado(enVenta(ESCUDO), "SUSPENDIDO"),
                        enVenta(POCION, "ITEM", "3000"), soloEnCreditos(enVenta("creditos"), 50)),
                AHORA);

        assertThat(dto.items()).extracting(ItemCarritoDto::motivo).containsExactly(MotivoDeLinea.AGOTADO,
                MotivoDeLinea.NO_DISPONIBLE, null, MotivoDeLinea.NO_DISPONIBLE, MotivoDeLinea.SIN_PRECIO_EN_MONEDA_REAL);
        assertThat(dto.items()).extracting(ItemCarritoDto::disponible).containsExactly(false, false, true, false, false);
        assertThat(dto.items().get(0).maximo()).isZero();
        assertThat(dto.total()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("mas unidades de las que quedan: tiraje insuficiente, con el maximo que admite")
    void tirajeInsuficiente() {
        CarritoDto dto = cotizador.cotizar(carrito(linea(1, ESPADA, 5, "6000")), Tarifa.enPesos(),
                catalogo(conTiraje(enVenta(ESPADA), 3)), AHORA);

        ItemCarritoDto item = dto.items().get(0);
        assertThat(item.disponible()).isFalse();
        assertThat(item.motivo()).isEqualTo(MotivoDeLinea.TIRAJE_INSUFICIENTE);
        assertThat(item.maximo()).isEqualTo(3);
        assertThat(dto.total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("con el catalogo caido: la instantanea, sin afirmar disponibilidad nueva, y preciosVigentes false")
    void catalogoCaido() {
        CarritoDto dto = cotizador.cotizar(carrito(linea(1, ESPADA, 2, "6000"), linea(2, null, 1, null)),
                Tarifa.enPesos(), Optional.empty(), AHORA);

        assertThat(dto.preciosVigentes()).isFalse();
        assertThat(dto.items().get(0).precioUnitario()).isEqualByComparingTo("6000");
        assertThat(dto.items().get(0).disponible()).isTrue();
        assertThat(dto.items().get(1).disponible()).isFalse();
        assertThat(dto.items().get(1).motivo()).isEqualTo(MotivoDeLinea.NO_DISPONIBLE);
        assertThat(dto.total()).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("en USD convierte precios e instantaneas con la tasa")
    void enDolares() {
        Tarifa usd = new Tarifa(Moneda.USD, new BigDecimal("4000"));

        CarritoDto vivo = cotizador.cotizar(carrito(linea(1, ESPADA, 1, "6000")), usd,
                catalogo(enVenta(ESPADA, "ARMA", "6000")), AHORA);
        CarritoDto caido = cotizador.cotizar(carrito(linea(1, ESPADA, 1, "6000")), usd, Optional.empty(), AHORA);

        assertThat(vivo.moneda()).isEqualTo("USD");
        assertThat(vivo.total()).isEqualByComparingTo("1.50");
        assertThat(vivo.items().get(0).producto().moneda()).isEqualTo("USD");
        assertThat(caido.total()).isEqualByComparingTo("1.50");
    }

    @Test
    @DisplayName("un carrito vacio: total 0 y sin moneda")
    void vacio() {
        CarritoDto dto = cotizador.cotizar(carrito(), Tarifa.enPesos(), Optional.of(Map.of()), AHORA);

        assertThat(dto.items()).isEmpty();
        assertThat(dto.moneda()).isNull();
        assertThat(dto.total()).isEqualByComparingTo("0");
        assertThat(dto.unidades()).isZero();
    }

    @Test
    @DisplayName("el maximo por linea: 20 con tiraje ilimitado, las que quedan si son menos, 0 agotado")
    void maximo() {
        assertThat(CotizadorDelCarrito.maximoPara(enVenta(ESPADA))).isEqualTo(20);
        assertThat(CotizadorDelCarrito.maximoPara(conTiraje(enVenta(ESPADA), 7))).isEqualTo(7);
        assertThat(CotizadorDelCarrito.maximoPara(conTiraje(enVenta(ESPADA), 50))).isEqualTo(20);
        assertThat(CotizadorDelCarrito.maximoPara(conTiraje(enVenta(ESPADA), 0))).isZero();
    }
}
