package com.nexusbattles.ms_finanzas.partidas;

import org.springframework.data.jpa.repository.JpaRepository;

/** Un contador por jugador, por su {@code uid} (cofres.yaml 1.1.0). */
public interface ContadorDeCofresRepository extends JpaRepository<ContadorDeCofres, String> {
}
