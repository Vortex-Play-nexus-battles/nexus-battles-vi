package com.nexusbattles.ms_identidad.auth.recuperacion;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * {@code ConfigurarPreguntasRequest} de ms-identidad-auth.yaml. La cantidad,
 * los largos y las repeticiones los valida el servicio (422
 * {@code preguntas-invalidas}, con el motivo), no la anotacion: un 400
 * generico no le dice a la persona que corregir.
 *
 * <p>{@link #toString()} tapa la contrasena y las respuestas.
 */
public record ConfigurarPreguntasRequest(@NotBlank String passwordActual, List<NuevaPregunta> preguntas) {

    public record NuevaPregunta(String texto, String respuesta) {

        @Override
        public String toString() {
            return "NuevaPregunta[texto=" + texto + ", respuesta=********]";
        }
    }

    @Override
    public String toString() {
        return "ConfigurarPreguntasRequest[preguntas=" + (preguntas == null ? 0 : preguntas.size()) + "]";
    }
}
