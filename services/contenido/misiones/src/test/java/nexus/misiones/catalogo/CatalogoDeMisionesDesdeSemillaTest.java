package nexus.misiones.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.Origen;
import nexus.misiones.dominio.simulacion.AzarConSemilla;
import nexus.misiones.dominio.simulacion.TiradaDeMasters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogoDeMisionesDesdeSemillaTest {

    /** D-42: las misiones de historia de nivel 1 a 7, en orden. */
    private static final List<String> PROGRESION = List.of("sendero-de-los-aprendices", "mina-abandonada",
            "pantano-de-los-susurros", "fortaleza-quebrada", "cumbres-heladas", "volcan-dormido",
            "ciudadela-de-las-sombras");

    @Test
    @DisplayName("D-42: la progresión publica siete misiones de historia, de nivel 1 a 7, cada una tras la anterior")
    void progresion() {
        CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.cargar(false);
        List<Mision> progresion = catalogo.todas().stream().filter(m -> m.origen() == Origen.PROGRESION).toList();

        assertThat(progresion).extracting(Mision::id).containsExactlyElementsOf(PROGRESION);
        for (int i = 0; i < progresion.size(); i++) {
            Mision m = progresion.get(i);
            assertThat(m.nivelRecomendado()).as(m.id()).isEqualTo(i + 1);
            assertThat(m.categoria()).as(m.id()).isEqualTo(Categoria.HISTORIA);
            assertThat(m.requisitosPrevios()).as(m.id())
                    .isEqualTo(i == 0 ? List.of() : List.of(progresion.get(i - 1).id()));
            assertThat(m.jefe()).as(m.id()).isNotNull();
            assertThat(m.encuentros()).as(m.id()).isGreaterThan(1);
            if (i > 0) {
                assertThat(m.duracionHoras()).as(m.id()).isGreaterThan(progresion.get(i - 1).duracionHoras());
            }
        }
        // Un heroe nuevo tiene donde empezar: nivel 1 y sin requisitos.
        assertThat(progresion.get(0).requisitosPrevios()).isEmpty();
        // Ninguna mision del catalogo recomienda un nivel que no existe (§6.1.1).
        assertThat(catalogo.todas()).allSatisfy(m -> assertThat(m.nivelRecomendado())
                .isBetween(Mision.NIVEL_MINIMO, Mision.NIVEL_MAXIMO));
    }

    @Test
    @DisplayName("D-42: una misión que recomienda un nivel fuera de 1..8 no se publica (el Templo decía 15)")
    void nivelFueraDeRango() throws java.io.IOException {
        String templo15 = new String(Objects.requireNonNull(getClass().getClassLoader()
                .getResourceAsStream(CatalogoDeMisionesDesdeSemilla.DEL_DOCUMENTO)).readAllBytes(),
                StandardCharsets.UTF_8).replace("\"nivelRecomendado\": 8,", "\"nivelRecomendado\": 15,");
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.leer(
                new ByteArrayInputStream(templo15.getBytes(StandardCharsets.UTF_8)), "templo-15"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("15");
        assertThatThrownBy(() -> new Mision("cero", Origen.PROGRESION, "Cero", Categoria.HISTORIA, "d", null,
                Dificultad.FACIL, 1, 0, List.of(), "n", null, Misiones.templo().objetivos(),
                Misiones.templo().enemigos(), Misiones.templo().jefe(), List.of(), Misiones.templo().recompensas(),
                false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6.1.1");
    }

    @Test
    @DisplayName("D-42: la semilla de progresión solo publica misiones de progresión, con su nivel y sin Tabla 20")
    void progresionSoloDeProgresion() {
        SemillaDeMisiones documento = documento(Misiones.templo());
        Mision ajena = Misiones.historia("ajena", List.of());
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                sinTabla(ajena), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ajena");

        Mision sinNivel = new Mision("sin-nivel", Origen.PROGRESION, "Sin nivel", Categoria.HISTORIA, "d", null,
                Dificultad.FACIL, 1, null, List.of(), "n", null, Misiones.templo().objetivos(),
                Misiones.templo().enemigos(), Misiones.templo().jefe(), List.of(), Misiones.templo().recompensas(),
                false, null, null);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                sinTabla(sinNivel), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nivel recomendado");

        EpicaDeTabla20 fila = CatalogoDeMisionesDesdeSemilla.cargar(false).tabla20().get(0);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                new SemillaDeMisiones("1", List.of(), List.of(fila), Map.of(), List.of()), null))
                .hasMessageContaining("Tabla 20");
    }

    /** Las cifras del PO (provisionales): lo que la semilla del documento declara. */
    static final Map<Dificultad, Double> MASTER_POR_DIFICULTAD = Map.of(
            Dificultad.FACIL, 0.10, Dificultad.NORMAL, 0.15, Dificultad.DIFICIL, 0.20, Dificultad.EXTREMO, 0.25);

    static SemillaDeMisiones documento(Mision... misiones) {
        return documentoConTabla20(List.of(), misiones);
    }

    static SemillaDeMisiones documentoConTabla20(List<EpicaDeTabla20> tabla20, Mision... misiones) {
        return new SemillaDeMisiones("1", List.of(), tabla20, MASTER_POR_DIFICULTAD, List.of(misiones));
    }

    /** Una semilla que no es la del documento: no declara la tabla de Master por dificultad. */
    static SemillaDeMisiones sinTabla(Mision... misiones) {
        return new SemillaDeMisiones("1", List.of(), List.of(), Map.of(), List.of(misiones));
    }

    @Test
    @DisplayName("la semilla del documento publica «El Templo Olvidado» con los datos de 7.8.14")
    void templo() {
        CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.cargar(false);

        assertThat(catalogo.todas()).extracting(Mision::id).startsWith("templo-olvidado");
        Mision templo = catalogo.buscar("templo-olvidado").orElseThrow();
        assertThat(templo.origen()).isEqualTo(Origen.DOCUMENTO);
        assertThat(templo.nombre()).isEqualTo("El Templo Olvidado");
        assertThat(templo.categoria()).isEqualTo(Categoria.HISTORIA);
        assertThat(templo.dificultad()).isEqualTo(Dificultad.NORMAL);
        assertThat(templo.duracionHoras()).isEqualTo(12);
        // D-42: el documento dice 15, que ningun heroe alcanza (§6.1.1: hasta 8).
        assertThat(templo.nivelRecomendado()).isEqualTo(Mision.NIVEL_MAXIMO);
        assertThat(templo.narrativa()).startsWith("En las profundidades del Bosque Sombrío")
                .endsWith("un guardián milenario que protege sus secretos.");
        assertThat(templo.objetivos()).hasSize(5);
        assertThat(templo.objetivos().stream().filter(o -> o.principal()).count()).isEqualTo(2);
        assertThat(templo.encuentrosRegulares()).isEqualTo(18);
        assertThat(templo.jefe().nombre()).isEqualTo("El Guardián Eterno");
        assertThat(templo.jefe().vida()).isEqualTo(100);
        assertThat(templo.masters()).singleElement().satisfies(master -> {
            assertThat(master.nombre()).isEqualTo("Sombra del Olvido");
            assertThat(master.prototipo()).isEqualTo("Pícaro Veneno");
            assertThat(master.probabilidad()).isEqualTo(0.15);
            assertThat(master.epica().nombre()).isEqualTo("Velo de Sombras");
            assertThat(master.epica().entregable()).as("tiene producto EPICA en el catalogo").isTrue();
            assertThat(master.epica().productoId()).isEqualTo(Misiones.ID_DE_VELO_DE_SOMBRAS);
        });
        assertThat(templo.recompensas().creditos()).isEqualTo(50);
        assertThat(templo.recompensas().garantizadas()).extracting(o -> o.nombre()).containsExactly("Cofre de Bronce");
        assertThat(templo.recompensas().potenciales()).extracting(o -> o.probabilidad())
                .containsExactly(0.6, 0.2, 0.15);
        assertThat(templo.recompensas().primeraVez().creditos()).isEqualTo(10);
        assertThat(templo.recompensas().primeraVez().otras()).containsExactly("Título «Explorador del Templo»");
        assertThat(templo.recompensasDestacadas())
                .containsExactly("50 créditos", "1 Cofre de Bronce", "Fragmento del Sello Antiguo (60 %)");
    }

    @Test
    @DisplayName("cada epica de la semilla (las ocho de la Tabla 20 y la del Master del Templo) apunta al producto que el catalogo siembra con su slug")
    void cadaEpicaApuntaAlProductoDelCatalogo() {
        CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.cargar(false);
        Map<String, String> esperados = new LinkedHashMap<>();
        catalogo.tabla20().forEach(fila -> esperados.put(fila.epica().nombre(),
                idDelCatalogo("epica-" + slug(fila.prototipo()) + "-" + slug(fila.epica().nombre()))));
        catalogo.buscar("templo-olvidado").orElseThrow().masters().forEach(master -> esperados.put(
                master.epica().nombre(),
                idDelCatalogo("epica-" + slug(master.prototipo()) + "-" + slug(master.epica().nombre()))));

        assertThat(esperados).hasSize(9);
        Map<String, String> reales = new LinkedHashMap<>();
        catalogo.tabla20().forEach(fila -> reales.put(fila.epica().nombre(), fila.epica().productoId()));
        catalogo.buscar("templo-olvidado").orElseThrow().masters()
                .forEach(master -> reales.put(master.epica().nombre(), master.epica().productoId()));
        assertThat(reales).containsExactlyInAnyOrderEntriesOf(esperados);
    }

    /** Como lo hace SemillaDelCatalogo de productos: UUID v3 de «nexus-battles-vi/catalogo-inicial/{slug}». */
    private static String idDelCatalogo(String slug) {
        return UUID.nameUUIDFromBytes(("nexus-battles-vi/catalogo-inicial/" + slug).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static String slug(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    @Test
    @DisplayName("la Tabla 20: ocho tipos, su probabilidad leida como porcentaje (0.04 = 4 %) y la epica con su producto del catalogo")
    void tabla20() {
        List<EpicaDeTabla20> tabla = CatalogoDeMisionesDesdeSemilla.cargar(false).tabla20();

        assertThat(tabla).extracting(EpicaDeTabla20::prototipo).containsExactly("Guerrero Tanque",
                "Guerrero Armas", "Mago Fuego", "Mago Hielo", "Pícaro Veneno", "Pícaro Machete", "Chamán", "Médico");
        assertThat(tabla).extracting(EpicaDeTabla20::probabilidadPorcentaje)
                .containsExactly(4.0, 1.0, 3.0, 5.0, 2.0, 1.0, 10.0, 10.0);
        assertThat(tabla).allSatisfy(fila -> assertThat(fila.epica().entregable()).isTrue());
        assertThat(tabla.get(0).comoMaster().probabilidad()).isEqualTo(0.04);
        assertThat(tabla.get(6).epica().efectoGeneral()).as("«No aplica»").isNull();
    }

    @Test
    @DisplayName("la semilla provisional solo entra si se pide, y va marcada")
    void provisional() {
        CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.cargar(true);

        assertThat(catalogo.todas()).extracting(Mision::id).startsWith("templo-olvidado")
                .endsWith("dev-prueba-de-humo").contains(PROGRESION.toArray(String[]::new));
        Mision humo = catalogo.buscar("dev-prueba-de-humo").orElseThrow();
        assertThat(humo.origen()).isEqualTo(Origen.PROVISIONAL_DEV);
        assertThat(humo.nombre()).startsWith(CatalogoDeMisionesDesdeSemilla.PREFIJO_PROVISIONAL);
        assertThat(CatalogoDeMisionesDesdeSemilla.cargar(false).buscar("dev-prueba-de-humo")).isEmpty();
        assertThat(catalogo.buscar(null)).isEmpty();
    }

    @Test
    @DisplayName("una mision provisional sin su marca no se publica")
    void provisionalSinMarca() {
        SemillaDeMisiones documento = documento(Misiones.templo());
        Mision sinMarca = Misiones.historia("inventada", List.of());

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                sinTabla(sinMarca)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CatalogoDeMisionesDesdeSemilla.PREFIJO_PROVISIONAL);
    }

    @Test
    @DisplayName("la semilla del documento no admite misiones provisionales")
    void documentoSoloDelDocumento() {
        SemillaDeMisiones documento = documento(Misiones.historia("inventada", List.of()));

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inventada");
    }

    @Test
    @DisplayName("ids repetidos, requisitos que no existen y tipos repetidos en la Tabla 20 tumban el arranque")
    void coherencia() {
        Mision templo = Misiones.templo();
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(
                documento(templo, templo), null))
                .hasMessageContaining("mismo identificador");

        Mision huerfana = new Mision("huerfana", Origen.DOCUMENTO, "Huérfana", Categoria.HISTORIA, "d", null,
                Dificultad.FACIL, 1, null, List.of("no-existe"), "n", null, templo.objetivos(), templo.enemigos(),
                templo.jefe(), List.of(), templo.recompensas(), false, null, null);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(
                documento(huerfana), null))
                .hasMessageContaining("no-existe");

        EpicaDeTabla20 fila = CatalogoDeMisionesDesdeSemilla.cargar(false).tabla20().get(0);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(
                documentoConTabla20(List.of(fila, fila), templo), null))
                .hasMessageContaining("repite");

        SemillaDeMisiones documento = documento(templo);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                new SemillaDeMisiones("1", List.of(), List.of(fila), Map.of(), List.of())))
                .hasMessageContaining("Tabla 20");
    }

    @Test
    @DisplayName("una errata en la semilla (campo que no existe o dato invalido) no se publica a medias")
    void lecturaEstricta() {
        String conErrata = "{\"version\":\"1\",\"notas\":[],\"tabla20\":[],\"misiones\":[],\"misionez\":[]}";
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.leer(
                new ByteArrayInputStream(conErrata.getBytes(StandardCharsets.UTF_8)), "prueba"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prueba");

        String invalida = "{\"version\":\"1\",\"tabla20\":[{\"prototipo\":\"\",\"epica\":null,"
                + "\"probabilidadPorcentaje\":1}]}";
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.leer(
                new ByteArrayInputStream(invalida.getBytes(StandardCharsets.UTF_8)), "prueba"))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.leer("semilla/no-existe.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-existe");
    }

    // ------------------------------------------------ semilla extra del banco E2E

    /** La semilla del banco, tal como la monta tests/e2e/compose.yml (la ruta es desde la carpeta del modulo). */
    private static final Path SEMILLA_DEL_BANCO = Path.of("..", "..", "..", "tests", "e2e",
            "semilla-misiones-banco.json");

    /** El productoId de la epica de la Tabla 20 del Guerrero Tanque en el catalogo oficial. */
    private static final String EPICA_DEL_TANQUE = "81af272d-74fb-3dc1-b6ff-01fdc99a1c1d";

    private static Mision conMarca(String id) {
        return new Mision(id, Origen.PROVISIONAL_DEV, CatalogoDeMisionesDesdeSemilla.PREFIJO_PROVISIONAL + " " + id,
                Categoria.HISTORIA, "d", null, Dificultad.FACIL, 1, null, List.of(), "n", null,
                Misiones.templo().objetivos(), Misiones.templo().enemigos(), Misiones.templo().jefe(), List.of(),
                Misiones.templo().recompensas(), false, null, null);
    }

    @Test
    @DisplayName("sin semilla extra el catalogo es el de siempre: la mision del banco no esta en el servicio")
    void sinSemillaExtraNadaCambia() {
        assertThat(CatalogoDeMisionesDesdeSemilla.cargar(true, null).todas())
                .extracting(Mision::id)
                .isEqualTo(CatalogoDeMisionesDesdeSemilla.cargar(true).todas().stream().map(Mision::id).toList())
                .doesNotContain("dev-master-seguro");
        assertThat(CatalogoDeMisionesDesdeSemilla.cargar(false, null).todas())
                .extracting(Mision::id).doesNotContain("dev-master-seguro", "dev-prueba-de-humo");
    }

    @Test
    @DisplayName("la semilla extra suma sus misiones al final, marcadas como provisionales de DEV")
    void semillaExtraSeSuma() {
        SemillaDeMisiones documento = documento(Misiones.templo());
        SemillaDeMisiones extra = sinTabla(conMarca("solo-banco"));

        CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.desde(documento, null, null, null,
                extra);

        assertThat(catalogo.todas()).extracting(Mision::id).containsExactly("templo-olvidado", "solo-banco");
        assertThat(catalogo.buscar("solo-banco").orElseThrow().origen()).isEqualTo(Origen.PROVISIONAL_DEV);
    }

    @Test
    @DisplayName("la semilla extra tiene las reglas de la provisional: marca, sin Tabla 20 y ids que no chocan")
    void semillaExtraConLasMismasReglas() {
        SemillaDeMisiones documento = documento(Misiones.templo());

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento, null, null, null,
                sinTabla(Misiones.historia("sin-marca", List.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin-marca")
                .hasMessageContaining(CatalogoDeMisionesDesdeSemilla.PREFIJO_PROVISIONAL);

        EpicaDeTabla20 fila = CatalogoDeMisionesDesdeSemilla.cargar(false).tabla20().get(0);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento, null, null, null,
                new SemillaDeMisiones("1", List.of(), List.of(fila), Map.of(), List.of(conMarca("solo-banco")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tabla 20");

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento, null, null,
                sinTabla(conMarca("repetida")), sinTabla(conMarca("repetida"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mismo identificador");
    }

    @Test
    @DisplayName("una ruta de semilla extra que no existe tumba el arranque: nunca se publica a medias")
    void semillaExtraQueNoExiste(@TempDir Path carpeta) {
        Path inexistente = carpeta.resolve("no-esta.json");

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.cargar(false, inexistente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-esta.json");
    }

    @Test
    @DisplayName("la semilla extra se lee de un archivo del disco, estricta como las demas")
    void semillaExtraDeArchivo(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("extra.json");
        Files.writeString(archivo, "{\"version\":\"1\",\"misionez\":[]}", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.cargar(false, archivo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("extra.json");
    }

    @Test
    @DisplayName("HU-SIM-005/006: la mision del banco trae un Master al 100 % con una epica de la Tabla 20 con su productoId")
    void misionDelBanco() {
        CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.cargar(true, SEMILLA_DEL_BANCO);

        Mision seguro = catalogo.buscar("dev-master-seguro").orElseThrow();
        assertThat(seguro.origen()).isEqualTo(Origen.PROVISIONAL_DEV);
        assertThat(seguro.nombre()).startsWith(CatalogoDeMisionesDesdeSemilla.PREFIJO_PROVISIONAL);
        assertThat(seguro.duracionHoras()).as("una hora: dos segundos en el banco").isEqualTo(1);
        assertThat(seguro.masters()).singleElement().satisfies(master -> {
            assertThat(master.probabilidad()).isEqualTo(1.0);
            assertThat(master.vida()).as("vida baja solo en el banco").isEqualTo(1);
            assertThat(master.defensa()).isZero();
            assertThat(master.epica().entregable()).isTrue();
            assertThat(master.epica().productoId()).isEqualTo(EPICA_DEL_TANQUE);
        });
        // La epica de la mision es la de la Tabla 20 del documento, no una inventada.
        EpicaDeTabla20 fila = catalogo.tabla20().stream()
                .filter(f -> f.prototipo().equals("Guerrero Tanque")).findFirst().orElseThrow();
        assertThat(seguro.masters().getFirst().epica()).isEqualTo(fila.epica());
        // Y el catalogo del servicio, sin la semilla extra, no la tiene.
        assertThat(CatalogoDeMisionesDesdeSemilla.cargar(true).buscar("dev-master-seguro")).isEmpty();
    }

    @Test
    @DisplayName("HU-SIM-005 C1: con la semilla fija del banco (7) y el heroe del kit sale el Master de prueba, y solo ese")
    void conLaSemillaDelBancoApareceSoloElMasterDePrueba() {
        CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.cargar(true, SEMILLA_DEL_BANCO);
        Mision seguro = catalogo.buscar("dev-master-seguro").orElseThrow();

        List<MasterDeMision> aparecen = TiradaDeMasters.quienesAparecen(seguro, "Guerrero Tanque",
                catalogo.tabla20(), new AzarConSemilla(7));

        assertThat(aparecen).extracting(MasterDeMision::nombre).containsExactly("Máster de prueba");
    }

    // ----------------------------------------------------- cada Master, su epica (HU-SIM-006, criterio 2)

    private static final Epica VELO = Misiones.VELO_DE_SOMBRAS;
    private static final Epica FRIO = new Epica("Frío concentrado", "-1 de poder al oponente",
            "No recibe ningún daño en el siguiente turno", "978446ae-c979-349b-b620-f4215852ab5f");

    /** Una mision del documento con los datos del Templo y los Master que se digan. */
    private static Mision conMasters(String id, MasterDeMision... masters) {
        Mision templo = Misiones.templo();
        return new Mision(id, Origen.DOCUMENTO, "Mision " + id, Categoria.HISTORIA, "d", null, Dificultad.NORMAL, 1,
                null, List.of(), "n", null, templo.objetivos(), templo.enemigos(), templo.jefe(), List.of(masters),
                templo.recompensas(), false, null, null);
    }

    private static MasterDeMision master(String nombre, Epica epica) {
        return master(nombre, "Pícaro Veneno", epica);
    }

    private static MasterDeMision master(String nombre, String prototipo, Epica epica) {
        return new MasterDeMision(nombre, prototipo, 0.15, epica);
    }

    private static CatalogoDeMisionesDesdeSemilla cargar(List<EpicaDeTabla20> tabla20, Mision... misiones) {
        return CatalogoDeMisionesDesdeSemilla.desde(documentoConTabla20(tabla20, misiones), null);
    }

    @Test
    @DisplayName("dos Master de misiones distintas no pueden soltar la misma epica: cada Master tiene la suya")
    void epicaCompartidaEntreMisiones() {
        assertThatThrownBy(() -> cargar(List.of(), conMasters("una", master("Sombra del Olvido", VELO)),
                conMasters("otra", master("Eco de la Niebla", VELO))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Velo de Sombras")
                .hasMessageContaining("Sombra del Olvido")
                .hasMessageContaining("Eco de la Niebla");
    }

    @Test
    @DisplayName("tampoco dentro de una misma mision, ni escribiendola con otras tildes o mayusculas")
    void epicaCompartidaEnLaMisma() {
        Epica casiIgual = new Epica("velo de sombras", "x", "y", null);
        assertThatThrownBy(() -> cargar(List.of(), conMasters("una", master("Sombra del Olvido", VELO),
                master("Eco de la Niebla", casiIgual))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Eco de la Niebla");
    }

    @Test
    @DisplayName("dos epicas con nombres distintos que entregan el mismo producto del catalogo tampoco son dos epicas")
    void mismoProducto() {
        Epica otroNombre = new Epica("Escarcha eterna", "x", "y", FRIO.productoId());
        assertThatThrownBy(() -> cargar(List.of(), conMasters("una", master("Hija de la Escarcha", FRIO)),
                conMasters("otra", master("Eco de la Niebla", otroNombre))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FRIO.productoId());
    }

    @Test
    @DisplayName("el mismo Master en dos misiones con la misma epica es el mismo Master, no un choque")
    void elMismoMasterDosVeces() {
        CatalogoDeMisionesDesdeSemilla catalogo = cargar(List.of(),
                conMasters("una", master("Sombra del Olvido", VELO)),
                conMasters("otra", master("Sombra del Olvido", VELO)));

        assertThat(catalogo.todas()).hasSize(2);
    }

    @Test
    @DisplayName("un Master de mision puede soltar la epica de una fila de la Tabla 20 (decision del PO de HU-MIS-012), pero uno solo")
    void epicaDeLaTabla20() {
        EpicaDeTabla20 filaDeHielo = new EpicaDeTabla20("Mago Hielo", FRIO, 0.05);

        assertThat(cargar(List.of(filaDeHielo), conMasters("una", master("Hija de la Escarcha", "Mago Hielo", FRIO)))
                .todas()).hasSize(1);
        assertThatThrownBy(() -> cargar(List.of(filaDeHielo),
                conMasters("una", master("Hija de la Escarcha", "Mago Hielo", FRIO)),
                conMasters("otra", master("Eco de la Niebla", "Mago Hielo", FRIO))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Frío concentrado");
    }

    @Test
    @DisplayName("la Tabla 20 no puede repetir una epica en dos tipos de heroe")
    void tabla20SinEpicasRepetidas() {
        assertThatThrownBy(() -> cargar(List.of(new EpicaDeTabla20("Mago Hielo", FRIO, 0.05),
                new EpicaDeTabla20("Mago Fuego", FRIO, 0.03)), Misiones.templo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tabla 20")
                .hasMessageContaining("Frío concentrado");
    }

    /** Una mision de progresion (D-42): nivel recomendado, de historia, con los Master que se digan (la real no trae). */
    private static Mision deProgresion(String id, MasterDeMision... masters) {
        Mision base = conMasters(id, masters);
        return new Mision(id, Origen.PROGRESION, base.nombre(), base.categoria(), base.descripcionBreve(), null,
                base.dificultad(), 1, 3, List.of(), "n", null, base.objetivos(), base.enemigos(), base.jefe(),
                List.of(masters), base.recompensas(), false, null, null);
    }

    @Test
    @DisplayName("la exclusividad se comprueba sobre todas las semillas juntas: la de progresion, sin Master, no estorba; con uno repetido, si")
    void epicaExclusivaEntreSemillas() {
        SemillaDeMisiones documento = documento(conMasters("del-documento", master("Sombra del Olvido", VELO)));
        SemillaDeMisiones progresionSinMaster = sinTabla(deProgresion("de-progresion"));
        SemillaDeMisiones progresionConElMismoVelo = sinTabla(
                deProgresion("de-progresion", master("Eco de la Niebla", VELO)));

        assertThat(CatalogoDeMisionesDesdeSemilla.desde(documento, progresionSinMaster, null).todas()).hasSize(2);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento, progresionConElMismoVelo, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Velo de Sombras")
                .hasMessageContaining("Eco de la Niebla");
    }

    @Test
    @DisplayName("las semillas publicadas cumplen la regla: cada Master suelta una epica distinta")
    void lasSemillasPublicadasCumplen() {
        CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.cargar(true);

        List<MasterDeMision> masters = catalogo.todas().stream().flatMap(m -> m.masters().stream()).toList();
        assertThat(masters).isNotEmpty();
        assertThat(masters.stream().map(m -> m.epica().nombre()).distinct()).hasSameSizeAs(masters);
        // Y las ocho de la Tabla 20 siguen siendo ocho epicas distintas, cada una con su producto.
        assertThat(catalogo.tabla20().stream().map(f -> f.epica().nombre()).distinct()).hasSize(8);
    }
}
