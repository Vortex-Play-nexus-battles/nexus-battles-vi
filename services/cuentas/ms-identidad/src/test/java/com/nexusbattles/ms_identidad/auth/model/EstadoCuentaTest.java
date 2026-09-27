package com.nexusbattles.ms_identidad.auth.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Estados de la cuenta con el nombre del contrato (B1/B2)")
class EstadoCuentaTest {

    @Test
    @DisplayName("las formas anteriores a B2 (femenino) se reconocen y se publican como el contrato")
    void formasAnteriores() {
        assertThat(EstadoCuenta.esBaneado("BANEADA")).isTrue();
        assertThat(EstadoCuenta.esBaneado("BANEADO")).isTrue();
        assertThat(EstadoCuenta.esSuspendido("SUSPENDIDA")).isTrue();
        assertThat(EstadoCuenta.esSuspendido("SUSPENDIDO")).isTrue();
        assertThat(EstadoCuenta.esBaneado("ACTIVO")).isFalse();
        assertThat(EstadoCuenta.esSuspendido(null)).isFalse();

        assertThat(EstadoCuenta.normalizado("BANEADA")).isEqualTo("BANEADO");
        assertThat(EstadoCuenta.normalizado("SUSPENDIDA")).isEqualTo("SUSPENDIDO");
        assertThat(EstadoCuenta.normalizado("PENDIENTE_VERIFICACION")).isEqualTo("PENDIENTE_VERIFICACION");
        assertThat(EstadoCuenta.normalizado(null)).isNull();
    }
}
