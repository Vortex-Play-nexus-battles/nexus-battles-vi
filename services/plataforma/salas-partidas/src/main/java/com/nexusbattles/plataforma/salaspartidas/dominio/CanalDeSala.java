package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.UUID;

/**
 * Puerto de salida hacia el canal en tiempo real de una sala — HU-SAL-002.
 *
 * <p>Tercer criterio de aceptacion del issue #30: «el estado de la sala se
 * actualiza para todos los participantes». Quien ya esta dentro tiene que
 * enterarse de que entro alguien sin recargar.
 *
 * <p>Vive en el dominio, no en la capa de transporte, por el mismo motivo que
 * {@link RepositorioDeSalas}: es el dominio quien declara que necesita anunciar
 * lo que pasa en una sala, y es la infraestructura la que decide si eso viaja
 * por STOMP, por una cola o por otra cosa. Asi el caso de uso se prueba sin
 * levantar un servidor.
 *
 * <p>El puerto habla de hechos del juego, no de destinos ni de JSON. La
 * traduccion al mensaje {@code sala.participante.ingreso} de
 * {@code contracts/websocket/salas-partidas.yaml} es cosa del adaptador.
 */
public interface CanalDeSala {

    /**
     * Anuncia que un jugador acaba de entrar.
     *
     * <p>Se invoca <b>despues</b> de que el ingreso quede guardado. Anunciarlo
     * antes contaria una entrada que todavia podria no persistirse.
     *
     * @param sala      sala ya actualizada, con el jugador dentro
     * @param idJugador quien acaba de entrar
     */
    void anunciarIngreso(Sala sala, UUID idJugador);

    /**
     * Anuncia que un jugador acaba de salir — mensaje {@code sala.participante.salio}.
     *
     * <p>Misma regla que el ingreso: se invoca <b>despues</b> de guardar. Sin
     * este aviso, quien sigue dentro ve un cupo ocupado por alguien que ya no
     * esta, y solo se entera al recargar — justo lo que el tercer criterio de
     * HU-SAL-002 vino a evitar.
     *
     * @param sala      sala ya actualizada, sin el jugador
     * @param idJugador quien acaba de salir
     */
    void anunciarSalida(Sala sala, UUID idJugador);

    /**
     * Anuncia que el anfitrion cancelo la sala — mensaje {@code sala.cancelada}.
     *
     * <p>Es el unico aviso que obliga a la interfaz a sacar a la persona de la
     * vista: la sala deja de existir para efectos practicos. Por eso lleva el
     * motivo, para que la pantalla pueda decir por que se cerro en vez de
     * limitarse a desaparecer.
     *
     * @param sala               sala ya cancelada
     * @param motivo             por que se cerro
     * @param creditosDevueltos  creditos que vuelven al anfitrion; 0 si no habia
     *                           recompensa comprometida o si no se pudo liberar
     */
    void anunciarCancelacion(Sala sala, MotivoDeCancelacion motivo, int creditosDevueltos);
}
