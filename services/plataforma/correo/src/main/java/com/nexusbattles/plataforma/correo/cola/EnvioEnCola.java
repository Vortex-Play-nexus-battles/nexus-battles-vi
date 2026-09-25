package com.nexusbattles.plataforma.correo.cola;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Una fila de la cola tal como la ve el trabajador al reclamarla.
 *
 * @param id           identificador del envio
 * @param plantilla    nombre corto de la plantilla (texto: puede venir de otra
 *                     version del servicio y no existir en esta)
 * @param destinatario direccion completa
 * @param asunto       asunto del correo
 * @param datos        variables de la plantilla
 * @param intentos     intentos empezados, contando el que se esta haciendo
 * @param trazaId      identificador de traza de la peticion que lo encolo
 * @param creadoEn     cuando se acepto
 */
public record EnvioEnCola(
        UUID id,
        String plantilla,
        String destinatario,
        String asunto,
        Map<String, Object> datos,
        int intentos,
        String trazaId,
        Instant creadoEn) {

    public EnvioEnCola {
        datos = datos == null ? Map.of() : datos;
    }
}
