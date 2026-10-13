package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.3.6 (7.4.5): nivel de detalle de las respuestas.
class AjusteDeDetalleTest {

    @Test
    void breve_dejaSoloLaPrimeraOracion() {
        assertThat(AjusteDeDetalle.aplicar("Primera oración. Segunda oración.", TipoRespuesta.DIRECTA,
            NivelDeDetalle.BREVE, List.of(), false)).isEqualTo("Primera oración.");
    }

    @Test
    void breve_cortaTambienEnExclamacionesYPreguntas() {
        assertThat(AjusteDeDetalle.aplicar("¡Hola! ¿En qué te ayudo?", TipoRespuesta.DIRECTA,
            NivelDeDetalle.BREVE, List.of(), false)).isEqualTo("¡Hola!");
    }

    // Un punto seguido de minuscula o de un numero no termina la oracion.
    @Test
    void breve_noCortaSiNoSigueUnaMayuscula() {
        String texto = "Pasar de nivel pide 100 × 1,2^(N−1) puntos. y cada enemigo da experiencia.";
        assertThat(AjusteDeDetalle.aplicar(texto, TipoRespuesta.DIRECTA, NivelDeDetalle.BREVE, List.of(), false))
            .isEqualTo(texto);
    }

    @Test
    void breve_enUnTemaPasoAPaso_noCambiaNada() {
        String texto = "Para empezar: 1) Crea tu cuenta. 2) Inicia sesión.";
        assertThat(AjusteDeDetalle.aplicar(texto, TipoRespuesta.PASO_A_PASO, NivelDeDetalle.BREVE, List.of(), false))
            .isEqualTo(texto);
    }

    @Test
    void normal_devuelveElTextoTalCual() {
        String texto = "Primera. Segunda.";
        assertThat(AjusteDeDetalle.aplicar(texto, TipoRespuesta.DIRECTA, NivelDeDetalle.NORMAL,
            List.of("Otro tema"), false)).isEqualTo(texto);
    }

    @Test
    void detallado_agregaLosTemasRelacionadosEnElIdiomaDeLaRespuesta() {
        assertThat(AjusteDeDetalle.aplicar("Texto.", TipoRespuesta.DIRECTA, NivelDeDetalle.DETALLADO,
            List.of("Tema A", "Tema B"), false)).isEqualTo("Texto. Temas relacionados: Tema A | Tema B.");
        assertThat(AjusteDeDetalle.aplicar("Text.", TipoRespuesta.DIRECTA, NivelDeDetalle.DETALLADO,
            List.of("Tema A"), true)).isEqualTo("Text. Related topics: Tema A.");
    }

    @Test
    void detallado_sinTemasRelacionados_noCambiaNada() {
        assertThat(AjusteDeDetalle.aplicar("Texto.", TipoRespuesta.DIRECTA, NivelDeDetalle.DETALLADO,
            List.of(), false)).isEqualTo("Texto.");
        assertThat(AjusteDeDetalle.aplicar("Texto.", TipoRespuesta.DIRECTA, NivelDeDetalle.DETALLADO,
            null, false)).isEqualTo("Texto.");
    }

    @Test
    void sinTextoOSinNivel_devuelveLoMismo() {
        assertThat(AjusteDeDetalle.aplicar(null, TipoRespuesta.DIRECTA, NivelDeDetalle.BREVE, List.of(), false))
            .isNull();
        assertThat(AjusteDeDetalle.aplicar("  ", TipoRespuesta.DIRECTA, NivelDeDetalle.BREVE, List.of(), false))
            .isEqualTo("  ");
        assertThat(AjusteDeDetalle.aplicar("Uno. Dos.", TipoRespuesta.DIRECTA, null, List.of(), false))
            .isEqualTo("Uno. Dos.");
    }
}
