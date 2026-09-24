package com.nexusbattles.ms_identidad.onboarding.service;

import java.util.UUID;

/**
 * Se publica dentro de la transaccion del registro; lo escucha
 * {@link AlRegistrarJugador} cuando esa transaccion se confirma.
 */
public record JugadorRegistrado(UUID uid, String apodo, String ip) {
}
