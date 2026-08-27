package nexus.combate;

import java.util.List;

/** Regla aprobada por negocio para resolver un empate exacto de vida. */
@FunctionalInterface
public interface CriterioDesempate {

    String desempatar(List<PuntuacionEquipo> equiposEmpatados);
}
