package com.nexusbattles.ms_chatbot.chat.entrada;

import com.nexusbattles.ms_chatbot.chat.privacidad.RedactorDeDatosSensibles;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("7.4.8 · revision de lo que escribe el usuario")
class RevisionDeEntradaTest {

    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private final RevisionDeEntrada revision = new RevisionDeEntrada(new RedactorDeDatosSensibles(), metricas);

    @Test
    void quitaControlesEInvisiblesYConservaLosSaltosDeLinea() {
        RevisionDeEntrada.Revisada revisada = revision.revisar(
            " ho\u0000la\u200B\u202E\nque\ttal\u0007 \uFEFF");
        assertThat(revisada.texto()).isEqualTo("hola\nque\ttal");
        assertThat(revisada.seTapoAlgo()).isFalse();
    }

    @Test
    void tapaLosDatosSensiblesYLoDice() {
        RevisionDeEntrada.Revisada revisada = revision.revisar(
            "mi contraseña es Hunter2! y mi tarjeta 4111 1111 1111 1111");
        assertThat(revisada.texto()).doesNotContain("Hunter2!").doesNotContain("4111 1111 1111 1111");
        assertThat(revisada.seTapoAlgo()).isTrue();
    }

    @Test
    void unaPreguntaNormalNoCambia() {
        assertThat(revision.revisar("¿Cómo publico una subasta?"))
            .isEqualTo(new RevisionDeEntrada.Revisada("¿Cómo publico una subasta?", false));
    }

    @Test
    void bloqueaLaInyeccionYLaCuentaPorTipoSinGuardarElTexto() {
        assertThatThrownBy(() -> revision.revisar("' OR '1'='1"))
            .isInstanceOfSatisfying(InyeccionDetectada.class, ex -> {
                assertThat(ex.tipo()).isEqualTo(DetectorDeInyeccion.Tipo.SQL);
                assertThat(ex.getMessage()).doesNotContain("1'='1");
            });
        assertThatThrownBy(() -> revision.revisar("<script>x()</script>")).isInstanceOf(InyeccionDetectada.class);

        assertThat(metricas.counter("chatbot.inyecciones.bloqueadas", "tipo", "SQL").count()).isEqualTo(1.0);
        assertThat(metricas.counter("chatbot.inyecciones.bloqueadas", "tipo", "HTML").count()).isEqualTo(1.0);
    }

    @Test
    void laInyeccionEscondidaConInvisiblesTambienSeVe() {
        assertThatThrownBy(() -> revision.revisar("<scr\u200Bipt>x()</script>")).isInstanceOf(InyeccionDetectada.class);
    }

    @Test
    void nuloSigueSiendoNuloYSoloControlesQuedaVacio() {
        assertThat(revision.revisar(null)).isEqualTo(new RevisionDeEntrada.Revisada(null, false));
        assertThat(revision.textoRevisado("\u0000\u0001\u200B")).isEmpty();
    }
}
