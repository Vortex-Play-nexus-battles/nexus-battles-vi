package com.nexusbattles.ms_identidad.admin.dto;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La ficha de gestion devuelve la clave de la CUENTA — RFINAL-06.
 *
 * <p>Defecto conocido «id de perfil usado como id de usuario»:
 * {@code AdminUsuarioResumenResponse.from} copiaba {@code perfil.getId()}, y el
 * panel usaba ese numero para suspender, banear, reactivar, restablecer la
 * clave y cambiar el rol, rutas que esperan la clave de la cuenta
 * (ms-identidad-admin.yaml, {@code UsuarioId}). Hoy los dos numeros coinciden
 * porque el perfil comparte la clave de su cuenta ({@code @MapsId},
 * {@code perfiles_usuario.usuario_id}), pero esa coincidencia es un detalle
 * del esquema, no una promesa: aqui se construye un perfil cuya clave no es la
 * de su cuenta y la ficha tiene que seguir dando la de la cuenta.
 */
@DisplayName("Ficha de gestion de una cuenta")
class AdminUsuarioResumenResponseTest {

    @Test
    @DisplayName("el id es el de la cuenta, aunque el del perfil fuera otro")
    void elIdEsElDeLaCuenta() {
        Usuario cuenta = new Usuario();
        cuenta.setId(15L);
        cuenta.setApodo("nyx_valiente");
        cuenta.setEmail("nyx@ejemplo.org");
        cuenta.setEstado("ACTIVO");
        cuenta.setRol(new RolEntity("MODERADOR", "rol de prueba"));
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setId(99L);
        perfil.setUsuario(cuenta);
        perfil.setNombres("Nyx");
        perfil.setApellidos("Valiente");

        AdminUsuarioResumenResponse ficha = AdminUsuarioResumenResponse.from(perfil);

        assertEquals(15L, ficha.getId());
        assertEquals("nyx_valiente", ficha.getApodo());
        assertEquals("MODERADOR", ficha.getRolNombre());
        assertEquals("Nyx", ficha.getNombres());
    }
}
