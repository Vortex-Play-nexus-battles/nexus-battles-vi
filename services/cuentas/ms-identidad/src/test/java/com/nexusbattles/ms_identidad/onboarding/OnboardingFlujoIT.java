package com.nexusbattles.ms_identidad.onboarding;

import com.nexusbattles.ms_identidad.onboarding.service.ProcesadorOnboarding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El alta de un jugador de punta a punta, con todo de verdad menos los
 * servicios de otros equipos: PostgreSQL con Flyway y {@code validate},
 * Tomcat, el interceptor de seguridad, la credencial de servicio (ADR-005),
 * los clientes HTTP con sus tiempos, y la maquina de estados del alta.
 * ms-finanzas, el inventario, el catalogo y la plataforma son servidores HTTP
 * del JDK que contestan lo que contestarian ellos; el inventario falso
 * guarda los elementos por uid para poder afirmar que no hay duplicados.
 *
 * <p>Lo que solo esta prueba demuestra: que registrarse desde cero deja a la
 * persona con creditos y un heroe equipado sin que nadie toque una base de
 * datos, que un servicio caido deja el alta reintentable (y se recupera sola
 * al iniciar sesion o con el boton), que dos trabajadores a la vez no acreditan
 * dos veces, y que el parametro del sistema manda sobre el respaldo de DEV.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "app.onboarding.ejecucion=sincrona",
        "app.onboarding.reintentos-automaticos=false",
        "app.onboarding.creditos-iniciales-respaldo=500",
        "app.onboarding.kit-inicial-respaldo=p-heroe,p-espada",
        "app.seguridad.permitir-header-rol=false"
})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Alta del jugador de punta a punta, con PostgreSQL y HTTP de verdad (R17)")
class OnboardingFlujoIT {

    private static final String CLAVE = "Segura-123!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BASE = new PostgreSQLContainer<>("postgres:15-alpine");

    static final ServidorFalso FINANZAS = new ServidorFalso();
    static final ServidorFalso CONTENIDO = new ServidorFalso();
    static final ServidorFalso PLATAFORMA = new ServidorFalso();

    /** Inventario falso: elementos y equipamiento por uid, como el de verdad. */
    static final Map<String, List<Map<String, Object>>> INVENTARIOS = new ConcurrentHashMap<>();
    static final Map<String, List<String>> EQUIPADOS = new ConcurrentHashMap<>();
    static final AtomicInteger SECUENCIA = new AtomicInteger();

