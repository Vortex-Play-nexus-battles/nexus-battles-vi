package com.nexusbattles.comun.error;

/**
 * Un error atribuible a un campo concreto de una peticion.
 *
 * <p>Viaja en el arreglo {@code errores} del problem details. La interfaz lo usa
 * para marcar el campo en variante Invalido y escribir el mensaje debajo, en vez
 * de mostrar un aviso general: ver {@code shared/ui-kit/MAPEO-ERRORES.md}.
 *
 * @param campo    nombre del campo tal y como aparece en el contrato OpenAPI
 * @param mensaje  texto dirigido a la persona, no al programador
 */
public record ErrorDeCampo(String campo, String mensaje) {

    public ErrorDeCampo {
        if (campo == null || campo.isBlank()) {
            throw new IllegalArgumentException("El error de campo necesita saber a que campo se refiere.");
        }
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("El error de campo necesita un mensaje para la persona.");
        }
    }
}
