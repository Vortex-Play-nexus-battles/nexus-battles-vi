package nexus.misiones;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las siete dependencias de misiones en un solo servidor HTTP de verdad
 * (JDK HttpServer), para que los clientes REST se prueben de punta a punta:
 * serializacion, cabeceras (credencial, traza, Idempotency-Key), codigos de
 * estado y tiempos de espera. Cada una responde con la forma de su contrato:
 * inventario.yaml 1.6.0, productos.yaml, heroes.yaml 1.2.0,
 * motor-combate.yaml 1.2.0, creditos.yaml 1.4.0, correo.yaml 1.4.0 y
 * ms-identidad-admin.yaml.
 *
 * <p>Rutas: inventario, productos, heroes y motor cuelgan de la raiz; el libro
 * de creditos de {@code /finanzas/api/v1}, correo de {@code /correo/api/v1},
 * identidad de {@code /identidad} y el emisor de credenciales de
 * {@code /auth/token}.
 */
public final class DependenciasFalsas implements AutoCloseable {

    public static final String TOKEN_DE_SERVICIO = "token-de-servicio-de-misiones";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public record Peticion(String metodo, String ruta, String consulta, Map<String, String> cabeceras,
                           String cuerpo) {

        public Map<String, Object> json() {
            return cuerpo == null || cuerpo.isBlank() ? Map.of() : JSON.readValue(cuerpo, Map.class);
        }

        public String cabecera(String nombre) {
            return cabeceras.get(nombre.toLowerCase());
        }
    }

    /** Un heroe del inventario falso. */
    public static final class Heroe {
        public final String id;
        public final String propietario;
        public final String productoId;
        public int nivel = 1;
        public double experiencia = 0;
        public String bloqueadoPor;
        public String subastaId;
        public boolean equipado = true;
        /** Ids de los elementos que lleva puestos. */
        public List<String> armas = List.of("arma-1");
        public Map<String, String> armaduras = Map.of();

        Heroe(String id, String propietario, String productoId) {
            this.id = id;
            this.propietario = propietario;
            this.productoId = productoId;
        }
    }

    private final HttpServer servidor;
    public final List<Peticion> recibidas = Collections.synchronizedList(new ArrayList<>());
    public final Map<String, Heroe> heroes = new ConcurrentHashMap<>();
    public final Map<String, String> prototipos = new ConcurrentHashMap<>();
    /** Los elementos de la vitrina del jugador: id, productoId, tipo, disponible. */
    public final List<Map<String, Object>> vitrina = Collections.synchronizedList(new ArrayList<>());
    /** Nombre de los productos que no son heroes (armas, armaduras, items, epicas). */
    public final Map<String, String> nombresDeProducto = new ConcurrentHashMap<>();
    /** Si es verdadero, el inventario no publica las formulas de las estadisticas. */
    public volatile boolean estadisticasSinFormulas;
    /** Cuantos elementos devuelve cada pagina de la vitrina. */
    public static final int ELEMENTOS_POR_PAGINA = 2;
    /** Dependencias que responden 500 («caidas»). */
    public final Set<String> caidas = Collections.synchronizedSet(new HashSet<>());
    /** Contacto que da identidad; nulo = 404. */
    public volatile String correoDelJugador = "jugador@ejemplo.com";

    public DependenciasFalsas() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public String base() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public Heroe conHeroe(String id, String propietario, String productoId, String prototipo) {
        Heroe heroe = new Heroe(id, propietario, productoId);
        heroes.put(id, heroe);
        prototipos.put(productoId, prototipo);
        conElemento(id, productoId, "HEROE", true);
        return heroe;
    }

    /** Anade un elemento a la vitrina del jugador; el nombre del producto es el del catalogo. */
    public void conElemento(String id, String productoId, String tipo, boolean disponible) {
        Map<String, Object> elemento = new LinkedHashMap<>();
        elemento.put("id", id);
        elemento.put("productoId", productoId);
        elemento.put("tipo", tipo);
        elemento.put("disponible", disponible);
        vitrina.add(elemento);
    }

    public List<Peticion> a(String metodo, String prefijo) {
        synchronized (recibidas) {
            return recibidas.stream()
                    .filter(p -> p.metodo().equals(metodo) && p.ruta().startsWith(prefijo))
                    .toList();
        }
    }

