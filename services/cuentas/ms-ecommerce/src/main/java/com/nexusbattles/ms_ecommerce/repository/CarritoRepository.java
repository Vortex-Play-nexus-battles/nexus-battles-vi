package com.nexusbattles.ms_ecommerce.repository;

import com.nexusbattles.ms_ecommerce.model.Carrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarritoRepository extends JpaRepository<Carrito, Long> {
    Optional<Carrito> findByUsuarioId(String usuarioId);
    // Nuevo método requerido por CheckoutService (2 parámetros)
    Optional<Carrito> findByIdAndUsuarioId(Long id, String usuarioId);
}
