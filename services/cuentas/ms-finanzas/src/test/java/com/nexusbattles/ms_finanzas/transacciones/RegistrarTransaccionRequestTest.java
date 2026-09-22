package com.nexusbattles.ms_finanzas.transacciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class RegistrarTransaccionRequestTest {

    private RegistrarTransaccionRequest valida(BigDecimal monto, String moneda) {
        return new RegistrarTransaccionRequest(
                "ref-1", "uid-xyz", monto, moneda, "compra-item",
                ResultadoTransaccion.APROBADO, null, null);
    }

    @Test
    void construccion_conCamposValidos_ok() {
        RegistrarTransaccionRequest solicitud = valida(new BigDecimal("50000.00"), "COP");
        assertThat(solicitud.refId()).isEqualTo("ref-1");
        assertThat(solicitud.moneda()).isEqualTo("COP");
    }

    @Test
    void refIdNull_lanzaNullPointer() {
        assertThatThrownBy(() -> new RegistrarTransaccionRequest(
                null, "uid", BigDecimal.TEN, "COP", "x",
                ResultadoTransaccion.APROBADO, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("refId");
    }

    @Test
    void refIdVacio_lanzaIllegalArgument() {
        assertThatThrownBy(() -> new RegistrarTransaccionRequest(
                "  ", "uid", BigDecimal.TEN, "COP", "x",
                ResultadoTransaccion.APROBADO, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refId");
    }

    @Test
    void uidUsuarioNull_lanzaNullPointer() {
        assertThatThrownBy(() -> new RegistrarTransaccionRequest(
                "ref-1", null, BigDecimal.TEN, "COP", "x",
                ResultadoTransaccion.APROBADO, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("uidUsuario");
    }

    @Test
    void uidUsuarioVacio_lanzaIllegalArgument() {
        assertThatThrownBy(() -> new RegistrarTransaccionRequest(
                "ref-1", "", BigDecimal.TEN, "COP", "x",
                ResultadoTransaccion.APROBADO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void montoNull_lanzaNullPointer() {
        assertThatThrownBy(() -> new RegistrarTransaccionRequest(
                "ref-1", "uid", null, "COP", "x",
                ResultadoTransaccion.APROBADO, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void montoNegativo_lanzaIllegalArgument() {
        assertThatThrownBy(() -> new RegistrarTransaccionRequest(
                "ref-1", "uid", new BigDecimal("-1"), "COP", "x",
                ResultadoTransaccion.APROBADO, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void monedaLongitudInvalida_lanzaIllegalArgument() {
        assertThatThrownBy(() -> valida(BigDecimal.TEN, "COLOMBIANO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO");
    }

    @Test
    void monedaNull_lanzaNullPointer() {
        assertThatThrownBy(() -> new RegistrarTransaccionRequest(
                "ref-1", "uid", BigDecimal.TEN, null, "x",
                ResultadoTransaccion.APROBADO, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void conceptoVacio_lanzaIllegalArgument() {
        assertThatThrownBy(() -> new RegistrarTransaccionRequest(
                "ref-1", "uid", BigDecimal.TEN, "COP", "  ",
                ResultadoTransaccion.APROBADO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultadoNull_lanzaNullPointer() {
        assertThatThrownBy(() -> new RegistrarTransaccionRequest(
                "ref-1", "uid", BigDecimal.TEN, "COP", "x",
                null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
