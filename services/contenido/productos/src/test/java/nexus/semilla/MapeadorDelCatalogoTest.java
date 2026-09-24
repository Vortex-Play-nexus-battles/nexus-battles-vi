package nexus.semilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import nexus.api.SolicitudCrearProducto;
import nexus.dominio.EstadoProducto;
import nexus.dominio.ParteArmadura;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.semilla.CatalogoInicial.EntradaCatalogo;
import nexus.semilla.CatalogoInicial.PreciosDemostracion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mapeo de cada tipo del catalogo inicial al {@link Producto} del dominio,
 * validado con las MISMAS reglas del alta ({@link SolicitudCrearProducto}).
 */
class MapeadorDelCatalogoTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    private final MapeadorDelCatalogo mapeador = new MapeadorDelCatalogo();

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    // ---------------------------------------------------------------- datos

    private static final EntradaCatalogo HEROE = new EntradaCatalogo(
            "heroe-guerrero-tanque", "HEROE", "Guerrero Tanque", "Guerrero Tanque",
            null, null, null, null, null, null, null, null,
            estadisticas("10", "44", "11", "10 + 1d6", "1d4", "-"),
            "Boveda/02-producto/reglas/heroes.md, Tabla 6");

    private static final EntradaCatalogo ARMA_CON_ATAQUE = new EntradaCatalogo(
            "arma-guerrero-tanque-espada-de-una-mano", "ARMA", "Espada de una mano", null,
            "Guerrero Tanque", "SET 1", null, "+1 al ataque, +1% de crítico al ataque", "3%",
            null, null, null, null, "Boveda/02-producto/reglas/armas.md, Tabla 8");

    private static final EntradaCatalogo ARMA_SIN_ATAQUE = new EntradaCatalogo(
            "arma-guerrero-tanque-escudo-de-dragon", "ARMA", "Escudo de dragón", null,
            "Guerrero Tanque", "SET 2", null, "+1 a la defensa", "5%",
            null, null, null, null, "Boveda/02-producto/reglas/armas.md, Tabla 8");

    private static final EntradaCatalogo ARMADURA = new EntradaCatalogo(
            "armadura-mago-fuego-caida-de-fuego", "ARMADURA", "Caída de fuego", null,
            "Mago Fuego", "GEAR 2", "Pantalón", "+1 a la defensa, +1 de vida", "3%",
            null, null, null, null, "Boveda/02-producto/reglas/armaduras.md, Tabla 13");

    private static final EntradaCatalogo ITEM = new EntradaCatalogo(
            "item-guerrero-tanque-pinchos-de-escudo", "ITEM", "Pinchos de escudo", null,
            "Guerrero Tanque", "EQUIPMENT 1", null,
            "Si el ataque del oponente es menor que la defensa del guerrero, el oponente recibe +1 de daño.",
            "20%", null, null, null, null, "Boveda/02-producto/reglas/items.md, Tabla 16");

    private static final EntradaCatalogo EPICA = new EntradaCatalogo(
            "epica-guerrero-tanque-golpe-de-defensa", "EPICA", "Golpe de defensa", null,
            "Guerrero Tanque", null, null, null, null,
            "+1 al ataque", "+4 al daño, +2% de crítico", "0.04%", null,
            "Boveda/02-producto/reglas/epicas.md, Tabla 20: Habilidades Épicas de los Héroes");

    private static final PreciosDemostracion PRECIOS = new PreciosDemostracion(
            Map.of("HEROE", 1000, "EPICA", 500, "ARMA", 300, "ARMADURA", 250, "ITEM", 150),
            Map.of("HEROE", new BigDecimal("20000"), "EPICA", new BigDecimal("10000"),
                    "ARMA", new BigDecimal("6000"), "ARMADURA", new BigDecimal("5000"),
                    "ITEM", new BigDecimal("3000")),
            -1, false);

    private static void assertPesos(String esperado, BigDecimal real) {
        assertTrue(real != null && new BigDecimal(esperado).compareTo(real) == 0,
                () -> "precioMonedaReal esperado " + esperado + " y fue " + real);
    }

    private static final CatalogoInicial CATALOGO = new CatalogoInicial(
            List.of(HEROE), List.of(ARMA_CON_ATAQUE, ARMA_SIN_ATAQUE), List.of(ARMADURA),
            List.of(ITEM), List.of(EPICA), PRECIOS);

    private static Map<String, String> estadisticas(
            String poder, String vida, String defensa, String ataque, String dano, String sanar) {
        Map<String, String> mapa = new LinkedHashMap<>();
        mapa.put("poder", poder);
        mapa.put("vida", vida);
        mapa.put("defensa", defensa);
        mapa.put("ataque", ataque);
        mapa.put("daño", dano);
        mapa.put("sanar", sanar);
        return mapa;
    }

    private SolicitudCrearProducto mapearValido(EntradaCatalogo entrada) {
        SolicitudCrearProducto solicitud = mapeador.aSolicitud(entrada, CATALOGO);
        Set<ConstraintViolation<SolicitudCrearProducto>> violaciones = validador.validate(solicitud);
        assertTrue(violaciones.isEmpty(), () -> entrada.id() + " viola el alta: " + violaciones);
        return solicitud;
    }

    // ------------------------------------------------------ identificadores

    @Test
    @DisplayName("el identificador sale del slug: UUID estable, igual en cada corrida")
    void identificadorEstable() {
        // Valores calculados aparte (MD5 del nombre, UUID version 3), no con
        // este mismo codigo: si alguien cambia la derivacion, esto lo detecta.
        assertEquals("aec4fbd2-9615-352a-9f2b-3fad781e123c",
                MapeadorDelCatalogo.identificador("heroe-guerrero-tanque"));
        assertEquals("1647b2ea-096d-37e7-b580-0172e4c62313",
                MapeadorDelCatalogo.identificador("arma-guerrero-tanque-espada-de-una-mano"));
        assertEquals(3, UUID.fromString(
                MapeadorDelCatalogo.identificador("item-medico-benditas")).version());
    }

    // ------------------------------------------------------- por cada tipo

    @Test
    @DisplayName("HEROE: prototipo, estadisticas de nivel 1 en la descripcion, precio de demostracion")
    void heroe() {
        SolicitudCrearProducto s = mapearValido(HEROE);

        assertEquals(TipoProducto.HEROE, s.tipo());
        assertEquals("Guerrero Tanque", s.nombre());
        assertEquals("Guerrero Tanque", s.prototipo());
        assertEquals(1000, s.precioCreditos());
        assertEquals(-1, s.tiraje());
        assertFalse(s.premium());
        assertPesos("20000", s.precioMonedaReal());
        assertTrue(s.descripcion().contains("poder 10"), s.descripcion());
        assertTrue(s.descripcion().contains("ataque 10 + 1d6"), s.descripcion());
        assertFalse(s.descripcion().contains("sanar"), "los valores '-' no se escriben");
        assertTrue(s.descripcion().contains("Tabla 6"), s.descripcion());
        assertTrue(s.imagen().startsWith("data:image/svg+xml,"), s.imagen());
    }

    @Test
    @DisplayName("ARMA: poder de ataque desde '+1 al ataque' y tasa de caida desde '3%'")
    void armaConAtaque() {
        SolicitudCrearProducto s = mapearValido(ARMA_CON_ATAQUE);

        assertEquals(TipoProducto.ARMA, s.tipo());
        assertEquals(1, s.poderDeAtaque());
        assertEquals(new BigDecimal("3"), s.tasaDeCaida());
        assertEquals(300, s.precioCreditos());
        assertPesos("6000", s.precioMonedaReal());
        assertNull(s.heroe(), "el alta de un ARMA no admite heroe");
        assertTrue(s.descripcion().contains("+1 al ataque, +1% de crítico al ataque"), s.descripcion());
        assertTrue(s.descripcion().contains("Guerrero Tanque"), s.descripcion());
    }

    @Test
    @DisplayName("ARMA sin '+N al ataque': el minimo que acepta el dominio")
    void armaSinAtaque() {
        SolicitudCrearProducto s = mapearValido(ARMA_SIN_ATAQUE);

        assertEquals(MapeadorDelCatalogo.PODER_DE_ATAQUE_NEUTRO, s.poderDeAtaque());
        assertEquals(new BigDecimal("5"), s.tasaDeCaida());
        assertTrue(s.descripcion().contains("+1 a la defensa"), s.descripcion());
    }

    @Test
    @DisplayName("ARMADURA: defensa desde '+1 a la defensa' y parte 'Pantalón' -> PANTALON")
    void armadura() {
        SolicitudCrearProducto s = mapearValido(ARMADURA);

        assertEquals(TipoProducto.ARMADURA, s.tipo());
        assertEquals(1, s.defensa());
        assertEquals(ParteArmadura.PANTALON, s.parte());
        assertEquals(new BigDecimal("3"), s.tasaDeCaida());
        assertEquals(250, s.precioCreditos());
        assertPesos("5000", s.precioMonedaReal());
        assertTrue(s.descripcion().contains("+1 a la defensa, +1 de vida"), s.descripcion());
    }

    @Test
    @DisplayName("ITEM: el efecto va literal al campo efecto; tasa de caida 20%")
    void item() {
        SolicitudCrearProducto s = mapearValido(ITEM);

        assertEquals(TipoProducto.ITEM, s.tipo());
        assertEquals(ITEM.efectos(), s.efecto());
        assertEquals(new BigDecimal("20"), s.tasaDeCaida());
        assertEquals(150, s.precioCreditos());
        assertPesos("3000", s.precioMonedaReal());
    }

    @Test
    @DisplayName("EPICA: efectos literales, dos turnos de recarga y heroe = id del producto HEROE")
    void epica() {
        SolicitudCrearProducto s = mapearValido(EPICA);

        assertEquals(TipoProducto.EPICA, s.tipo());
        assertEquals("+1 al ataque", s.efectoGeneral());
        assertEquals("+4 al daño, +2% de crítico", s.efectoPotenciado());
        assertEquals(2, s.turnosRecarga());
        assertEquals(MapeadorDelCatalogo.identificador("heroe-guerrero-tanque"), s.heroe());
        assertEquals(500, s.precioCreditos());
        assertPesos("10000", s.precioMonedaReal());
        assertFalse(s.premium(), "con precio en pesos sigue sin ser premium");
        assertTrue(s.descripcion().contains("0.04%"), s.descripcion());
    }

    @Test
    @DisplayName("si el JSON no trae precio en pesos para el tipo, queda vacio: no se inventa")
    void sinPrecioEnPesosParaElTipo() {
        PreciosDemostracion soloCreditos = new PreciosDemostracion(
                PRECIOS.creditos(), Map.of(), -1, false);
        CatalogoInicial catalogo = new CatalogoInicial(
                List.of(HEROE), List.of(), List.of(), List.of(), List.of(), soloCreditos);

        SolicitudCrearProducto s = mapeador.aSolicitud(HEROE, catalogo);

        assertNull(s.precioMonedaReal());
        assertTrue(validador.validate(s).isEmpty(), "sigue siendo valido con solo creditos");
    }

    @Test
    @DisplayName("EPICA de un heroe que no esta en el catalogo: se rechaza, no se inventa")
    void epicaSinHeroe() {
        CatalogoInicial sinHeroes = new CatalogoInicial(
                List.of(), List.of(), List.of(), List.of(), List.of(EPICA), PRECIOS);

        assertThrows(IllegalArgumentException.class, () -> mapeador.aSolicitud(EPICA, sinHeroes));
    }

    @Test
    @DisplayName("el producto conserva el id dado, queda ACTIVO, version 1 y con fechas")
    void aProducto() {
        Instant ahora = Instant.parse("2026-09-24T12:00:00Z");
        SolicitudCrearProducto s = mapearValido(ARMADURA);

        Producto p = mapeador.aProducto("id-fijo", s, ahora);

        assertEquals("id-fijo", p.id());
        assertEquals(EstadoProducto.ACTIVO, p.estado());
        assertEquals(1, p.version());
        assertEquals(ahora, p.creadoEn());
        assertEquals(ahora, p.modificadoEn());
        assertEquals(s.nombre(), p.nombre());
        assertEquals(s.imagen(), p.imagen());
        assertEquals(s.descripcion(), p.descripcion());
        assertEquals(TipoProducto.ARMADURA, p.tipo());
        assertEquals(-1, p.tiraje());
        assertEquals(250, p.precioCreditos());
        assertPesos("5000", p.precioMonedaReal());
        assertFalse(p.premium());
        assertEquals(1, p.defensa());
        assertEquals(ParteArmadura.PANTALON, p.parte());
        assertEquals(new BigDecimal("3"), p.tasaDeCaida());
    }

    // --------------------------------------------------- el archivo real

    @Test
    @DisplayName("los 56 productos del JSON real pasan las validaciones del alta")
    void catalogoRealCompleto() throws Exception {
        CatalogoInicial real;
        try (InputStream json = getClass().getResourceAsStream("/semilla/catalogo-inicial.json")) {
            real = SemillaDelCatalogo.leer(json);
        }

        List<EntradaCatalogo> todas = real.todas();
        assertEquals(56, todas.size());
        assertEquals(56, todas.stream().map(EntradaCatalogo::id).distinct().count(),
                "los slugs deben ser unicos");

        Map<TipoProducto, Long> porTipo = todas.stream()
                .map(e -> mapeador.aSolicitud(e, real))
                .peek(s -> {
                    var violaciones = validador.validate(s);
                    assertTrue(violaciones.isEmpty(), () -> s.nombre() + ": " + violaciones);
                    assertTrue(s.precioCreditos() != null && s.precioCreditos() > 0, s.nombre());
                    assertTrue(s.precioMonedaReal() != null
                            && s.precioMonedaReal().signum() > 0,
                            () -> s.nombre() + " sin precio en pesos: la tienda no lo mostraria");
                    assertFalse(s.premium(), s.nombre());
                })
                .collect(Collectors.groupingBy(SolicitudCrearProducto::tipo, Collectors.counting()));

        assertEquals(Map.of(
                TipoProducto.HEROE, 8L,
                TipoProducto.ARMA, 16L,
                TipoProducto.ARMADURA, 16L,
                TipoProducto.ITEM, 8L,
                TipoProducto.EPICA, 8L), porTipo);
    }

    @Test
    @DisplayName("en el JSON real: 3 armas traen '+N al ataque' y 13 reciben el valor minimo")
    void armasConValorNeutro() throws Exception {
        CatalogoInicial real;
        try (InputStream json = getClass().getResourceAsStream("/semilla/catalogo-inicial.json")) {
            real = SemillaDelCatalogo.leer(json);
        }

        long conAtaque = real.armas().stream()
                .filter(a -> NumerosDeLaRegla.bonificacionAlAtaque(a.efectos()).isPresent())
                .count();

        assertEquals(3, conAtaque);
        assertEquals(13, real.armas().size() - conAtaque);
    }
}
