package com.nexusbattles.ms_ecommerce.precios;

import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import com.nexusbattles.ms_ecommerce.integracion.parametros.ClienteDeParametros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Tasas de cambio: de admin-parametros, nunca inventadas")
class TasasDeCambioTest {

    /** Reloj que solo avanza cuando la prueba lo dice. */
    private static final class Reloj extends Clock {
        private Instant ahora = Instant.parse("2026-09-25T12:00:00Z");

        void avanzar(Duration intervalo) {
            ahora = ahora.plus(intervalo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private ClienteDeParametros parametros;
    private Reloj reloj;
    private TasasDeCambio tasas;

    @BeforeEach
    void preparar() {
        parametros = mock(ClienteDeParametros.class);
        reloj = new Reloj();
        tasas = new TasasDeCambio(parametros, reloj);
    }

    @Test
    @DisplayName("COP no depende de nada: tarifa 1 sin preguntar")
    void pesos() {
        assertThat(tasas.tarifa(Moneda.COP)).isEqualTo(Tarifa.enPesos());
        verifyNoInteractions(parametros);
    }

    @Test
    @DisplayName("con el parametro definido, la tarifa es la tasa de admin-parametros")
    void conTasa() {
        when(parametros.valor(TasasDeCambio.CLAVE_USD)).thenReturn(Optional.of("4000.50"));

        Tarifa tarifa = tasas.tarifa(Moneda.USD);

        assertThat(tarifa.moneda()).isEqualTo(Moneda.USD);
        assertThat(tarifa.copPorUnidad()).isEqualByComparingTo("4000.50");
    }

    @Test
    @DisplayName("sin parametro (o sin valor): esa moneda no esta disponible, y lo dice con las que si")
    void sinTasa() {
        when(parametros.valor(TasasDeCambio.CLAVE_USD)).thenReturn(Optional.empty());
        when(parametros.valor(TasasDeCambio.CLAVE_EUR)).thenReturn(Optional.of("4400"));

        assertThatThrownBy(() -> tasas.tarifa(Moneda.USD))
                .isInstanceOfSatisfying(MonedaNoDisponibleException.class, e -> {
                    assertThat(e.moneda()).isEqualTo(Moneda.USD);
                    assertThat(e.disponibles()).containsExactly(Moneda.COP, Moneda.EUR);
                });
        assertThat(tasas.disponibles()).containsExactlyInAnyOrder(Moneda.COP, Moneda.EUR);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"cero", "0", "-4000", "4.000,50"})
    @DisplayName("un valor que no es un numero positivo no es una tasa")
    void valorInvalido(String valor) {
        when(parametros.valor(TasasDeCambio.CLAVE_EUR)).thenReturn(Optional.of(valor));

        assertThatThrownBy(() -> tasas.tarifa(Moneda.EUR)).isInstanceOf(MonedaNoDisponibleException.class);
    }

    @Test
    @DisplayName("la tasa se guarda un minuto: dentro, no vuelve a preguntar; despues, si")
    void copiaDeUnMinuto() {
        when(parametros.valor(TasasDeCambio.CLAVE_USD)).thenReturn(Optional.of("4000"), Optional.of("4100"));

        tasas.tarifa(Moneda.USD);
        reloj.avanzar(Duration.ofSeconds(59));
        assertThat(tasas.tarifa(Moneda.USD).copPorUnidad()).isEqualByComparingTo("4000");
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(tasas.tarifa(Moneda.USD).copPorUnidad()).isEqualByComparingTo("4100");

        verify(parametros, times(2)).valor(TasasDeCambio.CLAVE_USD);
    }

    @Test
    @DisplayName("si admin-parametros cae, se sigue con la ultima tasa hasta 15 minutos; despues, sin tasa")
    void graciaAnteUnaCaida() {
        when(parametros.valor(TasasDeCambio.CLAVE_USD))
                .thenReturn(Optional.of("4000"))
                .thenThrow(new ServicioNoDisponibleException("admin-parametros", "caido"));

        tasas.tarifa(Moneda.USD);
        reloj.avanzar(Duration.ofMinutes(10));
        assertThat(tasas.tarifa(Moneda.USD).copPorUnidad()).isEqualByComparingTo("4000");
        reloj.avanzar(Duration.ofMinutes(6));
        assertThatThrownBy(() -> tasas.tarifa(Moneda.USD)).isInstanceOf(MonedaNoDisponibleException.class);
    }

    @Test
    @DisplayName("sin ninguna tasa leida y admin-parametros caido: la moneda no se ofrece, la tienda sigue en COP")
    void caidoSinTasaPrevia() {
        when(parametros.valor(TasasDeCambio.CLAVE_USD))
                .thenThrow(new ServicioNoDisponibleException("admin-parametros", "caido"));
        when(parametros.valor(TasasDeCambio.CLAVE_EUR))
                .thenThrow(new ServicioNoDisponibleException("admin-parametros", "caido"));

        assertThat(tasas.disponibles()).containsExactly(Moneda.COP);
    }
}
