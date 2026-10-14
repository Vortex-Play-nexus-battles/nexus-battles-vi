package com.nexusbattles.ms_chatbot.chat.texto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 7.4.2 «manejar errores ortograficos»: una misma tolerancia en todo el chatbot.
class DistanciaDeEdicionTest {

    @Test
    void cuentaInsertarBorrarCambiarEIntercambiarVecinas() {
        assertThat(DistanciaDeEdicion.entre("subasta", "subasta")).isZero();
        assertThat(DistanciaDeEdicion.entre("subasta", "subastas")).isEqualTo(1);
        assertThat(DistanciaDeEdicion.entre("subasta", "sbasta")).isEqualTo(1);
        assertThat(DistanciaDeEdicion.entre("subasta", "subasto")).isEqualTo(1);
        // Dos letras vecinas cambiadas: 1 (con Levenshtein clasica serian 2).
        assertThat(DistanciaDeEdicion.entre("subasta", "subatsa")).isEqualTo(1);
        assertThat(DistanciaDeEdicion.entre("inventario", "inventraio")).isEqualTo(1);
        assertThat(DistanciaDeEdicion.entre("", "abc")).isEqualTo(3);
        assertThat(DistanciaDeEdicion.entre("torneo", "mision")).isGreaterThan(1);
    }

    // Las palabras cortas solo coinciden exactas: «mis» y «mas» son otras palabras.
    @Test
    void lasPalabrasCortasSoloCoincidenExactas() {
        assertThat(DistanciaDeEdicion.coinciden("mis", "mis")).isTrue();
        assertThat(DistanciaDeEdicion.coinciden("mis", "mas")).isFalse();
        assertThat(DistanciaDeEdicion.coinciden("subasta", "subatsa")).isTrue();
        assertThat(DistanciaDeEdicion.coinciden("subasta", "subastasss")).isFalse();
        assertThat(DistanciaDeEdicion.coinciden("", "")).isFalse();
        assertThat(DistanciaDeEdicion.coinciden("subasta", "")).isFalse();
    }

    @Test
    void laFraseSeBuscaPorPalabrasSeguidas() {
        assertThat(DistanciaDeEdicion.contieneFrase("como van mis subastas", "mis subastas")).isTrue();
        assertThat(DistanciaDeEdicion.contieneFrase("como van mis subatsas", "mis subastas")).isTrue();
        assertThat(DistanciaDeEdicion.contieneFrase("como van mas subastas", "mis subastas")).isFalse();
        assertThat(DistanciaDeEdicion.contieneFrase("subastas mis", "mis subastas")).isFalse();
        assertThat(DistanciaDeEdicion.contieneFrase("mis", "mis subastas")).isFalse();
    }
}
