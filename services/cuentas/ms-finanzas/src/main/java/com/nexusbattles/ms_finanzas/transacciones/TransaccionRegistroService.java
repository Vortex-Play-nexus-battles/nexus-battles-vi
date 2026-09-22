package com.nexusbattles.ms_finanzas.transacciones;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escritor del historial de transacciones (HU-PAG-002). Es el punto único de
 * entrada por donde HU-PAG-001 (Juan Diego, pasarela simulada) persiste cada
 * intento — aprobado, rechazado o indeterminado — para que después el usuario
 * pueda consultarlo en su historial.
 *
 * <p>Idempotencia por {@code refId}: si HU-PAG-001 reintenta por un timeout,
 * se lanza {@link TransaccionYaRegistradaException} y NO se duplica el
 * registro. El llamador puede recuperar el estado ya persistido invocando
 * {@link #buscarPorRefId}.
 */
@Service
public class TransaccionRegistroService {

    private final TransaccionRepository repositorio;
    private final Clock reloj;

    public TransaccionRegistroService(TransaccionRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Transactional
    public Transaccion registrar(RegistrarTransaccionRequest solicitud) {
        if (repositorio.existsByRefId(solicitud.refId())) {
            throw new TransaccionYaRegistradaException(solicitud.refId());
        }
        Instant ahora = Instant.now(reloj);
        Transaccion transaccion = new Transaccion();
        transaccion.setRefId(solicitud.refId());
        transaccion.setUidUsuario(solicitud.uidUsuario());
        transaccion.setMonto(solicitud.monto());
        transaccion.setMoneda(solicitud.moneda().toUpperCase());
        transaccion.setConcepto(solicitud.concepto());
        transaccion.setResultado(solicitud.resultado());
        transaccion.setComprobanteUrl(solicitud.comprobanteUrl());
        transaccion.setPasarelaRefExterna(solicitud.pasarelaRefExterna());
        transaccion.setCreado(ahora);
        transaccion.setActualizado(ahora);
        return repositorio.save(transaccion);
    }

    @Transactional(readOnly = true)
    public Optional<Transaccion> buscarPorRefId(String refId) {
        return repositorio.findByRefId(refId);
    }
}
