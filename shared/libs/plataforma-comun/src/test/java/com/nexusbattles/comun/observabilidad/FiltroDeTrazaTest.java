package com.nexusbattles.comun.observabilidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Lectura del {@code traceparent} entrante.
 *
 * <p>Lo que importa aqui es que una cabecera mal formada NO se acepte: si se
 * colara, dos peticiones distintas podrian compartir identificador de traza y la
 * bitacora dejaria de servir justo cuando hace falta.
 */
@DisplayName("FiltroDeTraza · lectura del traceparent")
class FiltroDeTrazaTest {

    private static final String TRAZA = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    @DisplayName("reutiliza la traza de una cabecera bien formada")
    void cabeceraValida() {
        String traceparent = "00-" + TRAZA + "-00f067aa0ba902b7-01";

        assertEquals(TRAZA, FiltroDeTraza.extraerTrazaId(traceparent));
    }

    @Test
    @DisplayName("sin cabecera no hay traza que reutilizar")
    void sinCabecera() {
        assertNull(FiltroDeTraza.extraerTrazaId(null));
    }

    @Test
    @DisplayName("rechaza una cabecera incompleta")
    void cabeceraIncompleta() {
        assertNull(FiltroDeTraza.extraerTrazaId("00-" + TRAZA));
        assertNull(FiltroDeTraza.extraerTrazaId("cualquier-cosa"));
        assertNull(FiltroDeTraza.extraerTrazaId(""));
    }

    @Test
    @DisplayName("rechaza una traza que no son 32 hexadecimales")
    void trazaMalFormada() {
        assertNull(FiltroDeTraza.extraerTrazaId("00-abc-00f067aa0ba902b7-01"));
        assertNull(FiltroDeTraza.extraerTrazaId("00-" + TRAZA.toUpperCase() + "-00f067aa0ba902b7-01"));
        assertNull(FiltroDeTraza.extraerTrazaId("00-" + "z".repeat(32) + "-00f067aa0ba902b7-01"));
    }

    @Test
    @DisplayName("rechaza la traza de solo ceros, que el estandar declara invalida")
    void trazaTodoCeros() {
        assertNull(FiltroDeTraza.extraerTrazaId("00-" + "0".repeat(32) + "-00f067aa0ba902b7-01"));
    }
}
