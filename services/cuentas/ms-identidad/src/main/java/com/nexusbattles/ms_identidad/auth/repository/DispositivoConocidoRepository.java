package com.nexusbattles.ms_identidad.auth.repository;

import com.nexusbattles.ms_identidad.auth.model.DispositivoConocido;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DispositivoConocidoRepository extends JpaRepository<DispositivoConocido, Long> {
    Optional<DispositivoConocido> findByUsuarioAndHuella(Usuario usuario, String huella);
}
