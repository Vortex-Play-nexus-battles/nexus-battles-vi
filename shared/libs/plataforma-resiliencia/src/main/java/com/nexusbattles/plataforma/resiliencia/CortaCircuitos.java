package com.nexusbattles.plataforma.resiliencia;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Corta circuitos para una llamada saliente (HU-DIS-003, SCRUM-1146).
 *
 * <p>Protege <b>al que llama</b>, no al que esta caido. Si un servicio deja de
 * responder y se le sigue llamando, cada intento consume un hilo y un tiempo de
 * espera completo; con suficiente trafico el que se queda sin hilos es el
 * llamante, y ahi es donde la caida de un microservicio se convierte en la
 * caida de la aplicacion. Abrir el circuito corta esa cadena: las llamadas
 * fallan al instante y la contingencia responde en su lugar.
 *
 * <p>Sin dependencias externas a proposito. Un corta circuitos es una maquina
 * de tres estados y un contador; traer una biblioteca de resiliencia entera
 * para esto anadiria configuracion, metricas y modos de fallo que nadie del
 * equipo ha acordado todavia.
 *
 * <p>Es thread-safe por sincronizacion simple: la seccion critica es leer y
 * escribir un contador, no la llamada en si —esa se hace <b>fuera</b> del
 * bloque sincronizado, porque bloquear durante una llamada remota convertiria
 * el corta circuitos en el cuello de botella que pretende evitar—.
 */
public class CortaCircuitos {

    private final String dependencia;
    private final String seccion;
    private final int fallosParaAbrir;
    private final Duration esperaAntesDeReintentar;
    private final Clock reloj;
    private final RegistroDeDegradacion registro;

    private EstadoDelCorta estado = EstadoDelCorta.CERRADO;
    private int fallosSeguidos;
    private Instant abiertoDesde;

    public CortaCircuitos(
            String dependencia,
            String seccion,
            int fallosParaAbrir,
            Duration esperaAntesDeReintentar,
            Clock reloj,
            RegistroDeDegradacion registro) {

        if (fallosParaAbrir <= 0) {
            throw new IllegalArgumentException(
                    "hacen falta uno o mas fallos para abrir, y llego " + fallosParaAbrir);
        }
        if (esperaAntesDeReintentar == null || esperaAntesDeReintentar.isNegative()
                || esperaAntesDeReintentar.isZero()) {
            throw new IllegalArgumentException(
                    "la espera antes de reintentar debe ser positiva, y llego " + esperaAntesDeReintentar);
        }
        this.dependencia = dependencia;
        this.seccion = seccion;
        this.fallosParaAbrir = fallosParaAbrir;
        this.esperaAntesDeReintentar = esperaAntesDeReintentar;
        this.reloj = reloj;
        this.registro = registro;
    }

    public String dependencia() {
        return dependencia;
    }

    public String seccion() {
        return seccion;
    }

    public synchronized EstadoDelCorta estado() {
        return estadoAhora();
    }

    /**
     * Ejecuta la llamada y devuelve la contingencia si la dependencia no responde.
     *
     * <p>Esta es la forma que resuelve CA-01 y CA-03: el llamante <b>nunca</b> ve
     * una excepcion de la dependencia caida, asi que sigue sirviendo sus otras
     * funciones con normalidad.
     *
     * @param llamada la llamada real a la otra dependencia
     * @param contingencia que responder mientras esa dependencia no este (SCRUM-1147)
     */
    public <T> T ejecutar(Supplier<T> llamada, Supplier<T> contingencia) {
        try {
            return ejecutarOFallar(llamada);
        } catch (DependenciaDegradada degradada) {
            return contingencia.get();
        }
    }

    /**
     * Igual que {@link #ejecutar}, pero lanza {@link DependenciaDegradada} en vez
     * de responder una contingencia.
     *
     * <p>Para cuando no hay respuesta alternativa razonable y lo correcto es que
     * el jugador vea el aviso de seccion limitada (CA-02): un manejador global
     * convierte esta excepcion en el problem detail estandar.
     */
    public <T> T ejecutarOFallar(Supplier<T> llamada) {
        if (!dejaPasar()) {
            throw new DependenciaDegradada(dependencia, seccion, null);
        }

        try {
            // Fuera del bloque sincronizado: bloquear durante una llamada remota
            // convertiria el corta circuitos en el cuello de botella.
            T respuesta = llamada.get();
            anotarExito();
            return respuesta;
        } catch (RuntimeException fallo) {
            anotarFallo();
            throw new DependenciaDegradada(dependencia, seccion, fallo);
        }
    }

    /** True si la llamada puede intentarse; consume la prueba del semiabierto. */
    private synchronized boolean dejaPasar() {
        EstadoDelCorta actual = estadoAhora();
        if (actual == EstadoDelCorta.ABIERTO) {
            return false;
        }
        if (actual == EstadoDelCorta.SEMIABIERTO) {
            // Se deja pasar UNA prueba y se vuelve a cerrar el paso hasta saber
            // como fue: si pasaran todas, una avalancha de peticiones caeria
            // sobre un servicio que quiza aun se esta levantando.
            estado = EstadoDelCorta.ABIERTO;
            abiertoDesde = reloj.instant();
            return true;
        }
        return true;
    }

    private synchronized void anotarExito() {
        fallosSeguidos = 0;
        if (estado != EstadoDelCorta.CERRADO) {
            estado = EstadoDelCorta.CERRADO;
            abiertoDesde = null;
            registro.recuperada(dependencia);
        }
    }

    private synchronized void anotarFallo() {
        fallosSeguidos++;
        if (estado == EstadoDelCorta.CERRADO && fallosSeguidos < fallosParaAbrir) {
            return;
        }
        // Un fallo aislado no abre nada: la red pierde un paquete de vez en
        // cuando y abrir por eso dejaria sin servicio una seccion que funciona.
        boolean yaEstabaAbierto = estado == EstadoDelCorta.ABIERTO;
        estado = EstadoDelCorta.ABIERTO;
        abiertoDesde = reloj.instant();
        if (!yaEstabaAbierto) {
            registro.degradada(dependencia, seccion, reloj.instant());
        }
    }

    /** El estado teniendo en cuenta si ya toca reintentar. */
    private EstadoDelCorta estadoAhora() {
        if (estado == EstadoDelCorta.ABIERTO
                && abiertoDesde != null
                && !reloj.instant().isBefore(abiertoDesde.plus(esperaAntesDeReintentar))) {
            estado = EstadoDelCorta.SEMIABIERTO;
        }
        return estado;
    }
}
