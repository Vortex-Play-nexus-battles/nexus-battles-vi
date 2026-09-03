package com.nexusbattles.ms_identidad.rbac.repository;

import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.model.PermissionType;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Repositorio de la matriz de permisos versionada conforme a la Tabla 24 (Extendida).
 */
@Repository
public class RbacMatrixRepository {

    private final Map<Role, Map<Action, PermissionType>> matrix = new EnumMap<>(Role.class);

    public RbacMatrixRepository() {
        initMatrix();
    }

    private void initMatrix() {
        for (Role r : Role.values()) {
            matrix.put(r, new EnumMap<>(Action.class));
        }

        // --- 1. JUGADOR ---
        set(Role.JUGADOR, Action.CREAR_CUENTA_JUGADOR, PermissionType.GRANTED);
        set(Role.JUGADOR, Action.MODIFICAR_PERFIL_PROPIO, PermissionType.GRANTED);
        set(Role.JUGADOR, Action.PUBLICAR_COMENTARIOS, PermissionType.GRANTED);
        set(Role.JUGADOR, Action.ELIMINAR_COMENTARIO_PROPIO, PermissionType.GRANTED);
        set(Role.JUGADOR, Action.MODERAR_COMENTARIOS, PermissionType.DENIED);
        set(Role.JUGADOR, Action.EMITIR_ADVERTENCIAS, PermissionType.DENIED);
        set(Role.JUGADOR, Action.SUSPENDER_USUARIOS, PermissionType.DENIED);
        set(Role.JUGADOR, Action.BANEAR_DEFINITIVAMENTE, PermissionType.DENIED);
        set(Role.JUGADOR, Action.CREAR_ADMIN_MODERADOR, PermissionType.DENIED);
        set(Role.JUGADOR, Action.GESTIONAR_PRODUCTOS, PermissionType.DENIED);
        set(Role.JUGADOR, Action.ASIGNAR_ROL, PermissionType.DENIED);
        set(Role.JUGADOR, Action.GESTIONAR_CUENTAS, PermissionType.DENIED);

        // --- 2. MODERADOR ---
        set(Role.MODERADOR, Action.CREAR_CUENTA_JUGADOR, PermissionType.DENIED);
        set(Role.MODERADOR, Action.MODIFICAR_PERFIL_PROPIO, PermissionType.GRANTED);
        set(Role.MODERADOR, Action.PUBLICAR_COMENTARIOS, PermissionType.GRANTED);
        set(Role.MODERADOR, Action.ELIMINAR_COMENTARIO_PROPIO, PermissionType.GRANTED);
        set(Role.MODERADOR, Action.MODERAR_COMENTARIOS, PermissionType.GRANTED);
        set(Role.MODERADOR, Action.EMITIR_ADVERTENCIAS, PermissionType.GRANTED);
        set(Role.MODERADOR, Action.SUSPENDER_USUARIOS, PermissionType.TEMPORARY);
        set(Role.MODERADOR, Action.BANEAR_DEFINITIVAMENTE, PermissionType.DENIED);
        set(Role.MODERADOR, Action.CREAR_ADMIN_MODERADOR, PermissionType.DENIED);
        set(Role.MODERADOR, Action.GESTIONAR_PRODUCTOS, PermissionType.DENIED);
        set(Role.MODERADOR, Action.ASIGNAR_ROL, PermissionType.DENIED);
        set(Role.MODERADOR, Action.GESTIONAR_CUENTAS, PermissionType.DENIED);

        // --- 3. ADMINISTRADOR ---
        set(Role.ADMINISTRADOR, Action.CREAR_CUENTA_JUGADOR, PermissionType.DENIED);
        set(Role.ADMINISTRADOR, Action.MODIFICAR_PERFIL_PROPIO, PermissionType.GRANTED);
        set(Role.ADMINISTRADOR, Action.PUBLICAR_COMENTARIOS, PermissionType.GRANTED);
        set(Role.ADMINISTRADOR, Action.ELIMINAR_COMENTARIO_PROPIO, PermissionType.GRANTED);
        set(Role.ADMINISTRADOR, Action.MODERAR_COMENTARIOS, PermissionType.GRANTED);
        set(Role.ADMINISTRADOR, Action.EMITIR_ADVERTENCIAS, PermissionType.GRANTED);
        set(Role.ADMINISTRADOR, Action.SUSPENDER_USUARIOS, PermissionType.GRANTED);
        set(Role.ADMINISTRADOR, Action.BANEAR_DEFINITIVAMENTE, PermissionType.GRANTED);
        set(Role.ADMINISTRADOR, Action.CREAR_ADMIN_MODERADOR, PermissionType.DENIED);
        set(Role.ADMINISTRADOR, Action.GESTIONAR_PRODUCTOS, PermissionType.GRANTED);
        set(Role.ADMINISTRADOR, Action.ASIGNAR_ROL, PermissionType.DENIED);
        set(Role.ADMINISTRADOR, Action.GESTIONAR_CUENTAS, PermissionType.GRANTED);

        // --- 4. SUPER_ADMINISTRADOR ---
        set(Role.SUPER_ADMINISTRADOR, Action.CREAR_CUENTA_JUGADOR, PermissionType.DENIED);
        set(Role.SUPER_ADMINISTRADOR, Action.MODIFICAR_PERFIL_PROPIO, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.PUBLICAR_COMENTARIOS, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.ELIMINAR_COMENTARIO_PROPIO, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.MODERAR_COMENTARIOS, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.EMITIR_ADVERTENCIAS, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.SUSPENDER_USUARIOS, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.BANEAR_DEFINITIVAMENTE, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.CREAR_ADMIN_MODERADOR, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.GESTIONAR_PRODUCTOS, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.ASIGNAR_ROL, PermissionType.GRANTED);
        set(Role.SUPER_ADMINISTRADOR, Action.GESTIONAR_CUENTAS, PermissionType.GRANTED);
    }

    private void set(Role role, Action action, PermissionType permission) {
        matrix.get(role).put(action, permission);
    }

    public Optional<PermissionType> findPermission(Role role, Action action) {
        if (role == null || action == null) {
            return Optional.empty();
        }
        Map<Action, PermissionType> roleMap = matrix.get(role);
        if (roleMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roleMap.get(action));
    }

    public Map<Role, Map<Action, PermissionType>> findAll() {
        return Collections.unmodifiableMap(matrix);
    }
}
