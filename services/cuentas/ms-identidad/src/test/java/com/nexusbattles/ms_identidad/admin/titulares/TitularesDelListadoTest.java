package com.nexusbattles.ms_identidad.admin.titulares;

import com.nexusbattles.ms_identidad.admin.directorio.ConsultaInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Los uid de un listado (SA-4, ms-identidad-admin 1.9.0)")
class TitularesDelListadoTest {

    private static final String ANA = "11111111-2222-3333-4444-555555555555";
    private static final String BETO = "66666666-7777-8888-9999-000000000000";

    @Test
    @DisplayName("nada escrito es «sin filtro»: un conjunto vacio, no un error")
    void vacio() {
        assertThat(TitularesDelListado.de(null)).isEmpty();
        assertThat(TitularesDelListado.de("   ")).isEmpty();
        assertThat(TitularesDelListado.de(" , ,")).isEmpty();
    }

    @Test
    @DisplayName("se recortan, no se repiten y conservan el orden en que llegaron")
    void sinRepetir() {
        assertThat(TitularesDelListado.de(" " + BETO + " ," + ANA + "," + BETO.toUpperCase()))
                .containsExactly(UUID.fromString(BETO), UUID.fromString(ANA));
    }

    @Test
    @DisplayName("un uid mal escrito es un 400 con el valor y como escribirlo")
    void malEscrito() {
        assertThatThrownBy(() -> TitularesDelListado.de(ANA + ",ana"))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessage("«ana» no es el uid de una cuenta: escríbelo completo "
                        + "(por ejemplo, 11111111-2222-3333-4444-555555555555).");
    }

    @Test
    @DisplayName("un valor larguísimo se cita recortado")
    void citaRecortada() {
        assertThatThrownBy(() -> TitularesDelListado.de("x".repeat(80)))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageStartingWith("«" + "x".repeat(40) + "…» no es el uid");
    }

    @Test
    @DisplayName("hasta cien; ciento uno es un 400")
    void tope() {
        String cien = IntStream.range(0, 100).mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));
        assertThat(TitularesDelListado.de(cien)).hasSize(100);
        assertThatThrownBy(() -> TitularesDelListado.de(cien + "," + UUID.randomUUID()))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessage("Se pueden pedir como mucho 100 cuentas a la vez.");
    }

    @Test
    @DisplayName("el conjunto devuelto no se puede modificar")
    void inmutable() {
        assertThatThrownBy(() -> TitularesDelListado.de(ANA).add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
