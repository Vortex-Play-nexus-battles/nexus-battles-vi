package nexus.combate;

import java.util.List;

/**
 * Estrategia configurada con el criterio de desempate aprobado por el cliente.
 *
 * <p>El repositorio todavía no documenta cuál es ese criterio. Inyectarlo evita
 * fijar silenciosamente una regla de negocio no aprobada.</p>
 */
@FunctionalInterface
public interface CriterioDesempate {

    String desempatar(List<PuntuacionEquipo> equiposEmpatados);
}
