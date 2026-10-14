package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.enriquecido.Enriquecedor;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.7.0: el resultado de una respuesta lo dice quien la genera;
// sin decirlo, solo se deduce lo seguro.
class ResultadoMotorTest {

    @Test
    void sinResultadoSeDeduceSoloLoSeguro() {
        assertThat(ResultadoMotor.deTema("x", Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA, "clave").resultado())
            .isEqualTo(ResultadoDeRespuesta.TEMA);
        assertThat(ResultadoMotor.escalado("x", List.of()).resultado()).isEqualTo(ResultadoDeRespuesta.ESCALADO);
        // Sin tema y sin escalar podria ser cualquier cosa: no se adivina.
        assertThat(ResultadoMotor.deTema("x", null, TipoRespuesta.DIRECTA).resultado()).isNull();
    }

    @Test
    void elResultadoDadoSeConservaEnLasCopias() {
        ResultadoMotor resultado = ResultadoMotor.deTema("x", null, TipoRespuesta.CONTEXTUAL)
            .conResultado(ResultadoDeRespuesta.NO_DISPONIBLE)
            .conEnriquecido(Enriquecedor.soloSoporte())
            .enIngles(true);

        assertThat(resultado.resultado()).isEqualTo(ResultadoDeRespuesta.NO_DISPONIBLE);
        assertThat(resultado.ingles()).isTrue();
        assertThat(resultado.enriquecido().ofrecerSoporteHumano()).isTrue();
        assertThat(resultado.conResultado(ResultadoDeRespuesta.NAVEGACION).resultado())
            .isEqualTo(ResultadoDeRespuesta.NAVEGACION);
    }
}
