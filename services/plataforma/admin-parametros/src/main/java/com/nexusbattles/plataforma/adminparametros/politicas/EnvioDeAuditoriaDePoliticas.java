package com.nexusbattles.plataforma.adminparametros.politicas;

import com.nexusbattles.plataforma.adminparametros.parametros.ClienteAuditoria;
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
 * Lleva cada cambio publicado en una politica de uso a la bitacora central —
 * §7.3.5, con la misma cola que los cambios de parametros (SA-5): cada
 * {@code parametros.auditoria.reintento-ms} toma los pendientes cuya espera
 * paso; 201/200, auditado; 4xx, se queda con el motivo y una espera
 * creciente; sin respuesta o 5xx, se para la vuelta. Nunca se descarta
 * ninguno. Sin {@code AUDITORIA_URL} no hay cliente y los cambios esperan.
 *
 * <p>El asiento: {@code CREACION} de {@code politica:<POLITICA>}, con el
 * titulo y la vigencia como valor nuevo, el motivo y la IP de quien publico,
 * {@code ocurridoEn} = la publicacion y la referencia
 * {@code admin-parametros:politica-<id>}, que hace idempotente el reintento.
 */
public class EnvioDeAuditoriaDePoliticas {

    private static final Logger BITACORA = LoggerFactory.getLogger(EnvioDeAuditoriaDePoliticas.class);

    static final int LOTE = 50;
    static final String TIPO_ACCION = "CREACION";
    static final String REFERENCIA = "admin-parametros:politica-";

    private final CambioDePoliticaRepository cambios;
    private final ClienteAuditoria cliente;
    private final Clock reloj;
    private final Duration esperaBase;
    private final Duration esperaMaxima;

    public EnvioDeAuditoriaDePoliticas(CambioDePoliticaRepository cambios, ClienteAuditoria cliente, Clock reloj,
                                       Duration esperaBase, Duration esperaMaxima) {
        this.cambios = Objects.requireNonNull(cambios);
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

    /** @return cuantos cambios entraron en esta vuelta */
    public int ejecutar() {
        if (cliente == null) {
            return 0;
        }
        int entregados = 0;
        List<CambioDePolitica> pendientes = cambios.porAuditar(ahora(), PageRequest.of(0, LOTE));
        for (CambioDePolitica cambio : pendientes) {
            OffsetDateTime ahora = ahora();
            try {
                if (cliente.enviar(eventoDe(cambio)) == ClienteAuditoria.Resultado.REGISTRADO) {
                    cambio.auditado(ahora);
                    entregados++;
                } else {
                    cambio.falloDeAuditoria("rechazado por ms-cumplimiento", ahora, esperaBase, esperaMaxima);
                    BITACORA.error("El cambio en {} ({}) fue rechazado por la bitacora central; queda pendiente "
                            + "para revision", cambio.politica(), cambio.id());
                }
                cambios.save(cambio);
            } catch (RuntimeException noResponde) {
                cambio.falloDeAuditoria(noResponde.getClass().getSimpleName() + ": " + noResponde.getMessage(),
                        ahora, esperaBase, esperaMaxima);
                cambios.save(cambio);
                BITACORA.warn("La bitacora central no responde; {} cambio(s) de politica esperan: {}",
                        pendientes.size() - entregados, noResponde.getMessage());
                break;
            }
        }
        return entregados;
    }

    /** El asiento de un cambio publicado. Sin IP (no deberia pasar) dice «desconocida»: el campo es obligatorio. */
    static ClienteAuditoria.Evento eventoDe(CambioDePolitica c) {
        return new ClienteAuditoria.Evento(TIPO_ACCION, c.publicadoPor().toString(),
                "politica:" + c.politica().name(),
                null,
                c.titulo() + " (vigente desde " + c.vigenteDesde().toInstant() + ")",
                c.motivo(),
                c.ipOrigen() == null || c.ipOrigen().isBlank() ? "desconocida" : c.ipOrigen(),
                c.publicadoEn().toInstant().toString(),
                REFERENCIA + c.id());
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
