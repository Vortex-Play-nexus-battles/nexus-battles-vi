package nexus.misiones.aplicacion;

import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.Mision;

/**
 * Una ejecucion con su mision, que es lo que necesitan el panel de misiones en
 * curso y el reporte (el nombre, la categoria). {@code mision} puede ser nula
 * si la semilla ya no la publica; entonces se usa su identificador.
 */
public record EjecucionConMision(Ejecucion ejecucion, Mision mision) {
}
