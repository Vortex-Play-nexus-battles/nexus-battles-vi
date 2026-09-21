package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SancionRepository extends JpaRepository<Sancion, UUID> {

    List<Sancion> findByUsuarioIdOrderByEmitidaEnDesc(UUID usuarioId);

    /** Las que pueden restringir: no revertidas y distintas de advertencia. */
    List<Sancion> findByUsuarioIdAndRevertidaEnIsNullAndTipoNot(UUID usuarioId, Sancion.Tipo tipo);
}
