package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.model.PermissionType;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RbacMatrix44CombinationsTest {

    private RbacAuthorizationService rbacService;

    @BeforeEach
    void setUp() {
        RbacMatrixRepository repository = new RbacMatrixRepository();
        rbacService = new RbacAuthorizationService(repository);
    }

    @Test
    @DisplayName("Verificar exactamente las 44 combinaciones de la Tabla (4 Roles x 11 Acciones)")
    void testAll44CombinationsExactMatch() {
        Map<Role, Map<Action, PermissionType>> expected = new EnumMap<>(Role.class);
        for (Role r : Role.values()) {
            expected.put(r, new EnumMap<>(Action.class));
        }

        // --- 1. JUGADOR ---
        expected.get(Role.JUGADOR).put(Action.CREAR_CUENTA_JUGADOR, PermissionType.GRANTED);
        expected.get(Role.JUGADOR).put(Action.MODIFICAR_PERFIL_PROPIO, PermissionType.GRANTED);
        expected.get(Role.JUGADOR).put(Action.PUBLICAR_COMENTARIOS, PermissionType.GRANTED);
        expected.get(Role.JUGADOR).put(Action.ELIMINAR_COMENTARIO_PROPIO, PermissionType.GRANTED);
        expected.get(Role.JUGADOR).put(Action.MODERAR_COMENTARIOS, PermissionType.DENIED);
        expected.get(Role.JUGADOR).put(Action.EMITIR_ADVERTENCIAS, PermissionType.DENIED);
        expected.get(Role.JUGADOR).put(Action.SUSPENDER_USUARIOS, PermissionType.DENIED);
        expected.get(Role.JUGADOR).put(Action.BANEAR_DEFINITIVAMENTE, PermissionType.DENIED);
        expected.get(Role.JUGADOR).put(Action.CREAR_ADMIN_MODERADOR, PermissionType.DENIED);
        expected.get(Role.JUGADOR).put(Action.GESTIONAR_PRODUCTOS, PermissionType.DENIED);
        expected.get(Role.JUGADOR).put(Action.ASIGNAR_ROL, PermissionType.DENIED);

        // --- 2. MODERADOR ---
        expected.get(Role.MODERADOR).put(Action.CREAR_CUENTA_JUGADOR, PermissionType.DENIED);
        expected.get(Role.MODERADOR).put(Action.MODIFICAR_PERFIL_PROPIO, PermissionType.GRANTED);
        expected.get(Role.MODERADOR).put(Action.PUBLICAR_COMENTARIOS, PermissionType.GRANTED);
        expected.get(Role.MODERADOR).put(Action.ELIMINAR_COMENTARIO_PROPIO, PermissionType.GRANTED);
        expected.get(Role.MODERADOR).put(Action.MODERAR_COMENTARIOS, PermissionType.GRANTED);
        expected.get(Role.MODERADOR).put(Action.EMITIR_ADVERTENCIAS, PermissionType.GRANTED);
        expected.get(Role.MODERADOR).put(Action.SUSPENDER_USUARIOS, PermissionType.TEMPORARY);
        expected.get(Role.MODERADOR).put(Action.BANEAR_DEFINITIVAMENTE, PermissionType.DENIED);
        expected.get(Role.MODERADOR).put(Action.CREAR_ADMIN_MODERADOR, PermissionType.DENIED);
        expected.get(Role.MODERADOR).put(Action.GESTIONAR_PRODUCTOS, PermissionType.DENIED);
        expected.get(Role.MODERADOR).put(Action.ASIGNAR_ROL, PermissionType.DENIED);

        // --- 3. ADMINISTRADOR ---
        expected.get(Role.ADMINISTRADOR).put(Action.CREAR_CUENTA_JUGADOR, PermissionType.DENIED);
        expected.get(Role.ADMINISTRADOR).put(Action.MODIFICAR_PERFIL_PROPIO, PermissionType.GRANTED);
        expected.get(Role.ADMINISTRADOR).put(Action.PUBLICAR_COMENTARIOS, PermissionType.GRANTED);
        expected.get(Role.ADMINISTRADOR).put(Action.ELIMINAR_COMENTARIO_PROPIO, PermissionType.GRANTED);
        expected.get(Role.ADMINISTRADOR).put(Action.MODERAR_COMENTARIOS, PermissionType.GRANTED);
        expected.get(Role.ADMINISTRADOR).put(Action.EMITIR_ADVERTENCIAS, PermissionType.GRANTED);
        expected.get(Role.ADMINISTRADOR).put(Action.SUSPENDER_USUARIOS, PermissionType.GRANTED);
        expected.get(Role.ADMINISTRADOR).put(Action.BANEAR_DEFINITIVAMENTE, PermissionType.GRANTED);
        expected.get(Role.ADMINISTRADOR).put(Action.CREAR_ADMIN_MODERADOR, PermissionType.DENIED);
        expected.get(Role.ADMINISTRADOR).put(Action.GESTIONAR_PRODUCTOS, PermissionType.GRANTED);
        expected.get(Role.ADMINISTRADOR).put(Action.ASIGNAR_ROL, PermissionType.DENIED);

        // --- 4. SUPER_ADMINISTRADOR ---
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.CREAR_CUENTA_JUGADOR, PermissionType.DENIED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.MODIFICAR_PERFIL_PROPIO, PermissionType.GRANTED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.PUBLICAR_COMENTARIOS, PermissionType.GRANTED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.ELIMINAR_COMENTARIO_PROPIO, PermissionType.GRANTED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.MODERAR_COMENTARIOS, PermissionType.GRANTED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.EMITIR_ADVERTENCIAS, PermissionType.GRANTED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.SUSPENDER_USUARIOS, PermissionType.GRANTED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.BANEAR_DEFINITIVAMENTE, PermissionType.GRANTED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.CREAR_ADMIN_MODERADOR, PermissionType.GRANTED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.GESTIONAR_PRODUCTOS, PermissionType.GRANTED);
        expected.get(Role.SUPER_ADMINISTRADOR).put(Action.ASIGNAR_ROL, PermissionType.GRANTED);

        int count = 0;
        for (Role role : Role.values()) {
            for (Action action : Action.values()) {
                PermissionType actual = rbacService.evaluatePermission(role, action);
                PermissionType exp = expected.get(role).get(action);
                assertEquals(exp, actual, "Fallo en combinación: Rol " + role + " -> Acción " + action);
                count++;
            }
        }
        assertEquals(44, count, "Deben haberse evaluado exactamente 44 combinaciones");
    }

    @Test
    @DisplayName("Regla Default-Deny: Parámetros nulos o no mapeados deben ser denegados")
    void testDefaultDeny() {
        assertEquals(PermissionType.DENIED, rbacService.evaluatePermission(null, Action.BANEAR_DEFINITIVAMENTE));
        assertEquals(PermissionType.DENIED, rbacService.evaluatePermission(Role.JUGADOR, null));
        assertEquals(PermissionType.DENIED, rbacService.evaluatePermission(null, null));
        assertFalse(rbacService.isActionPermitted(null, Action.CREAR_CUENTA_JUGADOR));
    }
}
