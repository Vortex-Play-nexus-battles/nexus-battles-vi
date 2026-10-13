package com.nexusbattles.ms_identidad.admin.directorio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El criterio de cuentas de pruebas automaticas (RFINAL-06), sin base de datos.
 *
 * <p>Lo que se prueba aqui son los patrones {@code LIKE} que salen de la
 * configuracion: que los comodines de un prefijo se escapen (sin eso,
 * «qa_» ocultaria tambien a «qabot») y que la configuracion se lea igual la
 * escriba quien la escriba. Que la consulta los aplique bien contra una base
 * lo comprueba {@code DirectorioDeCuentasTest}.
 */
@DisplayName("Cuentas de las pruebas automaticas")
class CuentasDePruebaTest {

    @Test
    @DisplayName("por omision: el dominio nexus.test y los prefijos qa_, smoke_ y canario_")
    void valoresPorOmision() {
        CuentasDePrueba criterio = new CuentasDePrueba("nexus.test", "qa_,smoke_,canario_");

        assertEquals(List.of("nexus.test"), criterio.dominios());
        assertEquals(List.of("qa_", "smoke_", "canario_"), criterio.prefijos());
        assertEquals(List.of("%@nexus.test"), criterio.patronesDeCorreo());
        assertEquals(List.of("qa!_%", "smoke!_%", "canario!_%"), criterio.patronesDeApodo());
    }

    @Test
    @DisplayName("el _ de un prefijo es literal: «qabot» no es una cuenta de pruebas")
    void elGuionBajoNoEsComodin() {
        CuentasDePrueba criterio = new CuentasDePrueba("", "qa_");

        assertEquals(List.of("qa!_%"), criterio.patronesDeApodo());
    }

    @Test
    @DisplayName("tambien se escapan el % y el propio caracter de escape")
    void escapaTodosLosComodines() {
        assertEquals("100!%!!seguro!_", CuentasDePrueba.escapar("100%!seguro_"));
    }

    @Test
    @DisplayName("la configuracion se normaliza: espacios, mayusculas, arroba, vacios y repetidos")
    void normalizaLaConfiguracion() {
        CuentasDePrueba criterio = new CuentasDePrueba(" NEXUS.test , @Otro.Test,, nexus.test ", " QA_ ,,Smoke_");

        assertEquals(List.of("nexus.test", "otro.test"), criterio.dominios());
        assertEquals(List.of("qa_", "smoke_"), criterio.prefijos());
        assertEquals(List.of("%@nexus.test", "%@otro.test"), criterio.patronesDeCorreo());
    }

    @Test
    @DisplayName("sin listas configuradas no hay nada que excluir")
    void sinConfiguracionNoExcluyeNada() {
        CuentasDePrueba criterio = new CuentasDePrueba("", null);

        assertTrue(criterio.patronesDeCorreo().isEmpty());
        assertTrue(criterio.patronesDeApodo().isEmpty());
    }
}
