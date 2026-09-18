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
}
