package com.nexusbattles.ms_identidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toda propiedad sin valor por defecto esta declarada en el archivo base.
 *
 * <p><b>El defecto que esto cierra, dos veces.</b> {@code AuditoriaClient} leia
 * {@code app.auditoria.url} con {@code @Value} sin defecto, y la propiedad solo
 * estaba en {@code application-dev.properties}. Con
 * {@code SPRING_PROFILES_ACTIVE=prod} el contexto moria al construir el bean,
 * antes de atender una peticion. Se corrigio moviendola al archivo base... y
 * {@code app.notificaciones.url} tenia exactamente el mismo problema y
 * sobrevivio a esa correccion, porque nadie estaba comprobando la regla, solo
 * el caso.
 *
 * <p><b>Por que ninguna prueba lo veia.</b> Todas corren con
 * {@code @ActiveProfiles("test")} y {@code application-test.properties} define
 * esas propiedades a mano. El perfil {@code prod} —el que se despliega— no lo
 * carga ninguna.
 *
 * <p>Asi que esta prueba no comprueba un caso: comprueba la regla. Lee el
 * codigo fuente, saca cada {@code @Value("${clave}")} sin defecto y exige que
 * la clave este en {@code application.properties}, que es el unico archivo que
 * vale para los tres perfiles. No necesita levantar el contexto ni una base de
 * datos, y falla en cuanto alguien anada la tercera.
 */
@DisplayName("Ninguna propiedad obligatoria depende de un perfil concreto")
class PropiedadesSinDefectoTest {

    /** {@code @Value("${clave}")} o {@code @Value("${clave}") String x} — sin `:`. */
    private static final Pattern SIN_DEFECTO =
            Pattern.compile("@Value\\s*\\(\\s*\"\\$\\{([^:}\"]+)}\"");

    private static final Path FUENTES = Path.of("src", "main", "java");

    @Test
    @DisplayName("cada @Value sin defecto tiene su clave en application.properties")
    void todaClaveObligatoriaEstaEnElArchivoBase() throws IOException {
        Properties base = archivoBase();
        List<String> huerfanas = new ArrayList<>();

        try (Stream<Path> archivos = Files.walk(FUENTES)) {
            archivos.filter(p -> p.toString().endsWith(".java")).forEach(archivo -> {
                String codigo = leer(archivo);
                Matcher encontrado = SIN_DEFECTO.matcher(codigo);
                while (encontrado.find()) {
                    String clave = encontrado.group(1).trim();
                    if (!base.containsKey(clave)) {
                        huerfanas.add(clave + "  (" + FUENTES.relativize(archivo) + ")");
                    }
                }
            });
        }

        assertTrue(huerfanas.isEmpty(),
                "Estas propiedades se leen sin valor por defecto y no estan en "
                        + "application.properties, asi que el servicio no arranca en "
                        + "ningun perfil que no las declare:\n  "
                        + String.join("\n  ", huerfanas)
                        + "\n\nO se declaran ahi con un defecto sensato, o el @Value "
                        + "lleva el suyo: ${clave:valor}.");
    }

    /**
     * Se lee el archivo del disco, NO del classpath, y a proposito.
     *
     * <p>En el classpath de prueba manda {@code src/test/resources}, cuyo
     * {@code application.properties} define estas mismas claves a mano — que es
     * justo lo que venia tapando el defecto. Leer de ahi hacia que esta prueba
     * pasara incluso con la propiedad borrada del archivo real; se comprobo.
     * El unico archivo que cuenta es el que viaja en el jar.
     */
    private static Properties archivoBase() throws IOException {
        Properties propiedades = new Properties();
        propiedades.load(Files.newBufferedReader(
                Path.of("src", "main", "resources", "application.properties"),
                StandardCharsets.UTF_8));
        return propiedades;
    }

    private static String leer(Path archivo) {
        try {
            return Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + archivo, e);
        }
    }
}
