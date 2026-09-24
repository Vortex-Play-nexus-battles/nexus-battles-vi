package com.nexusbattles.plataforma.metricasplataforma.sistema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las sondas de la pantalla "Sistema" apuntan a algo alcanzable.
 *
 * ## El defecto que esta prueba impide que vuelva
 *
 * Las diecisiete URL nacieron apuntando a {@code http://localhost:<puerto>}.
 * Dentro de un contenedor, localhost es el propio servicio: la pantalla que
 * dice si el sistema esta sano reportaba los diecisiete como CAIDO, con
 * "Connection refused", mientras el sistema funcionaba. Comprobado contra dev
 * el 2026-09-24.
 *
 * Ninguna prueba lo podia ver, porque todas las que habia usaban una sonda de
 * mentira: el valor de la configuracion nunca se miraba. Esta prueba mira el
 * valor.
 *
 * La unica excepcion legitima es el propio servicio: para metricas-plataforma,
 * localhost SI es donde tiene que preguntar.
 */
class ConfiguracionDelSistemaTest {

    private static final Path YAML = Path.of("src/main/resources/application.yml");
    private static final Pattern ENTRADA =
            Pattern.compile("^\\s{4}([a-z0-9-]+):\\s*\\$\\{SISTEMA_SALUD_[A-Z_]+:(.*)\\}\\s*$");

    private static List<String[]> entradas() throws IOException {
        List<String[]> encontradas = new ArrayList<>();
        boolean dentro = false;
        for (String linea : Files.readAllLines(YAML)) {
            if (linea.startsWith("sistema:")) {
                dentro = true;
                continue;
            }
            if (dentro && !linea.isBlank() && !linea.startsWith(" ")) {
                break;
            }
            if (!dentro) {
                continue;
            }
            Matcher m = ENTRADA.matcher(linea);
            if (m.matches()) {
                encontradas.add(new String[] {m.group(1), m.group(2).trim()});
            }
        }
        return encontradas;
    }

    /**
     * Ningun servicio del catalogo se queda fuera de la pantalla.
     *
     * Se compara contra el catalogo y no contra un numero escrito a mano: el
     * dia que alguien anada un servicio, esta prueba lo pide aqui en vez de
     * dejarlo invisible en la unica pantalla que dice que hay encendido.
     */
    @Test
    void todoServicioDelCatalogoTieneSonda() throws IOException {
        Path catalogo = Path.of("../../../infrastructure/despliegue/servicios.json");
        List<String> delCatalogo = new ArrayList<>();
        Matcher m = Pattern.compile("\"nombre\"\\s*:\\s*\"([^\"]+)\"").matcher(Files.readString(catalogo));
        while (m.find()) {
            delCatalogo.add(m.group(1));
        }
        List<String> configurados = entradas().stream().map(e -> e[0]).toList();

        List<String> faltan = delCatalogo.stream().filter(n -> !configurados.contains(n)).toList();
        assertTrue(faltan.isEmpty(), "servicios del catalogo sin sonda en la pantalla: " + faltan);
        assertEquals(
                delCatalogo.size(),
                configurados.size(),
                "hay sondas de servicios que no estan en el catalogo: " + configurados);
    }

    /** El defecto exacto: ningun servicio ajeno se sondea en localhost. */
    @Test
    void ningunServicioAjenoSeSondeaEnLocalhost() throws IOException {
        List<String> culpables = new ArrayList<>();
        for (String[] entrada : entradas()) {
            String servicio = entrada[0];
            String url = entrada[1];
            if (url.contains("localhost") && !"metricas-plataforma".equals(servicio)) {
                culpables.add(servicio + " -> " + url);
            }
        }
        assertTrue(
                culpables.isEmpty(),
                "dentro de un contenedor localhost es este mismo servicio; usa el nombre de la"
                        + " red de despliegue (srv-<servicio>). Culpables: "
                        + culpables);
    }

    @Test
    void cadaValorEsUnaUrlHttpOUnaPalabraReservada() throws IOException {
        List<String> raros = new ArrayList<>();
        for (String[] entrada : entradas()) {
            String url = entrada[1];
            boolean valido =
                    url.startsWith("http://")
                            || url.startsWith("https://")
                            || ConfiguracionDelSistema.NO_DESPLEGADO.equals(url)
                            || ConfiguracionDelSistema.NO_OBSERVABLE.equals(url);
            if (!valido) {
                raros.add(entrada[0] + " -> " + url);
            }
        }
        assertTrue(raros.isEmpty(), "valores que no son URL ni palabra reservada: " + raros);
    }

    /** Una URL de sonda que no termina en una ruta de salud no comprueba salud. */
    @Test
    void cadaUrlDeSondaApuntaAUnaRutaDeSalud() throws IOException {
        List<String> raras = new ArrayList<>();
        for (String[] entrada : entradas()) {
            String url = entrada[1];
            if (url.startsWith("http") && !url.endsWith("/actuator/health")) {
                raras.add(entrada[0] + " -> " + url);
            }
        }
        assertTrue(raras.isEmpty(), "sondas que no apuntan a /actuator/health: " + raras);
    }
}