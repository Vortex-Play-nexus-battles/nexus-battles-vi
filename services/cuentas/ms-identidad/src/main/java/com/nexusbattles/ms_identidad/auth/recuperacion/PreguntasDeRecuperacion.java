package com.nexusbattles.ms_identidad.auth.recuperacion;

import java.util.List;
import java.util.UUID;

/**
 * {@code PreguntasDeRecuperacion} de ms-identidad-auth.yaml: las preguntas de
 * una cuenta, NUNCA sus respuestas. {@code configuradas=false} con la lista
 * vacia si la cuenta no configuro ninguna.
 */
public record PreguntasDeRecuperacion(boolean configuradas, List<Pregunta> preguntas) {

    public record Pregunta(UUID id, String texto) {
    }

    public static PreguntasDeRecuperacion de(List<PreguntaSeguridad> guardadas) {
        List<Pregunta> visibles = guardadas.stream().map(p -> new Pregunta(p.getId(), p.getTexto())).toList();
        return new PreguntasDeRecuperacion(!visibles.isEmpty(), visibles);
    }

    public static PreguntasDeRecuperacion ninguna() {
        return new PreguntasDeRecuperacion(false, List.of());
    }
}
