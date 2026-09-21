package com.nexusbattles.ms_cumplimiento.auditoria.controller;

import com.nexusbattles.ms_cumplimiento.auditoria.exception.AuditWriteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada excepcion del dominio sale con el estado y el titulo que fija la regla 4. */
class ManejadorDeErroresTest {

    private final ManejadorDeErrores manejador = new ManejadorDeErrores();

    @Test
    @DisplayName("Argumento invalido -> 400 con el detalle del error")
    void argumentoInvalido() {
        ProblemDetail problema = manejador.solicitudInvalida(new IllegalArgumentException("tipoAccion desconocido"));

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getTitle()).isEqualTo("Solicitud invalida");
        assertThat(problema.getDetail()).isEqualTo("tipoAccion desconocido");
    }

    @Test
    @DisplayName("Acceso denegado -> 403")
    void accesoDenegado() {
        ProblemDetail problema = manejador.accesoDenegado(new AccessDeniedException("Requiere rol Super Administrador"));

        assertThat(problema.getStatus()).isEqualTo(403);
        assertThat(problema.getTitle()).isEqualTo("Acceso denegado");
        assertThat(problema.getDetail()).isEqualTo("Requiere rol Super Administrador");
    }

    @Test
    @DisplayName("La bitacora no pudo escribir -> 503, nunca un 500 mudo")
    void bitacoraNoDisponible() {
        ProblemDetail problema = manejador.bitacoraNoDisponible(
                new AuditWriteException("No se pudo registrar el evento", new IllegalStateException("db")));

        assertThat(problema.getStatus()).isEqualTo(503);
        assertThat(problema.getTitle()).isEqualTo("Bitacora de auditoria no disponible");
        assertThat(problema.getDetail()).isEqualTo("No se pudo registrar el evento");
    }
}
