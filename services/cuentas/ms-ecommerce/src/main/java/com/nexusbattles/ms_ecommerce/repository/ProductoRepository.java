package com.nexusbattles.ms_ecommerce.repository;

import com.nexusbattles.ms_ecommerce.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
}