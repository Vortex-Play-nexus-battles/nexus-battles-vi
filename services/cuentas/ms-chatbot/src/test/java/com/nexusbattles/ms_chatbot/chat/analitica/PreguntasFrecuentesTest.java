package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PreguntaFrecuente;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.7.0 (7.4.7 «preguntas mas frecuentes», D-CHA-06).
class PreguntasFrecuentesTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();

    @Test
    void agrupaComoLeeElMotorYMuestraLaFormaNormalizada() {
        List<PreguntaFrecuente> frecuentes = PreguntasFrecuentes.contar(List.of(
            new RegistroDeTexto(A, "¿Cómo subo de nivel a mi héroe?"),
            new RegistroDeTexto(B, "como subo de nivel a mi heroe"),
            new RegistroDeTexto(C, "COMO SUBO DE NIVEL A MI HEROE!!"),
            new RegistroDeTexto(C, "como subo de nivel a mi heroe")), 10);

        assertThat(frecuentes).singleElement().satisfies(p -> {
            assertThat(p.texto()).isEqualTo("como subo de nivel a mi heroe");
            assertThat(p.preguntas()).isEqualTo(4);
            assertThat(p.conversaciones()).isEqualTo(3);
        });
    }

    // D-CHA-06: una pregunta que hizo una sola persona (aunque la repita) no
    // sale, ni la de dos: hacen falta tres conversaciones distintas.
    @Test
    void unaPreguntaDeMenosDeTresConversacionesNoSale() {
        List<PreguntaFrecuente> frecuentes = PreguntasFrecuentes.contar(List.of(
            new RegistroDeTexto(A, "mi apodo es zorrokiller y no puedo entrar"),
            new RegistroDeTexto(A, "mi apodo es zorrokiller y no puedo entrar"),
            new RegistroDeTexto(A, "mi apodo es zorrokiller y no puedo entrar"),
            new RegistroDeTexto(A, "mi apodo es zorrokiller y no puedo entrar"),
            new RegistroDeTexto(B, "como pujo"),
            new RegistroDeTexto(C, "como pujo")), 10);

        assertThat(frecuentes).isEmpty();
        assertThat(PreguntasFrecuentes.MINIMO_DE_CONVERSACIONES).isEqualTo(3);
    }

    @Test
    void ordenaPorVecesLuegoPorConversacionesYRespetaElLimite() {
        List<RegistroDeTexto> textos = List.of(
            new RegistroDeTexto(A, "como pujo"), new RegistroDeTexto(B, "como pujo"),
            new RegistroDeTexto(C, "como pujo"), new RegistroDeTexto(C, "como pujo"),
            new RegistroDeTexto(A, "hola"), new RegistroDeTexto(B, "hola"),
            new RegistroDeTexto(C, "hola"), new RegistroDeTexto(D, "hola"),
            new RegistroDeTexto(A, "mis misiones"), new RegistroDeTexto(B, "mis misiones"),
            new RegistroDeTexto(C, "mis misiones"));

        assertThat(PreguntasFrecuentes.contar(textos, 10)).extracting(PreguntaFrecuente::texto)
            .containsExactly("hola", "como pujo", "mis misiones");
        assertThat(PreguntasFrecuentes.contar(textos, 1)).extracting(PreguntaFrecuente::texto)
            .containsExactly("hola");
    }

    // Lo que la redaccion tapo no se agrupa como pregunta, y una pregunta
    // larga se recorta a 200 caracteres.
    @Test
    void ignoraLoQueSoloEraUnDatoTapadoYRecortaLasLargas() {
        assertThat(PreguntasFrecuentes.clave("***")).isEmpty();
        assertThat(PreguntasFrecuentes.clave("[tarjeta]")).isEmpty();
        assertThat(PreguntasFrecuentes.clave("?!")).isEmpty();
        assertThat(PreguntasFrecuentes.clave(null)).isEmpty();
        assertThat(PreguntasFrecuentes.clave("mi contraseña: ***")).isEqualTo("mi contrasena");
        assertThat(PreguntasFrecuentes.clave("palabra ".repeat(60))).hasSizeLessThanOrEqualTo(200);
    }
}
