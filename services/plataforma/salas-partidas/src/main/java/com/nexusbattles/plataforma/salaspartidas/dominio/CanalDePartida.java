package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Puerto de salida hacia el canal en tiempo real de una partida — HU-SAL-005.
 *
 * <p>Tercer criterio de aceptacion del issue #31: «la barra y el valor se
 * actualizan tras cada accion para todos los participantes». Quien esta en el
 * combate tiene que ver moverse las barras sin recargar, y las de todos los
 * afectados a la vez.
 *
 * <p>Mismo motivo que {@link CanalDeSala} para vivir en el dominio: el dominio
 * declara que necesita anunciar el resultado de una accion, y la infraestructura
 * decide por donde viaja. La traduccion al mensaje {@code partida.accion.resuelta}
 * de {@code contracts/websocket/salas-partidas.yaml} es cosa del adaptador.
 *
 * <p><b>Quien lo dispara.</b> Hoy, nadie en produccion: el resultado de una accion
 * lo produce el motor de combate (fuera de este bloque, ver Project Charter), y
 * mientras no exista su respuesta no hay {@link AccionResuelta} que anunciar. Este
 * puerto deja listo el tramo que si es nuestro —del servidor al navegador— para
 * que el dia que llegue ese resultado el anuncio sea una sola llamada.
 */
public interface CanalDePartida {

    /**
     * Anuncia a todos los suscritos a la partida que una accion quedo resuelta.
     *
     * @param accion resultado ya calculado, con la vida de cada afectado
     */
    void anunciarAccionResuelta(AccionResuelta accion);

    /**
     * Anuncia que el combate arranco — HU-SAL-004, RF-JUE-017.
     *
     * <p>Lo reciben quienes estaban en la sala de espera: es la senal para pasar
     * de la lista de participantes a la vista de combate, sin recargar y sin
     * preguntar cada pocos segundos si ya empezo.
     */
    void anunciarInicio(Sala sala, Partida partida);

    /**
     * Anuncia de quien es el turno.
     *
     * <p>Va aparte del inicio porque se repite en cada relevo, y aparte de
     * {@link #anunciarAccionResuelta} porque el turno cambia tambien sin accion
     * —por abandono o por tiempo agotado— y quien pinta la vista necesita
     * saberlo igual.
     */
    void anunciarTurno(Partida partida);

    /**
     * El combate termino — HU-JUE-005, RF-JUE-017, HU-JUE-014.
     *
     * <p>Se anuncia aparte del ultimo golpe: la vista tiene que poder pintar
     * primero la barra bajando a cero y despues el resultado, que es el orden
     * en el que ocurren. Un solo mensaje con las dos cosas obligaria a la vista
     * a animar hacia atras.
     *
     * @param partida la partida ya terminada
     * @param reparto resultado economico de la apuesta por participante
     *                (CA-04). Vacio cuando no habia apuesta o cuando el libro
     *                de creditos no respondio y la liquidacion quedo pendiente;
     *                en ese segundo caso se vuelve a anunciar el fin, con el
     *                reparto, cuando el reintento la cierre.
     */
    void anunciarFin(Partida partida, java.util.List<RepartoDeCreditos> reparto);

    /**
     * Igual que {@link #anunciarFin(Partida, java.util.List)}, con la
     * recompensa por jugar de HU-JUE-012. El adaptador real la incluye en el
     * mismo mensaje; por omision se descarta, para los dobles que no la miran.
     *
     * @param recompensa lo que el libro de creditos acredito por jugar (2/4 al
     *                   ganador, 1 por participar, cofre si hubo). Vacio si el
     *                   libro no respondio y quedo pendiente; entonces el fin se
     *                   vuelve a anunciar, con la recompensa, cuando el reintento
     *                   entre. Es distinto de {@code reparto}: uno es la apuesta
     *                   y el otro el premio por jugar, y viajan separados.
     */
    default void anunciarFin(Partida partida, java.util.List<RepartoDeCreditos> reparto,
                             java.util.List<CreditoPorPartida> recompensa) {
        anunciarFin(partida, reparto);
    }
}
