package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PalabraClave;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TendenciaDePalabra;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

// ms-chatbot.yaml 1.3.7 (7.4.7 «palabras clave», 7.4.8).
class PalabrasClaveFrecuentesTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    @Test
    void cuentaLasPalabrasComoLasLeeElMotor_laMasUsadaPrimero() {
        List<PalabraClave> palabras = PalabrasClaveFrecuentes.contar(List.of(
            new RegistroDeTexto(A, "¿Cómo PUJO en una subasta?"),
            new RegistroDeTexto(B, "subasta de armas"),
            new RegistroDeTexto(C, "quiero ver una subásta y armas")), 10);

        assertThat(palabras).extracting(PalabraClave::palabra).containsExactly("subasta", "armas");
        assertThat(palabras.get(0).preguntas()).isEqualTo(3);
        assertThat(palabras.get(0).conversaciones()).isEqualTo(3);
    }

    // Una palabra de una sola conversacion no sale: podria ser un dato de esa persona.
    @Test
    void unaPalabraDeUnaSolaConversacion_noSale() {
        List<PalabraClave> palabras = PalabrasClaveFrecuentes.contar(List.of(
            new RegistroDeTexto(A, "mi apodo es zorrokiller"),
            new RegistroDeTexto(A, "zorrokiller no entra"),
            new RegistroDeTexto(B, "no puedo entrar")), 10);

        assertThat(palabras).extracting(PalabraClave::palabra).doesNotContain("zorrokiller");
    }

    @Test
    void ignoraDigitosPalabrasCortasYVacias() {
        assertThat(PalabrasClaveFrecuentes.palabrasDe("hola quiero saber mi 3001234567 pin torneo torneo"))
            .containsExactly("torneo");
        assertThat(PalabrasClaveFrecuentes.palabrasDe(null)).isEmpty();
    }

    @Test
    void respetaElLimiteYDesempataPorOrdenAlfabetico() {
        List<PalabraClave> palabras = PalabrasClaveFrecuentes.contar(List.of(
            new RegistroDeTexto(A, "torneo misiones"),
            new RegistroDeTexto(B, "torneo misiones")), 1);

        assertThat(palabras).extracting(PalabraClave::palabra).containsExactly("misiones");
    }

    // 1.7.0 (7.4.7 «tendencias»): cada palabra contra el periodo anterior de
    // la misma duracion. Del anterior solo se cuenta cuantas preguntas la
    // tenian, aunque fueran de una sola conversacion: ahi no se muestra texto.
    @Test
    void cadaPalabraTraeSuTendenciaContraElPeriodoAnterior() {
        List<RegistroDeTexto> ahora = List.of(
            new RegistroDeTexto(A, "torneo subasta misiones inventario"),
            new RegistroDeTexto(B, "torneo subasta misiones inventario"),
            new RegistroDeTexto(C, "torneo subasta"));
        List<RegistroDeTexto> antes = List.of(
            new RegistroDeTexto(A, "torneo subasta misiones"),
            new RegistroDeTexto(A, "subasta misiones"),
            new RegistroDeTexto(A, "subasta misiones"),
            new RegistroDeTexto(A, "subasta"));

        List<PalabraClave> palabras = PalabrasClaveFrecuentes.contar(ahora, antes, 10);

        assertThat(palabras).extracting(PalabraClave::palabra, PalabraClave::preguntas,
                PalabraClave::preguntasPeriodoAnterior, PalabraClave::tendencia)
            .containsExactly(
                tuple("subasta", 3L, 4L, TendenciaDePalabra.BAJA),
                tuple("torneo", 3L, 1L, TendenciaDePalabra.SUBE),
                tuple("inventario", 2L, 0L, TendenciaDePalabra.NUEVA),
                tuple("misiones", 2L, 3L, TendenciaDePalabra.BAJA));
        assertThat(TendenciaDePalabra.de(2, 2)).isEqualTo(TendenciaDePalabra.IGUAL);
    }

    // Sin periodo anterior que comparar (la forma de 1.3.7), todas son nuevas.
    @Test
    void sinPeriodoAnterior_todasSonNuevas() {
        assertThat(PalabrasClaveFrecuentes.contar(List.of(
                new RegistroDeTexto(A, "torneo"),
                new RegistroDeTexto(B, "torneo")), 10))
            .singleElement()
            .satisfies(p -> {
                assertThat(p.preguntasPeriodoAnterior()).isZero();
                assertThat(p.tendencia()).isEqualTo(TendenciaDePalabra.NUEVA);
            });
        assertThat(new PalabraClave("torneo", 2, 2).tendencia()).isEqualTo(TendenciaDePalabra.NUEVA);
    }
}
