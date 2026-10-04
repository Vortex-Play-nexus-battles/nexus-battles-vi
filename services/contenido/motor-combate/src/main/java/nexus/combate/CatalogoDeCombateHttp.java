package nexus.combate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nexus.combate.reglas.AccionDelCatalogo;
import nexus.combate.reglas.CatalogoDeCombate;
import nexus.combate.reglas.Estadisticas;
import nexus.combate.reglas.FichaDeCombate;
import nexus.combate.reglas.Formula;
import nexus.combate.reglas.Nombres;
import nexus.combate.reglas.Reglamento;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El catalogo de heroes para el combate, por HTTP (heroes.yaml 1.2.0).
 *
 * <p>Junta dos respuestas del servicio de heroes:
 * <ul>
 *   <li>{@code GET /api/v1/heroes/{prototipo}/niveles/{nivel}}: las
 *       estadisticas ya escaladas por nivel (HU-HER-008), el tipo de heroe y
 *       la epica afin. El motor no reescribe la regla de escalado: la pide.</li>
 *   <li>{@code GET /api/v1/heroes/{prototipo}}: las TRES acciones de la
 *       Tabla 7 con su coste, su carga y su nivel de desbloqueo, para poder
 *       decir tambien por que una accion no esta disponible todavia.</li>
 * </ul>
 *
 * <p><b>Compatibilidad con un heroes anterior a 1.2.0</b>, que publicaba el
 * coste solo como texto («2 puntos de poder», «Todos los puntos de poder») y
 * sin nivel de desbloqueo: el coste se lee del texto y el nivel sale de la
 * posicion en la Tabla 7 (1, 4 y 8, regla RC-01). Asi un despliegue escalonado
 * no deja el combate sin acciones.
 *
 * <p><b>Cache.</b> La ficha de un prototipo en un nivel es un dato del
 * catalogo que no cambia entre un turno y el siguiente; se guarda
 * {@code vigencia} ({@code MOTOR_HEROES_CACHE_SEGUNDOS}, 300 s por omision; 0
 * la desactiva). Solo se guardan respuestas buenas: un fallo se reintenta en la
 * peticion siguiente.
 */
public final class CatalogoDeCombateHttp implements CatalogoDeCombate {

    /** RC-01: la primera accion desde el nivel 1, la segunda desde el 4, la tercera desde el 8. */
    private static final List<Integer> NIVELES_POR_POSICION = List.of(1, 4, 8);
    private static final Pattern PRIMER_NUMERO = Pattern.compile("(\\d+)");
    private static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(5);

    private final URI base;
    private final HttpClient http;
    private final Duration vigencia;
    private final Clock reloj;
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<String, Guardado<FichaDeCombate>> fichas = new ConcurrentHashMap<>();
    private final Map<String, Guardado<FichaJson>> fichasDePrototipo = new ConcurrentHashMap<>();

    public CatalogoDeCombateHttp(URI base, Duration vigencia) {
        this(base, HttpClient.newBuilder().connectTimeout(TIEMPO_MAXIMO).build(), vigencia, Clock.systemUTC());
    }

