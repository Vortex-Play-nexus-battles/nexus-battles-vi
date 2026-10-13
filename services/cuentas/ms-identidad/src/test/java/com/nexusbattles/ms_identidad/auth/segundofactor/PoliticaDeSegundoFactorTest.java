package com.nexusbattles.ms_identidad.auth.segundofactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La configuracion del segundo factor: que roles lo tienen obligatorio (ninguno
 * por omision, para que las pruebas automaticas sigan entrando) y las cifras
 * provisionales del desafio y de los codigos de recuperacion.
 */
@DisplayName("Politica del segundo factor (configuracion)")
class PoliticaDeSegundoFactorTest {

    @Test
    @DisplayName("por omision ningun rol lo tiene obligatorio")
    void apagadaPorOmision() {
        PoliticaDeSegundoFactor politica = new PoliticaDeSegundoFactor("", 5, 10, "Nexus Battles VI");
        for (String rol : new String[] {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"}) {
            assertThat(politica.esObligatorioPara(rol)).as(rol).isFalse();
        }
        assertThat(politica.rolesObligatorios()).isEmpty();
    }

    @Test
    @DisplayName("la lista admite comas, punto y coma, espacios y minusculas")
    void listaDeRoles() {
        PoliticaDeSegundoFactor politica = new PoliticaDeSegundoFactor(
                " moderador, ADMINISTRADOR;Super_Administrador ", 5, 10, "Nexus Battles VI");
        assertThat(politica.esObligatorioPara("MODERADOR")).isTrue();
        assertThat(politica.esObligatorioPara("ADMINISTRADOR")).isTrue();
        assertThat(politica.esObligatorioPara("SUPER_ADMINISTRADOR")).isTrue();
        assertThat(politica.esObligatorioPara("JUGADOR")).isFalse();
        assertThat(politica.esObligatorioPara(null)).isFalse();
        assertThat(politica.rolesObligatorios())
                .containsExactlyInAnyOrder("MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR");
    }

    @Test
    @DisplayName("un rol que no existe no se inventa: se ignora (y la bitacora lo dice)")
    void rolDesconocido() {
        PoliticaDeSegundoFactor politica = new PoliticaDeSegundoFactor("ADMIN,ADMINISTRADOR", 5, 10, "x");
        assertThat(politica.rolesObligatorios()).containsExactly("ADMINISTRADOR");
        assertThat(politica.esObligatorioPara("ADMIN")).isFalse();
    }

    @Test
    @DisplayName("vigencia del desafio, cuantos codigos de recuperacion y el emisor")
    void cifras() {
        PoliticaDeSegundoFactor politica = new PoliticaDeSegundoFactor(null, 7, 12, "  Nexus  ");
        assertThat(politica.vigenciaDelDesafio()).isEqualTo(Duration.ofMinutes(7));
        assertThat(politica.codigosDeRecuperacion()).isEqualTo(12);
        assertThat(politica.emisor()).isEqualTo("Nexus");
        assertThat(new PoliticaDeSegundoFactor(null, 5, 10, " ").emisor()).isEqualTo("Nexus Battles VI");
    }

    @Test
    @DisplayName("cifras imposibles son un error de configuracion que se ve al arrancar")
    void cifrasImposibles() {
        assertThatThrownBy(() -> new PoliticaDeSegundoFactor("", 0, 10, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeSegundoFactor("", 5, 0, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeSegundoFactor("", 5, 101, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
