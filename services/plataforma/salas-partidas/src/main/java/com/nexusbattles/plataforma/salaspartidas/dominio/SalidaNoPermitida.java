package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * La sala no admite que este jugador salga, o que se cancele, en su estado actual.
 *
 * <p>Es el simetrico de {@link IngresoNoPermitido} y comparte su codigo,
 * <b>409 Conflicto</b>, por el mismo motivo: no es que la peticion este mal
 * formada, es que el recurso esta en un estado que no admite la operacion. Una
 * partida en juego no se abandona a mitad, y una sala cancelada no se cancela
 * dos veces.
 *
 * <p>Cubre tres situaciones distintas, todas con el mismo codigo y distinto
 * detalle: el jugador no esta dentro, el anfitrion intenta abandonar en vez de
 * cancelar, y la sala ya no esta activa. El detalle dice cual, porque un 409 sin
 * motivo obliga a la persona a adivinar si volver a intentarlo.
 */
public class SalidaNoPermitida extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/salida-no-permitida");

    public SalidaNoPermitida(String detalle) {
        super(TIPO, "No puedes salir de esta sala", 409, detalle);
    }
}
