package com.nexusbattles.ms_identidad.auth.segundofactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base32 de la RFC 4648 (§6), el formato en el que las aplicaciones de
 * autenticacion esperan el secreto TOTP. Vectores de la propia RFC (§10).
 */
@DisplayName("Base32 (RFC 4648) del secreto TOTP")
class Base32Test {

    @ParameterizedTest(name = "«{0}» -> {1}")
    @CsvSource({
        "'', ''",
        "f, MY",
        "fo, MZXQ",
        "foo, MZXW6",
        "foob, MZXW6YQ",
        "fooba, MZXW6YTB",
        "foobar, MZXW6YTBOI",
    })
    @DisplayName("codifica los vectores de la RFC 4648, sin relleno")
    void codificaLosVectoresDeLaRfc(String claro, String esperado) {
        assertThat(Base32.codificar(claro.getBytes(StandardCharsets.US_ASCII))).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{1} -> «{0}»")
    @CsvSource({
        "f, MY======",
        "fo, MZXQ====",
        "foo, MZXW6===",
        "foob, MZXW6YQ=",
        "fooba, MZXW6YTB",
        "foobar, MZXW6YTBOI======",
    })
    @DisplayName("decodifica con y sin relleno")
    void decodificaConRelleno(String claro, String codificado) {
        assertThat(Base32.decodificar(codificado)).isEqualTo(claro.getBytes(StandardCharsets.US_ASCII));
        assertThat(Base32.decodificar(codificado.replace("=", "")))
                .isEqualTo(claro.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("el secreto de los vectores de la RFC 6238 es el que publican todas las implementaciones")
    void secretoDeLaRfc6238() {
        assertThat(Base32.codificar("12345678901234567890".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    @Test
    @DisplayName("como lo teclea una persona: minusculas y espacios valen")
    void toleraMinusculasYEspacios() {
        assertThat(Base32.decodificar("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"))
                .isEqualTo("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("ida y vuelta con secretos al azar de 20 bytes")
    void idaYVuelta() {
        SecureRandom azar = new SecureRandom();
        for (int i = 0; i < 50; i++) {
            byte[] secreto = new byte[20];
            azar.nextBytes(secreto);
            String codificado = Base32.codificar(secreto);
            assertThat(codificado).hasSize(32).matches("[A-Z2-7]+");
            assertThat(Base32.decodificar(codificado)).isEqualTo(secreto);
        }
    }

    @Test
    @DisplayName("un caracter fuera del alfabeto no se adivina: se rechaza")
    void rechazaCaracteresAjenos() {
        assertThatThrownBy(() -> Base32.decodificar("MZXW1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base32.decodificar(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
