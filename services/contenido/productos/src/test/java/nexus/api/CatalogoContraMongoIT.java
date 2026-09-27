package nexus.api;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import jakarta.validation.Validator;
import nexus.aplicacion.AdquirirProductoServicio;
import nexus.dominio.EstadoProducto;
import nexus.dominio.OrigenProducto;
import nexus.dominio.Producto;
import nexus.persistencia.AdquisicionRegistradaRepository;
import nexus.persistencia.ProductoRepository;
import nexus.productos.dominio.EstadoAdquisicion;
import nexus.semilla.MapeadorDelCatalogo;
import nexus.semilla.ResultadoSemilla;
import nexus.semilla.SemillaDelCatalogo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * B4 de punta a punta: el servicio entero contra MongoDB real, con la semilla
 * ENCENDIDA (como en todos los entornos desde B4) y tokens firmados de verdad
 * por el emisor de prueba (misma forma que ms-identidad).
 *
 * <p>Arranca sobre una base vacia: lo primero que se comprueba es que el
 * arranque, sin nada mas, deja el catalogo completo del documento.
 */
@SpringBootTest(properties = "catalogo.semilla.habilitada=true")
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Catalogo contra MongoDB real (B4)")
class CatalogoContraMongoIT {

        @Container
        @ServiceConnection
        static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

        private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
        private static final UUID UID_ADMIN = UUID.fromString("0f3c1a5e-2b7d-4e8f-9a01-23456789abcd");

        @DynamicPropertySource
        static void emisorReal(DynamicPropertyRegistry registro) {
                EmisorDeTokensDePrueba.registrarJwks(registro);
        }

        @Autowired
        private MockMvc mvc;

        @Autowired
        private ProductoRepository productos;

        @Autowired
        private AdquisicionRegistradaRepository adquisiciones;

        @Autowired
        private AdquirirProductoServicio adquirir;

        @Autowired
        private MapeadorDelCatalogo mapeador;

        @Autowired
        private Validator validador;

        @Autowired
        private SemillaDelCatalogo semilla;

        private static String admin() {
                return "Bearer " + EMISOR.tokenDeUsuario("raiz", UID_ADMIN, "ADMINISTRADOR");
        }

        private static String jugador() {
                return "Bearer " + EMISOR.tokenDeJugador("lyra", UUID.randomUUID());
        }

        private static String servicio() {
                return "Bearer " + EMISOR.tokenDeServicio("ms-ecommerce");
        }

        private static String id(String slug) {
                return MapeadorDelCatalogo.identificador(slug);
        }

        /** Un arma sembrada distinta en cada prueba: el contexto (y la base) se comparten. */
        private Producto productoDeLaSemilla(String slug) {
                return productos.findById(id(slug)).orElseThrow();
        }

        @Nested
        @DisplayName("Semilla versionada")
        class Semilla {

                @Test
                @DisplayName("base vacia: el arranque deja el catalogo completo, 56 productos marcados SEMILLA")
                void elArranqueSiembraElCatalogoCompleto() {
                        List<Producto> sembrados = productos.findAll().stream()
                                .filter(p -> p.origen() == OrigenProducto.SEMILLA)
                                .toList();

                        assertEquals(56, sembrados.size());
                        assertTrue(productos.existsById(id("heroe-guerrero-tanque")));
                        assertTrue(productos.existsById(id("epica-medico-reanimador-3000")));
                        // Las pruebas comparten la base: otra puede haber aplicado la v2.
                        assertTrue(sembrados.stream().allMatch(p -> p.semillaVersion() != null && p.semillaVersion() >= 1));
                        assertTrue(sembrados.stream().allMatch(p -> p.version() >= 1));
                }

                @Test
                @DisplayName("una segunda ejecucion no inserta ni actualiza nada")
                void segundaEjecucionNoCambiaNada() {
                        ResultadoSemilla otra = semilla.sembrar();

                        assertEquals(List.of(), otra.insertados());
                        assertEquals(List.of(), otra.actualizados());
                        assertEquals(56, otra.existentes().size() + otra.respetados().size());
                }

                @Test
                @DisplayName("una version nueva pone al dia lo no tocado y respeta lo que edito un administrador por la API")
                void versionNuevaRespetaLoEditadoPorLaApi() throws Exception {
                        String editado = id("item-medico-benditas");
                        mvc.perform(patch("/api/v1/productos/{id}", editado)
                                        .header("Authorization", admin())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"descripcion\": \"Descripcion reescrita por el administrador\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.modificadoPor").value(UID_ADMIN.toString()));

                        ResultadoSemilla v2 = semillaConOtraVersion(2).sembrar();

                        assertTrue(v2.respetados().contains(editado), v2.respetados().toString());
                        assertTrue(v2.actualizados().size() >= 50, "actualizados: " + v2.actualizados().size());
                        Producto respetado = productos.findById(editado).orElseThrow();
                        assertEquals("Descripcion reescrita por el administrador", respetado.descripcion());
                        assertEquals(1, respetado.semillaVersion());
                        assertEquals(2, productoDeLaSemilla("item-mago-fuego-anillo-para-piro-explosion").semillaVersion());
                }

