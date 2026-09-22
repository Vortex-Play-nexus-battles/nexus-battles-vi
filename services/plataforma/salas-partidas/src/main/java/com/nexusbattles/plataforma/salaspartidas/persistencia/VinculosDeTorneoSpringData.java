package com.nexusbattles.plataforma.salaspartidas.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface VinculosDeTorneoSpringData extends JpaRepository<VinculoDeTorneoEntidad, UUID> {
}
