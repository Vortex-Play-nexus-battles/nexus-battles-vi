package com.nexusbattles.ms_identidad.perfiles.dto;

import java.util.UUID;

/**
 * Lo que cualquier jugador con sesion puede ver de otro al buscarlo por apodo
 * ({@code PerfilPublico} en ms-identidad-perfiles.yaml): el {@code uid}
 * publico para escribirle, el apodo y el avatar.
 *
 * <p>Es un tipo aparte, y no {@link PerfilUsuarioResponse} con campos a null,
 * a proposito: lo que no esta aqui no puede salir por descuido. Correo,
 * nombres, apellidos, estado de la cuenta, rol o id interno son de la persona
 * y de quien administra, no de quien la busca.
 */
public record PerfilPublicoResponse(UUID uid, String apodo, String avatar) {
}
