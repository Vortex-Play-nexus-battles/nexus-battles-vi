package com.nexusbattles.plataforma.correo.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** El catalogo de correos y lo que cada uno no puede conservar. */
class PlantillaTest {

    private final PlantillaCorreoService plantillas = new PlantillaCorreoService();

    @Test
    void losDosCorreosConCodigoLoPierdenAlTerminar() {
        Map<String, Object> datos = Map.of("apodo", "ElGuerrero", "codigo", "482915", "minutosVigencia", 15);

        assertThat(Plantilla.RECUPERACION_CLAVE.sinDatosSensibles(datos))
                .containsOnlyKeys("apodo", "minutosVigencia");
        assertThat(Plantilla.CONFIRMACION_CUENTA.sinDatosSensibles(datos))
                .containsOnlyKeys("apodo", "minutosVigencia");
    }

    @Test
    void losDemasConservanSusDatos() {
        Map<String, Object> datos = Map.of("apodo", "ElGuerrero", "motivo", "Acoso");

        assertThat(Plantilla.SANCION.sinDatosSensibles(datos)).isEqualTo(datos);
        assertThat(Plantilla.SANCION.datosSensibles()).isEmpty();
        assertThat(Plantilla.BIENVENIDA.sinDatosSensibles(null)).isEmpty();
    }

    @Test
    void cadaPlantillaSeEncuentraPorSuNombreYTieneSuHtml() {
        for (Plantilla plantilla : Plantilla.values()) {
            assertThat(Plantilla.deNombre(plantilla.nombre())).contains(plantilla);
            assertThat(plantilla.ruta()).isEqualTo("email/" + plantilla.nombre());
            assertThat(getClass().getResourceAsStream("/templates/" + plantilla.ruta() + ".html"))
                    .as("falta la plantilla de %s", plantilla)
                    .isNotNull();
        }
        assertThat(Plantilla.deNombre("inventada")).isEmpty();
        assertThat(Plantilla.deNombre(null)).isEmpty();
    }

    @Test
    void elServicioDePlantillasSirveTodoElCatalogo() {
        // Si alguien anade una plantilla al catalogo sin su HTML, o la
        // renombra en un solo sitio, esto falla aqui y no en produccion.
        for (Plantilla plantilla : Plantilla.values()) {
            assertThat(plantillas.renderizar(plantilla.ruta(), Map.of("apodo", "Ana", "tipo", "ADVERTENCIA",
                            "motivo", "m", "saludo", "Ana")))
                    .contains("THE NEXUS BATTLES VI");
        }
    }
}
