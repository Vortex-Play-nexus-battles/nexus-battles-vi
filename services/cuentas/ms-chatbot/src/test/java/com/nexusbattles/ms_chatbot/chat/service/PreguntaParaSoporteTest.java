package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.service.PreguntaParaSoporte.Motivo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.8.0: que pregunta se lleva a soporte desde la conversacion.
class PreguntaParaSoporteTest {

    private static final Instant INICIO = Instant.parse("2026-10-14T15:00:00Z");

    private final List<Mensaje> conversacion = new ArrayList<>();

    private PreguntaParaSoporteTest jugador(String texto) {
        conversacion.add(new Mensaje(null, Remitente.USUARIO, texto, null, INICIO.plusSeconds(conversacion.size())));
        return this;
    }

    private PreguntaParaSoporteTest bot(ResultadoDeRespuesta resultado, Categoria categoria) {
        Mensaje mensaje = new Mensaje(null, Remitente.BOT, "respuesta", null, INICIO.plusSeconds(conversacion.size()));
        mensaje.registrarDatosDeRespuesta(null, categoria, resultado == ResultadoDeRespuesta.ESCALADO
            || resultado == ResultadoDeRespuesta.NO_SIRVIO, 5, resultado);
        conversacion.add(mensaje);
        return this;
    }

    // Una respuesta guardada antes de V13: sin resultado, solo la marca escalado.
    private PreguntaParaSoporteTest botAntiguo(boolean escalado, Categoria categoria) {
        Mensaje mensaje = new Mensaje(null, Remitente.BOT, "respuesta", null, INICIO.plusSeconds(conversacion.size()));
        mensaje.registrarDatosDeRespuesta(null, categoria, escalado, 5);
        conversacion.add(mensaje);
        return this;
    }

    private PreguntaParaSoporte elegida() {
        return PreguntaParaSoporte.de(conversacion).orElseThrow();
    }

    @Test
    void laQueNoEntendioEsLaPreguntaDeJustoAntes() {
        jugador("como pujo").bot(ResultadoDeRespuesta.TEMA, Categoria.SUBASTA_Y_COMERCIO)
            .jugador("donde esta el gremio").bot(ResultadoDeRespuesta.ESCALADO, null);

        assertThat(elegida()).isEqualTo(new PreguntaParaSoporte("donde esta el gremio", null, Motivo.NO_LA_ENTENDIO));
    }

    @Test
    void laUltimaSinResolverAunqueDespuesSeResolvieraOtra() {
        jugador("donde esta el gremio").bot(ResultadoDeRespuesta.ESCALADO, null)
            .jugador("como pujo").bot(ResultadoDeRespuesta.TEMA, Categoria.SUBASTA_Y_COMERCIO);

        assertThat(elegida().texto()).isEqualTo("donde esta el gremio");
        assertThat(elegida().motivo()).isEqualTo(Motivo.NO_LA_ENTENDIO);
    }

    @Test
    void siNoLeSirvioEsLaPreguntaQueRespondioLaRespuestaAnteriorConSuCategoria() {
        jugador("como recupero mi cuenta").bot(ResultadoDeRespuesta.TEMA, Categoria.CUENTA_Y_REGISTRO)
            .jugador("no me sirvio").bot(ResultadoDeRespuesta.NO_SIRVIO, Categoria.CUENTA_Y_REGISTRO);

        assertThat(elegida()).isEqualTo(new PreguntaParaSoporte("como recupero mi cuenta", Categoria.CUENTA_Y_REGISTRO,
            Motivo.NO_LE_SIRVIO));
    }

    @Test
    void siNoLeSirvioYElNoSirvioNoTraeCategoriaUsaLaDeLaRespuestaQueNoSirvio() {
        jugador("como recupero mi cuenta").bot(ResultadoDeRespuesta.TEMA, Categoria.CUENTA_Y_REGISTRO)
            .jugador("no me sirvio").bot(ResultadoDeRespuesta.NO_SIRVIO, null);

        assertThat(elegida().categoria()).isEqualTo(Categoria.CUENTA_Y_REGISTRO);
    }

    @Test
    void antesDeV13CuentaLaMarcaEscaladoYElNoMeSirvio() {
        jugador("como recupero mi cuenta").botAntiguo(false, Categoria.CUENTA_Y_REGISTRO)
            .jugador("no me sirvio").botAntiguo(true, Categoria.CUENTA_Y_REGISTRO);

        assertThat(elegida()).isEqualTo(new PreguntaParaSoporte("como recupero mi cuenta", Categoria.CUENTA_Y_REGISTRO,
            Motivo.NO_LE_SIRVIO));

        conversacion.clear();
        jugador("donde esta el gremio").botAntiguo(true, null);
        assertThat(elegida().motivo()).isEqualTo(Motivo.NO_LA_ENTENDIO);
    }

    @Test
    void conResultadoMandaElResultadoAunqueLaFraseParezcaUnNoMeSirvio() {
        // «no entiendo» como primer mensaje no es un seguimiento (no hay
        // respuesta anterior) y el motor lo escalo: no es una pregunta.
        jugador("no entiendo").bot(ResultadoDeRespuesta.ESCALADO, null)
            .jugador("como pujo").bot(ResultadoDeRespuesta.TEMA, Categoria.SUBASTA_Y_COMERCIO);

        assertThat(elegida()).isEqualTo(new PreguntaParaSoporte("como pujo", Categoria.SUBASTA_Y_COMERCIO,
            Motivo.ULTIMA_PREGUNTA));
    }

    @Test
    void sinNadaSinResolverEsLaUltimaPreguntaNoUnSeguimiento() {
        jugador("como pujo").bot(ResultadoDeRespuesta.TEMA, Categoria.SUBASTA_Y_COMERCIO)
            .jugador("mas detalle").bot(ResultadoDeRespuesta.TEMA, Categoria.SUBASTA_Y_COMERCIO)
            .jugador("en ingles");

        assertThat(elegida()).isEqualTo(new PreguntaParaSoporte("como pujo", Categoria.SUBASTA_Y_COMERCIO,
            Motivo.ULTIMA_PREGUNTA));
    }

    @Test
    void laUltimaPreguntaSinRespuestaTodaviaNoTieneCategoria() {
        jugador("como pujo");

        assertThat(elegida()).isEqualTo(new PreguntaParaSoporte("como pujo", null, Motivo.ULTIMA_PREGUNTA));
    }

    @Test
    void unNoMeSirvioSinPreguntaAntesNoEsUnaPregunta() {
        bot(ResultadoDeRespuesta.TEMA, null).jugador("no me sirvio").bot(ResultadoDeRespuesta.NO_SIRVIO, null);

        assertThat(PreguntaParaSoporte.de(conversacion)).isEmpty();
    }

    @Test
    void sinPreguntasNoHayNada() {
        assertThat(PreguntaParaSoporte.de(List.of())).isEmpty();
        assertThat(PreguntaParaSoporte.de(null)).isEmpty();
        bot(ResultadoDeRespuesta.ESCALADO, null).jugador("   ").jugador("mas detalle");
        assertThat(PreguntaParaSoporte.de(conversacion)).isEmpty();
    }
}
