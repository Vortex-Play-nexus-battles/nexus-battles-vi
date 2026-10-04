package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * Auditoria de DEV del 30-sep: toda violacion de integridad salia como 409, y
 * el emisor lee el 409 como «ese aviso ya estaba». Solo una clave repetida lo
 * es; lo demas (un dato que no cabe en su columna) es un 400.
 */
class ManejadorErroresNotificacionesTest {

    private final ManejadorErroresNotificaciones manejador = new ManejadorErroresNotificaciones();

    @Test
    @DisplayName("la clave unica repetida (23505) es el duplicado de la carrera: 409")
    void claveRepetidaEsConflicto() {
        DataIntegrityViolationException carrera = new DataIntegrityViolationException("insert",
                new RuntimeException("hibernate", new SQLException("duplicate key value", "23505")));

        assertEquals(409, manejador.manejarDuplicadoEnCarrera(carrera).getStatus());
        assertEquals(409, manejador.manejarDuplicadoEnCarrera(new DuplicateKeyException("dup")).getStatus());
    }

    @Test
    @DisplayName("un valor demasiado largo (22001) no es un duplicado: 400, y el emisor no lo da por entregado")
    void valorQueNoCabeEsSolicitudInvalida() {
        DataIntegrityViolationException noCabe = new DataIntegrityViolationException("insert",
                new SQLException("value too long for type character varying(64)", "22001"));

        assertEquals(400, manejador.manejarDuplicadoEnCarrera(noCabe).getStatus());
    }

    @Test
    @DisplayName("sin causa reconocible tampoco se inventa un duplicado")
    void sinCausaNoHayDuplicado() {
        assertEquals(400, manejador.manejarDuplicadoEnCarrera(
                new DataIntegrityViolationException("sin causa")).getStatus());
    }
}
