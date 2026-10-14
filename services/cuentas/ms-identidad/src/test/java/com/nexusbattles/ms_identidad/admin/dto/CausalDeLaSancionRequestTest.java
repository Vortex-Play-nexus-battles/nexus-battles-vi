package com.nexusbattles.ms_identidad.admin.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SA-4 — la causal que el panel manda al suspender o banear es una de las
 * ocho de §7.3.2, o ninguna; cualquier otra cosa es 400 aqui, sin llegar a
 * moderacion-sanciones.
 */
@DisplayName("Causal y politica al suspender y banear (ms-identidad-admin.yaml 1.8.0)")
class CausalDeLaSancionRequestTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void abrir() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    private static SuspenderCuentaRequest suspension(String causal, String politica) {
        SuspenderCuentaRequest datos = new SuspenderCuentaRequest();
        datos.setSuspendidoHasta(LocalDateTime.now().plusDays(1));
        datos.setMotivo("Insultos en la sala 12");
        datos.setCausal(causal);
        datos.setPolitica(politica);
        return datos;
    }

    @ParameterizedTest(name = "{0} es una causal valida")
    @ValueSource(strings = {"LENGUAJE_OFENSIVO", "ACOSO", "FRAUDE", "SOFTWARE_NO_AUTORIZADO",
        "SUPLANTACION_DE_IDENTIDAD", "VIOLACION_DE_PRIVACIDAD", "INCUMPLIMIENTO_DE_TERMINOS",
        "TERMINACION_DE_RELACION_LEGAL"})
    void lasOchoCausales(String causal) {
        assertThat(validador.validate(suspension(causal, null))).isEmpty();
        assertThat(validador.validate(new BanearCuentaRequest("Fraude", causal, null))).isEmpty();
    }

    @ParameterizedTest(name = "«{0}» no es una causal")
    @ValueSource(strings = {"OTRA", "acoso", "ACOSO ", "FRAUDE|ACOSO", ""})
    void otraCosaNoEsCausal(String causal) {
        Set<ConstraintViolation<SuspenderCuentaRequest>> enLaSuspension = validador.validate(suspension(causal, null));
        Set<ConstraintViolation<BanearCuentaRequest>> enElBaneo =
                validador.validate(new BanearCuentaRequest("Fraude", causal, null));

        assertThat(enLaSuspension).extracting(v -> v.getPropertyPath().toString()).containsExactly("causal");
        assertThat(enLaSuspension).extracting(ConstraintViolation::getMessage)
                .containsExactly("La causal debe ser una de las ocho de §7.3.2.");
        assertThat(enElBaneo).extracting(v -> v.getPropertyPath().toString()).containsExactly("causal");
    }

    @Test
    @DisplayName("sin causal ni politica sigue siendo valido, como antes de SA-4")
    void sinCausal() {
        assertThat(validador.validate(suspension(null, null))).isEmpty();
        assertThat(validador.validate(new BanearCuentaRequest("Fraude"))).isEmpty();
        assertThat(new BanearCuentaRequest("Fraude").causal()).isNull();
    }

    @Test
    @DisplayName("la politica no pasa de 200 caracteres")
    void politicaLarga() {
        assertThat(validador.validate(suspension("ACOSO", "x".repeat(200)))).isEmpty();
        assertThat(validador.validate(suspension("ACOSO", "x".repeat(201))))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("politica");
        assertThat(validador.validate(new BanearCuentaRequest("Fraude", "FRAUDE", "x".repeat(201))))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("politica");
    }
}