    CatalogoDeCombateHttp(URI base, HttpClient http, Duration vigencia, Clock reloj) {
        this.base = Objects.requireNonNull(base, "Hace falta la URL del servicio de heroes.");
        this.http = Objects.requireNonNull(http);
        this.vigencia = vigencia == null || vigencia.isNegative() ? Duration.ZERO : vigencia;
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public FichaDeCombate ficha(String prototipo, int nivel) {
        String clave = Nombres.normalizar(prototipo) + "#" + nivel;
        FichaDeCombate guardada = vigente(fichas, clave);
        if (guardada != null) {
            return guardada;
        }
        VistaJson vista = pedir(ruta(prototipo) + "/niveles/" + nivel, VistaJson.class, prototipo);
        FichaJson fichaDelPrototipo = fichaDelPrototipo(prototipo);
        FichaDeCombate ficha = new FichaDeCombate(
                fichaDelPrototipo.nombre() != null ? fichaDelPrototipo.nombre() : prototipo,
                vista.tipo(),
                vista.esSanador(),
                vista.nivel() > 0 ? vista.nivel() : nivel,
                estadisticas(vista.estadisticas(), prototipo),
                acciones(fichaDelPrototipo.acciones()),
                vista.epica() == null ? null : vista.epica().nombre());
        guardar(fichas, clave, ficha);
        return ficha;
    }

    private FichaJson fichaDelPrototipo(String prototipo) {
        String clave = Nombres.normalizar(prototipo);
        FichaJson guardada = vigente(fichasDePrototipo, clave);
        if (guardada != null) {
            return guardada;
        }
        FichaJson ficha = pedir(ruta(prototipo), FichaJson.class, prototipo);
        guardar(fichasDePrototipo, clave, ficha);
        return ficha;
    }

    // ------------------------------------------------------------------ HTTP

    /**
     * Ruta del prototipo, sin tildes (heroes busca sin distinguir tildes ni
     * mayusculas) y codificada: «Pícaro Veneno» viaja como {@code Picaro%20Veneno}.
     */
    private static String ruta(String prototipo) {
        String sinTildes = Normalizer.normalize(prototipo == null ? "" : prototipo.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return "/api/v1/heroes/" + URLEncoder.encode(sinTildes, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private <T> T pedir(String ruta, Class<T> tipo, String prototipo) {
        URI uri = base.resolve(ruta);
        HttpRequest peticion = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .timeout(TIEMPO_MAXIMO)
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ClienteHeroesException("No se pudo contactar al servicio de heroes para '" + prototipo + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClienteHeroesException("Consulta a heroes interrumpida para '" + prototipo + "'", e);
        }
        if (respuesta.statusCode() == 404) {
            throw new HeroeNoEncontradoException(prototipo, "no esta en el catalogo de heroes.");
        }
        if (respuesta.statusCode() != 200) {
            throw new ClienteHeroesException(
                    "Respuesta inesperada (" + respuesta.statusCode() + ") al consultar '" + prototipo + "'");
        }
        try {
            return json.readValue(respuesta.body(), tipo);
        } catch (IOException e) {
            throw new ClienteHeroesException("La respuesta de heroes para '" + prototipo + "' no se pudo interpretar", e);
        }
    }

    // --------------------------------------------------------------- traduccion

    private static Estadisticas estadisticas(EstadisticasJson e, String prototipo) {
        if (e == null) {
            throw new ClienteHeroesException("Heroes no publico las estadisticas de '" + prototipo + "'");
        }
        return new Estadisticas(e.poder(), e.vida(), e.defensa(),
                formula(e.ataqueDetalle()), formula(e.danoDetalle()), formula(e.sanarDetalle()));
    }

    private static Formula formula(FormulaJson f) {
        return f == null ? null : new Formula(f.base(), f.cantidadDados(), f.caras());
    }

    static List<AccionDelCatalogo> acciones(List<AccionJson> acciones) {
        if (acciones == null) {
            return List.of();
        }
        List<AccionDelCatalogo> lista = new ArrayList<>();
        for (int i = 0; i < acciones.size(); i++) {
            AccionJson a = acciones.get(i);
            boolean todoElPoder = Boolean.TRUE.equals(a.todoElPoder())
                    || (a.costoPoder() == null && a.costo() != null
                    && a.costo().toLowerCase(Locale.ROOT).contains("todos"));
            Integer costo = todoElPoder ? null : a.costoPoder() != null ? a.costoPoder() : costoDelTexto(a);
            int carga = a.turnosDeCarga() != null && a.turnosDeCarga() > 0
                    ? a.turnosDeCarga() : Reglamento.TURNOS_DE_CARGA_ACCION;
            int nivel = a.nivelRequerido() != null && a.nivelRequerido() > 0
                    ? a.nivelRequerido()
                    : NIVELES_POR_POSICION.get(Math.min(i, NIVELES_POR_POSICION.size() - 1));
            lista.add(new AccionDelCatalogo(a.nombre(), costo, todoElPoder, carga, nivel));
        }
        return lista;
    }

    private static Integer costoDelTexto(AccionJson a) {
        Matcher numero = PRIMER_NUMERO.matcher(a.costo() == null ? "" : a.costo());
        if (!numero.find()) {
            throw new ClienteHeroesException("La accion '" + a.nombre() + "' no trae un coste que se pueda leer.");
        }
        return Integer.parseInt(numero.group(1));
    }

    // -------------------------------------------------------------------- cache

    private record Guardado<T>(T valor, Instant venceEn) {
    }

    private <T> T vigente(Map<String, Guardado<T>> cache, String clave) {
        Guardado<T> guardado = cache.get(clave);
        if (guardado == null) {
            return null;
        }
        if (reloj.instant().isBefore(guardado.venceEn())) {
            return guardado.valor();
        }
        cache.remove(clave, guardado);
        return null;
    }

    private <T> void guardar(Map<String, Guardado<T>> cache, String clave, T valor) {
        if (!vigencia.isZero()) {
            cache.put(clave, new Guardado<>(valor, reloj.instant().plus(vigencia)));
        }
    }

    // --------------------------------------------------------------------- JSON

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VistaJson(String nombre, String tipo, boolean esSanador, int nivel, EstadisticasJson estadisticas,
                     EpicaJson epica) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FichaJson(String nombre, List<AccionJson> acciones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EstadisticasJson(int poder, int vida, int defensa, FormulaJson ataqueDetalle, FormulaJson danoDetalle,
                            FormulaJson sanarDetalle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FormulaJson(int base, int cantidadDados, int caras) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EpicaJson(String nombre) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccionJson(String nombre, String costo, Integer costoPoder, Boolean todoElPoder, Integer turnosDeCarga,
                      Integer nivelRequerido) {
    }
}
