package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;
import java.util.Objects;

/**
 * El jugador no puede combatir con el heroe que tiene — HU-SAL-003, RF-JUE-003.
 *
 * <p>Es la puerta de verdad, la que tiene efectos. {@code VerificarHeroe} avisa
 * <i>antes</i> de pulsar para que nadie se lleve la sorpresa; esta excepcion es
 * la que impide entrar o empezar cuando el aviso se ignora, el estado cambia
 * entre la consulta y la accion, o la peticion llega sin pasar por la interfaz.
 * Sin ella, la verificacion previa seria decorativa: un cliente que llamara
 * directo al endpoint entraria igual.
 *
 * <p><b>El codigo y los dos tipos salen del contrato, no de aqui</b> (regla 1).
 * {@code contracts/openapi/salas-partidas.yaml} ya fijaba para el ingreso un
 * <b>422</b> con dos ejemplos nombrados, {@code heroe-no-equipado} y
 * {@code heroe-ocupado}. Son dos tipos y no uno porque la interfaz los pinta
 * distinto: uno se arregla equipando un heroe y el otro esperando o cambiando
 * de heroe. Se implementa lo acordado.
 *
 * <p>422 y no 403: no es un problema de permisos —el jugador tiene derecho a
 * entrar a esta sala— sino de que no cumple un requisito suyo.
 */
public class HeroeNoDisponible extends ErrorDeNegocio {

    /** RF-JUE-003, ejemplo {@code heroeNoEquipado} del contrato. */
    public static final URI SIN_EQUIPAR =
            URI.create("https://nexusbattles.local/errores/heroe-no-equipado");

    /** RF-JUE-003, ejemplo {@code heroeOcupado} del contrato. */
    public static final URI OCUPADO =
            URI.create("https://nexusbattles.local/errores/heroe-ocupado");

    private final ResultadoVerificacion resultado;

    public HeroeNoDisponible(EstadoDelHeroe estado) {
        super(tipoDe(estado), tituloDe(estado), 422, detalleDe(estado));
        this.resultado = estado.resultado();
    }

    /** Motivo en forma de codigo, para quien ya tiene la excepcion en la mano. */
    public ResultadoVerificacion resultado() {
        return resultado;
    }

    private static URI tipoDe(EstadoDelHeroe estado) {
        return exigirRechazo(estado) == ResultadoVerificacion.SIN_HEROE_EQUIPADO
                ? SIN_EQUIPAR
                : OCUPADO;
    }

    private static String tituloDe(EstadoDelHeroe estado) {
        return exigirRechazo(estado) == ResultadoVerificacion.SIN_HEROE_EQUIPADO
                ? "No tienes un heroe equipado"
                : "Tu heroe esta en otra partida";
    }

    /**
     * El texto nombra el caso concreto. Un «no puedes entrar» generico obliga al
     * jugador a adivinar si le falta equipar un heroe o si el que tiene esta en
     * otra batalla, que son dos arreglos distintos.
     *
     * <p>Cuando el inventario dice donde esta el heroe, se repite aqui: sin ese
     * dato, «esta ocupado» no le dice a nadie donde mirar.
     */
    private static String detalleDe(EstadoDelHeroe estado) {
        if (exigirRechazo(estado) == ResultadoVerificacion.SIN_HEROE_EQUIPADO) {
            return "Equipa un heroe en tu inventario antes de entrar a una batalla.";
        }
        String heroe = estado.heroe() == null ? "Tu heroe" : estado.heroe().nombre();
        return estado.ocupadoPor() == null
                ? heroe + " ya esta combatiendo en otra partida. Elige otro heroe."
                : heroe + " esta combatiendo en " + estado.ocupadoPor() + ". Elige otro heroe.";
    }

    /**
     * DISPONIBLE no puede justificar un rechazo: quien construye esta excepcion
     * ya comprobo que el heroe no sirve. Si llegara, es un error de
     * programacion, y callarlo lo convertiria en un 422 inexplicable.
     */
    private static ResultadoVerificacion exigirRechazo(EstadoDelHeroe estado) {
        Objects.requireNonNull(estado, "Sin veredicto del inventario no hay motivo que dar.");
        if (estado.resultado() == ResultadoVerificacion.DISPONIBLE) {
            throw new IllegalArgumentException(
                    "Un heroe disponible no puede justificar un rechazo de ingreso.");
        }
        return estado.resultado();
    }
}
