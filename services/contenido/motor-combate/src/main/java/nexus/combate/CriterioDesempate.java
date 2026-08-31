package nexus.combate;

import java.util.List;

@FunctionalInterface
public interface CriterioDesempate {

    String desempatar(List<PuntuacionEquipo> equiposEmpatados);
}
