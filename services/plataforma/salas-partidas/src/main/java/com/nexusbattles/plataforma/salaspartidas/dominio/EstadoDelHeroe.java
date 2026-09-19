package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;

/**
 * Lo que el modulo de inventario responde sobre el heroe de un jugador.
 *
 * <p>Es la respuesta del puerto {@code HeroeDelJugador}, no la respuesta de la
 * API: aqui no hay creditos ni sala, porque el inventario no sabe nada de eso.
 * El caso de uso {@code VerificarHeroe} compone las dos mitades.
 *
 * <p><b>Frontera de alcance de HU-SAL-003</b> (tal como la fija el issue #27):
 * la disponibilidad del heroe —bloqueo por mision, torneo o subasta— la
 * resuelven RF-INV-009 y RF-MIS-012, del equipo de contenido. Este servicio
 * <i>consume</i> esa respuesta y rechaza el ingreso cuando la reportan; no la
 * calcula ni la guarda. Por eso {@link #ocupadoPor} es texto libre del
 * proveedor: quien sabe en que esta metido el heroe es el.
 *
 * @param resultado que dice el inventario
 * @param heroe     el heroe elegido, o {@code null} si no hay ninguno utilizable
 * @param ocupadoPor nombre de la actividad que lo retiene, solo si esta ocupado
 */
public record EstadoDelHeroe(ResultadoVerificacion resultado, HeroeDeCombate heroe, String ocupadoPor) {

    public EstadoDelHeroe {
        Objects.requireNonNull(resultado, "La verificacion sin resultado no dice nada.");
        if (resultado == ResultadoVerificacion.DISPONIBLE && heroe == null) {
            throw new IllegalArgumentException(
                    "Un heroe disponible tiene que venir con el heroe: el dialogo lo muestra.");
        }
    }

    /** El jugador tiene heroe equipado y libre. */
    public static EstadoDelHeroe disponible(HeroeDeCombate heroe) {
        return new EstadoDelHeroe(ResultadoVerificacion.DISPONIBLE, heroe, null);
    }

    /**
     * No hay heroe utilizable: o no tiene ninguno, o el que tiene esta desnudo.
     *
     * <p>Las dos situaciones se cuentan igual porque para el jugador son la
     * misma accion —ir al inventario y equipar— y el contrato solo define una
     * variante de dialogo para ambas.
     */
    public static EstadoDelHeroe sinHeroeEquipado() {
        return new EstadoDelHeroe(ResultadoVerificacion.SIN_HEROE_EQUIPADO, null, null);
    }

    /**
     * El heroe existe y esta equipado, pero el inventario lo da por comprometido.
     *
     * @param heroe      el heroe retenido; el dialogo lo nombra
     * @param ocupadoPor donde esta metido, para que el aviso lo diga
     */
    public static EstadoDelHeroe ocupado(HeroeDeCombate heroe, String ocupadoPor) {
        return new EstadoDelHeroe(ResultadoVerificacion.HEROE_OCUPADO, heroe, ocupadoPor);
    }

    public boolean puedeCombatir() {
        return resultado == ResultadoVerificacion.DISPONIBLE;
    }
}
