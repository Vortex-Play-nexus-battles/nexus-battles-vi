package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConfiguracionDeListaNegra · la politica sale de lista-negra.accion.*")
class ConfiguracionDeListaNegraTest {

    @Test
    @DisplayName("las claves con guiones son contextos; lo que no se configura es la tabla del contrato")
    void leeLaConfiguracion() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("lista-negra.accion.comentario", "BLOQUEAR")
                .withProperty("lista-negra.accion.nombre-equipo", "REVISION");

        PoliticaDeModeracion politica = new ConfiguracionDeListaNegra().politicaDeModeracion(entorno);

        assertThat(politica.siCoincide(ContextoDeTexto.COMENTARIO)).isEqualTo(AccionDeModeracion.BLOQUEAR);
        assertThat(politica.siCoincide(ContextoDeTexto.NOMBRE_EQUIPO)).isEqualTo(AccionDeModeracion.REVISION);
        assertThat(politica.siCoincide(ContextoDeTexto.APODO)).isEqualTo(AccionDeModeracion.RECHAZAR);
    }

    @Test
    @DisplayName("sin nada configurado, la politica del contrato")
    void sinConfiguracion() {
        PoliticaDeModeracion politica = new ConfiguracionDeListaNegra().politicaDeModeracion(new MockEnvironment());

        assertThat(politica.acciones()).isEqualTo(PoliticaDeModeracion.POR_OMISION);
    }

    @Test
    @DisplayName("una clave que no es un contexto no se ignora en silencio")
    void claveDesconocida() {
        assertThatThrownBy(() -> ConfiguracionDeListaNegra.porContexto(Map.of("foro", AccionDeModeracion.RECHAZAR)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lista-negra.accion.foro");
    }
}
