package com.nexusbattles.ms_identidad.admin.dto;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code POST /admin/cuentas} responde solo lo que la interfaz muestra al
 * confirmar el alta (ms-identidad-admin.yaml 1.6.0): nunca la entidad, que
 * llevaba el resumen BCrypt de la clave.
 */
@DisplayName("CuentaAdministrativaCreadaResponse")
class CuentaAdministrativaCreadaResponseTest {

    @Test
    @DisplayName("lleva id, uid, apodo, correo, rol y estado; nada mas")
    void deUnaCuentaCompleta() {
        Usuario cuenta = new Usuario();
        cuenta.setId(12L);
        UUID uid = UUID.fromString("3a2b1c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d");
        cuenta.setPublicId(uid);
        cuenta.setApodo("nueva_mod");
        cuenta.setEmail("nueva_mod@nexus.test");
        cuenta.setPassword("$2a$10$resumen-que-no-sale");
        cuenta.setEstado("INACTIVO");
        cuenta.setRol(new RolEntity("MODERADOR", "Moderador"));

        CuentaAdministrativaCreadaResponse respuesta = CuentaAdministrativaCreadaResponse.de(cuenta);

        assertThat(respuesta.id()).isEqualTo(12L);
        assertThat(respuesta.uid()).isEqualTo(uid.toString());
        assertThat(respuesta.apodo()).isEqualTo("nueva_mod");
        assertThat(respuesta.email()).isEqualTo("nueva_mod@nexus.test");
        assertThat(respuesta.rol()).isEqualTo("MODERADOR");
        assertThat(respuesta.estado()).isEqualTo("INACTIVO");
        assertThat(respuesta.toString()).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("sin uid ni rol (cuenta a medio crear) los campos salen vacios, sin fallar")
    void sinUidNiRol() {
        Usuario cuenta = new Usuario();
        cuenta.setApodo("a_medias");
        cuenta.setEstado("INACTIVO");

        CuentaAdministrativaCreadaResponse respuesta = CuentaAdministrativaCreadaResponse.de(cuenta);

        assertThat(respuesta.uid()).isNull();
        assertThat(respuesta.rol()).isNull();
        assertThat(respuesta.apodo()).isEqualTo("a_medias");
    }
}
