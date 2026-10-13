package com.nexusbattles.ms_identidad.auth.segundofactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los codigos de recuperacion (CA-02): de un solo uso, al azar, legibles y
 * faciles de transcribir (sin 0/O ni 1/I/L, en dos bloques).
 */
@DisplayName("Codigos de recuperacion del segundo factor")
class CodigosDeRecuperacionTest {

    private final CodigosDeRecuperacion codigos = new CodigosDeRecuperacion();

    @Test
    @DisplayName("genera los pedidos, distintos, en dos bloques de cinco con el alfabeto sin ambiguos")
    void formaDeLosCodigos() {
        List<String> generados = codigos.generar(10);

        assertThat(generados).hasSize(10).doesNotHaveDuplicates();
        assertThat(generados).allMatch(c -> c.matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{5}-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{5}"));
    }

    @Test
    @DisplayName("al azar de verdad: mil codigos no se repiten")
    void alAzar() {
        HashSet<String> vistos = new HashSet<>(codigos.generar(1000));
        assertThat(vistos).hasSize(1000);
    }

    @Test
    @DisplayName("como lo escribe una persona -> como se guardo: sin guion ni espacios y en mayusculas")
    void normaliza() {
        assertThat(CodigosDeRecuperacion.normalizar(" k7qx2-m9prt ")).isEqualTo("K7QX2M9PRT");
        assertThat(CodigosDeRecuperacion.normalizar("K7QX2 M9PRT")).isEqualTo("K7QX2M9PRT");
        assertThat(CodigosDeRecuperacion.normalizar(null)).isEmpty();
    }

    @Test
    @DisplayName("lo que no puede ser un codigo de recuperacion no se compara (BCrypt no ve mas de 72 bytes)")
    void pareceUnCodigo() {
        assertThat(CodigosDeRecuperacion.pareceUnCodigo("K7QX2M9PRT")).isTrue();
        assertThat(CodigosDeRecuperacion.pareceUnCodigo("K7QX2M9PR")).isFalse();
        assertThat(CodigosDeRecuperacion.pareceUnCodigo("K7QX2M9PRTT")).isFalse();
        assertThat(CodigosDeRecuperacion.pareceUnCodigo("K7QX2M9PR0")).isFalse();
        assertThat(CodigosDeRecuperacion.pareceUnCodigo("x".repeat(500))).isFalse();
    }

    @Test
    @DisplayName("una cantidad imposible es un error de configuracion")
    void cantidadImposible() {
        assertThatThrownBy(() -> codigos.generar(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
