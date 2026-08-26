package com.nexusbattles.ms_identidad.perfiles.repository;

import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PerfilUsuarioRepository extends JpaRepository<PerfilUsuario, Long> {
}