package com.nexusbattles.ms_identidad.auth.repository;

import com.nexusbattles.ms_identidad.auth.model.TokenCredencial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenCredencialRepository extends JpaRepository<TokenCredencial, Long> {
    Optional<TokenCredencial> findByToken(String token);
}
