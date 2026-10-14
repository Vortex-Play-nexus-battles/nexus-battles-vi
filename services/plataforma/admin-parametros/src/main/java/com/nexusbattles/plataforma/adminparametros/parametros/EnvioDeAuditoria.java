package com.nexusbattles.plataforma.adminparametros.parametros;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Lleva cada cambio de parametro a la bitacora central — §7.3.5 (SA-5).
 *
 * <p>Cada {@code parametros.auditoria.reintento-ms} (10 s) toma las versiones
 * pendientes cuya espera paso, de la mas antigua a la mas reciente:
 * 201/200, auditada; 4xx, se queda con el motivo y una espera creciente;
 * sin respuesta o 5xx, se para la vuelta y se reintenta despues. Nunca se
 * descarta ninguna. Sin {@code AUDITORIA_URL} no hay cliente y las versiones
 * esperan.
 *
 * <p>B12 sigue valiendo: el cliente lleva tiempos de conexion y de lectura,
 * y ademas esto ya no corre dentro de la peticion del administrador.
 */
public class EnvioDeAuditoria {

    private static final Logger BITACORA = LoggerFactory.getLogger(EnvioDeAuditoria.class);

    static final int LOTE = 50;

    private final VersionRepository versiones;
    private final ClienteAuditoria cliente;
    private final Clock reloj;
    private final Duration esperaBase;
    private final Duration esperaMaxima;

    public EnvioDeAuditoria(VersionRepository versiones, ClienteAuditoria cliente, Clock reloj,
                            Duration esperaBase, Duration esperaMaxima) {
        this.versiones = Objects.requireNonNull(versiones);
        this.cliente = cliente;
        this.reloj = Objects.requireNonNull(reloj);
        this.esperaBase = Objects.requireNonNull(esperaBase);
        this.esperaMaxima = Objects.requireNonNull(esperaMaxima);
    }

    @Scheduled(fixedDelayString = "${parametros.auditoria.reintento-ms:10000}",
            initialDelayString = "${parametros.auditoria.reintento-ms:10000}")
    public void entregarPendientes() {
        ejecutar();
    }

    /** @return cuantas versiones entraron en esta vuelta */
    public int ejecutar() {
        if (cliente == null) {
            return 0;
        }
        int entregadas = 0;
        List<Version> pendientes = versiones.porAuditar(ahora(), PageRequest.of(0, LOTE));
        for (Version version : pendientes) {
            OffsetDateTime ahora = ahora();
            try {
                if (cliente.entregar(version) == ClienteAuditoria.Resultado.REGISTRADO) {
                    version.auditado(ahora);
                    entregadas++;
                } else {
                    version.falloDeAuditoria("rechazado por ms-cumplimiento", ahora, esperaBase, esperaMaxima);
                    BITACORA.error("El cambio de {} (v{}) fue rechazado por la bitacora central; queda pendiente "
                            + "para revision", version.clave(), version.version());
                }
                versiones.save(version);
            } catch (RuntimeException noResponde) {
                version.falloDeAuditoria(noResponde.getClass().getSimpleName() + ": " + noResponde.getMessage(),
                        ahora, esperaBase, esperaMaxima);
                versiones.save(version);
                BITACORA.warn("La bitacora central no responde; {} cambio(s) esperan: {}",
                        pendientes.size() - entregadas, noResponde.getMessage());
                break;
            }
        }
        return entregadas;
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
