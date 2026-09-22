package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import java.util.UUID;

/**
 * Cuerpo del mensaje {@code EjecutarAccion} del AsyncAPI — RF-JUE-017.
 *
 * <p>Se acepta entero desde ya aunque este servicio todavia no resuelva la
 * accion: el dano y los efectos son del motor de combate. Aceptar la forma
 * definitiva ahora evita que el cliente tenga que cambiar el dia que el motor
 * publique su contrato.
 *
 * <p>Ningun identificador de jugador: quien juega sale del token. Si viajara
 * aqui, cualquiera podria jugar el turno de otro.
 *
 * @param codigoAccion accion elegida; la interpreta el motor de combate
 * @param idObjetivo   objetivo, si la accion lo requiere
 */
public record EjecutarAccionRequest(String codigoAccion, UUID idObjetivo) {
}
