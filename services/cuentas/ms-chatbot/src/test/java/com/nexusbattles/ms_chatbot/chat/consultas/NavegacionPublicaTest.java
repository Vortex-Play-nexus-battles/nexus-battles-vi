package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.enriquecido.EnlaceInterno;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// 1.11.0 (7.4.4 «navegación asistida»): las secciones que no son datos de
// nadie, para cualquiera.
class NavegacionPublicaTest {

    private final NavegacionPublica navegacion = new NavegacionPublica();

    private ResultadoMotor responder(String mensaje, boolean conSesion) {
        return navegacion.responder(mensaje, conSesion, IdiomaPreferido.AUTOMATICO).orElseThrow();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "llévame a la subasta | subastas",
        "Llévame a los torneos, por favor | torneos",
        "ir al centro de ayuda | ayuda",
        "abre la ayuda | ayuda",
        "llevame al registro | registro",
        "quiero ir a crear una cuenta | registro",
        "llévame a iniciar sesión | login",
        "ir al inicio | portada",
        "take me to the auctions | subastas",
        "go to the help center | ayuda",
        "llevame a la tiedna | login"
    })
    void unVisitanteLlegaALasSeccionesPublicas(String mensaje, String destino) {
        ResultadoMotor resultado = responder(mensaje, false);

        assertThat(resultado.enriquecido().enlaces()).extracting(EnlaceInterno::destino).first().isEqualTo(destino);
        assertThat(resultado.resultado()).isEqualTo(ResultadoDeRespuesta.NAVEGACION);
        assertThat(resultado.requiereEscalamiento()).isFalse();
    }

    @Test
    void laTiendaYJugarPidenSesionAUnVisitante() {
        ResultadoMotor tienda = responder("llévame a la tienda", false);

        assertThat(tienda.texto()).isEqualTo("Para abrir la «Tienda» necesitas iniciar sesión. "
            + "Si todavía no tienes cuenta, puedes crear una.");
        assertThat(tienda.enriquecido().enlaces()).extracting(EnlaceInterno::destino)
            .containsExactly("login", "registro");
        assertThat(responder("vamos a jugar", false).texto()).startsWith("Para abrir «Jugar online» necesitas");
    }

    @Test
    void conSesionLleganDirecto() {
        ResultadoMotor tienda = responder("llévame a la tienda", true);

        assertThat(tienda.texto()).isEqualTo("Aquí tienes la «Tienda».");
        assertThat(tienda.enriquecido().enlaces()).extracting(EnlaceInterno::destino).containsExactly("tienda");
        assertThat(responder("ir al chat general", true).enriquecido().enlaces())
            .extracting(EnlaceInterno::destino).containsExactly("chat");
        assertThat(responder("ir al inicio", true).enriquecido().enlaces())
            .extracting(EnlaceInterno::destino).containsExactly("home");
    }

    @Test
    void enInglesConLaEtiquetaDeLaBarra() {
        ResultadoMotor resultado = responder("take me to the store", true);

        assertThat(resultado.ingles()).isTrue();
        assertThat(resultado.texto()).isEqualTo("Here you go: the «Tienda» (Store).");
        assertThat(resultado.enriquecido().enlaces()).extracting(EnlaceInterno::texto).containsExactly("Go to Tienda");
        assertThat(navegacion.responder("ir a la subasta", false, IdiomaPreferido.EN).orElseThrow().texto())
            .isEqualTo("Here you go: the «Subasta» (Auction).");
    }

    // Solo cuando el mensaje entero es pedir ir: lo demás lo responde la base.
    @ParameterizedTest
    @ValueSource(strings = {"¿Qué se compra en la tienda?", "¿Cómo funciona la subasta?", "ayuda",
        "necesito ayuda con mi cuenta", "¿Dónde está la tienda?", "llévame a mi inventario", "hola", ""})
    void loQueNoEsIrAUnaSeccionSigue(String mensaje) {
        assertThat(navegacion.responder(mensaje, false, IdiomaPreferido.AUTOMATICO)).as(mensaje).isEmpty();
    }

    @Test
    void sinMensajeNoHayNada() {
        assertThat(navegacion.responder(null, true, null)).isEmpty();
    }
}
