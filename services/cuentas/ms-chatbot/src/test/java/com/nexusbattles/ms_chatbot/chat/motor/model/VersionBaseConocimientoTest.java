package com.nexusbattles.ms_chatbot.chat.motor.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionBaseConocimientoTest {

    private static final Instant AHORA = Instant.parse("2026-09-23T20:00:00Z");

    @Test
    void desplegarUnaCandidata_laPoneEnProduccionConFecha() {
        VersionBaseConocimiento candidata = VersionBaseConocimiento.nuevaCandidata(2, null);

        candidata.ponerEnProduccion(AHORA);

        assertEquals(EstadoVersion.PRODUCCION, candidata.getEstado());
        assertEquals(AHORA, candidata.getFechaDespliegue());
    }

    @Test
    void retirarYRestaurar_actualizaLaFechaDeDespliegue() {
        VersionBaseConocimiento version = VersionBaseConocimiento.nuevaCandidata(1, null);
        version.ponerEnProduccion(AHORA);

        version.retirar();
        assertEquals(EstadoVersion.RETIRADA, version.getEstado());

        Instant despues = AHORA.plusSeconds(3600);
        version.ponerEnProduccion(despues);
        assertEquals(EstadoVersion.PRODUCCION, version.getEstado());
        assertEquals(despues, version.getFechaDespliegue());
    }

    @Test
    void ponerEnProduccionLaQueYaEsta_lanza() {
        VersionBaseConocimiento version = VersionBaseConocimiento.nuevaCandidata(1, null);
        version.ponerEnProduccion(AHORA);

        assertThrows(IllegalStateException.class, () -> version.ponerEnProduccion(AHORA));
    }

    @Test
    void retirarUnaCandidata_lanza() {
        VersionBaseConocimiento candidata = VersionBaseConocimiento.nuevaCandidata(2, null);

        assertThrows(IllegalStateException.class, candidata::retirar);
    }

    @Test
    void casoSinClaveEsperada_esperaEscalamiento() {
        assertTrue(CasoEvaluacion.nuevo("como hackeo el juego", null).esperaEscalamiento());
        assertFalse(CasoEvaluacion.nuevo("como pujo", "clave-pujas").esperaEscalamiento());
    }

    @Test
    void evaluacion_calculaLaTasaYRechazaAciertosFueraDeRango() {
        VersionBaseConocimiento version = VersionBaseConocimiento.nuevaCandidata(1, null);

        assertEquals(0.75, EvaluacionVersion.registrar(version, 4, 3, AHORA).tasaAcierto());
        assertNull(EvaluacionVersion.registrar(version, 0, 0, AHORA).tasaAcierto());
        assertThrows(IllegalArgumentException.class, () -> EvaluacionVersion.registrar(version, 2, 3, AHORA));
    }
}