    /**
     * Las peticiones de una ruta que mencionan {@code texto} en la ruta, el
     * cuerpo o una cabecera. El trabajo en segundo plano atiende todas las
     * ejecuciones de la base, tambien las de otras pruebas: se filtra por la
     * ejecucion o el jugador de cada una.
     */
    public List<Peticion> con(String metodo, String prefijo, String texto) {
        return a(metodo, prefijo).stream()
                .filter(p -> p.ruta().contains(texto) || p.cuerpo().contains(texto)
                        || p.cabeceras().values().stream().anyMatch(v -> v.contains(texto)))
                .toList();
    }

    /** Todas las peticiones que mencionan {@code texto}, de cualquier ruta. */
    public long cuantasCon(String texto) {
        synchronized (recibidas) {
            return recibidas.stream()
                    .filter(p -> p.ruta().contains(texto) || p.cuerpo().contains(texto)
                            || p.cabeceras().values().stream().anyMatch(v -> v.contains(texto)))
                    .count();
        }
    }

    public void reiniciar() {
        recibidas.clear();
        heroes.clear();
        prototipos.clear();
        vitrina.clear();
        nombresDeProducto.clear();
        estadisticasSinFormulas = false;
        caidas.clear();
        correoDelJugador = "jugador@ejemplo.com";
    }

    @Override
    public void close() {
        servidor.stop(0);
    }

    // ---------------------------------------------------------------- rutas

    private static final Pattern ELEMENTO = Pattern.compile("^/api/v1/inventario/elementos/([^/]+)$");
    private static final Pattern BLOQUEO = Pattern.compile("^/api/v1/inventario/elementos/([^/]+)/bloqueo-mision$");
    private static final Pattern LIBERACION =
            Pattern.compile("^/api/v1/inventario/elementos/([^/]+)/bloqueo-mision/([^/]+)/liberacion$");
    private static final Pattern EQUIPAMIENTO = Pattern.compile("^/api/v1/inventario/heroes/([^/]+)/equipamiento$");
    private static final Pattern ESTADISTICAS = Pattern.compile("^/api/v1/inventario/heroes/([^/]+)/estadisticas$");
    private static final Pattern PRODUCTO = Pattern.compile("^/api/v1/productos/([^/]+)$");
    private static final Pattern VISTA = Pattern.compile("^/api/v1/heroes/([^/]+)/niveles/(\\d+)$");
    private static final Pattern EXPERIENCIA = Pattern.compile("^/api/v1/progresion/experiencia-por-enemigo/(\\d+)$");
    private static final Pattern CONTACTO = Pattern.compile("^/identidad/api/v1/internal/usuarios/([^/]+)/contacto$");

