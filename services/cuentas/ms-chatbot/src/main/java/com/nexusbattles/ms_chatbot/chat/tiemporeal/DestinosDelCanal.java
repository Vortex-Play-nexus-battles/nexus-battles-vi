package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import java.util.Set;

/**
 * 7.4.9 — los destinos del canal en tiempo real del chatbot, tal como los
 * declara {@code contracts/websocket/chatbot.yaml} 1.0.0. Viven en un solo
 * sitio para que el interceptor ({@link PoliticaDelCanal}), quien responde
 * ({@link ConversacionEnVivo}, {@link AvisosDeSoporte}) y el contrato no
 * puedan dejar de coincidir sin que una prueba lo note.
 */
public final class DestinosDelCanal {

    /** El endpoint del handshake, bajo el context-path: {@code /api/v1/ws-chatbot}. */
    public static final String ENDPOINT = "/ws-chatbot";

    /** El unico destino al que un cliente puede hacer SEND. */
    public static final String ENVIAR_MENSAJE = "/app/chat/mensajes";

    /** Prefijo de los destinos de usuario (se resuelven por la conexion). */
    public static final String PREFIJO_DE_USUARIO = "/user";

    // Lo que el servidor manda a una conexion o a un usuario, sin el prefijo
    // de usuario (asi se pasa a convertAndSendToUser).
    public static final String COLA_ESTADO = "/queue/chat/estado";
    public static final String COLA_RESPUESTAS = "/queue/chat/respuestas";
    public static final String COLA_ERRORES = "/queue/chat/errores";
    public static final String COLA_TICKETS = "/queue/soporte/tickets";

    /** Los cuatro destinos a los que se puede suscribir un cliente, escritos tal cual. */
    public static final Set<String> SUSCRIPCIONES = Set.of(
        PREFIJO_DE_USUARIO + COLA_ESTADO,
        PREFIJO_DE_USUARIO + COLA_RESPUESTAS,
        PREFIJO_DE_USUARIO + COLA_ERRORES,
        PREFIJO_DE_USUARIO + COLA_TICKETS);

    /** Cabecera STOMP con la que vuelve el {@code idCorrelacion} del mensaje. */
    public static final String CABECERA_CORRELACION = "id-correlacion";

    /** Cabecera STOMP con la sesion de visitante recien emitida (como en la API). */
    public static final String CABECERA_SESION_ANONIMA = "X-Id-Sesion-Anonima";

    private DestinosDelCanal() {
    }
}
