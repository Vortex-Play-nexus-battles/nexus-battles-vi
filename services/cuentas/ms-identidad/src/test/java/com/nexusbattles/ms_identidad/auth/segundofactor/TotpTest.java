package com.nexusbattles.ms_identidad.auth.segundofactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TOTP de la RFC 6238 con HMAC-SHA1, comprobado contra los vectores de su
 * apendice B (semilla ASCII «12345678901234567890»). Los vectores son de 8
 * digitos; el codigo de 6 es el mismo valor modulo 10^6, o sea, sus seis
 * ultimas cifras.
 */
@DisplayName("TOTP (RFC 6238, SHA-1, 30 s)")
class TotpTest {

    private static final byte[] SEMILLA = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest(name = "T={0}s -> {2}")
    @CsvSource({
        "59, 1, 94287082",
        "1111111109, 37037036, 07081804",
        "1111111111, 37037037, 14050471",
        "1234567890, 41152263, 89005924",
        "2000000000, 66666666, 69279037",
        "20000000000, 666666666, 65353130",
    })
    @DisplayName("vectores del apendice B de la RFC 6238 (SHA-1, 8 digitos)")
    void vectoresDeLaRfc(long segundos, long pasoEsperado, String codigoEsperado) {
        long paso = Totp.pasoDe(Instant.ofEpochSecond(segundos));
        assertThat(paso).isEqualTo(pasoEsperado);
        assertThat(Totp.codigo(SEMILLA, paso, 8)).isEqualTo(codigoEsperado);
        // El de 6 digitos que muestran las aplicaciones: las seis ultimas cifras.
        assertThat(Totp.codigo(SEMILLA, paso)).isEqualTo(codigoEsperado.substring(2));
    }

    @Test
    @DisplayName("el codigo vigente se acepta y devuelve su paso")
    void aceptaElCodigoVigente() {
        Instant ahora = Instant.ofEpochSecond(1111111111);
        OptionalLong paso = Totp.verificar(SEMILLA, "050471", ahora, null);
        assertThat(paso).hasValue(37037037);
    }

    @Test
    @DisplayName("±1 paso de holgura: el anterior y el siguiente valen, dos pasos atras no")
    void ventanaDeUnPaso() {
        Instant ahora = Instant.ofEpochSecond(1111111111); // paso 37037037
        String anterior = Totp.codigo(SEMILLA, 37037036);
        String siguiente = Totp.codigo(SEMILLA, 37037038);
        String dosAtras = Totp.codigo(SEMILLA, 37037035);
        String dosAdelante = Totp.codigo(SEMILLA, 37037039);

        assertThat(Totp.verificar(SEMILLA, anterior, ahora, null)).hasValue(37037036);
        assertThat(Totp.verificar(SEMILLA, siguiente, ahora, null)).hasValue(37037038);
        assertThat(Totp.verificar(SEMILLA, dosAtras, ahora, null)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, dosAdelante, ahora, null)).isEmpty();
    }

    @Test
    @DisplayName("sin repeticion: un paso ya usado, o anterior al ultimo usado, no vuelve a valer")
    void noAceptaUnPasoYaUsado() {
        Instant ahora = Instant.ofEpochSecond(1111111111); // paso 37037037
        String vigente = Totp.codigo(SEMILLA, 37037037);
        String anterior = Totp.codigo(SEMILLA, 37037036);
        String siguiente = Totp.codigo(SEMILLA, 37037038);

        assertThat(Totp.verificar(SEMILLA, vigente, ahora, 37037037L)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, anterior, ahora, 37037037L)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, siguiente, ahora, 37037037L)).hasValue(37037038);
    }

    @Test
    @DisplayName("lo que no son seis cifras no se compara: espacios alrededor y en medio si se toleran")
    void formaDelCodigo() {
        Instant ahora = Instant.ofEpochSecond(59);
        assertThat(Totp.verificar(SEMILLA, " 287 082 ", ahora, null)).hasValue(1);
        assertThat(Totp.verificar(SEMILLA, "28708", ahora, null)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, "2870821", ahora, null)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, "28708a", ahora, null)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, null, ahora, null)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, "", ahora, null)).isEmpty();
    }

    @Test
    @DisplayName("un codigo de seis cifras siempre lleva sus ceros a la izquierda")
    void cerosALaIzquierda() {
        // T=1234567890: 89005924 -> «005924».
        assertThat(Totp.codigo(SEMILLA, 41152263)).isEqualTo("005924").hasSize(6);
    }

    @Test
    @DisplayName("un secreto vacio es un error de programacion, no un codigo valido")
    void secretoVacio() {
        assertThatThrownBy(() -> Totp.codigo(new byte[0], 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
