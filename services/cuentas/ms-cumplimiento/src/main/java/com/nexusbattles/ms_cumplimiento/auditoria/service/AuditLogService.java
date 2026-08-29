package com.nexusbattles.ms_cumplimiento.auditoria.service;

import com.nexusbattles.ms_cumplimiento.auditoria.exception.AuditWriteException;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogSpecifications.*;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public AuditLog registrar(AuditActionType tipoAccion,
                              String administradorId,
                              String afectado,
                              String valorAnterior,
                              String valorNuevo,
                              String motivo,
                              String ipOrigen) {
        try {
            AuditLog entrada = AuditLog.builder()
                    .tipoAccion(tipoAccion)
                    .administradorId(administradorId)
                    .afectado(afectado)
                    .valorAnterior(valorAnterior)
                    .valorNuevo(valorNuevo)
                    .motivo(motivo)
                    .ipOrigen(ipOrigen)
                    .build();
            return repository.saveAndFlush(entrada);
        } catch (Exception e) {
            log.error("Fallo al escribir registro de auditoría, se cancela la acción administrativa", e);
            throw new AuditWriteException("No se pudo registrar la auditoría; acción cancelada", e);
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Page<AuditLog> consultar(String administradorId,
                                    AuditActionType tipoAccion,
                                    Instant desde,
                                    Instant hasta,
                                    Pageable pageable) {
        Specification<AuditLog> spec = Specification
                .where(conAdministrador(administradorId))
                .and(conTipoAccion(tipoAccion))
                .and(entreFechas(desde, hasta));
        return repository.findAll(spec, pageable);
    }
}