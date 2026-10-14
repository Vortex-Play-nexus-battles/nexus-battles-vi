package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.service.PreguntaParaSoporte;
import com.nexusbattles.ms_chatbot.chat.service.PreguntaParaSoporte.Motivo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.8.0: asunto y mensaje de la solicitud desde la conversacion.
class SolicitudDesdeConversacionTest {

    private static PreguntaParaSoporte pregunta(String texto, Motivo motivo) {
        return new PreguntaParaSoporte(texto, null, motivo);
    }

    @Test
    void elAsuntoLlevaLaPreguntaEnUnaLineaYCabeEn150() {
        assertThat(SolicitudDesdeConversacion.asunto(pregunta("  como\n  pujo  ", Motivo.NO_LA_ENTENDIO)))
            .isEqualTo("Sin respuesta del asistente: como pujo");

        String asunto = SolicitudDesdeConversacion.asunto(pregunta("a".repeat(300), Motivo.NO_LA_ENTENDIO));
        assertThat(asunto).hasSize(150).startsWith("Sin respuesta del asistente: aaa").endsWith("a…");

        // Si el corte cae en un espacio, no queda colgando antes del «…».
        String conEspacios = SolicitudDesdeConversacion.asunto(
            pregunta("palabra ".repeat(40), Motivo.NO_LA_ENTENDIO));
        assertThat(conEspacios).hasSizeLessThanOrEqualTo(150).endsWith("palabra…");
    }

    @Test
    void elMensajeDiceQuePasoConLaPregunta() {
        assertThat(SolicitudDesdeConversacion.mensaje(pregunta("como pujo", Motivo.NO_LA_ENTENDIO), null))
            .isEqualTo("Le pregunté al asistente: «como pujo» y no la entendió.");
        assertThat(SolicitudDesdeConversacion.mensaje(pregunta("como pujo", Motivo.NO_LE_SIRVIO), "   "))
            .isEqualTo("Le pregunté al asistente: «como pujo» y su respuesta no me sirvió.");
        assertThat(SolicitudDesdeConversacion.mensaje(pregunta("como pujo", Motivo.ULTIMA_PREGUNTA), "Urgente"))
            .isEqualTo("Le pregunté al asistente: «como pujo» y necesito que me ayude una persona del equipo."
                + "\n\nUrgente");
    }

    @Test
    void siNoCabeSeRecortaLaPreguntaNuncaElComentario() {
        String comentario = "c".repeat(1800);
        String mensaje = SolicitudDesdeConversacion.mensaje(pregunta("p".repeat(1900), Motivo.NO_LA_ENTENDIO),
            comentario);

        assertThat(mensaje).hasSize(SolicitudDesdeConversacion.LARGO_MAXIMO_DEL_MENSAJE)
            .endsWith("\n\n" + comentario).contains("…» y no la entendió.");
    }

    @Test
    void conUnComentarioQueOcupaCasiTodoVaSoloElComentario() {
        String comentario = "c".repeat(1990);

        assertThat(SolicitudDesdeConversacion.mensaje(pregunta("como pujo", Motivo.NO_LA_ENTENDIO), comentario))
            .isEqualTo(comentario);
        assertThat(SolicitudDesdeConversacion.mensaje(pregunta("como pujo", Motivo.NO_LA_ENTENDIO),
            "c".repeat(2000))).hasSize(2000);
    }

    @Test
    void recortarYEnUnaLinea() {
        assertThat(SolicitudDesdeConversacion.recortar("hola", 10)).isEqualTo("hola");
        assertThat(SolicitudDesdeConversacion.recortar("hola mundo", 6)).isEqualTo("hola…");
        assertThat(SolicitudDesdeConversacion.enUnaLinea(null)).isEmpty();
        assertThat(SolicitudDesdeConversacion.enUnaLinea("a\t\nb")).isEqualTo("a b");
    }
}
