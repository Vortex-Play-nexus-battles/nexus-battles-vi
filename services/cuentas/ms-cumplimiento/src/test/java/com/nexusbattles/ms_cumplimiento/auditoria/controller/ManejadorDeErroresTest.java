package com.nexusbattles.ms_cumplimiento.auditoria.controller;

import com.nexusbattles.ms_cumplimiento.auditoria.exception.ExportacionExcedeMaximoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test unitario del manejador, sin contexto de Spring (HU-AUD-004, pedido
 * por Simon en la revision de #498): confirma que
 * ExportacionExcedeMaximoException se traduce al ProblemDetail exacto que
 * el equipo acordo (422/UNPROCESSABLE_CONTENT, titulo con tilde, las dos
 * cifras como propiedades).
 */
class ManejadorDeErroresTest {

    private final ManejadorDeErrores manejador = new ManejadorDeErrores();

    @Test
    @DisplayName("exportacionExcedeMaximo: 422, titulo con tilde y las dos cifras en la raiz")
    void exportacionExcedeMaximo_devuelveProblemDetailConLasCifras() {
        ExportacionExcedeMaximoException error = new ExportacionExcedeMaximoException(15000, 10000);

        ProblemDetail problema = manejador.exportacionExcedeMaximo(error);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), problema.getStatus());
        assertEquals("La exportación excede el máximo por operación", problema.getTitle());
        assertEquals(error.getMessage(), problema.getDetail());
        assertEquals(15000L, problema.getProperties().get("totalEncontrado"));
        assertEquals(10000, problema.getProperties().get("maximoPermitido"));
    }
}
