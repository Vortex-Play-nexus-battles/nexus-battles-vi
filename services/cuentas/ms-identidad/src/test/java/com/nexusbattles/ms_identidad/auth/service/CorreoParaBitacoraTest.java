package com.nexusbattles.ms_identidad.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El correo que escribe quien falla el login llega a la bitacora de auditoria
 * enmascarado y solo si parece un correo: con un salto de linea se podria
 * fingir otra linea de auditoria (inyeccion en bitacora).
 */
@DisplayName("Correo en la bitacora de LOGIN_FALLIDO")
class CorreoParaBitacoraTest {

    @Test
    @DisplayName("un correo normal queda enmascarado: primera letra y dominio")
    void enmascaraElBuzon() {
        assertThat(LoginService.correoParaBitacora("jugadora.uno@nexus.test")).isEqualTo("j***@nexus.test");
    }

    @Test
    @DisplayName("un salto de linea o un caracter de control no se imprime")
    void noImprimeSaltosNiControl() {
        assertThat(LoginService.correoParaBitacora("a@b.co\nLOGIN_EXITOSO usuarioId=1"))
                .isEqualTo("<no-imprimible>");
        assertThat(LoginService.correoParaBitacora("a@b.co\r")).isEqualTo("<no-imprimible>");
        assertThat(LoginService.correoParaBitacora("a\u0000@b.co")).isEqualTo("<no-imprimible>");
    }

    @Test
    @DisplayName("sin correo, vacio o sin buzon antes de la arroba no revela nada")
    void casosSinBuzon() {
        assertThat(LoginService.correoParaBitacora(null)).isEqualTo("<no-imprimible>");
        assertThat(LoginService.correoParaBitacora("")).isEqualTo("<no-imprimible>");
        assertThat(LoginService.correoParaBitacora("sin-arroba")).isEqualTo("***");
        assertThat(LoginService.correoParaBitacora("@nexus.test")).isEqualTo("***");
    }
}