                private SemillaDelCatalogo semillaConOtraVersion(int version) throws IOException {
                        String json;
                        try (InputStream real = new ClassPathResource("semilla/catalogo-inicial.json").getInputStream()) {
                                json = new String(real.readAllBytes(), StandardCharsets.UTF_8)
                                        .replaceFirst("\"version\": 1,", "\"version\": " + version + ",");
                        }
                        Resource archivo = new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
                        return new SemillaDelCatalogo(productos, mapeador, validador, true, archivo);
                }
        }

        @Nested
        @DisplayName("Proyeccion publica")
        class ProyeccionPublica {

                @Test
                @DisplayName("sin token no se ve un producto suspendido; un jugador si (lo puede tener); un admin lo ve todo")
                void losSuspendidosSegunQuienPregunta() throws Exception {
                        String producto = id("arma-picaro-veneno-vision-borrosa");
                        mvc.perform(put("/api/v1/productos/{id}/suspender", producto).header("Authorization", admin()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("SUSPENDIDO"));

                        mvc.perform(get("/api/v1/productos/{id}", producto))
                                .andExpect(status().isNotFound());
                        mvc.perform(get("/api/v1/productos/{id}", producto).header("Authorization", jugador()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("SUSPENDIDO"))
                                .andExpect(jsonPath("$.version").doesNotExist())
                                .andExpect(jsonPath("$.tasaDeCaida").doesNotExist())
                                .andExpect(jsonPath("$.origen").doesNotExist());
                        mvc.perform(get("/api/v1/productos/{id}", producto).header("Authorization", admin()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.version").isNumber())
                                .andExpect(jsonPath("$.tasaDeCaida").value(4))
                                .andExpect(jsonPath("$.origen").value("SEMILLA"))
                                // 1, o 2 si otra prueba ya aplico la version nueva: la base se comparte.
                                .andExpect(jsonPath("$.semillaVersion").isNumber());
                }

                @Test
                @DisplayName("el listado sin token no trae suspendidos aunque los pida, ni campos internos")
                void elListadoPublico() throws Exception {
                        mvc.perform(put("/api/v1/productos/{id}/suspender", id("arma-mago-hielo-venas-heladas"))
                                        .header("Authorization", admin()))
                                .andExpect(status().isOk());

                        mvc.perform(get("/api/v1/productos").param("estado", "SUSPENDIDO"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(0)));
                        mvc.perform(get("/api/v1/productos").param("size", "50"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].version").doesNotExist())
                                .andExpect(jsonPath("$.content[0].origen").doesNotExist());
                        mvc.perform(get("/api/v1/productos").param("estado", "SUSPENDIDO")
                                        .header("Authorization", admin()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").isNumber());
                }
        }

        @Nested
        @DisplayName("Suspender y reactivar")
        class SuspenderYReactivar {

                @Test
                @DisplayName("un administrador suspende y reactiva; el tiraje no cambia; un jugador recibe 403")
                void cicloCompleto() throws Exception {
                        String producto = id("armadura-picaro-machete-pie-de-atleta");

                        mvc.perform(put("/api/v1/productos/{id}/suspender", producto).header("Authorization", jugador()))
                                .andExpect(status().isForbidden());
                        mvc.perform(put("/api/v1/productos/{id}/suspender", producto))
                                .andExpect(status().isUnauthorized());

                        mvc.perform(put("/api/v1/productos/{id}/suspender", producto).header("Authorization", admin()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productoId").value(producto))
                                .andExpect(jsonPath("$.estado").value("SUSPENDIDO"))
                                .andExpect(jsonPath("$.tiraje").value(-1));
                        // Repetir no es un error.
                        mvc.perform(put("/api/v1/productos/{id}/suspender", producto).header("Authorization", admin()))
                                .andExpect(status().isOk());
                        mvc.perform(put("/api/v1/productos/{id}/reactivar", producto).header("Authorization", admin()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("ACTIVO"));

                        assertEquals(EstadoProducto.ACTIVO, productos.findById(producto).orElseThrow().estado());
                }

                @Test
                @DisplayName("un producto inexistente es 404")
                void inexistente() throws Exception {
                        mvc.perform(put("/api/v1/productos/{id}/suspender", UUID.randomUUID()).header("Authorization", admin()))
                                .andExpect(status().isNotFound());
                }
        }

        @Nested
        @DisplayName("Adquisiciones")
        class Adquisiciones {

                private ResultActions adquirirComo(String portador, String producto, String clave) throws Exception {
                        var peticion = post("/api/v1/productos/{id}/adquisiciones", producto);
                        if (portador != null) {
                                peticion = peticion.header("Authorization", portador);
                        }
                        if (clave != null) {
                                peticion = peticion.header("Idempotency-Key", clave);
                        }
                        return mvc.perform(peticion);
                }

                @Test
                @DisplayName("un servicio reserva; la misma clave devuelve lo mismo y no vuelve a descontar")
                void idempotentePorClave() throws Exception {
                        Producto limitado = productoConTiraje("arma-chaman-raiz-china", 3);

                        adquirirComo(servicio(), limitado.id(), "orden-1-linea-1")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("ACEPTADA"));
                        adquirirComo(servicio(), limitado.id(), "orden-1-linea-1")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("ACEPTADA"));

                        assertEquals(2, productos.findById(limitado.id()).orElseThrow().tiraje());
                        assertTrue(adquisiciones.existsById("orden-1-linea-1"));
                }

                @Test
                @DisplayName("al llegar a cero: 409 AGOTADO, y esa respuesta tambien se repite con su clave")
                void agotado() throws Exception {
                        Producto uno = productoConTiraje("arma-chaman-yerbabuena", 1);

                        adquirirComo(servicio(), uno.id(), "a-1").andExpect(status().isOk());
                        adquirirComo(servicio(), uno.id(), "a-2")
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.estado").value("AGOTADO"));
                        adquirirComo(servicio(), uno.id(), "a-2")
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.estado").value("AGOTADO"));
                        assertEquals(0, productos.findById(uno.id()).orElseThrow().tiraje());
                }

                @Test
                @DisplayName("suspendido: 409 SUSPENDIDO; la misma clave con otro producto: 409 problem")
                void suspendidoYClaveReutilizada() throws Exception {
                        String suspendido = id("arma-medico-kit-de-urgencias");
                        mvc.perform(put("/api/v1/productos/{id}/suspender", suspendido).header("Authorization", admin()))
                                .andExpect(status().isOk());

                        adquirirComo(servicio(), suspendido, "s-1")
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.estado").value("SUSPENDIDO"));
                        adquirirComo(servicio(), id("arma-medico-reanimador"), "s-1")
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.type").value("urn:nexus:problema:clave-de-idempotencia-reutilizada"));
                }

                @Test
                @DisplayName("solo un servicio: jugador y administrador 403, sin token 401; sin clave 400; inexistente 404")
                void quienYComo() throws Exception {
                        String producto = id("heroe-mago-fuego");
                        adquirirComo(jugador(), producto, "q-1").andExpect(status().isForbidden());
                        adquirirComo(admin(), producto, "q-1").andExpect(status().isForbidden());
                        adquirirComo(null, producto, "q-1").andExpect(status().isUnauthorized());
                        adquirirComo(servicio(), producto, null).andExpect(status().isBadRequest());
                        adquirirComo(servicio(), producto, "x".repeat(101)).andExpect(status().isBadRequest());

                        String inexistente = UUID.randomUUID().toString();
                        adquirirComo(servicio(), inexistente, "q-404").andExpect(status().isNotFound());
                        // Un 404 no consume la clave.
                        assertTrue(adquisiciones.findById("q-404").isEmpty());
                }

                @Test
                @DisplayName("N peticiones concurrentes con claves distintas contra un tiraje K: exactamente K aceptadas")
                void concurrentesContraTirajeK() throws Exception {
                        int tiraje = 5;
                        int peticiones = 24;
                        Producto producto = productoConTiraje("armadura-medico-bata-de-cirujano", tiraje);
                        CountDownLatch salida = new CountDownLatch(1);

                        List<EstadoAdquisicion> resultados = new ArrayList<>();
                        try (ExecutorService ejecutor = Executors.newFixedThreadPool(peticiones)) {
                                List<Future<EstadoAdquisicion>> futuros = new ArrayList<>();
                                for (int i = 0; i < peticiones; i++) {
                                        String clave = "concurrente-" + i;
                                        futuros.add(ejecutor.submit(() -> {
                                                salida.await();
                                                return adquirir.adquirir(producto.id(), clave, "ms-ecommerce").estado();
                                        }));
                                }
                                salida.countDown();
                                for (Future<EstadoAdquisicion> futuro : futuros) {
                                        resultados.add(futuro.get());
                                }
                        }

                        assertEquals(tiraje, resultados.stream().filter(EstadoAdquisicion.ACEPTADA::equals).count());
                        assertEquals(peticiones - tiraje,
                                resultados.stream().filter(EstadoAdquisicion.AGOTADO::equals).count());
                        assertEquals(0, productos.findById(producto.id()).orElseThrow().tiraje());
                }

                /** Un producto sembrado al que un administrador le pone un tiraje limitado. */
                private Producto productoConTiraje(String slug, int tiraje) throws Exception {
                        mvc.perform(patch("/api/v1/productos/{id}", id(slug))
                                        .header("Authorization", admin())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"tiraje\": " + tiraje + "}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.tiraje").value(tiraje));
                        return productoDeLaSemilla(slug);
                }
        }
}
