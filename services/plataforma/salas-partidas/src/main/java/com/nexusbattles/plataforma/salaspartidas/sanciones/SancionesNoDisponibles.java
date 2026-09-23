package com.nexusbattles.plataforma.salaspartidas.sanciones;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * No se pudo comprobar si el jugador esta sancionado — 503.
 *
 * <p><b>Fail-closed a proposito</b> (decision D-14). Si el servicio de
 * sanciones no responde, la alternativa seria dar por buena la accion, y eso
 * convierte una caida en una via de escape: justo mientras el sistema esta
 * peor es cuando un sancionado podria entrar. Se prefiere decir «ahora no
 * puedo comprobarlo» a decir «adelante» sin saberlo.
 *
 * <p>Es la excepcion a la degradacion controlada de HU-DIS-003, y es
 * deliberada: alli se degrada lo que es <i>consulta</i>, no lo que es
 * <i>puerta</i>.
 */
public class SancionesNoDisponibles extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/sanciones-no-disponibles");

    public SancionesNoDisponibles() {
        this("No se pudo comprobar tu estado de sanciones",
                "El servicio de sanciones no respondio y la accion no se completo."
                        + " Intenta de nuevo en un momento.");
    }

    /**
     * Mismo 503 con el texto de quien lo lanza: al jugador del chat le importa
     * que su mensaje no salio, y al de la puerta que no entro.
     *
     * @param titulo titulo humano
     * @param detalle que le paso a lo que estaba intentando
     */
    public SancionesNoDisponibles(String titulo, String detalle) {
        super(TIPO, titulo, 503, detalle);
    }
}
