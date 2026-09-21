package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.LiquidacionDeApuesta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Acceso generado por Spring Data. El puerto del dominio lo envuelve. */
interface LiquidacionesSpringData extends JpaRepository<LiquidacionEntidad, UUID> {

    List<LiquidacionEntidad> findByEstadoOrderByCreadaEnAsc(LiquidacionDeApuesta.Estado estado,
                                                            Pageable pagina);
}
