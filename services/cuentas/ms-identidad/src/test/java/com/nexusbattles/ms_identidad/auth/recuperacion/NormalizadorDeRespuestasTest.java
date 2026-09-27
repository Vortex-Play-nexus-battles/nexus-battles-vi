package com.nexusbattles.ms_identidad.auth.recuperacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Respuestas de seguridad: normalizadas y de tamano fijo antes de BCrypt (7.1.1)")
class NormalizadorDeRespuestasTest {

    @Test
    @DisplayName("sin espacios en los extremos, minusculas, sin tildes y con los espacios interiores colapsados")
    void normaliza() {
        assertThat(NormalizadorDeRespuestas.normalizar("  Bogotá   D.C. ")).isEqualTo("bogota d.c.");
        assertThat(NormalizadorDeRespuestas.normalizar("ÑANDÚ")).isEqualTo("nandu");
        assertThat(NormalizadorDeRespuestas.normalizar(null)).isEmpty();
    }

    @Test
    @DisplayName("el resumen es igual para respuestas equivalentes, distinto para otras, y siempre cabe en BCrypt")
    void resumen() {
        String a = NormalizadorDeRespuestas.paraResumir("  Bogotá ");
        assertThat(a).isEqualTo(NormalizadorDeRespuestas.paraResumir("BOGOTA"));
        assertThat(a).isNotEqualTo(NormalizadorDeRespuestas.paraResumir("Medellín"));
        String larga = NormalizadorDeRespuestas.paraResumir("ñ".repeat(100));
        assertThat(larga).hasSize(44);
        // 100 letras no ASCII son 200 bytes: sin el resumen, BCrypt las rechazaria.
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        assertThat(bcrypt.matches(larga, bcrypt.encode(larga))).isTrue();
    }
}
