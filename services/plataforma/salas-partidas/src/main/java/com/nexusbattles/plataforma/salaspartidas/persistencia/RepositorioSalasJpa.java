package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaModificadaConcurrentemente;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida: implementa el puerto del dominio contra PostgreSQL.
 *
 * <p>Su unico trabajo es traducir entre {@link Sala} y {@link SalaEntidad}. No
 * toma decisiones: si aqui apareciera una regla del juego, estaria en el sitio
 * equivocado.
 */
@Repository
public class RepositorioSalasJpa implements RepositorioDeSalas {

    private final SalasSpringData datos;

    RepositorioSalasJpa(SalasSpringData datos) {
        this.datos = datos;
    }

    /**
     * Guarda la sala y sus participantes en la misma transaccion.
     *
     * <p>Desde HU-SAL-002 no es una sola fila: la sala y su coleccion de
     * participantes tienen que escribirse juntas o no escribirse. Media
     * operacion dejaria un aforo que no coincide con quienes estan dentro, que
     * es justo lo que este incremento vino a impedir.
     *
     * <p><b>Contrato de la marca de concurrencia</b> ({@code version}, V4).
     * La entidad se reconstruye desde el dominio con la version que este leyo,
     * y el {@code UPDATE} lleva {@code WHERE version = ?}: una copia leida
     * antes de otra escritura no encuentra la fila y se traduce a
     * {@link SalaModificadaConcurrentemente}, sin pisar nada. Lo que se
     * garantiza es que la marca avanza <b>exactamente en uno por escritura que
     * llega a la base</b> y que una copia vieja falla. El valor con el que
     * <i>nace</i> la fila no es parte del contrato: como el identificador lo
     * asigna el dominio, Spring Data hace {@code merge}, Hibernate inserta la
     * sala con 0 y, al volcar la coleccion de participantes en la copia
     * gestionada, la marca como sucia y sube la version a 1 en el mismo flush
     * ({@code insert} + {@code update salas set version}). Una sentencia de mas
     * una sola vez por sala, a cambio de no tener que consultar antes si existe.
     * Las pruebas comparan la marca con la observada tras crear, no con 0.
     */
    @Override
    @Transactional
    public Sala guardar(Sala sala) {
        try {
            // saveAndFlush y no save: el UPDATE con `WHERE version = ?` tiene
            // que ejecutarse DENTRO de este metodo, no al confirmar la
            // transaccion despues de salir, para que el conflicto se pueda
            // traducir aqui y no escape como excepcion de infraestructura.
            datos.saveAndFlush(SalaEntidad.desde(sala));
        } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException otroSeAdelanto) {
            throw new SalaModificadaConcurrentemente(sala.id());
        }
        return sala;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Sala> buscarPorId(UUID id) {
        return datos.findById(id).map(SalaEntidad::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDeSalas listar(Modalidad modalidad, EstadoSala estado,
                                int pagina, int tamano) {

        Page<SalaEntidad> resultado = datos.listar(EstadoSala.delListado(),
                modalidad, estado, PageRequest.of(pagina, tamano));

        return new PaginaDeSalas(
                resultado.getContent().stream().map(SalaEntidad::aDominio).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }
}
