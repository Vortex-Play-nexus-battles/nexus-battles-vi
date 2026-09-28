package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La lista negra de punta a punta: Flyway (V4-V7 con la semilla provisional)
 * sobre PostgreSQL, la cache en Redis, la cadena de seguridad con tokens
 * firmados de verdad y el servidor web. Es la regresion del caso que fallo
 * delante del profesor, contra la base real y no contra un doble.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "sanciones.avisos.reintento-ms=3600000"})
@DisplayName("Lista negra · de punta a punta con la semilla, la cache y la seguridad reales")
class ListaNegraIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private TerminoProhibidoRepository repositorio;

    @Autowired
    private CacheManager cacheManager;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private HttpResponse<String> enviar(String metodo, String ruta, String token, String cuerpo) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode verificar(String texto, String contexto, String token) throws Exception {
        String cuerpo = json.writeValueAsString(contexto == null
                ? java.util.Map.of("texto", texto) : java.util.Map.of("texto", texto, "contexto", contexto));
        HttpResponse<String> respuesta = enviar("POST", "/api/v1/lista-negra/verificar", token, cuerpo);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    @Test
    @DisplayName("la semilla existe, es coherente con el normalizador y cubre cada categoria del 7.1.1")
    void semilla() {
        List<TerminoProhibido> semilla = repositorio.findAll().stream()
                .filter(t -> "semilla".equals(t.creadoPor())).toList();

        assertThat(semilla).isNotEmpty();
        assertThat(semilla).allSatisfy(t -> {
            assertThat(t.normalizado()).as(t.termino()).isEqualTo(NormalizadorDeTexto.compacta(t.termino()));
            assertThat(t.activo()).isTrue();
            assertThat(t.creadoEn()).isNotNull();
        });
        Set<CategoriaDeTermino> categorias = semilla.stream().map(TerminoProhibido::categoria)
                .collect(Collectors.toSet());
        assertThat(categorias).containsAll(EnumSet.of(CategoriaDeTermino.OFENSIVO, CategoriaDeTermino.MARCA,
                CategoriaDeTermino.CELEBRIDAD, CategoriaDeTermino.POLITICO, CategoriaDeTermino.DIRIGENTE));
        assertThat(repositorio.findByNormalizado("spiderman")).get()
                .satisfies(t -> assertThat(t.categoria()).isEqualTo(CategoriaDeTermino.MARCA));
        assertThat(semilla).filteredOn(t -> t.normalizado().length() < ModoDeCoincidencia.LONGITUD_MINIMA_SUBCADENA)
                .as("los terminos cortos van como palabra").allSatisfy(t ->
                        assertThat(t.modo()).isEqualTo(ModoDeCoincidencia.PALABRA));
    }

    @Test
    @DisplayName("el caso del profesor: todas las variantes de spiderman se rechazan como apodo, sin decir cual")
    void elCasoDelProfesor() throws Exception {
        for (String apodo : List.of("spiderman", "Spiderman", "SPIDERMAN", "spider-man", "spider man", "sp1derman",
                "$piderman", "spíderman", "spider_man", "s.p.i.d.e.r.m.a.n", "xXspidermanXx")) {
            JsonNode respuesta = verificar(apodo, "APODO", null);
            assertThat(respuesta.path("aprobado").asBoolean()).as(apodo).isFalse();
            assertThat(respuesta.path("accion").asString()).as(apodo).isEqualTo("RECHAZAR");
            assertThat(respuesta.path("motivo").asString()).as(apodo).isNotBlank().doesNotContain("spider");
            assertThat(respuesta.has("categoria")).as("sin token no hay detalle").isFalse();
            assertThat(respuesta.has("coincidencias")).isFalse();
        }
    }

    @Test
    @DisplayName("con token de servicio la respuesta trae la categoria y el termino")
    void detalleConTokenDeServicio() throws Exception {
        JsonNode respuesta = verificar("Soy Spider-Man", "CHAT_SALA", emisor.tokenDeServicio("salas-partidas"));

        assertThat(respuesta.path("accion").asString()).isEqualTo("BLOQUEAR");
        assertThat(respuesta.path("categoria").asString()).isEqualTo("MARCA");
        assertThat(respuesta.path("coincidencias").get(0).asString()).isEqualTo("spiderman");
    }

    @Test
    @DisplayName("los apodos legitimos y las palabras normales pasan; en comentario la accion es REVISION")
    void legitimos() throws Exception {
        for (String texto : List.of("Valkiria", "ElGuerrero", "Mariposa", "Clasico99", "Spidey", "computadora",
                "el mes siguiente", "esta línea", "está lindo", "cálculo", "vehículo")) {
            assertThat(verificar(texto, "APODO", null).path("aprobado").asBoolean()).as(texto).isTrue();
        }
        assertThat(verificar("que culo", "COMENTARIO", null).path("accion").asString()).isEqualTo("REVISION");
        assertThat(verificar("Messi10", null, null).path("accion").asString()).isEqualTo("RECHAZAR");
    }

    @Test
    @DisplayName("se administra en caliente: alta, apagar, encender y baja cambian la verificacion al momento")
    void enCaliente() throws Exception {
        String superAdmin = emisor.tokenDeUsuario("root_it", UUID.randomUUID(), "SUPER_ADMINISTRADOR");
        String termino = "Zorrudo Prueba";

        assertThat(verificar("xXzorrudopruebaXx", "APODO", null).path("aprobado").asBoolean()).isTrue();

        HttpResponse<String> alta = enviar("POST", "/api/v1/lista-negra/terminos", superAdmin,
                "{\"termino\":\"" + termino + "\",\"categoria\":\"OFENSIVO\"}");
        assertThat(alta.statusCode()).as(alta.body()).isEqualTo(201);
        JsonNode creado = json.readTree(alta.body());
        assertThat(creado.path("normalizado").asString()).isEqualTo("zorrudoprueba");
        assertThat(creado.path("modo").asString()).isEqualTo("SUBCADENA");
        assertThat(creado.path("creadoPor").asString()).isEqualTo("root_it");
        assertThat(verificar("xXzorrudopruebaXx", "APODO", null).path("aprobado").asBoolean())
                .as("la cache se vacia en cada escritura").isFalse();

        String camino = "/api/v1/lista-negra/terminos/" + URLEncoder.encode("ZORRUDO-PRUEBA", StandardCharsets.UTF_8);
        HttpResponse<String> apagado = enviar("PUT", camino, superAdmin,
                "{\"termino\":\"" + termino + "\",\"activo\":false}");
        assertThat(apagado.statusCode()).as(apagado.body()).isEqualTo(200);
        assertThat(json.readTree(apagado.body()).path("categoria").asString())
                .as("lo omitido se conserva").isEqualTo("OFENSIVO");
        assertThat(verificar("xXzorrudopruebaXx", "APODO", null).path("aprobado").asBoolean()).isTrue();

        enviar("PUT", camino, superAdmin, "{\"termino\":\"" + termino + "\",\"activo\":true}");
        assertThat(verificar("zorrudo_prueba", "APODO", null).path("aprobado").asBoolean()).isFalse();

        assertThat(enviar("DELETE", camino, superAdmin, null).statusCode()).isEqualTo(204);
        assertThat(verificar("xXzorrudopruebaXx", "APODO", null).path("aprobado").asBoolean()).isTrue();
        assertThat(enviar("DELETE", camino, superAdmin, null).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("dar de alta otra forma de un termino existente es 409; el listado filtra y pagina")
    void duplicadoYListado() throws Exception {
        String moderador = emisor.tokenDeUsuario("mod_it", UUID.randomUUID(), "MODERADOR");

        HttpResponse<String> duplicado = enviar("POST", "/api/v1/lista-negra/terminos", moderador,
                "{\"termino\":\"Spider-Man\"}");
        assertThat(duplicado.statusCode()).isEqualTo(409);
        assertThat(json.readTree(duplicado.body()).path("motivo").asString()).isEqualTo("TERMINO_DUPLICADO");

        HttpResponse<String> listado = enviar("GET",
                "/api/v1/lista-negra/terminos?categoria=MARCA&activo=true&buscar=SPIDER&pagina=0&tamano=16",
                moderador, null);
        assertThat(listado.statusCode()).as(listado.body()).isEqualTo(200);
        JsonNode pagina = json.readTree(listado.body());
        assertThat(pagina.path("tamano").asInt()).isEqualTo(16);
        assertThat(pagina.path("total").asLong()).isEqualTo(1);
        assertThat(pagina.path("contenido").get(0).path("termino").asString()).isEqualTo("spiderman");

        HttpResponse<String> jugador = enviar("GET", "/api/v1/lista-negra/terminos",
                emisor.tokenDeJugador("lyra_it", UUID.randomUUID()), null);
        assertThat(jugador.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("la cache de terminos activos caduca (5 min por omision): ya no vive para siempre")
    void cacheConCaducidad() throws Exception {
        verificar("hola", null, null);
        RedisCache cache = (RedisCache) cacheManager.getCache(CatalogoDeTerminosActivos.CACHE);

        assertThat(cache).isNotNull();
        assertThat(cache.getCacheConfiguration().getTtlFunction().getTimeToLive("activos", List.of()))
                .isEqualTo(Duration.ofMinutes(5));
    }
}
