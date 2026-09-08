package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * La sala no admite a este jugador.
 *
 * <p><b>RF-JUE-002</b>, tercer criterio de HU-SAL-002: «sala llena, cerrada o
 * iniciada rechaza el ingreso». Tambien cubre el intento de ocupar dos cupos
 * con el mismo jugador, que es la otra forma de saltarse el limite de aforo.
 *
 * <p>El estado es <b>409 Conflicto</b>, no 422, porque lo fija el contrato
 * OpenAPI: «La sala esta llena o la partida ya comenzo». No es que la peticion
 * este mal formada ni que al jugador le falte algo suyo; es que el recurso
 * cambio de estado y ya no admite la operacion.
 *
 * <p>El detalle dice el motivo concreto porque el criterio de aceptacion de la
 * historia hermana ya obliga a comunicarlo, y una sala que rechaza sin decir
 * por que obliga a la persona a adivinar si volver a intentarlo.
 */
public class IngresoNoPermitido extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/ingreso-no-permitido");

    public IngresoNoPermitido(String detalle) {
        super(TIPO, "No puedes entrar a esta sala", 409, detalle);
    }
}
