package com.nexusbattles.plataforma.metricasplataforma.sistema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RFINAL-08 — en DEV, la pantalla «Sistema» pregunta a cada servicio donde de
 * verdad vive.
 *
 * <h2>El defecto que esta prueba impide que vuelva</h2>
 *
 * El 28-sep ms-subastas se mudo al host de contenido (claseHost «contenido» en
 * el catalogo) y su sonda siguio en {@code srv-ms-subastas}, un nombre que en
 * plataforma ya no existe: la consola lo pinto CAIDO durante una semana
 * mientras las subastas cargaban. ms-chatbot paso a desplegarse el 29-sep y la
 * pantalla siguio diciendo NO_DESPLEGADO. Y los cinco de contenido salian NO
 * OBSERVABLES aunque su grupo de seguridad admite a este host.
 *
 * Aqui se cruza el bloque {@code srv-metricas-plataforma} de
 * {@code docker-compose.deploy.yml} con {@code infrastructure/despliegue/servicios.json}:
 * cada servicio del catalogo tiene su sonda, en su host (por nombre de
 * contenedor si es de plataforma, por la direccion del host de contenido si es
 * de contenido), en su puerto y en su ruta de salud. Mover un servicio de host
 * sin mover su sonda deja de pasar en silencio.
 */
@DisplayName("Sondas de la pantalla Sistema en el despliegue de DEV (RFINAL-08)")
class SondasDelDespliegueTest {

    private static final Path RAIZ = Path.of("../../..");
    private static final Path CATALOGO = RAIZ.resolve("infrastructure/despliegue/servicios.json");
    private static final Path COMPOSE = RAIZ.resolve("docker-compose.deploy.yml");
    private static final Path APLICACION = Path.of("src/main/resources/application.yml");

    private static final Pattern OBJETO_DE_SERVICIO = Pattern.compile("\\{[^{}]*\"nombre\"[^{}]*\\}");
    private static final Pattern ENTRADA_DE_APLICACION =
            Pattern.compile("^\\s{4}([a-z0-9-]+):\\s*\\$\\{(SISTEMA_SALUD_[A-Z_]+):.*\\}\\s*$");
    private static final Pattern VARIABLE_DEL_COMPOSE = Pattern.compile("^\\s+(SISTEMA_SALUD_[A-Z_]+):\\s*(.+?)\\s*$");
    private static final Pattern SOBRESCRIBIBLE = Pattern.compile("^\\$\\{([A-Z_]+):-(.+)\\}$");
    private static final Pattern URL = Pattern.compile("^http://([^:/]+):(\\d+)(/.*)$");

    record Servicio(String nombre, String puertoHost, String puertoContenedor, String rutaSalud, String claseHost) { }

    private static String campo(String objeto, String nombre) {
        Matcher m = Pattern.compile("\"" + nombre + "\"\\s*:\\s*\"([^\"]*)\"").matcher(objeto);
        if (!m.find()) {
            throw new IllegalStateException("el catalogo no declara " + nombre + " en " + objeto);
        }
        return m.group(1);
    }

    private static List<Servicio> catalogo() throws IOException {
        List<Servicio> servicios = new ArrayList<>();
        Matcher m = OBJETO_DE_SERVICIO.matcher(Files.readString(CATALOGO));
        while (m.find()) {
            String o = m.group();
            servicios.add(new Servicio(campo(o, "nombre"), campo(o, "puertoHost"), campo(o, "puertoContenedor"),
                    campo(o, "rutaSalud"), campo(o, "claseHost")));
        }
        return servicios;
    }

    /** servicio -> variable SISTEMA_SALUD_* con que lo lee application.yml. */
    private static Map<String, String> variablesDeLaAplicacion() throws IOException {
        Map<String, String> variables = new LinkedHashMap<>();
        boolean dentro = false;
        for (String linea : Files.readAllLines(APLICACION)) {
            if (linea.startsWith("sistema:")) {
                dentro = true;
                continue;
            }
            if (dentro && !linea.isBlank() && !linea.startsWith(" ")) {
                break;
            }
            Matcher m = ENTRADA_DE_APLICACION.matcher(linea);
            if (dentro && m.matches()) {
                variables.put(m.group(1), m.group(2));
            }
        }
        return variables;
    }

