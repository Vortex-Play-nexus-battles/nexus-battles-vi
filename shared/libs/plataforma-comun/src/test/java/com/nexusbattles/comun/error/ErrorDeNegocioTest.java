package com.nexusbattles.comun.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Esta clase la heredan los 20 modulos. Si sus invariantes fallan, todos emiten
 * errores mal formados a la vez, asi que conviene tenerlas cubiertas.
 */
@DisplayName("ErrorDeNegocio")
class ErrorDeNegocioTest {

    private static final URI TIPO = URI.create("https://nexusbattles.local/errores/prueba");

    /** Subclase minima: la clase base es abstracta a proposito. */
    private static final class ErrorDePrueba extends ErrorDeNegocio {
        ErrorDePrueba(URI tipo, String titulo, int estado, String detalle) {
            super(tipo, titulo, estado, detalle);
        }

        ErrorDePrueba(List<ErrorDeCampo> errores) {
            super(TIPO, "Titulo", 400, "Detalle", errores);
        }
    }

    @Test
    @DisplayName("expone los cuatro campos del problem details")
    void camposBasicos() {
        ErrorDeNegocio error = new ErrorDePrueba(TIPO, "Creditos insuficientes", 422, "Tienes 240.");

        assertEquals(TIPO, error.tipo());
        assertEquals("Creditos insuficientes", error.titulo());
        assertEquals(422, error.estado());
        assertEquals("Tienes 240.", error.detalle());
    }

    @Test
    @DisplayName("el detalle es tambien el mensaje de la excepcion")
    void detalleEsElMensaje() {
        ErrorDeNegocio error = new ErrorDePrueba(TIPO, "Titulo", 400, "El motivo del rechazo.");

        assertEquals(error.getMessage(), error.detalle());
    }

    @Test
    @DisplayName("sin tipo no hay identificador estable sobre el que programar")
    void exigeTipo() {
        assertThrows(IllegalArgumentException.class,
                () -> new ErrorDePrueba(null, "Titulo", 400, "Detalle"));
    }

    @Test
    @DisplayName("exige titulo")
    void exigeTitulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new ErrorDePrueba(TIPO, "  ", 400, "Detalle"));
    }

    @Test
    @DisplayName("exige explicar el motivo, no solo que fallo")
    void exigeDetalle() {
        assertThrows(IllegalArgumentException.class,
                () -> new ErrorDePrueba(TIPO, "Titulo", 400, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ErrorDePrueba(TIPO, "Titulo", 400, ""));
    }

    @Test
    @DisplayName("sin errores de campo la lista queda vacia, nunca nula")
    void sinErroresDeCampo() {
        assertTrue(new ErrorDePrueba(TIPO, "Titulo", 400, "Detalle").errores().isEmpty());
        assertTrue(new ErrorDePrueba(null).errores().isEmpty());
    }

    @Test
    @DisplayName("la lista de errores no se puede modificar desde fuera")
    void listaInmutable() {
        List<ErrorDeCampo> original = new ArrayList<>();
        original.add(new ErrorDeCampo("nombre", "Muy corto."));

        ErrorDeNegocio error = new ErrorDePrueba(original);
        original.add(new ErrorDeCampo("otro", "Colado despues."));

        assertEquals(1, error.errores().size(), "copiar la lista al construir, no guardarla");
        assertThrows(UnsupportedOperationException.class,
                () -> error.errores().add(new ErrorDeCampo("x", "y")));
    }
}
