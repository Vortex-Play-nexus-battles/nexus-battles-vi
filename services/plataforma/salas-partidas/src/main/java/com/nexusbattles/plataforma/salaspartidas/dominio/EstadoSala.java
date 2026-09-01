package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Estado del ciclo de vida de una sala.
 *
 * <p>Se corresponde uno a uno con las variantes del componente {@code Insignia}
 * del sistema de diseno y con el enumerado {@code EstadoSala} de
 * {@code contracts/openapi/salas-partidas.yaml}. Si aqui aparece un valor nuevo,
 * el contrato y el componente tienen que cambiar tambien.
 */
public enum EstadoSala {

    /** Admite jugadores. Es la unica en la que se puede entrar sin mas. */
    ABIERTA,

    /** Alcanzo su maximo de participantes y ya no admite mas. */
    LLENA,

    /** La partida comenzo. */
    EN_JUEGO,

    /** Aparece en el listado, pero solo se entra con codigo de invitacion. */
    PRIVADA,

    /** El anfitrion la cancelo antes de empezar; los creditos se devuelven. */
    CANCELADA,

    /** La partida termino. */
    FINALIZADA;

    /**
     * Los tres estados que el listado de RF-JUE-002 sabe pintar.
     *
     * <p>No es una decision de este servicio: la toma el sistema de diseno. El
     * conjunto {@code Tarjeta de sala} tiene exactamente tres variantes —
     * {@code Abierta}, {@code Llena} y {@code Privada} — y ninguna para
     * {@code En juego}, {@code Cancelada} ni {@code Finalizada}. Una sala que la
     * interfaz no puede representar no se lista.
     */
    private static final Set<EstadoSala> EN_EL_LISTADO =
            Collections.unmodifiableSet(EnumSet.of(ABIERTA, LLENA, PRIVADA));

    /** Estados que aparecen en el listado, para que el almacen los filtre. */
    public static Set<EstadoSala> delListado() {
        return EN_EL_LISTADO;
    }

    /**
     * Si una sala en este estado aparece en el listado.
     *
     * <p>Aparecer no es poder entrar: una sala {@code PRIVADA} se ve con su
     * insignia y su ingreso se rechaza con 403 si falta la invitacion. Si no
     * apareciera, ese rechazo seria inalcanzable desde el listado.
     */
    public boolean apareceEnElListado() {
        return EN_EL_LISTADO.contains(this);
    }
}
