package com.nexusbattles.ms_chatbot.chat.model;

/**
 * ms-chatbot.yaml 1.7.0 (7.4.7 «tasa de resolución», V13): cómo terminó una
 * respuesta del bot. Lo decide el motor que la generó y se guarda con ella en
 * {@code mensajes.resultado}: las analíticas no lo adivinan después.
 *
 * <p>Resuelven sin intervención humana {@link #TEMA}, {@link #CONSULTA_EN_VIVO}
 * y {@link #NAVEGACION}; las demás no.
 */
public enum ResultadoDeRespuesta {

    /** La respondió un tema de la base (también «más detalle», «en inglés» o «repítelo»). */
    TEMA(true),

    /**
     * Una consulta sobre la propia cuenta que trajo datos, aunque digan «no
     * tienes ninguno»; el informe de actividad, si llegó al menos una parte.
     */
    CONSULTA_EN_VIVO(true),

    /** Llevó al jugador a la sección que pidió (navegación asistida, 7.4.4). */
    NAVEGACION(true),

    /** La consulta en vivo no respondió (o ninguna parte del informe). */
    NO_DISPONIBLE(false),

    /** El motor no entendió la pregunta y ofreció soporte humano. */
    ESCALADO(false),

    /** «No me sirvió»: la respuesta anterior no le sirvió y se ofreció soporte humano. */
    NO_SIRVIO(false),

    /** Pidió la pregunta completa (seguimiento sin tema anterior) o un criterio de búsqueda más largo. */
    PIDE_REFORMULAR(false);

    private final boolean resuelve;

    ResultadoDeRespuesta(boolean resuelve) {
        this.resuelve = resuelve;
    }

    /** Si cuenta como consulta resuelta sin intervención humana. */
    public boolean resuelve() {
        return resuelve;
    }
}
