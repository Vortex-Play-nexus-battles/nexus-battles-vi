package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Orquesta una puja: abre la transaccion, toma el lock pesimista de la
 * subasta, arma el ContextoParticipacion desde la base de datos y delega las
 * reglas de negocio en MotorPujasService.
 *
 * El lock se toma ANTES de leer la puja vigente y se suelta al cerrar la
 * transaccion, de modo que "leer oferta vigente -> validar -> escribir nueva
 * puja" es atomico frente a otras pujas sobre la misma subasta. Esa es la
 * garantia que exige el caso de prueba de concurrencia de HU-SUB-004.
 *
 * Ojo con la latencia: la reserva de creditos en ms-finanzas ocurre dentro de
 * este lock, asi que el tiempo de respuesta de ese servicio serializa todas
 * las pujas de la subasta. Es el numero que hay que acordar con Juan Diego.
 */
@Service
@RequiredArgsConstructor
public class PujaApplicationService {

    private final SubastaRepository subastaRepository;
    private final PujaRepository pujaRepository;
    private final MotorPujasService motorPujas;

    @Transactional
    public Puja pujar(UUID subastaId, UUID jugadorId, BigDecimal monto, String idempotencyKey) {
        Subasta subasta = cargarConLock(subastaId);
        Puja pujaVigente = pujaRepository.findBySubastaIdAndEstado(subastaId, EstadoPuja.ACTIVA).orElse(null);

        Puja nuevaPuja = motorPujas.pujar(subasta, pujaVigente, jugadorId, monto, contextoDe(jugadorId, subastaId), idempotencyKey);

        // saveAndFlush, no save: el indice unico parcial solo admite una puja
        // ACTIVA por subasta, e Hibernate ordena los INSERT antes que los
        // UPDATE al volcar la sesion. Sin este flush explicito, la nueva puja
        // se insertaria antes de que la anterior pase a SUPERADA y la
        // constraint la rechazaria.
        if (pujaVigente != null) {
            pujaRepository.saveAndFlush(pujaVigente);
        }
        subastaRepository.save(subasta);
        return pujaRepository.save(nuevaPuja);
    }

    @Transactional
    public Puja comprarAhora(UUID subastaId, UUID jugadorId) {
        Subasta subasta = cargarConLock(subastaId);
        Puja pujaVigente = pujaRepository.findBySubastaIdAndEstado(subastaId, EstadoPuja.ACTIVA).orElse(null);

        Puja pujaGanadora = motorPujas.comprarAhora(subasta, pujaVigente, jugadorId);

        // Mismo motivo que en pujar(): la puja vigente debe dejar de ser ACTIVA
        // en la base de datos antes de insertar la ganadora.
        if (pujaVigente != null) {
            pujaRepository.saveAndFlush(pujaVigente);
        }
        subastaRepository.save(subasta);
        return pujaRepository.save(pujaGanadora);
    }

    @Transactional
    public void cerrarPorVencimiento(UUID subastaId) {
        Subasta subasta = cargarConLock(subastaId);
        Puja pujaVigente = pujaRepository.findBySubastaIdAndEstado(subastaId, EstadoPuja.ACTIVA).orElse(null);

        motorPujas.cerrarPorVencimiento(subasta, pujaVigente);

        if (pujaVigente != null) {
            pujaRepository.save(pujaVigente);
        }
        subastaRepository.save(subasta);
    }

    private Subasta cargarConLock(UUID subastaId) {
        return subastaRepository.findByIdParaActualizar(subastaId)
                .orElseThrow(() -> new SubastaNoEncontradaException(subastaId));
    }

    private ContextoParticipacion contextoDe(UUID jugadorId, UUID subastaId) {
        return new ContextoParticipacion(
                pujaRepository.findFirstByJugadorIdOrderByCreadaEnDesc(jugadorId).map(Puja::getCreadaEn).orElse(null),
                pujaRepository.countByJugadorIdAndEstado(jugadorId, EstadoPuja.ACTIVA),
                pujaRepository.contarSubastasActivasExcluyendo(jugadorId, EstadoPuja.ACTIVA, subastaId));
    }
}
