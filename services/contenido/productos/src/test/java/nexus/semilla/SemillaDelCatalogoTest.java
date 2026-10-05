package nexus.semilla;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import nexus.dominio.EstadoProducto;
import nexus.dominio.OrigenProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

/**
 * La semilla del catalogo inicial contra un repositorio en memoria: inserta lo
 * que falta, pone al dia lo que quedo en una version anterior sin que nadie lo
 * editara, respeta lo que edito un administrador, y apagada no toca la base.
 * La misma semilla contra MongoDB real: SemillaDelCatalogoIT.
 */
class SemillaDelCatalogoTest {

    private static final Resource CATALOGO_REAL =
            new ClassPathResource("semilla/catalogo-inicial.json");

    private ValidatorFactory fabrica;
    private Validator validador;
    private ProductoRepository repositorio;
    private Map<String, Producto> base;

    @BeforeEach
    void prepararRepositorioEnMemoria() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
        base = new LinkedHashMap<>();
        repositorio = mock(ProductoRepository.class);
        when(repositorio.findById(anyString()))
                .thenAnswer(i -> Optional.ofNullable(base.get(i.getArgument(0, String.class))));
        when(repositorio.insert(any(Producto.class))).thenAnswer(i -> {
            Producto p = i.getArgument(0, Producto.class);
            if (base.containsKey(p.id())) {
                throw new DuplicateKeyException("ya existe " + p.id());
            }
            base.put(p.id(), p);
            return p;
        });
        // El reemplazo condicional de MongoDB: solo si la version sigue siendo
        // la leida y ningun administrador lo edito.
        when(repositorio.reemplazarSemillaSiNoCambio(any(Producto.class), anyInt())).thenAnswer(i -> {
            Producto reemplazo = i.getArgument(0, Producto.class);
            int esperada = i.getArgument(1, Integer.class);
            Producto actual = base.get(reemplazo.id());
            if (actual == null || actual.version() != esperada || actual.editadoPorAdministrador()) {
                return false;
            }
            base.put(reemplazo.id(), reemplazo);
            return true;
        });
    }

    @AfterEach
    void cerrar() {
        fabrica.close();
    }

    private SemillaDelCatalogo semilla(boolean habilitada, Resource archivo) {
        return new SemillaDelCatalogo(
                repositorio, new MapeadorDelCatalogo(), validador, habilitada, archivo);
    }

    @Test
    @DisplayName("con la propiedad en false no lee ni escribe nada")
    void apagadaNoHaceNada() throws Exception {
        ResultadoSemilla resultado = semilla(false, CATALOGO_REAL).sembrar();

        assertFalse(resultado.habilitada());
        assertTrue(resultado.insertados().isEmpty());
        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("el arranque de Spring (run) respeta la propiedad apagada")
    void runApagadoNoHaceNada() throws Exception {
        semilla(false, CATALOGO_REAL).run(null);

        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("si la base falla, run() lo registra y el servicio arranca igual, sin sembrar")
    void fallaDeLaBaseNoTumbaElArranque() {
        when(repositorio.findById(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Mongo no responde"));
        SemillaDelCatalogo semilla = semilla(true, CATALOGO_REAL);

        assertDoesNotThrow(() -> semilla.run(null));

        verify(repositorio, never()).insert(any(Producto.class));
        assertTrue(base.isEmpty());
    }

    @Test
    @DisplayName("si el JSON no se puede leer, run() lo registra y el servicio arranca igual")
    void jsonIlegibleNoTumbaElArranque() {
        Resource roto = new ByteArrayResource("{ esto no es json".getBytes(StandardCharsets.UTF_8));
        SemillaDelCatalogo semilla = semilla(true, roto);

        assertDoesNotThrow(() -> semilla.run(null));

        verify(repositorio, never()).insert(any(Producto.class));
    }

    @Test
    @DisplayName("sembrar() si propaga la falla, para que se vea en las pruebas")
    void sembrarPropagaLaFalla() {
        when(repositorio.findById(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Mongo no responde"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> semilla(true, CATALOGO_REAL).sembrar());
    }

    @Test
    @DisplayName("primera corrida: crea los 56 productos, activos, con el id derivado del slug y las marcas de la semilla")
    void primeraCorrida() {
        ResultadoSemilla resultado = semilla(true, CATALOGO_REAL).sembrar();

        assertTrue(resultado.habilitada());
        assertEquals(1, resultado.version());
        assertEquals(56, resultado.insertados().size());
        assertTrue(resultado.rechazados().isEmpty(), resultado.rechazados().toString());
        assertEquals(56, base.size());
        assertTrue(base.values().stream().allMatch(p -> p.estado() == EstadoProducto.ACTIVO));
        // B4: cada documento sembrado dice de donde salio y con que version.
        assertTrue(base.values().stream().allMatch(p -> p.origen() == OrigenProducto.SEMILLA));
        assertTrue(base.values().stream().allMatch(p -> Integer.valueOf(1).equals(p.semillaVersion())));
        // La tienda solo proyecta productos con precio en pesos mayor que cero.
        assertTrue(base.values().stream().allMatch(
                p -> p.precioMonedaReal() != null && p.precioMonedaReal().signum() > 0));

        Producto tanque = base.get(MapeadorDelCatalogo.identificador("heroe-guerrero-tanque"));
        assertEquals("Guerrero Tanque", tanque.nombre());
        assertEquals(TipoProducto.HEROE, tanque.tipo());
    }

    @Test
    @DisplayName("segunda corrida: no inserta ni actualiza nada, no duplica")
    void segundaCorridaIdempotente() {
        semilla(true, CATALOGO_REAL).sembrar();
        Map<String, Producto> despuesDeLaPrimera = new LinkedHashMap<>(base);

        ResultadoSemilla segunda = semilla(true, CATALOGO_REAL).sembrar();

        assertTrue(segunda.insertados().isEmpty());
        assertTrue(segunda.actualizados().isEmpty());
        assertEquals(56, segunda.existentes().size());
        assertEquals(despuesDeLaPrimera, base);
        verify(repositorio, never()).reemplazarSemillaSiNoCambio(any(Producto.class), anyInt());
    }

    @Test
    @DisplayName("B4: una version nueva de la semilla pone al dia lo que nadie edito y respeta lo que edito un administrador")
    void versionNuevaActualizaLoNoTocadoYRespetaLoTocado() {
        semilla(true, CATALOGO_REAL).sembrar();
        String tocado = MapeadorDelCatalogo.identificador("arma-guerrero-tanque-espada-de-una-mano");
        String intacto = MapeadorDelCatalogo.identificador("arma-guerrero-armas-piedra-de-afilar");
        Producto editado = editadoPor(base.get(tocado), "uid-del-admin", "Espada editada por el admin");
        base.put(tocado, editado);

        ResultadoSemilla v2 = semilla(true, conVersionYPrecioDeArma(2, 999)).sembrar();

        assertEquals(2, v2.version());
        assertEquals(List.of(tocado), v2.respetados());
        assertEquals(55, v2.actualizados().size());
        assertSame(editado, base.get(tocado), "lo que edito el administrador no se toca");
        Producto puesto = base.get(intacto);
        assertEquals(999, puesto.precioCreditos());
        assertEquals(2, puesto.semillaVersion());
        assertEquals(2, puesto.version(), "la puesta al dia sube la version, como un guardado");
        assertNull(puesto.modificadoPor());
    }

    @Test
    @DisplayName("B4: la puesta al dia conserva el estado (una suspension), la fecha de alta y las reservas")
    void laPuestaAlDiaConservaLoQueNoEsDeLaSemilla() {
        semilla(true, CATALOGO_REAL).sembrar();
        String id = MapeadorDelCatalogo.identificador("arma-guerrero-armas-piedra-de-afilar");
        Producto sembrado = base.get(id);
        Producto suspendido = new Producto(sembrado.id(), sembrado.nombre(), sembrado.imagen(),
                sembrado.descripcion(), sembrado.tipo(), sembrado.tiraje(), sembrado.precioCreditos(),
                sembrado.precioMonedaReal(), sembrado.premium(), sembrado.prototipo(), sembrado.heroe(),
                sembrado.costoPoder(), sembrado.multiplicadorNivel(), sembrado.turnosCarga(),
                sembrado.turnosRecarga(), sembrado.efectoGeneral(), sembrado.efectoPotenciado(),
                sembrado.defensa(), sembrado.parte(), sembrado.efecto(), sembrado.poderDeAtaque(),
                sembrado.tasaDeCaida(), EstadoProducto.SUSPENDIDO, 3, sembrado.creadoEn(),
                sembrado.modificadoEn(), null, OrigenProducto.SEMILLA, 1, null,
                EstadoProducto.ACTIVO, List.of("compra-1"));
        base.put(id, suspendido);

        semilla(true, conVersionYPrecioDeArma(2, 999)).sembrar();

        Producto puesto = base.get(id);
        assertEquals(EstadoProducto.SUSPENDIDO, puesto.estado());
        assertEquals(EstadoProducto.ACTIVO, puesto.estadoAnteriorSuspension());
        assertEquals(sembrado.creadoEn(), puesto.creadoEn());
        assertEquals(List.of("compra-1"), puesto.reservasRecientes());
        assertEquals(4, puesto.version());
        assertEquals(999, puesto.precioCreditos());
    }

    @Test
    @DisplayName("B4: un documento sembrado antes de las marcas se adopta si no se edito (version 1)")
    void adoptaLoSembradoAntesDeLasMarcas() {
        String id = MapeadorDelCatalogo.identificador("heroe-medico");
        base.put(id, sinMarcas(id, "Médico", 1));

        ResultadoSemilla resultado = semilla(true, CATALOGO_REAL).sembrar();

        assertTrue(resultado.actualizados().contains(id));
        assertEquals(OrigenProducto.SEMILLA, base.get(id).origen());
        assertEquals(1, base.get(id).semillaVersion());
        assertEquals(55, resultado.insertados().size());
    }

    @Test
    @DisplayName("un producto sembrado antes de las marcas y editado por un admin (version > 1) no se pisa")
    void noPisaProductoEditado() {
        String id = MapeadorDelCatalogo.identificador("arma-guerrero-tanque-espada-de-una-mano");
        Producto editado = new Producto(id, "Espada editada por el admin", "img", "otra",
                TipoProducto.ARMA, 7, 999, null, false, null, null, null, null, null, null,
                null, null, null, null, null, 9, null, EstadoProducto.SUSPENDIDO, 4,
                Instant.EPOCH, Instant.EPOCH);
        base.put(id, editado);

        ResultadoSemilla resultado = semilla(true, CATALOGO_REAL).sembrar();

        assertSame(editado, base.get(id));
        assertTrue(resultado.respetados().contains(id));
        assertEquals(55, resultado.insertados().size());
        verify(repositorio, never()).save(any(Producto.class));
    }

    @Test
    @DisplayName("B4: lo que dio de alta un administrador con el mismo id no es asunto de la semilla")
    void noTocaLoDeAdministracion() {
        String id = MapeadorDelCatalogo.identificador("heroe-medico");
        Producto delAdmin = conOrigen(sinMarcas(id, "Médico de la casa", 1), OrigenProducto.ADMINISTRACION);
        base.put(id, delAdmin);

        ResultadoSemilla resultado = semilla(true, CATALOGO_REAL).sembrar();

        assertSame(delAdmin, base.get(id));
        assertTrue(resultado.existentes().contains(id));
    }

    @Test
    @DisplayName("B4: si alguien escribe el producto entre la lectura y la puesta al dia, no se pisa")
    void carreraConUnaEdicion() {
        String id = MapeadorDelCatalogo.identificador("heroe-medico");
        base.put(id, sinMarcas(id, "Médico", 1));
        when(repositorio.reemplazarSemillaSiNoCambio(any(Producto.class), anyInt())).thenReturn(false);

        ResultadoSemilla resultado = semilla(true, CATALOGO_REAL).sembrar();

        assertTrue(resultado.existentes().contains(id));
        assertNull(base.get(id).origen());
    }

    @Test
    @DisplayName("si otra instancia lo inserto entre la consulta y el alta, cuenta como existente")
    void carreraConOtraInstancia() {
        String id = MapeadorDelCatalogo.identificador("heroe-medico");
        when(repositorio.findById(anyString())).thenReturn(Optional.empty());
        base.put(id, new Producto(id, "ya", "img", "d", TipoProducto.HEROE, -1, 1, null, false,
                "Médico", null, null, null, null, null, null, null, null, null, null, null, null,
                EstadoProducto.ACTIVO, 1, Instant.EPOCH, Instant.EPOCH));

        ResultadoSemilla resultado = semilla(true, CATALOGO_REAL).sembrar();

        assertTrue(resultado.existentes().contains(id));
        assertEquals(55, resultado.insertados().size());
    }

    @Test
    @DisplayName("una entrada que viola el alta se rechaza sin tumbar el arranque ni las demas")
    void entradaInvalidaSeRechaza() {
        String json = """
                {
                  "heroes": [
                    {"tipo": "HEROE", "nombre": "Médico", "prototipo": "Médico", "id": "heroe-medico",
                     "estadisticasNivel1": {"poder": "10"}, "fuente": "x, Tabla 6"}
                  ],
                  "armas": [
                    {"tipo": "ARMA", "nombre": "Sin caida", "heroe": "Médico", "grupo": "SET 1",
                     "efectos": "+1 al ataque", "fuente": "x, Tabla 11", "id": "arma-sin-caida"}
                  ],
                  "epicas": [
                    {"tipo": "EPICA", "nombre": "Huerfana", "heroe": "Heroe Inexistente",
                     "id": "epica-huerfana", "efectoGeneral": "a", "efectoPotenciado": "b",
                     "probabilidadMaster": "1%", "fuente": "x, Tabla 20"}
                  ],
                  "preciosDemostracion": {"creditos": {"HEROE": 1000, "ARMA": 300, "EPICA": 500},
                                          "tiraje": -1, "premium": false}
                }
                """;
        Resource archivo = new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));

        ResultadoSemilla resultado = semilla(true, archivo).sembrar();

        assertEquals(1, resultado.insertados().size());
        assertEquals(2, resultado.rechazados().size(), resultado.rechazados().toString());
        assertTrue(resultado.rechazados().get(0).startsWith("arma-sin-caida"),
                resultado.rechazados().toString());
        assertTrue(resultado.rechazados().get(1).startsWith("epica-huerfana"),
                resultado.rechazados().toString());
    }

    @Test
    @DisplayName("leer() entiende el JSON real en UTF-8 (tildes y enie intactas) y su version")
    void leeElJsonReal() throws IOException {
        CatalogoInicial catalogo;
        try (InputStream json = CATALOGO_REAL.getInputStream()) {
            catalogo = SemillaDelCatalogo.leer(json);
        }

        assertEquals(1, catalogo.versionDelContenido());
        assertEquals("Pícaro Veneno", catalogo.heroes().get(4).prototipo());
        assertEquals("1d4", catalogo.heroes().get(0).estadisticasNivel1().get("daño"));
        assertEquals(300, catalogo.preciosDemostracion().creditos().get("ARMA"));
        assertEquals(0, new java.math.BigDecimal("6000")
                .compareTo(catalogo.preciosDemostracion().cop().get("ARMA")));
        assertEquals(-1, catalogo.preciosDemostracion().tiraje());
    }

    @Test
    @DisplayName("un archivo sin version se lee como la version 1")
    void sinVersionEsLaUno() {
        CatalogoInicial sinVersion = new CatalogoInicial(List.of(), List.of(), List.of(), List.of(), List.of(), null);

        assertEquals(1, sinVersion.versionDelContenido());
    }

    @Test
    @DisplayName("B4: application.properties la deja ENCENDIDA salvo que CATALOGO_SEMILLA diga otra cosa")
    void propiedadPorOmisionEncendida() throws IOException {
        Properties propiedades = new Properties();
        try (InputStream entrada = new ClassPathResource("application.properties").getInputStream()) {
            propiedades.load(entrada);
        }

        assertEquals("${CATALOGO_SEMILLA:true}", propiedades.getProperty("catalogo.semilla.habilitada"));
    }

    @Test
    @DisplayName("la tabla de identificadores del README coincide con la derivacion")
    void tablaDeIdentificadoresAlDia() throws IOException {
        String tabla = Files.readString(Path.of("docs/catalogo-inicial-identificadores.md"));
        CatalogoInicial catalogo;
        try (InputStream json = CATALOGO_REAL.getInputStream()) {
            catalogo = SemillaDelCatalogo.leer(json);
        }

        for (CatalogoInicial.EntradaCatalogo entrada : catalogo.todas()) {
            String fila = "| `" + entrada.id() + "` | `"
                    + MapeadorDelCatalogo.identificador(entrada.id()) + "` |";
            assertTrue(tabla.contains(fila), "falta o esta mal la fila: " + fila);
        }
    }

    // ------------------------------------------------------------- ayudantes

    /** El catalogo real con otra version y otro precio en creditos para las armas. */
    private static Resource conVersionYPrecioDeArma(int version, int precioArma) {
        try (InputStream json = CATALOGO_REAL.getInputStream()) {
            String texto = new String(json.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceFirst("\"version\": 1,", "\"version\": " + version + ",")
                    .replaceFirst("\"ARMA\": 300", "\"ARMA\": " + precioArma);
            return new ByteArrayResource(texto.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Producto editadoPor(Producto p, String autor, String nombre) {
        return new Producto(p.id(), nombre, p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                p.precioCreditos(), p.precioMonedaReal(), p.premium(), p.prototipo(), p.heroe(),
                p.costoPoder(), p.multiplicadorNivel(), p.turnosCarga(), p.turnosRecarga(),
                p.efectoGeneral(), p.efectoPotenciado(), p.defensa(), p.parte(), p.efecto(),
                p.poderDeAtaque(), p.tasaDeCaida(), p.estado(), p.version() + 1, p.creadoEn(),
                Instant.now(), p.promocion(), p.origen(), p.semillaVersion(), autor,
                p.estadoAnteriorSuspension(), p.reservasRecientes());
    }

    /** Un heroe como lo dejaba la semilla anterior a B4: sin origen ni version de semilla. */
    private static Producto sinMarcas(String id, String prototipo, int version) {
        return new Producto(id, prototipo, "img", "d", TipoProducto.HEROE, -1, 1000, null, false,
                prototipo, null, null, null, null, null, null, null, null, null, null, null, null,
                EstadoProducto.ACTIVO, version, Instant.EPOCH, Instant.EPOCH);
    }

    private static Producto conOrigen(Producto p, OrigenProducto origen) {
        return new Producto(p.id(), p.nombre(), p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                p.precioCreditos(), p.precioMonedaReal(), p.premium(), p.prototipo(), p.heroe(),
                p.costoPoder(), p.multiplicadorNivel(), p.turnosCarga(), p.turnosRecarga(),
                p.efectoGeneral(), p.efectoPotenciado(), p.defensa(), p.parte(), p.efecto(),
                p.poderDeAtaque(), p.tasaDeCaida(), p.estado(), p.version(), p.creadoEn(),
                p.modificadoEn(), p.promocion(), origen, p.semillaVersion(), p.modificadoPor(),
                p.estadoAnteriorSuspension(), p.reservasRecientes());
    }
}
