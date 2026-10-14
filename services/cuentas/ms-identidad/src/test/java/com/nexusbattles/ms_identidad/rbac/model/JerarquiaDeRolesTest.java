package com.nexusbattles.ms_identidad.rbac.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quien puede actuar sobre quien (7.3.1). La matriz de permisos dice QUE hace
 * cada rol; esta tabla dice SOBRE QUIEN, celda por celda.
 */
@DisplayName("Jerarquia de roles (7.3.1)")
class JerarquiaDeRolesTest {

    @ParameterizedTest(name = "{0} sanciona a {1}: {2}")
    @CsvSource({
        // actor,               objetivo,             puede
        "JUGADOR,               JUGADOR,              false",
        "MODERADOR,             JUGADOR,              true",
        "MODERADOR,             MODERADOR,            false",
        "MODERADOR,             ADMINISTRADOR,        false",
        "MODERADOR,             SUPER_ADMINISTRADOR,  false",
        "ADMINISTRADOR,         JUGADOR,              true",
        "ADMINISTRADOR,         MODERADOR,            true",
        "ADMINISTRADOR,         ADMINISTRADOR,        false",
        "ADMINISTRADOR,         SUPER_ADMINISTRADOR,  false",
        "SUPER_ADMINISTRADOR,   JUGADOR,              true",
        "SUPER_ADMINISTRADOR,   MODERADOR,            true",
        "SUPER_ADMINISTRADOR,   ADMINISTRADOR,        true",
        "SUPER_ADMINISTRADOR,   SUPER_ADMINISTRADOR,  false",
    })
    void sancionarSoloSobreUnRolInferior(String actor, String objetivo, boolean puede) {
        assertThat(JerarquiaDeRoles.puedeSancionar(actor, objetivo)).isEqualTo(puede);
    }

    @ParameterizedTest(name = "{0} gestiona a {1}: {2}")
    @CsvSource({
        "JUGADOR,               JUGADOR,              false",
        "MODERADOR,             JUGADOR,              true",
        "MODERADOR,             MODERADOR,            false",
        "ADMINISTRADOR,         MODERADOR,            true",
        "ADMINISTRADOR,         ADMINISTRADOR,        false",
        "ADMINISTRADOR,         SUPER_ADMINISTRADOR,  false",
        "SUPER_ADMINISTRADOR,   ADMINISTRADOR,        true",
        "SUPER_ADMINISTRADOR,   SUPER_ADMINISTRADOR,  true",
    })
    void gestionarSobreUnRolInferiorOSuperSobreSuper(String actor, String objetivo, boolean puede) {
        assertThat(JerarquiaDeRoles.puedeGestionar(actor, objetivo)).isEqualTo(puede);
    }

    @Test
    @DisplayName("un rol desconocido o vacio no actua sobre nadie (fail-closed)")
    void rolDesconocido() {
        assertThat(JerarquiaDeRoles.rango(null)).isEqualTo(JerarquiaDeRoles.SIN_ROL);
        assertThat(JerarquiaDeRoles.rango(" ")).isEqualTo(JerarquiaDeRoles.SIN_ROL);
        assertThat(JerarquiaDeRoles.rango("ROOT")).isEqualTo(JerarquiaDeRoles.SIN_ROL);
        assertThat(JerarquiaDeRoles.puedeSancionar("ROOT", "JUGADOR")).isFalse();
        assertThat(JerarquiaDeRoles.puedeGestionar(null, "JUGADOR")).isFalse();
    }

    @Test
    @DisplayName("el rol se compara sin mayusculas ni espacios")
    void normaliza() {
        assertThat(JerarquiaDeRoles.rango(" super_administrador ")).isEqualTo(JerarquiaDeRoles.SUPER_ADMINISTRADOR);
        assertThat(JerarquiaDeRoles.esAdministrativo("Moderador")).isTrue();
        assertThat(JerarquiaDeRoles.esAdministrativo("JUGADOR")).isFalse();
    }
}
