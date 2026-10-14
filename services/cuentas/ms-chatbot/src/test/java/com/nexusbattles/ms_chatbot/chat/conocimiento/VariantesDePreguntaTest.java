package com.nexusbattles.ms_chatbot.chat.conocimiento;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariantesDePreguntaTest {

    @Test
    void unirYSepararSonInversas() {
        String unidas = VariantesDePregunta.unir(List.of(" como pujo ", "hacer una puja"));

        assertThat(unidas).isEqualTo("como pujo, hacer una puja");
        assertThat(VariantesDePregunta.separar(unidas)).containsExactly("como pujo", "hacer una puja");
        assertThat(VariantesDePregunta.unir(List.of())).isNull();
        assertThat(VariantesDePregunta.separar(null)).isEmpty();
    }

    // 1.6.0: la misma variante repetida (sin contar tildes, mayusculas ni
    // signos, como la compara el motor) queda una vez, con su primera forma.
    @Test
    void unirSinRepetirDejaCadaVarianteUnaVez() {
        assertThat(VariantesDePregunta.unirSinRepetir(Arrays.asList("Cómo pujo", "como PUJO?", null, " ",
            "hacer una puja", "Hacer una puja"))).isEqualTo("Cómo pujo, hacer una puja");
        assertThat(VariantesDePregunta.unirSinRepetir(List.of("a", "b"))).isEqualTo("a, b");
        assertThat(VariantesDePregunta.unirSinRepetir(null)).isNull();
        assertThat(VariantesDePregunta.unirSinRepetir(List.of(" "))).isNull();
    }

    @Test
    void detectaElSeparadorEnCualquierVariante() {
        assertThat(VariantesDePregunta.algunaContieneSeparador(List.of("a", "b, c"))).isTrue();
        assertThat(VariantesDePregunta.algunaContieneSeparador(List.of("a"))).isFalse();
        assertThat(VariantesDePregunta.algunaContieneSeparador(null)).isFalse();
    }
}
