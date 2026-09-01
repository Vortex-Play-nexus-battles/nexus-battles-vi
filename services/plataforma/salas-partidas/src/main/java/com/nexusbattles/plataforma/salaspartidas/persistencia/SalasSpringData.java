package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

/**
 * Acceso a la tabla de salas por Spring Data.
 *
 * <p>Es un detalle de infraestructura y por eso no es publica: el resto del
 * servicio habla con {@code RepositorioDeSalas}, el puerto del dominio. Si
 * manana esto dejara de ser JPA, nada fuera de este paquete se enteraria.
 */
interface SalasSpringData extends JpaRepository<SalaEntidad, UUID> {

    /**
     * Salas que aparecen en el listado — RF-JUE-002.
     *
     * <p>{@code estado IN :estados} es la regla, no un filtro opcional: los
     * estados admitidos los decide {@code EstadoSala.delListado()} y llegan
     * desde el adaptador. Una sala en juego, cancelada o finalizada no se lista
     * porque el sistema de diseno no tiene tarjeta para ella.
     *
     * <p>Se filtra por estado y no por la columna {@code privada} porque una
     * sala privada tambien se muestra: lo que no se le permite es el ingreso.
     *
     * <p>Los dos filtros del jugador se anulan cuando el parametro es nulo,
     * para no tener cuatro consultas casi iguales. La paginacion la resuelve la
     * base de datos: traer todo y cortar en memoria dejaria de funcionar en
     * cuanto haya salas de verdad.
     */
    @Query("""
            SELECT s FROM SalaEntidad s
            WHERE s.estado IN :estados
              AND (:modalidad IS NULL OR s.modalidad = :modalidad)
              AND (:estado IS NULL OR s.estado = :estado)
            """)
    Page<SalaEntidad> listar(@Param("estados") Collection<EstadoSala> estados,
                             @Param("modalidad") Modalidad modalidad,
                             @Param("estado") EstadoSala estado,
                             Pageable paginado);
}
