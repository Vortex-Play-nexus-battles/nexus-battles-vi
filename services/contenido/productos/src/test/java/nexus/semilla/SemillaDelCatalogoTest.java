package nexus.semilla;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Map;
import java.util.Properties;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import nexus.dominio.EstadoProducto;
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
 * La semilla del catalogo inicial contra un repositorio en memoria: solo
 * inserta lo que falta, nunca pisa, y apagada no toca la base.
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
        when(repositorio.existsById(anyString()))
                .thenAnswer(i -> base.containsKey(i.getArgument(0, String.class)));
        when(repositorio.insert(any(Producto.class))).thenAnswer(i -> {
            Producto p = i.getArgument(0, Producto.class);
            if (base.containsKey(p.id())) {
                throw new DuplicateKeyException("ya existe " + p.id());
            }
            base.put(p.id(), p);
            return p;
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
        when(repositorio.existsById(anyString()))
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
        when(repositorio.existsById(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Mongo no responde"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> semilla(true, CATALOGO_REAL).sembrar());
    }

    @Test
    @DisplayName("primera corrida: crea los 56 productos, activos, con el id derivado del slug")
    void primeraCorrida() {
        ResultadoSemilla resultado = semilla(true, CATALOGO_REAL).sembrar();

        assertTrue(resultado.habilitada());
        assertEquals(56, resultado.insertados().size());
        assertTrue(resultado.rechazados().isEmpty(), resultado.rechazados().toString());
        assertEquals(56, base.size());
        assertTrue(base.values().stream().allMatch(p -> p.estado() == EstadoProducto.ACTIVO));
        // La tienda solo proyecta productos con precio en pesos mayor que cero.
        assertTrue(base.values().stream().allMatch(
                p -> p.precioMonedaReal() != null && p.precioMonedaReal().signum() > 0));

        Producto tanque = base.get(MapeadorDelCatalogo.identificador("heroe-guerrero-tanque"));
        assertEquals("Guerrero Tanque", tanque.nombre());
        assertEquals(TipoProducto.HEROE, tanque.tipo());
    }

    @Test
    @DisplayName("segunda corrida: no inserta nada ni duplica")
    void segundaCorridaIdempotente() {
        semilla(true, CATALOGO_REAL).sembrar();

        ResultadoSemilla segunda = semilla(true, CATALOGO_REAL).sembrar();

        assertTrue(segunda.insertados().isEmpty());
        assertEquals(56, segunda.existentes().size());
        assertEquals(56, base.size());
    }

    @Test
    @DisplayName("un producto ya existente y editado por un admin no se pisa")
    void noPisaProductoEditado() {
        String id = MapeadorDelCatalogo.identificador("arma-guerrero-tanque-espada-de-una-mano");
        Producto editado = new Producto(id, "Espada editada por el admin", "img", "otra",
                TipoProducto.ARMA, 7, 999, null, false, null, null, null, null, null, null,
                null, null, null, null, null, 9, null, EstadoProducto.SUSPENDIDO, 4,
                Instant.EPOCH, Instant.EPOCH);
        base.put(id, editado);

        ResultadoSemilla resultado = semilla(true, CATALOGO_REAL).sembrar();

        assertSame(editado, base.get(id));
        assertTrue(resultado.existentes().contains(id));
        assertEquals(55, resultado.insertados().size());
        verify(repositorio, never()).save(any(Producto.class));
    }

    @Test
    @DisplayName("si otra instancia lo inserto entre la consulta y el alta, cuenta como existente")
    void carreraConOtraInstancia() {
        when(repositorio.existsById(anyString())).thenReturn(false);
        String id = MapeadorDelCatalogo.identificador("heroe-medico");
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
    @DisplayName("leer() entiende el JSON real en UTF-8 (tildes y enie intactas)")
    void leeElJsonReal() throws IOException {
        CatalogoInicial catalogo;
        try (InputStream json = CATALOGO_REAL.getInputStream()) {
            catalogo = SemillaDelCatalogo.leer(json);
        }

        assertEquals("Pícaro Veneno", catalogo.heroes().get(4).prototipo());
        assertEquals("1d4", catalogo.heroes().get(0).estadisticasNivel1().get("daño"));
        assertEquals(300, catalogo.preciosDemostracion().creditos().get("ARMA"));
        assertEquals(0, new java.math.BigDecimal("6000")
                .compareTo(catalogo.preciosDemostracion().cop().get("ARMA")));
        assertEquals(-1, catalogo.preciosDemostracion().tiraje());
    }

    @Test
    @DisplayName("application.properties la deja apagada salvo que CATALOGO_SEMILLA diga otra cosa")
    void propiedadPorOmisionApagada() throws IOException {
        Properties propiedades = new Properties();
        try (InputStream entrada = new ClassPathResource("application.properties").getInputStream()) {
            propiedades.load(entrada);
        }

        assertEquals("${CATALOGO_SEMILLA:false}", propiedades.getProperty("catalogo.semilla.habilitada"));
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
}
