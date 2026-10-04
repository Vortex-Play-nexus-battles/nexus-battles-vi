package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.net.URI;

/**
 * Por que no se entrego un mensaje privado — el enumerado {@code motivo} de
 * {@code EnvioRechazado} en {@code contracts/websocket/mensajes-directos.yaml}.
 *
 * <p>Un solo enumerado para los dos caminos: por STOMP el motivo viaja tal
 * cual en {@code {tipo: RECHAZO, motivo, idCliente}}; por REST (la via de
 * respaldo) se convierte en un problem details con el estado y el
 * {@code type} de aqui. Asi las dos vias no pueden decir cosas distintas del
 * mismo rechazo, que es lo que exige «las mismas reglas».
 *
 * <p>Los {@code type} que ya existian en el servicio se reutilizan
 * ({@code mensaje-invalido} y {@code contenido-bloqueado} del chat,
 * {@code jugador-sancionado} de las puertas de sala): la interfaz decide por
 * {@code type} y un mismo hecho no debe tener dos nombres.
 */
public enum MotivoDeRechazo {

    TEXTO_INVALIDO(400, "mensaje-invalido", "Revisa el mensaje",
            "El mensaje no puede estar vacío ni pasar de 500 caracteres, y tiene que ser texto:"
                    + " ni un dibujo de símbolos, ni demasiadas líneas, ni una racha del mismo carácter."),

    DESTINATARIO_PROPIO(400, "destinatario-propio", "No puedes escribirte a ti mismo",
            "Elige a otro jugador para enviarle un mensaje privado."),

    SANCIONADO(403, "jugador-sancionado", "Tienes una sanción activa",
            "Mientras la sanción siga vigente no puedes enviar mensajes privados. Puedes consultarla,"
                    + " y apelarla si crees que es injusta, en «Mis sanciones»."),

    DESTINATARIO_INEXISTENTE(404, "destinatario-inexistente", "Ese jugador no puede recibir mensajes",
            "La cuenta no existe o no está activa."),

    TEXTO_NO_PERMITIDO(422, "contenido-bloqueado", "Mensaje bloqueado",
            "El mensaje contiene términos que no están permitidos y no se entregó."),

    /**
     * Quien escribe tiene bloqueado al destinatario (D-40): para volver a
     * escribirle, primero lo desbloquea.
     */
    CONVERSACION_BLOQUEADA(409, "conversacion-bloqueada", "Bloqueaste a este jugador",
            "Desbloquéalo si quieres volver a escribirle."),

    /**
     * El destinatario tiene bloqueado a quien escribe (D-40). No se dice que
     * lo bloqueo: solo que no recibe sus mensajes.
     */
    NO_ADMITE(403, "destinatario-no-admite", "Este jugador no recibe tus mensajes",
            "No puedes enviarle mensajes privados."),

    DEMASIADO_RAPIDO(429, "demasiados-mensajes", "Vas demasiado rápido",
            "Espera unos segundos antes de enviar otro mensaje."),

    /**
     * Alguna comprobacion previa a la entrega no respondio: la lista negra, la
     * sancion del remitente o el directorio de cuentas. Fail-closed (D-14): un
     * mensaje que no se pudo revisar no se entrega.
     */
    MODERACION_NO_DISPONIBLE(503, "moderacion-no-disponible", "No se pudo revisar el mensaje",
            "La moderación no respondió y el mensaje no se entregó sin revisar. Inténtalo de nuevo en un momento.");

    private static final String BASE_DE_TIPOS = "https://nexusbattles.local/errores/";

    private final int estado;
    private final URI tipo;
    private final String titulo;
    private final String detalle;

    MotivoDeRechazo(int estado, String tipo, String titulo, String detalle) {
        this.estado = estado;
        this.tipo = URI.create(BASE_DE_TIPOS + tipo);
        this.titulo = titulo;
        this.detalle = detalle;
    }

    /** Codigo HTTP del problem details de la via REST. */
    public int estado() {
        return estado;
    }

    /** {@code type} estable del problem details. */
    public URI tipo() {
        return tipo;
    }

    public String titulo() {
        return titulo;
    }

    public String detalle() {
        return detalle;
    }
}