    private void atender(HttpExchange intercambio) throws IOException {
        String metodo = intercambio.getRequestMethod();
        String ruta = intercambio.getRequestURI().getPath();
        Map<String, String> cabeceras = new LinkedHashMap<>();
        intercambio.getRequestHeaders().forEach((nombre, valores) ->
                cabeceras.put(nombre.toLowerCase(), String.join(",", valores)));
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Peticion peticion = new Peticion(metodo, ruta, intercambio.getRequestURI().getQuery(), cabeceras, cuerpo);
        recibidas.add(peticion);
        try {
            Respuesta respuesta = responder(peticion);
            byte[] bytes = respuesta.cuerpo == null ? new byte[0]
                    : respuesta.cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", respuesta.tipo);
            intercambio.sendResponseHeaders(respuesta.estado, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(bytes);
                }
            }
        } finally {
            intercambio.close();
        }
    }

    private record Respuesta(int estado, String tipo, String cuerpo) {
    }

    private static Respuesta json(int estado, Object cuerpo) {
        return new Respuesta(estado, "application/json", JSON.writeValueAsString(cuerpo));
    }

    private static Respuesta problema(int estado, String detalle) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", "Problema");
        p.put("status", estado);
        p.put("detail", detalle);
        return new Respuesta(estado, "application/problem+json", JSON.writeValueAsString(p));
    }

    private boolean caida(String dependencia) {
        return caidas.contains(dependencia);
    }

    private Respuesta responder(Peticion p) {
        String ruta = p.ruta();
        if (ruta.equals("/auth/token")) {
            return json(200, Map.of("access_token", TOKEN_DE_SERVICIO, "token_type", "Bearer", "expires_in", 3600));
        }
        if (ruta.startsWith("/api/v1/inventario")) {
            // «liberacion» tumba solo la liberacion del heroe: la simulacion ya necesita al inventario
            // (estadisticas, equipo y epicas del heroe), asi que «inventario» tumba tambien la simulacion.
            boolean caeLaLiberacion = caida("liberacion") && ruta.endsWith("/liberacion");
            return (caida("inventario") && !ruta.endsWith("/entregas")) || caeLaLiberacion
                    ? problema(500, "caido") : inventario(p);
        }
        if (ruta.startsWith("/api/v1/productos")) {
            if (caida("productos")) {
                return problema(500, "caido");
            }
            Matcher m = PRODUCTO.matcher(ruta);
            if (m.matches() && nombresDeProducto.containsKey(m.group(1))) {
                Map<String, Object> producto = new LinkedHashMap<>();
                producto.put("id", m.group(1));
                producto.put("tipo", "ARMA");
                producto.put("nombre", nombresDeProducto.get(m.group(1)));
                producto.put("estado", "ACTIVO");
                return json(200, producto);
            }
            if (m.matches() && prototipos.containsKey(m.group(1))) {
                Map<String, Object> producto = new LinkedHashMap<>();
                producto.put("id", m.group(1));
                producto.put("tipo", "HEROE");
                producto.put("nombre", prototipos.get(m.group(1)));
                producto.put("prototipo", prototipos.get(m.group(1)));
                producto.put("estado", "ACTIVO");
                return json(200, producto);
            }
            return problema(404, "No existe ningun producto con ese identificador");
        }
        if (ruta.startsWith("/api/v1/heroes") || ruta.startsWith("/api/v1/estrategias")
                || ruta.startsWith("/api/v1/equipos") || ruta.startsWith("/api/v1/progresion")) {
            return caida("heroes") ? problema(500, "caido") : heroes(p);
        }
        if (ruta.equals("/api/v1/combate/turnos") || ruta.equals("/api/v1/combate/acciones")) {
            return caida("motor") ? problema(500, "caido") : motor(p);
        }
        if (ruta.equals("/finanzas/api/v1/creditos/acreditar")) {
            if (caida("finanzas")) {
                return problema(500, "caido");
            }
            Map<String, Object> cuerpo = p.json();
            return json(200, Map.of("transaccionId", "TX-ACR-0A1B2C3D", "refId", cuerpo.get("refId"),
                    "estado", "APLICADO", "montoAcreditado", cuerpo.get("monto"), "nuevoSaldoDisponible", 100));
        }
        if (ruta.equals("/correo/api/v1/correos/mision")) {
            return caida("correo") ? problema(500, "caido") : new Respuesta(202, "application/json", null);
        }
        Matcher contacto = CONTACTO.matcher(ruta);
        if (contacto.matches()) {
            if (caida("identidad")) {
                return problema(500, "caido");
            }
            if (correoDelJugador == null) {
                return problema(404, "No existe una cuenta con ese uid.");
            }
            return json(200, Map.of("uid", contacto.group(1), "email", correoDelJugador, "apodo", "lyra",
                    "estado", "ACTIVO"));
        }
        return problema(404, "Ruta desconocida en el servidor falso: " + ruta);
    }

    private Respuesta inventario(Peticion p) {
        String ruta = p.ruta();
        Matcher m;
        if ((m = LIBERACION.matcher(ruta)).matches() && p.metodo().equals("POST")) {
            Heroe heroe = heroes.get(m.group(1));
            if (heroe == null) {
                return problema(404, "El heroe o elemento no existe");
            }
            if (heroe.bloqueadoPor == null) {
                return json(200, elemento(heroe));
            }
            if (!heroe.bloqueadoPor.equals(m.group(2))) {
                return problema(409, "El heroe esta bloqueado por otra ejecucion de mision");
            }
            double puntos = ((Number) p.json().getOrDefault("experiencia", 0)).doubleValue();
            heroe.experiencia += puntos;
            double necesaria = 100 * Math.pow(1.2, heroe.nivel - 1);
            while (heroe.nivel < 8 && heroe.experiencia >= necesaria) {
                heroe.experiencia -= necesaria;
                heroe.nivel++;
                necesaria = 100 * Math.pow(1.2, heroe.nivel - 1);
            }
            heroe.bloqueadoPor = null;
            return json(200, elemento(heroe));
        }
        if ((m = BLOQUEO.matcher(ruta)).matches() && p.metodo().equals("PUT")) {
            Heroe heroe = heroes.get(m.group(1));
            if (heroe == null) {
                return problema(404, "El heroe o elemento no existe");
            }
            String ejecucion = String.valueOf(p.json().get("ejecucionId"));
            if (heroe.subastaId != null) {
                return problema(409, "El heroe esta bloqueado por una subasta vigente");
            }
            if (heroe.bloqueadoPor != null && !heroe.bloqueadoPor.equals(ejecucion)) {
                return problema(409, "El heroe ya esta en otra mision");
            }
            heroe.bloqueadoPor = ejecucion;
            return json(200, elemento(heroe));
        }
        if ((m = ELEMENTO.matcher(ruta)).matches() && p.metodo().equals("GET")) {
            Heroe heroe = heroes.get(m.group(1));
            if (heroe == null) {
                return problema(404, "El heroe o elemento no existe");
            }
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("elementoId", heroe.id);
            detalle.put("productoId", heroe.productoId);
            detalle.put("propietarioUid", heroe.propietario);
            detalle.put("enUso", false);
            detalle.put("disponible", heroe.bloqueadoPor == null && heroe.subastaId == null);
            detalle.put("subastaId", heroe.subastaId);
            detalle.put("tipo", "HEROE");
            detalle.put("nombrePropio", "Vorn");
            detalle.put("nivel", heroe.nivel);
            detalle.put("experiencia", heroe.experiencia);
            detalle.put("ejecucionMisionId", heroe.bloqueadoPor);
            return json(200, detalle);
        }
        if (ruta.equals("/api/v1/inventario/elementos") && p.metodo().equals("GET")) {
            int pagina = 0;
            if (p.consulta() != null && p.consulta().contains("pagina=")) {
                pagina = Integer.parseInt(p.consulta().replaceAll(".*pagina=(\\d+).*", "$1"));
            }
            List<Map<String, Object>> todos;
            synchronized (vitrina) {
                todos = new ArrayList<>(vitrina);
            }
            int desde = Math.min(todos.size(), pagina * ELEMENTOS_POR_PAGINA);
            int hasta = Math.min(todos.size(), desde + ELEMENTOS_POR_PAGINA);
            int paginas = Math.max(1, (todos.size() + ELEMENTOS_POR_PAGINA - 1) / ELEMENTOS_POR_PAGINA);
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("elementos", todos.subList(desde, hasta));
            cuerpo.put("pagina", pagina);
            cuerpo.put("totalPaginas", paginas);
            cuerpo.put("ultima", pagina + 1 >= paginas);
            return json(200, cuerpo);
        }
        if ((m = EQUIPAMIENTO.matcher(ruta)).matches()) {
            Heroe heroe = heroes.get(m.group(1));
            if (heroe == null || !heroe.propietario.equals(p.cabecera("X-User-Name"))) {
                return problema(heroe == null ? 404 : 403, "No es tuyo");
            }
            return json(200, Map.of("heroeId", heroe.id, "armas", heroe.equipado ? heroe.armas : List.of(),
                    "armaduras", heroe.armaduras, "items", List.of()));
        }
        if ((m = ESTADISTICAS.matcher(ruta)).matches()) {
            Heroe heroe = heroes.get(m.group(1));
            if (heroe == null || !heroe.propietario.equals(p.cabecera("X-User-Name"))) {
                return problema(heroe == null ? 404 : 403, "No es tuyo");
            }
            Map<String, Object> estadisticas = new LinkedHashMap<>();
            estadisticas.put("heroeId", heroe.id);
            estadisticas.put("nivel", heroe.nivel);
            estadisticas.put("poder", 10);
            estadisticas.put("vida", 44);
            estadisticas.put("defensa", 11);
            if (!estadisticasSinFormulas) {
                estadisticas.put("ataque", Map.of("base", 11, "cantidadDados", 1, "caras", 6, "formula", "11 + 1d6"));
                estadisticas.put("dano", Map.of("base", 3, "cantidadDados", 1, "caras", 4, "formula", "3 + 1d4"));
                estadisticas.put("sanar", null);
            }
            return json(200, estadisticas);
        }
        if (ruta.equals("/api/v1/inventario/entregas") && p.metodo().equals("POST")) {
            if (caida("entregas")) {
                return problema(500, "caido");
            }
            Map<String, Object> cuerpo = p.json();
            return json(201, Map.of("id", "entrega-1", "uid", cuerpo.get("uid"), "origen", cuerpo.get("origen"),
                    "referencia", cuerpo.get("referencia"), "elementos", List.of(),
                    "entregadaEn", "2026-09-25T12:00:00Z"));
        }
        return problema(404, "Ruta de inventario desconocida: " + ruta);
    }

    private static Map<String, Object> elemento(Heroe heroe) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", heroe.id);
        e.put("productoId", heroe.productoId);
        e.put("tipo", "HEROE");
        e.put("nombrePropio", "Vorn");
        e.put("disponible", heroe.bloqueadoPor == null && heroe.subastaId == null);
        e.put("subastaId", heroe.subastaId);
        e.put("nivel", heroe.nivel);
        e.put("experiencia", heroe.experiencia);
        e.put("ejecucionMisionId", heroe.bloqueadoPor);
        return e;
    }

    @SuppressWarnings("unchecked")
    private Respuesta heroes(Peticion p) {
        String ruta = p.ruta();
        Matcher m;
        if (ruta.equals("/api/v1/estrategias/validacion")) {
            Map<String, Object> cuerpo = p.json();
            List<Map<String, Object>> rotaciones = (List<Map<String, Object>>) cuerpo.getOrDefault("rotaciones",
                    List.of());
            boolean conVulcano = rotaciones.stream()
                    .anyMatch(r -> ((List<String>) r.get("pasos")).contains("Vulcano"));
            Map<String, Object> veredicto = new LinkedHashMap<>();
            veredicto.put("valida", !conVulcano);
            veredicto.put("heroe", cuerpo.get("heroe"));
            veredicto.put("nivel", cuerpo.getOrDefault("nivel", 1));
            veredicto.put("habilidadesValidas", List.of("Embate sangriento", "Ataque básico"));
            veredicto.put("porDefecto", rotaciones.isEmpty());
            veredicto.put("comportamientoPorDefecto", "Ataque básico");
            if (conVulcano) {
                veredicto.put("motivo", "La rotación 1 usa una habilidad que el héroe no posee: Vulcano.");
            } else {
                List<Map<String, Object>> validadas = new ArrayList<>();
                String[] prioridades = {"Alta", "Media", "Baja"};
                for (int i = 0; i < rotaciones.size(); i++) {
                    validadas.add(Map.of("prioridad", prioridades[i], "pasos", rotaciones.get(i).get("pasos")));
                }
                veredicto.put("rotaciones", validadas);
            }
            return json(200, veredicto);
        }
        if (ruta.equals("/api/v1/equipos/validacion")) {
            List<String> equipo = (List<String>) p.json().get("heroes");
            boolean sanador = equipo.stream().anyMatch(h -> h.equals("Chamán") || h.equals("Médico"));
            Map<String, Object> veredicto = new LinkedHashMap<>();
            veredicto.put("valida", !sanador);
            veredicto.put("sanadores", sanador ? 1 : 0);
            veredicto.put("individual", equipo.size() == 1);
            if (sanador) {
                veredicto.put("motivo", "Un sanador no combate solo: los sanadores solo participan en equipos.");
            }
            return json(200, veredicto);
        }
        if (ruta.equals("/api/v1/estrategias/decision")) {
            return json(200, Map.of("heroe", "Guerrero Armas", "nivel", 1, "turno", 1,
                    "accion", "Embate sangriento", "rotacion", 1, "costoDePoder", 0,
                    "evaluaciones", List.of(), "cursoresSiguientes", List.of(0)));
        }
        if ((m = VISTA.matcher(ruta)).matches()) {
            return json(200, Map.of("nombre", m.group(1), "tipo", "Guerrero", "esSanador", false,
                    "nivel", Integer.parseInt(m.group(2)),
                    "estadisticas", Map.of("poder", 10, "vida", 20, "defensa", 5,
                            "ataqueDetalle", Map.of("base", 9, "cantidadDados", 1, "caras", 6),
                            "danoDetalle", Map.of("base", 1, "cantidadDados", 1, "caras", 4)),
                    "accionesDisponibles", List.of(), "multiplicadorDeEfecto", 1,
                    "epica", Map.of("nombre", "Golpe de defensa")));
        }
        if ((m = EXPERIENCIA.matcher(ruta)).matches()) {
            int dado = Integer.parseInt(m.group(1));
            return json(200, Map.of("dado", dado, "puntos", 10 * Math.pow(1.2, dado)));
        }
        return problema(404, "Ruta de heroes desconocida: " + ruta);
    }

    /**
     * El motor falso (motor-combate.yaml 1.2.0): el heroe pega 60 y los rivales
     * 1, para que la mision se gane en pocas rondas. Devuelve el estado de todos
     * como el motor de verdad: con las estadisticas resueltas, el poder
     * explicito y las acciones que tiene cada uno.
     */
    @SuppressWarnings("unchecked")
    private Respuesta motor(Peticion p) {
        Map<String, Object> cuerpo = p.json();
        List<Map<String, Object>> mesa = resueltos((List<Map<String, Object>>) cuerpo.get("combatientes"));
        if (p.ruta().endsWith("/turnos")) {
            String quien = (String) cuerpo.get("combatiente");
            for (Map<String, Object> c : mesa) {
                if (quien.equals(c.get("id"))) {
                    int maximo = ((Number) ((Map<String, Object>) c.get("estadisticas")).get("poder")).intValue();
                    c.put("poderActual", Math.min(maximo, ((Number) c.get("poderActual")).intValue() + 2));
                }
            }
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("combatiente", quien);
            respuesta.put("afectados", List.of());
            respuesta.put("eventos", List.of());
            respuesta.put("combatientes", mesa);
            return json(200, respuesta);
        }
        String accion = (String) cuerpo.get("accion");
        String ejecutor = (String) cuerpo.get("ejecutor");
        Map<String, Object> blanco = mesa.stream().filter(c -> !ejecutor.equals(c.get("id"))).findFirst().orElseThrow();
        int vidaAntes = ((Number) blanco.get("vidaActual")).intValue();
        int dano = "heroe".equals(ejecutor) ? 60 : 1;
        int vidaDespues = Math.max(0, vidaAntes - dano);
        blanco.put("vidaActual", vidaDespues);
        Map<String, Object> golpe = new LinkedHashMap<>();
        golpe.put("ataqueResuelto", 15);
        golpe.put("defensaObjetivo", ((Number) ((Map<String, Object>) blanco.get("estadisticas")).get("defensa")).intValue());
        golpe.put("acierta", true);
        golpe.put("categoria", "CAUSAR_DANO");
        golpe.put("indiceTabla", 4000);
        golpe.put("porcentajeDano", 100);
        golpe.put("danoBase", dano);
        golpe.put("danoAplicado", dano);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("accion", accion);
        respuesta.put("accionEjecutada", accion);
        respuesta.put("enValorBase", false);
        respuesta.put("ejecutor", ejecutor);
        respuesta.put("objetivo", blanco.get("id"));
        respuesta.put("tipo", "ATAQUE");
        respuesta.put("ataque", golpe);
        respuesta.put("afectados", List.of(Map.of("id", blanco.get("id"), "vidaAntes", vidaAntes,
                "vidaDespues", vidaDespues, "diferencia", vidaDespues - vidaAntes)));
        respuesta.put("eventos", List.of(Map.of("tipo", "DANO", "combatiente", blanco.get("id"), "origen", ejecutor,
                "efecto", accion, "cantidad", dano)));
        respuesta.put("combatientes", mesa);
        return json(200, respuesta);
    }

    /** Los combatientes como los devuelve el motor: estadisticas, poder y acciones resueltos. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> resueltos(List<Map<String, Object>> entrada) {
        List<Map<String, Object>> mesa = new ArrayList<>();
        for (Map<String, Object> original : entrada) {
            Map<String, Object> c = new LinkedHashMap<>(original);
            if (c.get("estadisticas") == null) {
                Map<String, Object> estadisticas = new LinkedHashMap<>();
                estadisticas.put("poder", 10);
                estadisticas.put("vida", Math.max(1, ((Number) c.get("vidaActual")).intValue()));
                estadisticas.put("defensa", "heroe".equals(c.get("id")) ? 11 : 5);
                estadisticas.put("ataque", Map.of("base", 10, "cantidadDados", 1, "caras", 6));
                estadisticas.put("dano", Map.of("base", 1, "cantidadDados", 1, "caras", 4));
                estadisticas.put("sanar", null);
                c.put("estadisticas", estadisticas);
            }
            if (c.get("poderActual") == null) {
                c.put("poderActual", ((Map<String, Object>) c.get("estadisticas")).get("poder"));
            }
            c.put("acciones", List.of(Map.of("codigo", "ATAQUE_BASICO", "nombre", "Ataque básico", "tipo", "ATAQUE",
                    "disponible", true)));
            mesa.add(c);
        }
        return mesa;
    }
}
