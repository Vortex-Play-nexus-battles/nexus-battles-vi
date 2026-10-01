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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import nexus.api.ProductoCreado;
import nexus.aplicacion.ProductoMapper;
import nexus.aplicacion.ProyeccionDeProductos;
import nexus.aplicacion.Visibilidad;
import nexus.dominio.EstadoProducto;
import nexus.dominio.OrigenProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
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

    /**
     * La version del contenido del catalogo real. Sube cuando cambia un
     * producto del JSON: la 2 es la que quita el precio de venta a las epicas
     * (RG-085) en las bases que ya las tenian sembradas con la 1.
     */
    private static final int VERSION_DEL_CATALOGO = 2;

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
        assertEquals(VERSION_DEL_CATALOGO, resultado.version());
        assertEquals(56, resultado.insertados().size());
        assertTrue(resultado.rechazados().isEmpty(), resultado.rechazados().toString());
        assertEquals(56, base.size());
        assertTrue(base.values().stream().allMatch(p -> p.estado() == EstadoProducto.ACTIVO));
        // B4: cada documento sembrado dice de donde salio y con que version.
        assertTrue(base.values().stream().allMatch(p -> p.origen() == OrigenProducto.SEMILLA));
        assertTrue(base.values().stream().allMatch(
                p -> Integer.valueOf(VERSION_DEL_CATALOGO).equals(p.semillaVersion())));
        // La tienda solo proyecta productos con precio en pesos mayor que cero;
        // las epicas son la excepcion (RG-085): no se venden, ver ningunaEpicaSeVende.
        assertTrue(base.values().stream().filter(p -> p.tipo() != TipoProducto.EPICA).allMatch(
                p -> p.precioMonedaReal() != null && p.precioMonedaReal().signum() > 0));

        Producto tanque = base.get(MapeadorDelCatalogo.identificador("heroe-guerrero-tanque"));
        assertEquals("Guerrero Tanque", tanque.nombre());
        assertEquals(TipoProducto.HEROE, tanque.tipo());
    }

    @Test
    @DisplayName("RG-085: la semilla no deja ninguna epica con precio de venta (ni en pesos ni en creditos), pero las 8 existen activas")
    void ningunaEpicaSeVende() {
        semilla(true, CATALOGO_REAL).sembrar();

        List<Producto> epicas = base.values().stream().filter(p -> p.tipo() == TipoProducto.EPICA).toList();

        assertEquals(8, epicas.size(), "las misiones las entregan por productoId: tienen que existir");
        for (Producto epica : epicas) {
            assertEquals(EstadoProducto.ACTIVO, epica.estado(), epica.nombre());
            assertEquals(0, epica.precioCreditos(), epica.nombre() + " no tiene precio en creditos");
            assertEquals(0, BigDecimal.ZERO.compareTo(epica.precioMonedaReal()),
                    epica.nombre() + " no tiene precio en pesos: la vitrina no la muestra");
            assertFalse(epica.premium(), epica.nombre());
        }
        // El resto del catalogo sigue a la venta (la vitrina pide pesos > 0).
        assertEquals(48, base.values().stream()
                .filter(p -> p.tipo() != TipoProducto.EPICA)
                .filter(p -> p.precioMonedaReal().signum() > 0)
                .count());
    }

    @Test
    @DisplayName("RG-085: aunque el JSON de la semilla traiga precio para las epicas, la semilla no se lo pone")
    void unPrecioDeEpicaEnElJsonNoSeAplica() {
        String json = leerCatalogoReal()
                .replace("\"creditos\": {", "\"creditos\": {\"EPICA\": 500,")
                .replace("\"cop\": {", "\"cop\": {\"EPICA\": 10000,");
        assertTrue(json.contains("\"EPICA\": 10000"), "la prueba necesita que el JSON traiga el precio");
        Resource conPrecio = new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));

        semilla(true, conPrecio).sembrar();

        assertTrue(base.values().stream().filter(p -> p.tipo() == TipoProducto.EPICA)
                .allMatch(p -> p.precioCreditos() == 0 && p.precioMonedaReal().signum() == 0));
    }

    @Test
    @DisplayName("RG-085: la version nueva quita el precio a las epicas ya sembradas con la 1 y no toca la que edito un administrador")
    void laVersionNuevaQuitaElPrecioALasEpicasYaSembradas() {
        semilla(true, CATALOGO_REAL).sembrar();
        // DEV tal como quedo con la semilla 1: todo con la version 1 y las epicas a 500 creditos / 10.000 COP.
        base.replaceAll((id, p) -> p.tipo() == TipoProducto.EPICA
                ? conPrecioYVersionDeSemilla(p, 500, new BigDecimal("10000"), 1)
                : conPrecioYVersionDeSemilla(p, p.precioCreditos(), p.precioMonedaReal(), 1));
        String editada = MapeadorDelCatalogo.identificador("epica-mago-hielo-frio-concentrado");
        Producto delAdmin = editadoPor(base.get(editada), "uid-del-admin", "Frio concentrado (promocion)");
        base.put(editada, delAdmin);

        ResultadoSemilla resultado = semilla(true, CATALOGO_REAL).sembrar();

        assertEquals(List.of(editada), resultado.respetados());
        assertSame(delAdmin, base.get(editada), "lo que edito un administrador no se toca");
        assertEquals(500, base.get(editada).precioCreditos());
        assertEquals(0, new BigDecimal("10000").compareTo(base.get(editada).precioMonedaReal()));
        List<Producto> epicas = base.values().stream()
                .filter(p -> p.tipo() == TipoProducto.EPICA && !p.id().equals(editada)).toList();
        assertEquals(7, epicas.size());
        for (Producto epica : epicas) {
            assertEquals(0, epica.precioCreditos(), epica.nombre());
            assertEquals(0, BigDecimal.ZERO.compareTo(epica.precioMonedaReal()), epica.nombre());
            assertEquals(VERSION_DEL_CATALOGO, epica.semillaVersion(), epica.nombre());
            assertEquals(EstadoProducto.ACTIVO, epica.estado(), epica.nombre());
        }
    }

    @Test
    @DisplayName("RG-085: la vista publica de lo sembrado no muestra ninguna epica con precio")
    void laProyeccionPublicaNoTraePrecioDeEpicas() {
        semilla(true, CATALOGO_REAL).sembrar();
        ProyeccionDeProductos proyeccion = new ProyeccionDeProductos(
                Mappers.getMapper(ProductoMapper.class),
                Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));

        List<ProductoCreado> epicas = base.values().stream()
                .map(p -> proyeccion.proyectar(p, Visibilidad.PUBLICA))
                .filter(p -> p.tipo() == TipoProducto.EPICA)
                .toList();

        assertEquals(8, epicas.size());
        for (ProductoCreado epica : epicas) {
            assertEquals(0, epica.precioCreditos(), epica.nombre());
            assertEquals(0, BigDecimal.ZERO.compareTo(epica.precioMonedaReal()), epica.nombre());
        }
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

        ResultadoSemilla v2 = semilla(true, conVersionYPrecioDeArma(VERSION_DEL_CATALOGO + 1, 999)).sembrar();

        assertEquals(VERSION_DEL_CATALOGO + 1, v2.version());
        assertEquals(List.of(tocado), v2.respetados());
        assertEquals(55, v2.actualizados().size());
        assertSame(editado, base.get(tocado), "lo que edito el administrador no se toca");
        Producto puesto = base.get(intacto);
        assertEquals(999, puesto.precioCreditos());
        assertEquals(VERSION_DEL_CATALOGO + 1, puesto.semillaVersion());
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

        semilla(true, conVersionYPrecioDeArma(VERSION_DEL_CATALOGO + 1, 999)).sembrar();

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
        assertEquals(VERSION_DEL_CATALOGO, base.get(id).semillaVersion());
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

        assertEquals(VERSION_DEL_CATALOGO, catalogo.versionDelContenido());
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
                    .replaceFirst("\"version\": \\d+,", "\"version\": " + version + ",")
                    .replaceFirst("\"ARMA\": 300", "\"ARMA\": " + precioArma);
            return new ByteArrayResource(texto.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String leerCatalogoReal() {
        try (InputStream json = CATALOGO_REAL.getInputStream()) {
            return new String(json.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** El producto como lo dejo una version anterior de la semilla: otro precio y otra version. */
    private static Producto conPrecioYVersionDeSemilla(
            Producto p, Integer precioCreditos, BigDecimal precioMonedaReal, int semillaVersion) {
        return new Producto(p.id(), p.nombre(), p.imagen(), p.descripcion(), p.tipo(), p.tiraje(),
                precioCreditos, precioMonedaReal, p.premium(), p.prototipo(), p.heroe(),
                p.costoPoder(), p.multiplicadorNivel(), p.turnosCarga(), p.turnosRecarga(),
                p.efectoGeneral(), p.efectoPotenciado(), p.defensa(), p.parte(), p.efecto(),
                p.poderDeAtaque(), p.tasaDeCaida(), p.estado(), p.version(), p.creadoEn(),
                p.modificadoEn(), p.promocion(), p.origen(), semillaVersion, p.modificadoPor(),
                p.estadoAnteriorSuspension(), p.reservasRecientes());
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
