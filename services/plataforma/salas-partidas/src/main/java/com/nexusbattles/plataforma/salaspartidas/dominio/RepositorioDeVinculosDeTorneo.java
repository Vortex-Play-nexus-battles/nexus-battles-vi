package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia del vinculo sala ↔ encuentro de torneo. */
public interface RepositorioDeVinculosDeTorneo {

    void guardar(VinculoDeTorneo vinculo);

    Optional<VinculoDeTorneo> buscarPorSala(UUID idSala);
}
