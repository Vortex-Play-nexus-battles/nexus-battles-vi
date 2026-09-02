package com.nexusbattles.plataforma.correo.template;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-COR-001 / CA-03: "cuando el equipo de diseño actualiza el logotipo o el
 * pie de página, los siguientes correos reflejan automáticamente la nueva
 * apariencia".
 *
 * <p>Eso solo se cumple si la plantilla tiene una única fuente de verdad. Hay
 * dos archivos —el {@code .mjml} que se edita y el {@code .html} que Thymeleaf
 * usa en tiempo de ejecución— y el segundo se genera desde el primero con
 * {@code ./gradlew :services:plataforma:correo:compilarPlantillas}.
 *
 * <p>Esta prueba es la red de seguridad: falla si alguien edita el {@code .mjml}
 * y olvida regenerar, o si alguien edita a mano el {@code .html} generado. Sin
 * ella, el diseño y el correo real podrían divergir en silencio.
 */
class PlantillaSincronizadaTest {

    private static final String FUENTE = "/mjml/layout-corporativo.mjml";
    private static final String GENERADO = "/templates/email/layout-corporativo.html";
    private static final String HASHES = "/mjml/layout-corporativo.mjml.sha256";

    private static final String COMO_ARREGLAR =
            "\nEjecuta: ./gradlew :services:plataforma:correo:compilarPlantillas";

    @Test
    void laPlantillaGeneradaCorrespondeAlMjmlFuente() {
        Properties hashes = leerHashes();

        assertThat(sha256(leerRecurso(FUENTE)))
                .as("El .mjml cambió pero la plantilla no se regeneró." + COMO_ARREGLAR)
                .isEqualTo(hashes.getProperty("fuente"));

        assertThat(sha256(leerRecurso(GENERADO)))
                .as("La plantilla generada se editó a mano." + COMO_ARREGLAR)
                .isEqualTo(hashes.getProperty("generado"));
    }

    @Test
    void laPlantillaGeneradaConservaLoQueLaHaceUtil() {
        String generado = leerRecurso(GENERADO);

        assertThat(generado)
                .as("sin este marcador, ningún correo puede inyectar su contenido")
                .contains("layout:fragment=\"contenido\"");

        assertThat(generado)
                .as("el andamiaje condicional de Outlook es lo que sostiene CA-02")
                .contains("<!--[if mso | IE]>");

        assertThat(generado)
                .as("no debe filtrarse la ruta local de quien compiló")
                .doesNotContain("<!-- FILE:");
    }

    private static Properties leerHashes() {
        Properties propiedades = new Properties();
        try (InputStream entrada = PlantillaSincronizadaTest.class.getResourceAsStream(HASHES)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró " + HASHES + "." + COMO_ARREGLAR);
            }
            propiedades.load(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return propiedades;
    }

    private static String leerRecurso(String ruta) {
        try (InputStream entrada = PlantillaSincronizadaTest.class.getResourceAsStream(ruta)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró el recurso " + ruta);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Normaliza CRLF a LF: Git en Windows puede cambiarlos al hacer checkout. */
    private static String sha256(String contenido) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(contenido.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
