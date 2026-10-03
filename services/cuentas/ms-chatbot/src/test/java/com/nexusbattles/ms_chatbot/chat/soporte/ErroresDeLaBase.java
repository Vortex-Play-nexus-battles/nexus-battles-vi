package com.nexusbattles.ms_chatbot.chat.soporte;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

// Los errores de integridad tal como llegan de PostgreSQL (SQLState y mensaje
// del servidor), envueltos por Spring. Para las pruebas unitarias; contra la
// base real los prueba TicketsDeSoporteIT.
final class ErroresDeLaBase {

    private ErroresDeLaBase() {
    }

    /** Dos tickets abiertos del mismo jugador: el indice unico parcial de V6. */
    static DataIntegrityViolationException violacionDelIndiceDeV6() {
        return new DataIntegrityViolationException("could not execute statement", new SQLException(
            "ERROR: duplicate key value violates unique constraint \"uk_tickets_soporte_uno_abierto_por_jugador\"",
            "23505"));
    }

    /** Un texto mas largo que su columna: tambien es de integridad, pero no es un 409. */
    static DataIntegrityViolationException textoMasLargoQueLaColumna() {
        return new DataIntegrityViolationException("could not execute statement", new SQLException(
            "ERROR: value too long for type character varying(150)", "22001"));
    }
}
