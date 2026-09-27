package com.nexusbattles.ms_ecommerce.compra.pago;

import com.nexusbattles.ms_ecommerce.integracion.PropiedadesDeLaTienda;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Formulario de pago (7.5): Luhn, vencimiento, codigo y titular; nunca se repite el valor")
class ValidadorDeTarjetaTest {

    /** 25 de septiembre de 2026 en Colombia. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-25T17:00:00Z"), ZoneOffset.UTC);

    private static final PropiedadesDeLaTienda PROPIEDADES = new PropiedadesDeLaTienda(
            new PropiedadesDeLaTienda.Http(Duration.ofSeconds(2), Duration.ofSeconds(5)),
            new PropiedadesDeLaTienda.Servicios("i", "f", "c", "id", "p"),
            new PropiedadesDeLaTienda.Credencial("", "", ""),
            new PropiedadesDeLaTienda.Correo(true),
            new PropiedadesDeLaTienda.Ordenes(Duration.ofMinutes(2), Duration.ofMinutes(30), Duration.ofSeconds(15),
                    Duration.ofMinutes(15), 20),
            ZoneId.of("America/Bogota"));

    private final ValidadorDeTarjeta validador = new ValidadorDeTarjeta(RELOJ, PROPIEDADES);

    private static SolicitudDePago solicitud(String titular, String numero, String vencimiento, String codigo) {
        return new SolicitudDePago(titular, numero, vencimiento, codigo, Moneda.COP);
    }

    private static SolicitudDePago valida(String numero) {
        return solicitud("Ana Pérez", numero, "12/28", "123");
    }

    @Test
    @DisplayName("una tarjeta valida: marca y cuatro ultimos; nada mas se conserva a la vista")
    void valida() {
        TarjetaValidada tarjeta = validador.validar(valida("4242 4242 4242 4242"));

        assertThat(tarjeta.marca()).isEqualTo("VISA");
        assertThat(tarjeta.ultimos4()).isEqualTo("4242");
        assertThat(tarjeta.toString()).doesNotContain("4242 4242").doesNotContain("424242424242");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "4242424242424242, VISA",
            "5555555555554444, MASTERCARD",
            "2223003122003222, MASTERCARD",
            "378282246310005, AMEX",
            "36227206271667, DINERS",
            "6011111111111117, DISCOVER",
            "9000000000000009, TARJETA"
    })
    @DisplayName("reconoce la marca por el prefijo")
    void marcas(String numero, String marca) {
        assertThat(ValidadorDeTarjeta.marcaDe(numero)).isEqualTo(marca);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"4242424242424241", "1234567890123", "4242-4242-4242-4242", "42424242424", "abcd",
            "4242 4242 4242 4242 4242 4", ""})
    @DisplayName("Luhn, longitud o caracteres: numeroTarjeta invalido, sin repetir el valor")
    void numeroInvalido(String numero) {
        assertThatThrownBy(() -> validador.validar(valida(numero)))
                .isInstanceOfSatisfying(DatosDePagoInvalidosException.class, e -> {
                    assertThat(e.campo()).isEqualTo("numeroTarjeta");
                    if (!numero.isEmpty()) {
                        assertThat(e.getMessage()).doesNotContain(numero);
                    }
                });
    }

    @Test
    @DisplayName("sin numero: numeroTarjeta")
    void sinNumero() {
        assertThatThrownBy(() -> validador.validar(solicitud("Ana", null, "12/28", "123")))
                .isInstanceOfSatisfying(DatosDePagoInvalidosException.class,
                        e -> assertThat(e.campo()).isEqualTo("numeroTarjeta"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"09/26", "12/26", "01/30"})
    @DisplayName("vencimiento en el mes en curso o despues: vale")
    void vencimientoVigente(String vencimiento) {
        assertThat(validador.validar(solicitud("Ana", "4242424242424242", vencimiento, "123"))).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"08/26", "12/25", "13/28", "00/28", "1/28", "12-28", "1228", ""})
    @DisplayName("vencida o sin la forma MM/AA: vencimiento")
    void vencimientoInvalido(String vencimiento) {
        assertThatThrownBy(() -> validador.validar(solicitud("Ana", "4242424242424242", vencimiento, "123")))
                .isInstanceOfSatisfying(DatosDePagoInvalidosException.class,
                        e -> assertThat(e.campo()).isEqualTo("vencimiento"));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"12", "12345", "12a", ""})
    @DisplayName("el codigo son 3 o 4 cifras")
    void codigoInvalido(String codigo) {
        assertThatThrownBy(() -> validador.validar(solicitud("Ana", "4242424242424242", "12/28", codigo)))
                .isInstanceOfSatisfying(DatosDePagoInvalidosException.class, e -> {
                    assertThat(e.campo()).isEqualTo("codigoSeguridad");
                    assertThat(e.getMessage()).doesNotContain(codigo.isEmpty() ? "\u0000" : codigo);
                });
    }

    @Test
    @DisplayName("4 cifras tambien son un codigo (AMEX)")
    void codigoDeCuatro() {
        assertThat(validador.validar(solicitud("Ana", "378282246310005", "12/28", "1234")).marca()).isEqualTo("AMEX");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "  ", "Al"})
    @DisplayName("el titular tiene de 3 a 80 caracteres")
    void titularInvalido(String titular) {
        assertThatThrownBy(() -> validador.validar(solicitud(titular, "4242424242424242", "12/28", "123")))
                .isInstanceOfSatisfying(DatosDePagoInvalidosException.class,
                        e -> assertThat(e.campo()).isEqualTo("titular"));
        assertThatThrownBy(() -> validador.validar(solicitud("A".repeat(81), "4242424242424242", "12/28", "123")))
                .isInstanceOf(DatosDePagoInvalidosException.class);
        assertThatThrownBy(() -> validador.validar(solicitud(null, "4242424242424242", "12/28", "123")))
                .isInstanceOf(DatosDePagoInvalidosException.class);
    }

    @Test
    @DisplayName("la solicitud no ensena los datos de la tarjeta en su toString")
    void solicitudSinDatosEnToString() {
        SolicitudDePago solicitud = new SolicitudDePago("Ana", "4242424242424242", "12/28", "987", null);

        assertThat(solicitud.toString()).doesNotContain("4242").doesNotContain("987");
        assertThat(solicitud.monedaPedida()).isEqualTo(Moneda.COP);
    }

    @Test
    @DisplayName("pasarela simulada: 0002 rechaza, 0069 cae, el resto aprueba; y cuenta lo que hizo")
    void pasarelaSimulada() {
        PasarelaSimulada pasarela = new PasarelaSimulada();
        UUID orden = UUID.randomUUID();

        ResultadoDeCobro aprobado = pasarela.cobrar(validador.validar(valida("4242424242424242")), BigDecimal.TEN,
                Moneda.COP, orden);
        ResultadoDeCobro rechazado = pasarela.cobrar(validador.validar(valida("4000000000000002")), BigDecimal.TEN,
                Moneda.COP, orden);
        ResultadoDeCobro caido = pasarela.cobrar(validador.validar(valida("4000000000000069")), BigDecimal.TEN,
                Moneda.COP, orden);
        String reembolso = pasarela.reembolsar("SIM-x", BigDecimal.TEN, Moneda.COP);

        assertThat(aprobado).isInstanceOfSatisfying(ResultadoDeCobro.Aprobado.class,
                a -> assertThat(a.referencia()).startsWith("SIM-"));
        assertThat(rechazado).isInstanceOfSatisfying(ResultadoDeCobro.Rechazado.class,
                r -> assertThat(r.motivo()).contains("fondos insuficientes"));
        assertThat(caido).isInstanceOf(ResultadoDeCobro.NoDisponible.class);
        assertThat(reembolso).startsWith("SIMR-");
        assertThat(pasarela.cobrosAprobados()).isEqualTo(1);
        assertThat(pasarela.cobrosRechazados()).isEqualTo(1);
        assertThat(pasarela.caidas()).isEqualTo(1);
        assertThat(pasarela.reembolsos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Luhn sobre las tarjetas de prueba del contrato")
    void tarjetasDePrueba() {
        assertThat(ValidadorDeTarjeta.pasaLuhn("4242424242424242")).isTrue();
        assertThat(ValidadorDeTarjeta.pasaLuhn("4000000000000002")).isTrue();
        assertThat(ValidadorDeTarjeta.pasaLuhn("4000000000000069")).isTrue();
        assertThat(ValidadorDeTarjeta.pasaLuhn("4242424242424243")).isFalse();
    }
}
