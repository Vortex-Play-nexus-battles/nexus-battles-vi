package nexus.combate;

import java.util.List;

public interface InventarioBotin {

    List<ElementoCandidatoBotin> listarCandidatos(
            String propietarioEnemigoId,
            String heroeEnemigoId);

    void otorgar(String jugadorId, ElementoCandidatoBotin elemento);
}
