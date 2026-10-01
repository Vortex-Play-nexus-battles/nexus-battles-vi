package nexus.misiones.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.Origen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        SemillaDeMisiones documento = new SemillaDeMisiones("1", List.of(), List.of(), List.of(Misiones.templo()));
        Mision ajena = Misiones.historia("ajena", List.of());
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                new SemillaDeMisiones("1", List.of(), List.of(), List.of(ajena)), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ajena");

        Mision sinNivel = new Mision("sin-nivel", Origen.PROGRESION, "Sin nivel", Categoria.HISTORIA, "d", null,
                Dificultad.FACIL, 1, null, List.of(), "n", null, Misiones.templo().objetivos(),
                Misiones.templo().enemigos(), Misiones.templo().jefe(), List.of(), Misiones.templo().recompensas(),
                false, null, null);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                new SemillaDeMisiones("1", List.of(), List.of(), List.of(sinNivel)), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nivel recomendado");

        EpicaDeTabla20 fila = CatalogoDeMisionesDesdeSemilla.cargar(false).tabla20().get(0);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                new SemillaDeMisiones("1", List.of(), List.of(fila), List.of()), null))
                .hasMessageContaining("Tabla 20");
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
            assertThat(master.epica().entregable()).as("no esta en el catalogo oficial").isFalse();
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
    @DisplayName("la Tabla 20: ocho tipos, su probabilidad literal y la epica con su producto del catalogo")
    void tabla20() {
        List<EpicaDeTabla20> tabla = CatalogoDeMisionesDesdeSemilla.cargar(false).tabla20();

        assertThat(tabla).extracting(EpicaDeTabla20::prototipo).containsExactly("Guerrero Tanque",
                "Guerrero Armas", "Mago Fuego", "Mago Hielo", "Pícaro Veneno", "Pícaro Machete", "Chamán", "Médico");
        assertThat(tabla).extracting(EpicaDeTabla20::probabilidadPorcentaje)
                .containsExactly(0.04, 0.01, 0.03, 0.05, 0.02, 0.01, 0.1, 0.1);
        assertThat(tabla).allSatisfy(fila -> assertThat(fila.epica().entregable()).isTrue());
        assertThat(tabla.get(0).comoMaster().probabilidad()).isEqualTo(0.0004);
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
        SemillaDeMisiones documento = new SemillaDeMisiones("1", List.of(), List.of(), List.of(Misiones.templo()));
        Mision sinMarca = Misiones.historia("inventada", List.of());

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                new SemillaDeMisiones("1", List.of(), List.of(), List.of(sinMarca))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CatalogoDeMisionesDesdeSemilla.PREFIJO_PROVISIONAL);
    }

    @Test
    @DisplayName("la semilla del documento no admite misiones provisionales")
    void documentoSoloDelDocumento() {
        SemillaDeMisiones documento = new SemillaDeMisiones("1", List.of(), List.of(),
                List.of(Misiones.historia("inventada", List.of())));

        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inventada");
    }

    @Test
    @DisplayName("ids repetidos, requisitos que no existen y tipos repetidos en la Tabla 20 tumban el arranque")
    void coherencia() {
        Mision templo = Misiones.templo();
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(
                new SemillaDeMisiones("1", List.of(), List.of(), List.of(templo, templo)), null))
                .hasMessageContaining("mismo identificador");

        Mision huerfana = new Mision("huerfana", Origen.DOCUMENTO, "Huérfana", Categoria.HISTORIA, "d", null,
                Dificultad.FACIL, 1, null, List.of("no-existe"), "n", null, templo.objetivos(), templo.enemigos(),
                templo.jefe(), List.of(), templo.recompensas(), false, null, null);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(
                new SemillaDeMisiones("1", List.of(), List.of(), List.of(huerfana)), null))
                .hasMessageContaining("no-existe");

        EpicaDeTabla20 fila = CatalogoDeMisionesDesdeSemilla.cargar(false).tabla20().get(0);
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(
                new SemillaDeMisiones("1", List.of(), List.of(fila, fila), List.of(templo)), null))
                .hasMessageContaining("repite");

        SemillaDeMisiones documento = new SemillaDeMisiones("1", List.of(), List.of(), List.of(templo));
        assertThatThrownBy(() -> CatalogoDeMisionesDesdeSemilla.desde(documento,
                new SemillaDeMisiones("1", List.of(), List.of(fila), List.of())))
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

    // ----------------------------------------------------- cada Master, su epica (HU-SIM-006, criterio 2)

    private static final Epica VELO = Misiones.VELO_DE_SOMBRAS;
    private static final Epica FRIO = new Epica("Frío concentrado", "-1 de poder al oponente",
            "No recibe ningún daño en el siguiente turno", "978446ae-c979-349b-b620-f4215852ab5f");

    /** Una mision del documento con los datos del Templo y los Master que se digan. */
    private static Mision conMasters(String id, MasterDeMision... masters) {
        Mision templo = Misiones.templo();
        return new Mision(id, Origen.DOCUMENTO, "Mision " + id, Categoria.HISTORIA, "d", null, Dificultad.FACIL, 1,
                null, List.of(), "n", null, templo.objetivos(), templo.enemigos(), templo.jefe(), List.of(masters),
                templo.recompensas(), false, null, null);
    }

    private static MasterDeMision master(String nombre, Epica epica) {
        return new MasterDeMision(nombre, "Pícaro Veneno", 0.15, epica);
    }

    private static CatalogoDeMisionesDesdeSemilla cargar(List<EpicaDeTabla20> tabla20, Mision... misiones) {
        return CatalogoDeMisionesDesdeSemilla.desde(new SemillaDeMisiones("1", List.of(), tabla20, List.of(misiones)),
                null);
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

        assertThat(cargar(List.of(filaDeHielo), conMasters("una", master("Hija de la Escarcha", FRIO))).todas())
                .hasSize(1);
        assertThatThrownBy(() -> cargar(List.of(filaDeHielo), conMasters("una", master("Hija de la Escarcha", FRIO)),
                conMasters("otra", master("Eco de la Niebla", FRIO))))
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
