package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PalabraClave;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
