package com.nexusbattles.ms_ecommerce.precios;

import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.catalogo.PromocionDelCatalogo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Calculadora de precios: el precio que se paga lo calcula el servidor")
class CalculadoraDePreciosTest {

    private static final Tarifa USD_A_4000 = new Tarifa(Moneda.USD, new BigDecimal("4000"));
    private static final Tarifa EUR_A_4400 = new Tarifa(Moneda.EUR, new BigDecimal("4400"));

    @Test
    @DisplayName("en COP sin promocion: el precio del catalogo, a pesos enteros")
    void pesosSinPromocion() {
        PrecioCalculado precio = CalculadoraDePrecios.calcular(new BigDecimal("45000.00"), null, Tarifa.enPesos());

        assertThat(precio.moneda()).isEqualTo(Moneda.COP);
        assertThat(precio.precioOriginal()).isEqualByComparingTo("45000");
        assertThat(precio.precioFinal()).isEqualByComparingTo("45000");
        assertThat(precio.precioFinal().scale()).isZero();
        assertThat(precio.porcentajeDescuento()).isNull();
        assertThat(precio.enPromocion()).isFalse();
    }

    @ParameterizedTest(name = "{0} COP con {1} % = {2} COP")
    @CsvSource({
            "10000, 20, 8000",
            "999, 15, 849",      // 849,15 -> 849
            "999, 50, 500",      // 499,5 -> 500 (mitad hacia arriba)
            "45000.40, 10, 40500",
            "3, 99, 1"           // nunca gratis por redondeo
    })
    @DisplayName("la promocion se aplica en COP y se redondea a pesos, mitad hacia arriba")
    void promocionEnPesos(String base, int porcentaje, String esperado) {
        PrecioCalculado precio = CalculadoraDePrecios.calcular(new BigDecimal(base), porcentaje, Tarifa.enPesos());

        assertThat(precio.precioFinal()).isEqualByComparingTo(esperado);
        assertThat(precio.porcentajeDescuento()).isEqualTo(porcentaje);
        assertThat(precio.enPromocion()).isTrue();
    }

    @Test
    @DisplayName("en USD: pesos entre la tasa, a centavos; el descuento se aplica antes, en COP")
    void conversionADolares() {
        PrecioCalculado precio = CalculadoraDePrecios.calcular(new BigDecimal("10000"), 15, USD_A_4000);

        // original 10000 / 4000 = 2.50; final (10000 - 15 %) = 8500 / 4000 = 2.125 -> 2.13
        assertThat(precio.moneda()).isEqualTo(Moneda.USD);
        assertThat(precio.precioOriginal()).isEqualByComparingTo("2.50");
        assertThat(precio.precioFinal()).isEqualByComparingTo("2.13");
        assertThat(precio.precioFinal().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("en EUR con la tasa del euro")
    void conversionAEuros() {
        PrecioCalculado precio = CalculadoraDePrecios.calcular(new BigDecimal("45000"), null, EUR_A_4400);

        assertThat(precio.precioFinal()).isEqualByComparingTo("10.23"); // 10.2272... -> 10.23
    }

    @Test
    @DisplayName("un precio positivo nunca se convierte en 0,00: sube a 0,01")
    void nuncaGratisAlConvertir() {
        assertThat(CalculadoraDePrecios.calcular(new BigDecimal("10"), null, USD_A_4000).precioFinal())
                .isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("subtotal = unitario x cantidad, sin volver a redondear")
    void subtotal() {
        PrecioCalculado precio = CalculadoraDePrecios.calcular(new BigDecimal("10000"), 15, USD_A_4000);

        assertThat(precio.subtotal(3)).isEqualByComparingTo("6.39");
    }

    @Test
    @DisplayName("convertir un importe en COP: en pesos queda igual; en otra moneda, con la tasa")
    void convertirInstantaneas() {
        assertThat(CalculadoraDePrecios.convertir(new BigDecimal("6000.00"), Tarifa.enPesos())).isEqualByComparingTo("6000");
        assertThat(CalculadoraDePrecios.convertir(new BigDecimal("6000"), USD_A_4000)).isEqualByComparingTo("1.50");
        assertThat(CalculadoraDePrecios.convertir(BigDecimal.ZERO, USD_A_4000)).isEqualByComparingTo("0");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("un precio que no es positivo no se calcula")
    void precioNoPositivo(String precio) {
        assertThatThrownBy(() -> CalculadoraDePrecios.calcular(new BigDecimal(precio), null, Tarifa.enPesos()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} %")
    @ValueSource(ints = {0, 100, -5})
    @DisplayName("un porcentaje fuera de 1..99 no se aplica")
    void porcentajeInvalido(int porcentaje) {
        assertThatThrownBy(() -> CalculadoraDePrecios.calcular(BigDecimal.TEN, porcentaje, Tarifa.enPesos()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("de un producto del catalogo: la promocion solo si esta vigente en ese instante")
    void deProducto() {
        ProductoDelCatalogo producto = new ProductoDelCatalogo("p", "P", null, null, "ARMA", -1, null,
                new BigDecimal("10000"), true, "ACTIVO", null,
                new PromocionDelCatalogo(30, Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-30T00:00:00Z"), true));

        assertThat(CalculadoraDePrecios.deProducto(producto, Tarifa.enPesos(), Instant.parse("2026-09-15T00:00:00Z"))
                .precioFinal()).isEqualByComparingTo("7000");
        assertThat(CalculadoraDePrecios.deProducto(producto, Tarifa.enPesos(), Instant.parse("2026-09-30T00:00:00Z"))
                .precioFinal()).as("hasta es excluido").isEqualByComparingTo("10000");
        assertThat(CalculadoraDePrecios.deProducto(producto, Tarifa.enPesos(), Instant.parse("2026-09-01T00:00:00Z"))
                .precioFinal()).as("desde es incluido").isEqualByComparingTo("7000");
    }

    @Test
    @DisplayName("una promocion sin fechas no se aplica: no se sabe si sigue")
    void promocionSinFechas() {
        PromocionDelCatalogo sinFechas = new PromocionDelCatalogo(30, null, null, true);

        assertThat(sinFechas.porcentajeVigenteEn(Instant.now())).isNull();
    }

    @Test
    @DisplayName("una tarifa necesita una tasa positiva; COP no guarda tasa en la orden")
    void tarifa() {
        assertThatThrownBy(() -> new Tarifa(Moneda.USD, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Tarifa.enPesos().tasaParaMostrar()).isNull();
        assertThat(USD_A_4000.tasaParaMostrar()).isEqualByComparingTo("4000");
    }
}
