package nexus.misiones.aplicacion;

import nexus.misiones.dominio.Ejecucion;

/**
 * El resultado de matricular.
 *
 * @param repetida la misma clave de idempotencia ya habia creado esta ejecucion
 */
public record Matricula(Ejecucion ejecucion, boolean repetida) {
}
