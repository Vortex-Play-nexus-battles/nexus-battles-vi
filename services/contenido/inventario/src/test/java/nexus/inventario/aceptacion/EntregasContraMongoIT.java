package nexus.inventario.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import nexus.inventario.api.ComoLlamador;
import nexus.inventario.aplicacion.ResolutorDeEstadisticasHeroe;
import nexus.inventario.aplicacion.ResolutorDeProducto;
import nexus.inventario.aplicacion.ResolutorDeProducto.DetalleProducto;
import nexus.inventario.aplicacion.SolicitudDeEntrega;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.EstadisticasHeroe;
import nexus.inventario.dominio.FormulaDetalle;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.LineaDeEntrega;
import nexus.inventario.dominio.OrigenDeEntrega;
import nexus.inventario.dominio.RepositorioDeEntregas;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B4 — {@code POST /api/v1/inventario/entregas} contra MongoDB de verdad
 * (standalone, sin transacciones multi-documento), con la cadena de seguridad
 * real y tokens firmados. El catalogo es un doble: lo que se verifica es la
 * idempotencia, el todo o nada y que no se pierdan escrituras.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class EntregasContraMongoIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    private static final String RUTA = "/api/v1/inventario/entregas";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RepositorioDeInventarios inventarios;

    @Autowired
    private RepositorioDeEntregas entregas;

    @MockitoBean
    private ResolutorDeProducto productos;

    @MockitoBean
    private ResolutorDeEstadisticasHeroe heroes;

    private static final Map<String, DetalleProducto> CATALOGO = Map.of(
            "espada", new DetalleProducto("Espada de una mano", "ARMA", null, "ACTIVO"),
            "peto", new DetalleProducto("Peto de escamas", "ARMADURA", null, "ACTIVO", "PECHO"),
            "guerrero", new DetalleProducto("Guerrero Tanque", "HEROE", "Guerrero Tanque", "ACTIVO"),
            "retirado", new DetalleProducto("Retirado", "ITEM", null, "SUSPENDIDO"));

    @BeforeEach
    void catalogoDePrueba() {
        when(productos.resolver(anyString())).thenAnswer(invocacion -> {
            DetalleProducto detalle = CATALOGO.get(invocacion.<String>getArgument(0));
            if (detalle == null) {
                throw new nexus.inventario.aplicacion.ProductoNoEncontradoException(
                        invocacion.getArgument(0), "No existe");
            }
            return detalle;
        });
    }

    private static String cuerpo(UUID uid, String referencia, String productoId, int cantidad) {
        return """
                {"uid":"%s","origen":"COMPRA","referencia":"%s",
                 "productos":[{"productoId":"%s","cantidad":%d}]}
                """.formatted(uid, referencia, productoId, cantidad);
    }

    private MvcResult entregar(String clave, String cuerpo) throws Exception {
        return mvc.perform(post(RUTA)
                        .header("Authorization", ComoLlamador.portadorDeServicio("ms-ecommerce"))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andReturn();
    }

    private static String texto(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Inventario inventarioDe(UUID uid) {
        return inventarios.buscarPorPropietario(uid.toString()).orElseThrow();
    }

    private long entregasConClave(String clave) {
        return mongoTemplate.getCollection("entregas").countDocuments(Filters.eq("clave", clave));
    }

    @Test
    @DisplayName("la coleccion entregas tiene indice unico en la clave")
    void indiceUnicoDeLaClave() throws Exception {
        entregar("indice-" + UUID.randomUUID(), cuerpo(UUID.randomUUID(), "orden-indice", "espada", 1));

        List<Document> indices = mongoTemplate.getCollection("entregas").listIndexes().into(new ArrayList<>());

        assertThat(indices).anySatisfy(indice -> {
            assertThat(indice.get("key", Document.class)).isEqualTo(new Document("clave", 1));
            assertThat(indice.getBoolean("unique")).isTrue();
        });
    }

    @Test
    @DisplayName("201 la primera vez, 200 con la misma entrega despues; una sola entrega y los elementos una vez")
    void entregaYRepeticion() throws Exception {
        UUID uid = UUID.randomUUID();
        String clave = "orden-500-" + UUID.randomUUID();

        MvcResult primera = entregar(clave, cuerpo(uid, "orden-500", "peto", 2));
        MvcResult segunda = entregar(clave, cuerpo(uid, "orden-500", "peto", 2));

        assertThat(primera.getResponse().getStatus()).isEqualTo(201);
        assertThat(segunda.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(texto(segunda), "$.id")).isEqualTo(JsonPath.read(texto(primera), "$.id"));
        assertThat(entregasConClave(clave)).isEqualTo(1);
        List<ElementoInventario> elementos = inventarioDe(uid).elementos();
        assertThat(elementos).hasSize(2).allSatisfy(elemento -> {
            assertThat(elemento.origen()).isEqualTo(OrigenDeEntrega.COMPRA);
            assertThat(elemento.referencia()).isEqualTo("orden-500");
            assertThat(elemento.parteArmadura()).hasToString("PECHO");
        });

        // El jugador lo ve en su vitrina, con el rastro de por donde llego.
        mvc.perform(get("/api/v1/inventario/elementos")
                        .header("Authorization", ComoLlamador.portadorDeJugador("comprador", uid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.elementos[0].origen").value("COMPRA"))
                .andExpect(jsonPath("$.elementos[0].referencia").value("orden-500"));
    }

    @Test
    @DisplayName("con otro cuerpo, 409; un producto suspendido o inexistente no deja nada guardado")
    void rechazosSinRastro() throws Exception {
        UUID uid = UUID.randomUUID();
        String clave = "orden-501-" + UUID.randomUUID();
        entregar(clave, cuerpo(uid, "orden-501", "espada", 1));

        assertThat(entregar(clave, cuerpo(uid, "orden-501", "espada", 2)).getResponse().getStatus()).isEqualTo(409);

        UUID otro = UUID.randomUUID();
        String conSuspendido = """
                {"uid":"%s","origen":"COFRE","referencia":"cofre-1",
                 "productos":[{"productoId":"espada","cantidad":1},{"productoId":"retirado","cantidad":1}]}
                """.formatted(otro);
        assertThat(entregar("cofre-1-" + otro, conSuspendido).getResponse().getStatus()).isEqualTo(409);
        assertThat(entregar("cofre-2-" + otro, cuerpo(otro, "cofre-2", "espada-corta", 1)).getResponse().getStatus())
                .isEqualTo(422);

        assertThat(inventarios.buscarPorPropietario(otro.toString())).isEmpty();
        assertThat(entregasConClave("cofre-1-" + otro) + entregasConClave("cofre-2-" + otro)).isZero();
        assertThat(inventarioDe(uid).elementos()).hasSize(1);
    }

    @Test
    @DisplayName("16 peticiones simultaneas con la misma clave: una entrega, los elementos una sola vez")
    void concurrentesConLaMismaClave() throws Exception {
        UUID uid = UUID.randomUUID();
        String clave = "orden-502-" + UUID.randomUUID();
        String cuerpo = cuerpo(uid, "orden-502", "espada", 3);
        int peticiones = 16;
        ExecutorService hilos = Executors.newFixedThreadPool(peticiones);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<MvcResult>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < peticiones; i++) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return entregar(clave, cuerpo);
                }));
            }
            salida.countDown();
            Set<String> ids = ConcurrentHashMap.newKeySet();
            for (Future<MvcResult> resultado : resultados) {
                MvcResult respuesta = resultado.get(60, TimeUnit.SECONDS);
                assertThat(respuesta.getResponse().getStatus()).isIn(200, 201);
                ids.add(JsonPath.read(texto(respuesta), "$.id"));
            }
            assertThat(ids).hasSize(1);
        } finally {
            hilos.shutdownNow();
        }

        assertThat(entregasConClave(clave)).isEqualTo(1);
        assertThat(inventarioDe(uid).elementos()).hasSize(3);
        assertThat(inventarioDe(uid).entregas()).hasSize(1);
    }

    @Test
    @DisplayName("si el proceso cayo tras escribir el inventario y antes de completar, el reintento la completa sin duplicar")
    void reintentoTrasAplicarSinCompletar() throws Exception {
        UUID uid = UUID.randomUUID();
        String clave = "orden-503-" + UUID.randomUUID();
        SolicitudDeEntrega solicitud = new SolicitudDeEntrega(uid, OrigenDeEntrega.COMPRA, "orden-503",
                List.of(new LineaDeEntrega("espada", 2)));
        List<ElementoInventario> planeados = List.of(
                ElementoInventario.entregado("planeado-1", "espada", TipoElementoInventario.ARMA,
                        "Espada de una mano", null, OrigenDeEntrega.COMPRA, "orden-503"),
                ElementoInventario.entregado("planeado-2", "espada", TipoElementoInventario.ARMA,
                        "Espada de una mano", null, OrigenDeEntrega.COMPRA, "orden-503"));
        Entrega pendiente = Entrega.pendiente("entrega-503-" + uid, clave, solicitud.huella(), uid.toString(),
                OrigenDeEntrega.COMPRA, "orden-503", solicitud.productos(), planeados, "ms-ecommerce", Instant.now());
        entregas.registrar(pendiente);
        inventarios.guardar(Inventario.vacio(uid.toString()).recibirEntrega(pendiente.id(), planeados));

        MvcResult reintento = entregar(clave, cuerpo(uid, "orden-503", "espada", 2));

        assertThat(reintento.getResponse().getStatus()).isEqualTo(201);
        assertThat((String) JsonPath.read(texto(reintento), "$.id")).isEqualTo(pendiente.id());
        assertThat((List<String>) JsonPath.read(texto(reintento), "$.elementos[*].id"))
                .containsExactly("planeado-1", "planeado-2");
        assertThat(inventarioDe(uid).elementos()).extracting(ElementoInventario::id)
                .containsExactly("planeado-1", "planeado-2");
        assertThat(entregas.buscarPorClave(clave).orElseThrow().completada()).isTrue();
    }

    @Test
    @DisplayName("si el proceso cayo antes de escribir el inventario, el reintento aplica los elementos planeados una vez")
    void reintentoTrasRegistrarSinAplicar() throws Exception {
        UUID uid = UUID.randomUUID();
        String clave = "orden-504-" + UUID.randomUUID();
        SolicitudDeEntrega solicitud = new SolicitudDeEntrega(uid, OrigenDeEntrega.COMPRA, "orden-504",
                List.of(new LineaDeEntrega("guerrero", 1)));
        List<ElementoInventario> planeados = List.of(ElementoInventario.entregado("heroe-planeado", "guerrero",
                TipoElementoInventario.HEROE, "Guerrero Tanque", null, OrigenDeEntrega.COMPRA, "orden-504"));
        entregas.registrar(Entrega.pendiente("entrega-504-" + uid, clave, solicitud.huella(), uid.toString(),
                OrigenDeEntrega.COMPRA, "orden-504", solicitud.productos(), planeados, "ms-ecommerce", Instant.now()));

        assertThat(entregar(clave, cuerpo(uid, "orden-504", "guerrero", 1)).getResponse().getStatus()).isEqualTo(201);
        assertThat(entregar(clave, cuerpo(uid, "orden-504", "guerrero", 1)).getResponse().getStatus()).isEqualTo(200);

        List<ElementoInventario> elementos = inventarioDe(uid).elementos();
        assertThat(elementos).extracting(ElementoInventario::id).containsExactly("heroe-planeado");
        assertThat(elementos.getFirst().nivel()).isEqualTo(1);
        assertThat(elementos.getFirst().experiencia()).isZero();
    }

    @Test
    @DisplayName("entregas y renombres a la vez sobre el mismo inventario: no se pierde ningun elemento entregado")
    void entregasYRenombresConcurrentesNoPierdenNada() throws Exception {
        UUID uid = UUID.randomUUID();
        inventarios.guardar(Inventario.vacio(uid.toString()).agregar(
                new ElementoInventario("renombrable-" + uid, "espada", TipoElementoInventario.ARMA, "Original")));
        String portadorDelJugador = ComoLlamador.portadorDeJugador("jugador-concurrente", uid);
        int entregasEnParalelo = 8;
        int renombres = 8;
        ExecutorService hilos = Executors.newFixedThreadPool(entregasEnParalelo + renombres);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<MvcResult>> deEntregas = new ArrayList<>();
        List<Future<MvcResult>> deRenombres = new ArrayList<>();
        try {
            for (int i = 0; i < entregasEnParalelo; i++) {
                String clave = "concurrente-" + i + "-" + uid;
                String cuerpo = cuerpo(uid, "orden-concurrente-" + i, "espada", 1);
                deEntregas.add(hilos.submit(() -> {
                    salida.await();
                    return entregar(clave, cuerpo);
                }));
            }
            for (int i = 0; i < renombres; i++) {
                String nombre = "Nombre " + i;
                Callable<MvcResult> renombrar = () -> {
                    salida.await();
                    return mvc.perform(patch("/api/v1/inventario/elementos/{id}", "renombrable-" + uid)
                                    .header("Authorization", portadorDelJugador)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"nombrePropio\":\"" + nombre + "\"}"))
                            .andReturn();
                };
                deRenombres.add(hilos.submit(renombrar));
            }
            salida.countDown();
            for (int i = 0; i < entregasEnParalelo; i++) {
                int estado = deEntregas.get(i).get(60, TimeUnit.SECONDS).getResponse().getStatus();
                // Una entrega que agoto sus reintentos es 503 y queda PENDIENTE:
                // quien entrega reintenta con la misma clave y la termina.
                assertThat(estado).isIn(201, 503);
                if (estado == 503) {
                    assertThat(entregar("concurrente-" + i + "-" + uid,
                            cuerpo(uid, "orden-concurrente-" + i, "espada", 1)).getResponse().getStatus())
                            .isEqualTo(201);
                }
            }
            for (Future<MvcResult> renombre : deRenombres) {
                // Un renombre que choco con otra escritura es 503 sin cambios: nada se pisa.
                assertThat(renombre.get(60, TimeUnit.SECONDS).getResponse().getStatus()).isIn(200, 503);
            }
        } finally {
            hilos.shutdownNow();
        }

        Inventario resultado = inventarioDe(uid);
        assertThat(resultado.elementos()).hasSize(1 + entregasEnParalelo);
        assertThat(resultado.entregas()).hasSize(entregasEnParalelo);
        assertThat(resultado.elementos()).filteredOn(elemento -> elemento.origen() == OrigenDeEntrega.COMPRA)
                .extracting(ElementoInventario::referencia)
                .containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.range(0, entregasEnParalelo)
                        .mapToObj(i -> "orden-concurrente-" + i).toList());
    }

    @Test
    @DisplayName("un inventario guardado antes de B4 (sin version, sin entregas, heroe sin nivel) recibe entregas y se lee en nivel 1")
    void documentoAnteriorSinVersion() throws Exception {
        UUID uid = UUID.randomUUID();
        String heroeId = "heroe-antiguo-" + uid;
        inventarios.guardar(Inventario.vacio(uid.toString())
                .agregar(new ElementoInventario(heroeId, "guerrero", TipoElementoInventario.HEROE, "Veterano")));
        mongoTemplate.getCollection("inventarios").updateOne(Filters.eq("propietarioId", uid.toString()),
                Updates.combine(Updates.unset("version"), Updates.unset("entregas"),
                        Updates.unset("elementos.$[].nivel"), Updates.unset("elementos.$[].experiencia")));
        Document antes = mongoTemplate.getCollection("inventarios")
                .find(Filters.eq("propietarioId", uid.toString())).first();
        assertThat(antes).doesNotContainKey("version").doesNotContainKey("entregas");

        assertThat(entregar("antiguo-" + uid, cuerpo(uid, "orden-antigua", "espada", 1)).getResponse().getStatus())
                .isEqualTo(201);

        Document despues = mongoTemplate.getCollection("inventarios")
                .find(Filters.eq("propietarioId", uid.toString())).first();
        assertThat(despues).containsKey("version");
        assertThat(despues.getList("entregas", String.class)).hasSize(1);
        Inventario inventario = inventarioDe(uid);
        assertThat(inventario.elementos()).hasSize(2);
        assertThat(inventario.elemento(heroeId).nivel()).isEqualTo(1);
        assertThat(inventario.elemento(heroeId).experiencia()).isZero();

        // Y sus estadisticas salen del nivel que guarda el inventario.
        when(heroes.resolver(eq("Guerrero Tanque"), eq(1))).thenReturn(new EstadisticasHeroe(
                10, 44, 11, new FormulaDetalle(10, 1, 6), new FormulaDetalle(0, 1, 4), null));
        mvc.perform(get("/api/v1/inventario/heroes/{id}/estadisticas", heroeId)
                        .header("Authorization", ComoLlamador.portadorDeJugador("veterano", uid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value(1))
                .andExpect(jsonPath("$.vida").value(44));
    }
}
