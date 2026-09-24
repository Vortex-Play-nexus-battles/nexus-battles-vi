package nexus.inventario.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import nexus.inventario.aplicacion.BuscarElementosInventario;
import nexus.inventario.aplicacion.CatalogoDeProductosEnMemoria;
import nexus.inventario.aplicacion.ConsultarElementoInventario;
import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import nexus.inventario.aplicacion.GestionarInventario;
import nexus.inventario.aplicacion.GestionarBloqueoSubasta;
import nexus.inventario.aplicacion.RepositorioInventariosEnMemoria;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InventarioApiTest {

    private RepositorioInventariosEnMemoria repositorio;
    private GestionarInventario gestion;
    private CatalogoDeProductosEnMemoria catalogo;
    private GestionarBloqueoSubasta gestionBloqueo;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        // Los ids arbitrarios de estas pruebas existen en el catalogo de prueba
        // con el tipo que cada una ya declaraba: lo que verifican no cambia.
        catalogo = new CatalogoDeProductosEnMemoria()
                .registrar("113609ca-3c15-42f5-b427-d452ce06f9a8", TipoElementoInventario.ITEM)
                .registrar("producto-1", TipoElementoInventario.ITEM)
                .registrar("producto-2", TipoElementoInventario.HEROE)
                .registrar("producto-casco", TipoElementoInventario.ARMADURA)
                .registrar("producto-armadura", TipoElementoInventario.ARMADURA)
                .registrar("producto-bruma", TipoElementoInventario.ITEM)
                .registrar("producto-solar", TipoElementoInventario.ARMA)
                .registrar("producto-ajeno", TipoElementoInventario.ITEM);
        gestion = new GestionarInventario(repositorio, catalogo);
        gestionBloqueo = new GestionarBloqueoSubasta(repositorio);
        mvc = MockMvcBuilders.standaloneSetup(
                        new InventarioController(
                                gestion,
                                new ConsultarInventarioPaginado(repositorio),
                                new BuscarElementosInventario(repositorio),
                                new ConsultarElementoInventario(repositorio),
                                new IdentidadDelLlamador()),
                        new BloqueoSubastaController(gestionBloqueo))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    @DisplayName("GET por id entrega los datos estables que necesita subastas")
    void consultarElementoPorId() throws Exception {
        String propietarioUid = "ae8df97e-9ab9-4af5-bd2a-25715919e5f1";
        String productoId = "113609ca-3c15-42f5-b427-d452ce06f9a8";
        ElementoInventario creado = gestion.crear(
                propietarioUid, productoId, TipoElementoInventario.ITEM, "Amuleto");

        mvc.perform(get("/api/v1/inventario/elementos/{elementoId}", creado.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementoId").value(creado.id()))
                .andExpect(jsonPath("$.productoId").value(productoId))
                .andExpect(jsonPath("$.propietarioUid").value(propietarioUid))
                .andExpect(jsonPath("$.enUso").value(false))
                .andExpect(jsonPath("$.disponible").value(true));
    }

    @Test
    @DisplayName("POST crea en el inventario indicado por la identidad autenticada")
    void crearElemento() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-1","tipo":"ITEM","nombrePropio":"Amuleto de Niebla"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productoId").value("producto-1"))
                .andExpect(jsonPath("$.nombrePropio").value("Amuleto de Niebla"));

        mvc.perform(post("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-2","tipo":"HEROE","nombrePropio":"Mi guerrero"}
                                """))
                .andExpect(status().isCreated());

        assertEquals(1, repositorio.buscarPorPropietario("jugador-A").orElseThrow().elementos().size());
        assertEquals(1, repositorio.buscarPorPropietario("jugador-B").orElseThrow().elementos().size());
    }

    @Test
    @DisplayName("POST conserva la parte de una armadura para su ranura")
    void crearArmaduraConParte() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-casco","tipo":"ARMADURA",
                                 "nombrePropio":"Casco de Bruma","parteArmadura":"CASCO"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parteArmadura").value("CASCO"));
    }

    @Test
    @DisplayName("POST rechaza una armadura sin parte mientras productos no resuelve la ranura")
    void rechazarArmaduraSinParte() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-armadura","tipo":"ARMADURA",
                                 "nombrePropio":"Armadura sin parte"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud invalida"));

        assertEquals(0, repositorio.buscarPorPropietario("jugador-A").stream().count());
    }

    @Test
    @DisplayName("PATCH permite al propietario modificar su elemento")
    void modificarElementoPropio() throws Exception {
        ElementoInventario creado = gestion.crear(
                "jugador-A", "producto-1", TipoElementoInventario.ITEM, "Amuleto de Niebla");

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Amuleto de Bruma\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombrePropio").value("Amuleto de Bruma"));
    }

    @Test
    @DisplayName("crear y modificar se reflejan al consultar la vitrina")
    void escriturasSeReflejanEnLaVitrina() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-1","tipo":"ITEM","nombrePropio":"Amuleto de Niebla"}
                                """))
                .andExpect(status().isCreated());

        ElementoInventario creado = repositorio.buscarPorPropietario("jugador-A")
                .orElseThrow().elementos().getFirst();

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Amuleto de Bruma\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .param("pagina", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos.length()").value(1))
                .andExpect(jsonPath("$.elementos[0].id").value(creado.id()))
                .andExpect(jsonPath("$.elementos[0].nombrePropio").value("Amuleto de Bruma"));
    }

    @Test
    @DisplayName("PATCH rechaza modificar el elemento de otro jugador y conserva sus datos")
    void rechazarModificacionAjena() throws Exception {
        ElementoInventario elementoDeB = gestion.crear(
                "jugador-B", "producto-1", TipoElementoInventario.ITEM, "Daga Corta");

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", elementoDeB.id())
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Daga Robada\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Inventario ajeno"))
                .andExpect(jsonPath("$.detail").value("No tienes permiso sobre ese inventario."));

        assertEquals("Daga Corta", repositorio.buscarPorPropietario("jugador-B").orElseThrow()
                .elementos().getFirst().nombrePropio());
    }

    @Test
    @DisplayName("una solicitud sin identidad autenticada responde 401")
    void identidadRequerida() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-1","tipo":"ITEM","nombrePropio":"Daga"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Identidad requerida"));
    }

    @Test
    @DisplayName("POST con datos invalidos responde 400 sin escribir")
    void solicitudInvalida() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":\"\",\"tipo\":\"ITEM\",\"nombrePropio\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud invalida"));

        assertEquals(0, repositorio.buscarPorPropietario("jugador-A").stream().count());
    }

    @Test
    @DisplayName("PATCH de un elemento inexistente responde 404")
    void elementoInexistente() throws Exception {
        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", "elemento-inexistente")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Otro nombre\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Elemento no encontrado"));
    }

    @Test
    @DisplayName("una escritura fallida responde 503 y conserva el estado completo anterior")
    void escrituraFallidaEsAtomica() throws Exception {
        ElementoInventario creado = gestion.crear(
                "jugador-A", "producto-1", TipoElementoInventario.ITEM, "Amuleto original");
        repositorio.fallarSiguienteGuardado();

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Amuleto incompleto\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Inventario no disponible"));

        ElementoInventario persistido = repositorio.buscarPorPropietario("jugador-A")
                .orElseThrow().elemento(creado.id());
        assertEquals("Amuleto original", persistido.nombrePropio());
        assertEquals("producto-1", persistido.productoId());
    }

    @Test
    @DisplayName("un producto publicado figura no disponible y rechaza modificacion y eliminacion")
    void productoBloqueadoEnSubasta() throws Exception {
        UUID propietarioUid = UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
        ElementoInventario creado = gestion.crear(
                propietarioUid.toString(), "producto-1", TipoElementoInventario.ITEM, "Amuleto");

        mvc.perform(put("/api/v1/inventario/elementos/{elementoId}/bloqueo-subasta", creado.id())
                        .header("Idempotency-Key", "publicar-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propietarioUid":"%s",
                                 "subastaId":"89d9040d-52e0-44ae-8d8c-8ec033978afb"}
                                """.formatted(propietarioUid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.subastaId")
                        .value("89d9040d-52e0-44ae-8d8c-8ec033978afb"));

        mvc.perform(get("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", propietarioUid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos[0].disponible").value(false));

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .with(ComoLlamador.servicio()).header("X-User-Name", propietarioUid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Amuleto cambiado\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Producto no disponible"));

        mvc.perform(delete("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .with(ComoLlamador.servicio()).header("X-User-Name", propietarioUid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Producto no disponible"));
    }

    @Test
    @DisplayName("el aviso de cierre libera el producto y permite volver a modificarlo")
    void liberarProductoAlCerrarSubasta() throws Exception {
        UUID propietarioUid = UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
        ElementoInventario creado = gestion.crear(
                propietarioUid.toString(), "producto-1", TipoElementoInventario.ITEM, "Amuleto");
        UUID subastaId = UUID.fromString("89d9040d-52e0-44ae-8d8c-8ec033978afb");
        mvc.perform(put("/api/v1/inventario/elementos/{elementoId}/bloqueo-subasta", creado.id())
                        .header("Idempotency-Key", "publicar-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propietarioUid":"%s","subastaId":"%s"}
                                """.formatted(propietarioUid, subastaId)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/inventario/elementos/{elementoId}/bloqueo-subasta/{subastaId}",
                        creado.id(), subastaId)
                        .header("Idempotency-Key", "cerrar-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.subastaId").doesNotExist());

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .with(ComoLlamador.servicio()).header("X-User-Name", propietarioUid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Amuleto liberado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombrePropio").value("Amuleto liberado"));
    }

    @Test
    @DisplayName("un aviso ajeno no libera el producto y responde conflicto")
    void noLiberarProductoConOtraSubasta() throws Exception {
        UUID propietarioUid = UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
        ElementoInventario creado = gestion.crear(
                propietarioUid.toString(), "producto-1", TipoElementoInventario.ITEM, "Amuleto");
        UUID subastaVigente = UUID.fromString("89d9040d-52e0-44ae-8d8c-8ec033978afb");
        gestionBloqueo.bloquear(
                propietarioUid, creado.id(), subastaVigente, "publicar-1");

        mvc.perform(delete("/api/v1/inventario/elementos/{elementoId}/bloqueo-subasta/{subastaId}",
                        creado.id(), "51326b9d-1aa2-4d8a-bb7d-d3ad593f902d")
                        .header("Idempotency-Key", "cerrar-anterior"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "El aviso no corresponde a la subasta que mantiene el bloqueo."));

        assertFalse(repositorio.buscarPorElementoId(creado.id())
                .orElseThrow().elemento(creado.id()).disponible());
    }

    @Test
    @DisplayName("sin respuesta de subastas la disponibilidad conserva el bloqueo registrado")
    void conservarBloqueoSiSubastasNoResponde() throws Exception {
        UUID propietarioUid = UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
        ElementoInventario creado = gestion.crear(
                propietarioUid.toString(), "producto-1", TipoElementoInventario.ITEM, "Reliquia");
        UUID subastaId = UUID.fromString("89d9040d-52e0-44ae-8d8c-8ec033978afb");
        gestionBloqueo.bloquear(propietarioUid, creado.id(), subastaId, "publicar-1");

        mvc.perform(get("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", propietarioUid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos[0].disponible").value(false))
                .andExpect(jsonPath("$.elementos[0].subastaId").value(subastaId.toString()));

        mvc.perform(delete("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .with(ComoLlamador.servicio()).header("X-User-Name", propietarioUid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Producto no disponible"));

        assertEquals(subastaId.toString(), repositorio.buscarPorElementoId(creado.id())
                .orElseThrow().elemento(creado.id()).subastaId());
    }

    @Test
    @DisplayName("GET entrega la vitrina en paginas de dieciseis del inventario propio")
    void consultarPaginaDeLaVitrina() throws Exception {
        for (int i = 0; i < 20; i++) {
            catalogo.registrar("producto-" + i, TipoElementoInventario.ARMA);
            gestion.crear("jugador-A", "producto-" + i,
                    TipoElementoInventario.ARMA, "Espada " + i);
        }

        mvc.perform(get("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .param("pagina", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos.length()").value(16))
                .andExpect(jsonPath("$.totalElementos").value(20))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.ultima").value(false));
    }

    @Test
    @DisplayName("GET de un jugador sin inventario responde 200 con la pagina vacia")
    void consultarSinInventario() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-nuevo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos.length()").value(0))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    @DisplayName("GET sin identidad no expone ningun inventario")
    void consultarSinIdentidad() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET con una pagina negativa es una solicitud invalida")
    void consultarPaginaNegativa() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .param("pagina", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET busqueda localiza elementos propios por la informacion registrada")
    void buscarElementosPropios() throws Exception {
        gestion.crear("jugador-A", "producto-bruma", TipoElementoInventario.ITEM,
                "Amuleto de Bruma");
        gestion.crear("jugador-A", "producto-solar", TipoElementoInventario.ARMA,
                "Espada Solar");
        gestion.crear("jugador-B", "producto-ajeno", TipoElementoInventario.ITEM,
                "Amuleto de Bruma ajeno");

        mvc.perform(get("/api/v1/inventario/elementos/busqueda")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .param("criterio", "bruma")
                        .param("pagina", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos.length()").value(1))
                .andExpect(jsonPath("$.elementos[0].productoId").value("producto-bruma"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.tamanio").value(16));
    }

    @Test
    @DisplayName("GET busqueda rechaza menos de cuatro caracteres")
    void buscarConCriterioCorto() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos/busqueda")
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .param("criterio", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Criterio de busqueda invalido"))
                .andExpect(jsonPath("$.detail")
                        .value("Ingresa al menos cuatro caracteres para buscar."));
    }

    @Test
    @DisplayName("GET busqueda exige la identidad autenticada")
    void buscarSinIdentidad() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos/busqueda")
                        .param("criterio", "bruma"))
                .andExpect(status().isUnauthorized());
    }

    // --- Solo productos del catalogo (RG-074: los items los crea el disenador) ---

    private org.springframework.test.web.servlet.ResultActions crearComoJugadorA(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/inventario/elementos")
                .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    @Test
    @DisplayName("POST con un producto del catalogo crea el elemento con el tipo del producto")
    void crearConProductoDelCatalogo() throws Exception {
        catalogo.registrar("arma-guerrero-tanque-espada-de-una-mano", TipoElementoInventario.ARMA);

        crearComoJugadorA("""
                {"productoId":"arma-guerrero-tanque-espada-de-una-mano","tipo":"ARMA","nombrePropio":"Mi espada"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productoId").value("arma-guerrero-tanque-espada-de-una-mano"))
                .andExpect(jsonPath("$.tipo").value("ARMA"));
    }

    @Test
    @DisplayName("POST con un producto que no esta en el catalogo responde 422 legible y no guarda nada")
    void rechazarProductoInexistente() throws Exception {
        crearComoJugadorA("""
                {"productoId":"espada-corta","tipo":"ARMA","nombrePropio":"Espada inventada"}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Producto inexistente"))
                .andExpect(jsonPath("$.detail").value("El producto no existe en el catalogo."));

        assertEquals(0, repositorio.buscarPorPropietario("jugador-A").stream().count());
    }

    @Test
    @DisplayName("POST con un producto suspendido responde 409 con mensaje propio")
    void rechazarProductoSuspendido() throws Exception {
        catalogo.registrar("item-retirado", TipoElementoInventario.ITEM, "SUSPENDIDO");

        crearComoJugadorA("""
                {"productoId":"item-retirado","tipo":"ITEM","nombrePropio":"Reliquia"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Producto suspendido"))
                .andExpect(jsonPath("$.detail").value(
                        "El producto esta suspendido en el catalogo y no se puede agregar al inventario."));

        assertEquals(0, repositorio.buscarPorPropietario("jugador-A").stream().count());
    }

    @Test
    @DisplayName("POST con un tipo distinto al del producto responde 400 legible")
    void rechazarTipoQueNoCoincide() throws Exception {
        catalogo.registrar("heroe-guerrero-tanque", TipoElementoInventario.HEROE);

        crearComoJugadorA("""
                {"productoId":"heroe-guerrero-tanque","tipo":"ARMA","nombrePropio":"Heroe disfrazado"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Tipo no coincide"))
                .andExpect(jsonPath("$.detail").value("El tipo no coincide con el producto del catalogo."));

        assertEquals(0, repositorio.buscarPorPropietario("jugador-A").stream().count());
    }

    @Test
    @DisplayName("POST con el servicio de productos caido responde 503 legible y no acepta a ciegas")
    void rechazarSiProductosNoResponde() throws Exception {
        catalogo.caer();

        crearComoJugadorA("""
                {"productoId":"producto-1","tipo":"ITEM","nombrePropio":"Amuleto"}
                """)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Catalogo no disponible"))
                .andExpect(jsonPath("$.detail").value(
                        "No fue posible verificar el producto en el catalogo. Intenta nuevamente."));

        assertEquals(0, repositorio.buscarPorPropietario("jugador-A").stream().count());
    }

    @Test
    @DisplayName("PATCH no cambia el producto de un elemento aunque la peticion lo traiga")
    void modificarNoCambiaElProducto() throws Exception {
        ElementoInventario creado = gestion.crear(
                "jugador-A", "producto-1", TipoElementoInventario.ITEM, "Amuleto");

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .with(ComoLlamador.servicio()).header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombrePropio":"Amuleto nuevo","productoId":"espada-corta"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value("producto-1"));
    }
}
