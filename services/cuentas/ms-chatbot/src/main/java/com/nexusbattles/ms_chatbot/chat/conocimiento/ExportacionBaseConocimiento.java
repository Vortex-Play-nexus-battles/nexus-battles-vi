package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;

import java.time.Instant;
import java.util.List;

// HU-CHA-012 (RF-CHA-013): archivo de exportacion de una version completa.
// El bloque 'temas' es exactamente lo que acepta la importacion.
public record ExportacionBaseConocimiento(
    int version,
    EstadoVersion estado,
    Instant fechaExportacion,
    List<TemaIntercambio> temas
) {
}