    @DynamicPropertySource
    static void servicios(DynamicPropertyRegistry registro) {
        registro.add("app.onboarding.creditos-url", () -> FINANZAS.url() + "/api/v1");
        registro.add("app.onboarding.inventario-url", CONTENIDO::url);
        registro.add("app.onboarding.productos-url", CONTENIDO::url);
        registro.add("app.onboarding.parametros-url", () -> PLATAFORMA.url() + "/api/v1");
        registro.add("app.lista-negra.url", () -> PLATAFORMA.url() + "/api/v1/lista-negra/verificar");
        registro.add("app.correo.url-bienvenida", () -> PLATAFORMA.url() + "/api/v1/correos/bienvenida");
        registro.add("app.correo.url-aviso-acceso", () -> PLATAFORMA.url() + "/api/v1/correos/aviso-acceso");
        registro.add("app.notificaciones.url", () -> PLATAFORMA.url() + "/api/v1/internal/notifications");
        registro.add("app.auditoria.url", () -> PLATAFORMA.url() + "/api/v1/admin/auditoria/eventos");
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProcesadorOnboarding procesador;

    @AfterAll
    static void apagar() {
        FINANZAS.close();
        CONTENIDO.close();
        PLATAFORMA.close();
    }

    @BeforeEach
    void serviciosSanos() {
        PLATAFORMA.responder("POST", "/api/v1/lista-negra/verificar", 200, "{\"aprobado\":true}")
                .responder("POST", "/api/v1/correos/.*", 202, "{}")
                .responder("POST", "/api/v1/internal/notifications", 202, "{}")
                .responder("POST", "/api/v1/admin/auditoria/eventos", 201, "{}")
                .responder("GET", "/api/v1/parametros/.*/valor", 404, "{}");
        FINANZAS.responder("POST", "/api/v1/creditos/acreditar", peticion -> acreditado(peticion.cuerpo()));
        CONTENIDO.responder("GET", "/api/v1/productos/p-heroe", 200,
                        "{\"id\":\"p-heroe\",\"nombre\":\"Guerrero Tanque\",\"tipo\":\"HEROE\",\"parte\":null}")
                .responder("GET", "/api/v1/productos/p-espada", 200,
                        "{\"id\":\"p-espada\",\"nombre\":\"Espada\",\"tipo\":\"ARMA\",\"parte\":null}")
                .responder("GET", "/api/v1/inventario/elementos", peticion -> pagina(peticion.cabecera("X-User-Name")))
                .responder("POST", "/api/v1/inventario/elementos", peticion -> crear(peticion))
                .responder("GET", "/api/v1/inventario/heroes/[^/]+/equipamiento", peticion -> equipamiento(peticion))
                .responder("PUT", "/api/v1/inventario/heroes/[^/]+/equipamiento/[^/]+", peticion -> equipar(peticion));
    }

    // ---------------------------------------------------------------- falsos

    private static ServidorFalso.Respuesta acreditado(String cuerpo) {
        String ref = campo(cuerpo, "refId");
        String monto = numero(cuerpo, "monto");
        return new ServidorFalso.Respuesta(200, "{\"transaccionId\":\"TX-ACR-" + String.format("%08X", ref.hashCode())
                + "\",\"refId\":\"" + ref + "\",\"estado\":\"APLICADO\",\"montoAcreditado\":" + monto
                + ",\"nuevoSaldoDisponible\":" + monto + "}");
    }

    private static ServidorFalso.Respuesta pagina(String uid) {
        List<Map<String, Object>> elementos = INVENTARIOS.getOrDefault(uid, List.of());
        StringBuilder json = new StringBuilder("{\"elementos\":[");
        for (int i = 0; i < elementos.size(); i++) {
            Map<String, Object> e = elementos.get(i);
            json.append(i == 0 ? "" : ",").append("{\"id\":\"").append(e.get("id")).append("\",\"productoId\":\"")
                    .append(e.get("productoId")).append("\",\"tipo\":\"").append(e.get("tipo"))
                    .append("\",\"nombrePropio\":\"x\",\"disponible\":true}");
        }
        json.append("],\"numero\":0,\"tamanio\":16,\"totalElementos\":").append(elementos.size())
                .append(",\"totalPaginas\":1,\"ultima\":true}");
        return new ServidorFalso.Respuesta(200, json.toString());
    }

    private static ServidorFalso.Respuesta crear(ServidorFalso.Peticion peticion) {
        String uid = peticion.cabecera("X-User-Name");
        String id = "el-" + SECUENCIA.incrementAndGet();
        String producto = campo(peticion.cuerpo(), "productoId");
        String tipo = campo(peticion.cuerpo(), "tipo");
        INVENTARIOS.computeIfAbsent(uid, u -> new CopyOnWriteArrayList<>())
                .add(Map.of("id", id, "productoId", producto, "tipo", tipo));
        return new ServidorFalso.Respuesta(201, "{\"id\":\"" + id + "\",\"productoId\":\"" + producto
                + "\",\"tipo\":\"" + tipo + "\",\"nombrePropio\":\"x\",\"disponible\":true}");
    }

    private static ServidorFalso.Respuesta equipamiento(ServidorFalso.Peticion peticion) {
        String heroe = peticion.ruta().split("/")[5];
        List<String> puestos = EQUIPADOS.getOrDefault(heroe, List.of());
        return new ServidorFalso.Respuesta(200, "{\"heroeId\":\"" + heroe + "\",\"armas\":["
                + String.join(",", puestos.stream().map(p -> "\"" + p + "\"").toList())
                + "],\"armaduras\":{},\"items\":[]}");
    }

    private static ServidorFalso.Respuesta equipar(ServidorFalso.Peticion peticion) {
        String[] partes = peticion.ruta().split("/");
        String heroe = partes[5];
        String elemento = partes[7];
        List<String> puestos = EQUIPADOS.computeIfAbsent(heroe, h -> new CopyOnWriteArrayList<>());
        if (puestos.contains(elemento)) {
            return new ServidorFalso.Respuesta(409, "{\"detail\":\"ya equipado\"}");
        }
        puestos.add(elemento);
        return equipamiento(peticion);
    }

    private static String campo(String json, String nombre) {
        Matcher m = Pattern.compile("\"" + nombre + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String numero(String json, String nombre) {
        Matcher m = Pattern.compile("\"" + nombre + "\"\\s*:\\s*([0-9.]+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // ------------------------------------------------------------- ayudantes

    private record Respuesta(int estado, String cuerpo) {
    }

    private RestClient cliente() {
        return RestClient.create("http://localhost:" + puerto);
    }

    private Respuesta registrar(String apodo, String correo, String accept) {
        MultiValueMap<String, Object> formulario = new LinkedMultiValueMap<>();
        formulario.add("nombres", "Profe");
        formulario.add("apellidos", "De Prueba");
        formulario.add("email", correo);
        formulario.add("password", CLAVE);
        formulario.add("apodo", apodo);
        return cliente().post().uri("/api/v1/auth/registro")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(HttpHeaders.ACCEPT, accept)
                .body(formulario)
                .exchange((peticion, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
                        new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private Respuesta registrar(String apodo) {
        return registrar(apodo, apodo + "@upb.edu.co", MediaType.APPLICATION_JSON_VALUE);
    }

    private Respuesta login(String correo) {
        return cliente().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}")
                .exchange((peticion, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
                        new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private Respuesta onboarding(String token, boolean reintentar) {
        RestClient.RequestHeadersSpec<?> peticion = reintentar
                ? cliente().post().uri("/api/v1/auth/onboarding/reintentos")
                : cliente().get().uri("/api/v1/auth/onboarding");
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((p, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
                        new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private UUID uidDe(String apodo) {
        return jdbc.queryForObject("SELECT public_id FROM usuarios WHERE apodo = ?", UUID.class, apodo);
    }

    private String estadoDelAlta(UUID uid) {
        return jdbc.queryForObject("SELECT estado FROM onboarding_jugador WHERE usuario_uid = ?", String.class, uid);
    }

    private String estadoDelPaso(UUID uid, String paso) {
        return jdbc.queryForObject("SELECT estado FROM onboarding_paso WHERE usuario_uid = ? AND paso = ?",
                String.class, uid, paso);
    }

    private List<ServidorFalso.Peticion> acreditacionesDe(UUID uid) {
        List<ServidorFalso.Peticion> suyas = new ArrayList<>();
        for (ServidorFalso.Peticion p : FINANZAS.recibidas("POST", "/api/v1/creditos/acreditar")) {
            if (p.cuerpo().contains(uid.toString())) {
                suyas.add(p);
            }
        }
        return suyas;
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    @DisplayName("registrarse desde cero deja creditos, heroe y equipo, sin tocar ninguna base a mano")
    void altaCompletaDesdeElRegistro() {
        Respuesta alta = registrar("ada");
        assertThat(alta.estado()).as(alta.cuerpo()).isEqualTo(201);
        UUID uid = uidDe("ada");

        assertThat(estadoDelAlta(uid)).isEqualTo("COMPLETO");
        List<ServidorFalso.Peticion> acreditaciones = acreditacionesDe(uid);
        assertThat(acreditaciones).hasSize(1);
        ServidorFalso.Peticion credito = acreditaciones.get(0);
        assertThat(credito.cuerpo()).contains("\"refId\":\"bono-registro-v1-" + uid + "\"")
                .contains("\"monto\":500").contains("\"concepto\":\"bono-registro\"");
        // Credencial de servicio de verdad (ADR-005): firmada por ms-identidad, rol SERVICIO.
        String carga = new String(Base64.getUrlDecoder().decode(
                credito.cabecera("Authorization").substring(7).split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(carga).contains("\"rol\":\"SERVICIO\"").contains("ms-identidad");
        // Todas las llamadas del alta comparten traza (regla 5).
        String traza = credito.cabecera("traceparent").split("-")[1];
        assertThat(CONTENIDO.recibidas().stream()
                .filter(p -> uid.toString().equals(p.cabecera("X-User-Name")))
                .map(p -> p.cabecera("traceparent").split("-")[1]))
                .isNotEmpty().allMatch(traza::equals);

        assertThat(INVENTARIOS.get(uid.toString())).extracting(e -> e.get("productoId"))
                .containsExactlyInAnyOrder("p-heroe", "p-espada");
        String heroe = (String) INVENTARIOS.get(uid.toString()).stream()
                .filter(e -> "HEROE".equals(e.get("tipo"))).findFirst().orElseThrow().get("id");
        assertThat(EQUIPADOS.get(heroe)).hasSize(1);

        Respuesta sesion = login("ada@upb.edu.co");
        assertThat(sesion.estado()).isEqualTo(200);
        assertThat(sesion.cuerpo()).contains("\"uid\":\"" + uid + "\"").contains("\"onboardingListo\":true");
        String token = campo(sesion.cuerpo(), "token");

        Respuesta estado = onboarding(token, false);
        assertThat(estado.estado()).isEqualTo(200);
        assertThat(estado.cuerpo()).contains("\"estado\":\"COMPLETO\"").contains("\"listo\":true")
                .contains("\"creditosIniciales\":500").contains("\"heroeInicial\":\"" + heroe + "\"");
    }

    @Test
    @DisplayName("ms-finanzas caido al registrarse: el heroe se crea igual y el login termina el alta cuando vuelve")
    void finanzasCaidaYElLoginLaRecupera() {
        FINANZAS.responder("POST", "/api/v1/creditos/acreditar", 503, "{\"title\":\"no disponible\"}");
        assertThat(registrar("bruno").estado()).isEqualTo(201);
        UUID uid = uidDe("bruno");

        assertThat(estadoDelAlta(uid)).isEqualTo("ERROR_REINTENTABLE");
        assertThat(estadoDelPaso(uid, "CREDITOS")).isEqualTo("ERROR");
        assertThat(estadoDelPaso(uid, "HEROE")).isEqualTo("HECHO");
        assertThat(estadoDelPaso(uid, "EQUIPO")).isEqualTo("HECHO");

        // Vuelve ms-finanzas; el jugador inicia sesion y el alta se termina sola.
        FINANZAS.responder("POST", "/api/v1/creditos/acreditar", peticion -> acreditado(peticion.cuerpo()));
        Respuesta sesion = login("BRUNO@upb.edu.co");
        assertThat(sesion.estado()).as("el correo se encuentra sin importar las mayusculas").isEqualTo(200);
        assertThat(sesion.cuerpo()).contains("\"onboardingListo\":true");

        assertThat(estadoDelAlta(uid)).isEqualTo("COMPLETO");
        assertThat(acreditacionesDe(uid)).hasSize(2)
                .allSatisfy(p -> assertThat(p.cuerpo()).contains("bono-registro-v1-" + uid));
        assertThat(INVENTARIOS.get(uid.toString())).as("el heroe no se duplico al reintentar").hasSize(2);
    }

    @Test
    @DisplayName("inventario caido: la pantalla lo explica y el boton Reintentar lo termina")
    void inventarioCaidoYElBoton() throws Exception {
        CONTENIDO.responder("GET", "/api/v1/inventario/elementos", 503, "{}");
        assertThat(registrar("carla").estado()).isEqualTo(201);
        UUID uid = uidDe("carla");
        String token = campo(login("carla@upb.edu.co").cuerpo(), "token");

        Respuesta estado = onboarding(token, false);
        assertThat(estado.cuerpo()).contains("\"estado\":\"ERROR_REINTENTABLE\"").contains("\"listo\":false")
                .contains("El servicio no respondió a tiempo").contains("Espera a que el héroe inicial esté listo")
                .doesNotContain("503");
        assertThat(estadoDelPaso(uid, "CREDITOS")).isEqualTo("HECHO");

        CONTENIDO.responder("GET", "/api/v1/inventario/elementos", peticion -> pagina(peticion.cabecera("X-User-Name")));
        Thread.sleep(3_100); // la pausa entre reintentos manuales
        Respuesta reintento = onboarding(token, true);
        assertThat(reintento.estado()).isEqualTo(202);

        assertThat(onboarding(token, false).cuerpo()).contains("\"estado\":\"COMPLETO\"");
        assertThat(acreditacionesDe(uid)).as("los creditos no se repiten").hasSize(1);
    }

    @Test
    @DisplayName("dos trabajadores a la vez sobre la misma alta: uno la procesa, el otro no toca nada")
    void dosTrabajadoresALaVez() throws Exception {
        FINANZAS.responder("POST", "/api/v1/creditos/acreditar", 503, "{}");
        assertThat(registrar("dora").estado()).isEqualTo(201);
        UUID uid = uidDe("dora");
        assertThat(estadoDelAlta(uid)).isEqualTo("ERROR_REINTENTABLE");

        FINANZAS.responder("POST", "/api/v1/creditos/acreditar", peticion -> {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return acreditado(peticion.cuerpo());
        });
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<ProcesadorOnboarding.Resultado>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return procesador.procesar(uid);
                }));
            }
            salida.countDown();
            List<ProcesadorOnboarding.Resultado> obtenidos = new ArrayList<>();
            for (Future<ProcesadorOnboarding.Resultado> r : resultados) {
                obtenidos.add(r.get());
            }
            assertThat(obtenidos).containsExactlyInAnyOrder(
                    ProcesadorOnboarding.Resultado.COMPLETO, ProcesadorOnboarding.Resultado.NO_TOMADO);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(acreditacionesDe(uid)).as("una del registro (503) y UNA del reintento").hasSize(2);
        assertThat(estadoDelAlta(uid)).isEqualTo("COMPLETO");
    }

    @Test
    @DisplayName("el parametro del sistema manda sobre el respaldo de DEV, y queda anotado de donde salio")
    void parametroMandaSobreElRespaldo() {
        PLATAFORMA.responder("GET", "/api/v1/parametros/jugador.creditos-iniciales/valor", 200,
                "{\"clave\":\"jugador.creditos-iniciales\",\"valor\":750,\"tipo\":\"ENTERO\",\"version\":2}");

        assertThat(registrar("elena").estado()).isEqualTo(201);
        UUID uid = uidDe("elena");

        assertThat(acreditacionesDe(uid)).singleElement()
                .satisfies(p -> assertThat(p.cuerpo()).contains("\"monto\":750"));
        assertThat(jdbc.queryForObject("SELECT detalle FROM onboarding_paso WHERE usuario_uid = ? AND paso = 'CREDITOS'",
                String.class, uid)).contains("monto=750").contains("fuente=PARAMETRO");
    }

    @Test
    @DisplayName("el mismo correo con otras mayusculas no crea una segunda cuenta; el rechazo marca el campo")
    void correoRepetido() {
        assertThat(registrar("fede", "fede@upb.edu.co", MediaType.APPLICATION_JSON_VALUE).estado()).isEqualTo(201);

        Respuesta repetido = registrar("fede2", "FEDE@UPB.edu.co", MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        assertThat(repetido.estado()).isEqualTo(400);
        assertThat(repetido.cuerpo()).contains("/errors/correo-en-uso").contains("\"campo\":\"email\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE lower(email) = 'fede@upb.edu.co'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM onboarding_jugador o JOIN usuarios u"
                + " ON u.public_id = o.usuario_uid WHERE u.apodo = 'fede2'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("sin sesion no se ve el alta de nadie")
    void sinSesion() {
        Respuesta anonimo = cliente().get().uri("/api/v1/auth/onboarding")
                .exchange((p, r) -> new Respuesta(r.getStatusCode().value(), ""));
        assertThat(anonimo.estado()).isEqualTo(403);
    }
}
