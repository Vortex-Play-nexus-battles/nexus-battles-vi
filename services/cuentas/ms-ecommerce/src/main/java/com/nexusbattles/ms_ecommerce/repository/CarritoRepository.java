package com.nexusbattles.ms_ecommerce.repository;

import com.nexusbattles.ms_ecommerce.model.Carrito;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarritoRepository extends JpaRepository<Carrito, Long> {

    /** El carrito del jugador (uno como mucho desde V3). */
    Optional<Carrito> findByUsuarioId(String usuarioId);

    /**
     * El carrito del jugador, bloqueado hasta el final de la transaccion
     * ({@code SELECT ... FOR UPDATE}). Lo usan todas las escrituras del carrito
     * y la compra: dos pestanas que cambian el mismo carrito a la vez se
     * ponen en fila en vez de pisarse, y la compra lo lee sin que nadie lo
     * cambie a mitad.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carrito c where c.usuarioId = :usuarioId")
    Optional<Carrito> bloquear(@Param("usuarioId") String usuarioId);

    /**
     * Crea el carrito del jugador si no tenia. Con la restriccion unica de V3,
     * dos primeras peticiones simultaneas no pueden crearle dos carritos, y la
     * segunda no falla: espera a la primera y no hace nada.
     */
    @Modifying
    @Query(value = """
            INSERT INTO carritos (usuario_id, total) VALUES (:usuarioId, 0)
            ON CONFLICT (usuario_id) DO NOTHING""", nativeQuery = true)
    int crearSiNoExiste(@Param("usuarioId") String usuarioId);
}
