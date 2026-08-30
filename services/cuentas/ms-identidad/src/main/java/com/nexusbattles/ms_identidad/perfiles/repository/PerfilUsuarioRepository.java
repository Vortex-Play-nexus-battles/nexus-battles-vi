package com.nexusbattles.ms_identidad.perfiles.repository;

import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PerfilUsuarioRepository extends JpaRepository<PerfilUsuario, Long> {

    @Query("SELECT p FROM PerfilUsuario p JOIN FETCH p.usuario WHERE p.id = :usuarioId")
    Optional<PerfilUsuario> findByIdConUsuario(Long usuarioId);
}