package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Orquesta la configuracion de la puja automatica del criterio 4 de
 * HU-SUB-004: consulta el saldo en ms-finanzas, deja que
 * {@link MotorPujaAutomaticaService} valide, y persiste el resultado.
 *
 * <p><b>Sin lock pesimista, a diferencia de {@link PujaApplicationService}.</b>
 * Configurar no escribe en la subasta ni compite por la oferta vigente: solo la
 * lee para comprobar que el limite es alcanzable. Tomar el lock de escritura de
 * la fila serializaria cada configuracion contra todas las pujas de esa
 * subasta, que es justo el cuello de botella que el diseno evita. Si entre la
 * lectura y el guardado otro jugador sube la oferta por encima del limite, el
 * efecto es el mismo que si hubiera subido un segundo despues: el job de
 * emision la desactiva y avisa a su dueno. Ese camino ya esta cubierto por
 * {@code EmisionDePujasAutomaticasJob}.
 *
 * <p>La escritura es un upsert porque el contrato declara el PUT idempotente
 * por jugador y subasta, y la tabla tiene un unico (subasta_id, jugador_id):
 * insertar una segunda configuracion reventaria contra esa restriccion.
 * Reconfigurar tambien reactiva: si el jugador subio su limite despues de que
 * el motor lo desactivara por quedarse corto, lo natural es que vuelva a
 * competir sin tener que borrar y crear.
 */
@Service
@RequiredArgsConstructor
public class PujaAutomaticaApplicationService {

    private final SubastaRepository subastaRepository;
    private final PujaAutomaticaRepository pujaAutomaticaRepository;
    private final MotorPujaAutomaticaService motorAutomatico;
    private final CreditoClient creditoClient;

    @Transactional
    public PujaAutomatica configurar(UUID subastaId, UUID jugadorId, BigDecimal limite) {
        Subasta subasta = subastaRepository.findById(subastaId)
                .orElseThrow(() -> new SubastaNoEncontradaException(subastaId));

        BigDecimal saldoDisponible = creditoClient.saldoDisponible(jugadorId);
        PujaAutomatica validada = motorAutomatico.configurar(subasta, jugadorId, limite, saldoDisponible);

        return pujaAutomaticaRepository.findBySubastaIdAndJugadorId(subastaId, jugadorId)
                .map(existente -> {
                    existente.setLimite(validada.getLimite());
                    existente.setActiva(true);
                    return pujaAutomaticaRepository.save(existente);
                })
                .orElseGet(() -> pujaAutomaticaRepository.save(validada));
    }

    /**
     * Desactiva en vez de borrar: la fila es la unica evidencia de que el
     * jugador llego a configurar una puja automatica, y borrarla perderia el
     * rastro de por que se emitieron sus pujas.
     *
     * <p>Idempotente a proposito: si no habia ninguna configurada, no es un
     * error — el estado que pedia el cliente (ninguna puja automatica activa)
     * es exactamente el que queda. Lo que si falla es desactivar sobre una
     * subasta inexistente, porque ahi el cliente se equivoco de recurso.
     */
    @Transactional
    public void desactivar(UUID subastaId, UUID jugadorId) {
        if (!subastaRepository.existsById(subastaId)) {
            throw new SubastaNoEncontradaException(subastaId);
        }
        pujaAutomaticaRepository.findBySubastaIdAndJugadorId(subastaId, jugadorId)
                .ifPresent(automatica -> {
                    automatica.setActiva(false);
                    pujaAutomaticaRepository.save(automatica);
                });
    }
}