    /** variable -> valor tal cual, en el bloque de srv-metricas-plataforma. */
    private static Map<String, String> variablesDelCompose() throws IOException {
        Map<String, String> variables = new LinkedHashMap<>();
        boolean dentro = false;
        for (String linea : Files.readAllLines(COMPOSE)) {
            if (linea.equals("  srv-metricas-plataforma:")) {
                dentro = true;
                continue;
            }
            if (dentro && (linea.matches("^  [a-z].*") || linea.matches("^[a-z].*"))) {
                break;
            }
            Matcher m = VARIABLE_DEL_COMPOSE.matcher(linea);
            if (dentro && m.matches()) {
                variables.put(m.group(1), m.group(2));
            }
        }
        return variables;
    }

    @Test
    @DisplayName("cada servicio del catalogo tiene su sonda en el compose de DEV, sobrescribible por variable")
    void cadaServicioTieneSuSondaEnElCompose() throws IOException {
        Map<String, String> deLaAplicacion = variablesDeLaAplicacion();
        Map<String, String> delCompose = variablesDelCompose();

        List<String> problemas = new ArrayList<>();
        for (Servicio s : catalogo()) {
            String variable = deLaAplicacion.get(s.nombre());
            if (variable == null) {
                problemas.add(s.nombre() + ": application.yml no le da variable SISTEMA_SALUD_*");
                continue;
            }
            String valor = delCompose.get(variable);
            if (valor == null) {
                problemas.add(s.nombre() + ": falta " + variable + " en srv-metricas-plataforma");
                continue;
            }
            Matcher sobrescribible = SOBRESCRIBIBLE.matcher(valor);
            if (!sobrescribible.matches() || !sobrescribible.group(1).equals(variable)) {
                problemas.add(s.nombre() + ": " + variable + " no usa el patron ${" + variable + ":-valor}: " + valor);
            }
        }
        assertThat(problemas).isEmpty();
    }

    @Test
    @DisplayName("cada sonda apunta al host, puerto y ruta de salud que dice el catalogo")
    void cadaSondaApuntaADondeViveElServicio() throws IOException {
        Map<String, String> deLaAplicacion = variablesDeLaAplicacion();
        Map<String, String> delCompose = variablesDelCompose();

        List<String> problemas = new ArrayList<>();
        Set<String> hostsDeContenido = new LinkedHashSet<>();
        for (Servicio s : catalogo()) {
            String valor = delCompose.get(deLaAplicacion.get(s.nombre()));
            Matcher sobrescribible = valor == null ? null : SOBRESCRIBIBLE.matcher(valor);
            if (sobrescribible == null || !sobrescribible.matches()) {
                continue; // lo reporta la otra prueba
            }
            String url = sobrescribible.group(2);
            Matcher partes = URL.matcher(url);
            if (!partes.matches()) {
                problemas.add(s.nombre() + ": no es una URL http con puerto: " + url);
                continue;
            }
            String host = partes.group(1);
            String puerto = partes.group(2);
            String ruta = partes.group(3);
            if (!ruta.equals(s.rutaSalud())) {
                problemas.add(s.nombre() + ": ruta " + ruta + " y el catalogo dice " + s.rutaSalud());
            }
            if ("metricas-plataforma".equals(s.nombre())) {
                // El unico que se pregunta a si mismo: dentro de su contenedor,
                // localhost SI es el.
                if (!"localhost".equals(host) || !puerto.equals(s.puertoContenedor())) {
                    problemas.add(s.nombre() + ": deberia ser localhost:" + s.puertoContenedor() + " y es " + url);
                }
            } else if ("plataforma".equals(s.claseHost())) {
                if (!("srv-" + s.nombre()).equals(host) || !puerto.equals(s.puertoContenedor())) {
                    problemas.add(s.nombre() + ": vive en este host; se le pregunta por srv-" + s.nombre() + ":"
                            + s.puertoContenedor() + " y no por " + host + ":" + puerto);
                }
            } else {
                if (host.startsWith("srv-") || "localhost".equals(host)) {
                    problemas.add(s.nombre() + ": vive en el host de contenido; " + host
                            + " es un nombre de esta red, no ese host");
                }
                if (!puerto.equals(s.puertoHost())) {
                    problemas.add(s.nombre() + ": el host de contenido lo publica en " + s.puertoHost()
                            + " y la sonda usa " + puerto);
                }
                hostsDeContenido.add(host);
            }
        }
        assertThat(problemas).isEmpty();
        assertThat(hostsDeContenido)
                .as("todos los de contenido viven en el mismo host")
                .hasSizeLessThanOrEqualTo(1);
    }
}
