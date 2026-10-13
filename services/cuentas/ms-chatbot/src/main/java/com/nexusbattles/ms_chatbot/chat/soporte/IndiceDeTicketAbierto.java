package com.nexusbattles.ms_chatbot.chat.soporte;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

// Revision de plataforma (1.3.9): de todos los errores de integridad de la
// base, solo el del indice unico parcial de V6 quiere decir "el jugador ya
// tiene un ticket abierto". Los demas (un texto mas largo que su columna, un
// CHECK que no se cumple) no son un 409: se relanzan tal cual.
//
// Se reconoce por el nombre del indice en la cadena de causas: primero el que
// Hibernate extrae del error de PostgreSQL (getConstraintName) y, si no lo
// trae, el nombre entre comillas en el mensaje ("violates unique constraint
// \"uk_...\"").
final class IndiceDeTicketAbierto {

    static final String NOMBRE = "uk_tickets_soporte_uno_abierto_por_jugador";

    private static final String NOMBRE_EN_EL_MENSAJE = "constraint \"" + NOMBRE + "\"";
    private static final int PROFUNDIDAD_MAXIMA = 10;

    private IndiceDeTicketAbierto() {
    }

    static boolean loIncumple(DataIntegrityViolationException error) {
        Throwable actual = error;
        for (int i = 0; actual != null && i < PROFUNDIDAD_MAXIMA; i++) {
            if (actual instanceof ConstraintViolationException violacion
                && NOMBRE.equalsIgnoreCase(violacion.getConstraintName())) {
                return true;
            }
            String mensaje = actual.getMessage();
            if (mensaje != null && mensaje.toLowerCase(Locale.ROOT).contains(NOMBRE_EN_EL_MENSAJE)) {
                return true;
            }
            actual = actual.getCause() == actual ? null : actual.getCause();
        }
        return false;
    }
}
