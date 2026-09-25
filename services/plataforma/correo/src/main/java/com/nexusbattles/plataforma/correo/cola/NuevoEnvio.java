package com.nexusbattles.plataforma.correo.cola;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Una fila a punto de entrar en la cola.
 *
 * @param id                  identificador del envio
 * @param plantilla           nombre corto de la plantilla
 * @param destinatario        direccion completa
 * @param asunto              asunto del correo
 * @param datos               variables de la plantilla
 * @param estado              PENDIENTE, u OMITIDO si quien llama pidio no enviarlo
 * @param motivo              motivo de la omision; nulo si hay que enviarlo
 * @param claveDeIdempotencia cabecera Idempotency-Key, o nulo
 * @param trazaId             identificador de traza de la peticion, o nulo
 * @param creadoEn            momento de la aceptacion
 */
public record NuevoEnvio(
        UUID id,
        String plantilla,
        String destinatario,
        String asunto,
        Map<String, Object> datos,
        EstadoDeEnvio estado,
        String motivo,
        String claveDeIdempotencia,
        String trazaId,
        Instant creadoEn) {
}
