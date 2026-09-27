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
    void elCorreoDeTorneoConservaLaReferenciaDelTorneo() {
        // La referencia no es un secreto: queda en la fila como evidencia de a
        // que torneo se referia el envio, tambien despues de terminar.
        Map<String, Object> datos = Map.of("apodo", "Ana", "asunto", "a", "mensaje", "m",
                "torneoId", "5b0f3c1e-8d2a-4c71-9e0b-2f6a7d4c9e11");

        assertThat(Plantilla.deNombre("torneo")).contains(Plantilla.TORNEO);
        assertThat(Plantilla.TORNEO.datosSensibles()).isEmpty();
        assertThat(Plantilla.TORNEO.sinDatosSensibles(datos)).isEqualTo(datos);
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
