package nexus.inventario.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Renombrar un elemento propio. 1.7.0: el nombre tiene un maximo de
 * {@value #MAXIMO_NOMBRE} caracteres —el mas largo del catalogo oficial tiene 29—;
 * sin tope, un jugador podia inflar su propio documento hasta el limite de Mongo
 * y romper la tarjeta de la vitrina.
 */
public record ModificarElementoRequest(@NotBlank @Size(max = MAXIMO_NOMBRE) String nombrePropio) {

    public static final int MAXIMO_NOMBRE = 60;
}
