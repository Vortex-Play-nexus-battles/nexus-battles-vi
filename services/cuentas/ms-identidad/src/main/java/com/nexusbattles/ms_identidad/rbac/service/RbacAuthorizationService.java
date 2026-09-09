package com.nexusbattles.ms_identidad.rbac.service;

import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.model.PermissionType;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Servicio central de autorización RBAC conforme a la Tabla 24 (HU-RBAC-001).
 * Implementa la política Default-Deny (Falla Seguro).
 */
@Service
public class RbacAuthorizationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RbacAuthorizationService.class);
    private final RbacMatrixRepository matrixRepository;

    public RbacAuthorizationService(RbacMatrixRepository matrixRepository) {
        this.matrixRepository = matrixRepository;
    }

    public PermissionType evaluatePermission(Role role, Action action) {
        if (role == null || action == null) {
            log.warn("RBAC_INCONSISTENCY_DETECTED: Intento de evaluar permiso con rol o acción nulos (rol={}, accion={}). Aplicando Default-Deny.", role, action);
            return PermissionType.DENIED;
        }

        return matrixRepository.findPermission(role, action)
                .orElseGet(() -> {
                    log.warn("RBAC_INCONSISTENCY_DETECTED: Combinación no contemplada en la matriz (rol={}, accion={}). Aplicando Default-Deny.", role, action);
                    return PermissionType.DENIED;
                });
    }

    public boolean isActionPermitted(Role role, Action action) {
        PermissionType type = evaluatePermission(role, action);
        return type == PermissionType.GRANTED || type == PermissionType.TEMPORARY;
    }

    public Map<Role, Map<Action, PermissionType>> getFullMatrix() {
        return matrixRepository.findAll();
    }
}
