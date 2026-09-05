package com.nexusbattles.ms_ecommerce.repository;

import com.nexusbattles.ms_ecommerce.model.ListaDeseos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ListaDeseosRepository extends JpaRepository<ListaDeseos, Long> {
    List<ListaDeseos> findByUsuarioId(String usuarioId);
    Optional<ListaDeseos> findByUsuarioIdAndProductoId(String usuarioId, Long productoId);
    void deleteByUsuarioIdAndProductoId(String usuarioId, Long productoId);
}