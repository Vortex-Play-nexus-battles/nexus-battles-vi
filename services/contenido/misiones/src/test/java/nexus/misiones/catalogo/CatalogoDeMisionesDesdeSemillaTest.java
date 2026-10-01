package nexus.misiones.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.Origen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogoDeMisionesDesdeSemillaTest {

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

        assertThat(catalogo.todas()).extracting(Mision::id).contains("templo-olvidado");
        Mision templo = catalogo.buscar("templo-olvidado").orElseThrow();
        assertThat(templo.origen()).isEqualTo(Origen.DOCUMENTO);
        assertThat(templo.nombre()).isEqualTo("El Templo Olvidado");
        assertThat(templo.categoria()).isEqualTo(Categoria.HISTORIA);
        assertThat(templo.dificultad()).isEqualTo(Dificultad.NORMAL);
        assertThat(templo.duracionHoras()).isEqualTo(12);
        assertThat(templo.nivelRecomendado()).isEqualTo(15);
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

        assertThat(catalogo.todas()).extracting(Mision::id).containsExactly("templo-olvidado", "la-forja-sumergida",
                "dev-prueba-de-humo");
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
}
