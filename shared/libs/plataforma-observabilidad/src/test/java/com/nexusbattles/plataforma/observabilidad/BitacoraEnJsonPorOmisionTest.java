package com.nexusbattles.plataforma.observabilidad;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regla 6: la bitacora sale en JSON sin que cada servicio tenga que pedirlo,
 * y sin pisar al que ya pidio otra cosa.
 */
@DisplayName("Regla 6: JSON por omision, y solo por omision")
class BitacoraEnJsonPorOmisionTest {

    private final BitacoraEnJsonPorOmision procesador = new BitacoraEnJsonPorOmision();

    @Test
    @DisplayName("un servicio que no dice nada sobre el formato acaba en JSON")
    void porOmisionJson() {
        MockEnvironment entorno = new MockEnvironment();

        procesador.postProcessEnvironment(entorno, null);

        assertThat(entorno.getProperty(BitacoraEnJsonPorOmision.PROPIEDAD))
                .as("los dieciocho servicios que no declaraban formato")
                .isEqualTo("ecs");
    }

    @Test
    @DisplayName("el servicio que YA declaro un formato conserva el suyo")
    void noPisaAlQueYaLoDijo() {
        // productos usa `logstash` y lo tiene escrito en su
        // application.properties desde antes. Una convencion compartida que
        // pisara eso no seria una convencion: seria un cambio a escondidas en
        // un servicio de otro equipo.
        MockEnvironment entorno = new MockEnvironment();
        entorno.getPropertySources().addFirst(new MapPropertySource(
                "el-servicio", Map.of(BitacoraEnJsonPorOmision.PROPIEDAD, "logstash")));

        procesador.postProcessEnvironment(entorno, null);

        assertThat(entorno.getProperty(BitacoraEnJsonPorOmision.PROPIEDAD)).isEqualTo("logstash");
    }

    @Test
    @DisplayName("una variable de entorno vacia devuelve el texto legible de siempre")
    void sePuedeApagarParaDesarrollo() {
        // Leer JSON a mano en una terminal es incomodo. Que se pueda apagar
        // con `LOGGING_STRUCTURED_FORMAT_CONSOLE=` es lo que evita que alguien
        // borre esta clase para poder trabajar.
        MockEnvironment entorno = new MockEnvironment();
        entorno.getPropertySources().addFirst(new MapPropertySource(
                "variables-de-entorno", Map.of(BitacoraEnJsonPorOmision.PROPIEDAD, "")));

        procesador.postProcessEnvironment(entorno, null);

        assertThat(entorno.getProperty(BitacoraEnJsonPorOmision.PROPIEDAD)).isEmpty();
    }

    @Test
    @DisplayName("la fuente se anade en la ultima posicion: es la de menor precedencia")
    void esLaDeMenorPrecedencia() {
        // Si se anadiera con addFirst, ganaria a la linea de comandos y a las
        // variables de entorno, y dejaria de ser un valor por omision para
        // pasar a ser una imposicion. Esta prueba fija esa diferencia.
        MockEnvironment entorno = new MockEnvironment();
        entorno.getPropertySources().addFirst(new MapPropertySource("otra", Map.of("x", "y")));

        procesador.postProcessEnvironment(entorno, null);

        assertThat(entorno.getPropertySources().precedenceOf(
                entorno.getPropertySources().get(BitacoraEnJsonPorOmision.FUENTE)))
                .isEqualTo(entorno.getPropertySources().size() - 1);
    }
}
