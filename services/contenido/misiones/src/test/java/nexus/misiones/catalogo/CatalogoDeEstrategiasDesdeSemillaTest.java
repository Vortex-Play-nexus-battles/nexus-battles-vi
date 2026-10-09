package nexus.misiones.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nexus.misiones.dominio.EstrategiaPredefinida;
import nexus.misiones.ia.Tabla7Local;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las estrategias predefinidas de los enemigos (7.8.6, «estrategias predefinidas») como datos versionados
 * (HU-SIM-004): la semilla del repositorio completa y valida, y un archivo con una estrategia mala que no tumba al
 * servicio ni a las demas.
 */
class CatalogoDeEstrategiasDesdeSemillaTest {

    /** Lo que la Tabla 7 dice que hacen estas acciones: ni ataque ni dano (defensa o sanacion). */
    private static final Set<String> NO_SON_DE_ATAQUE = Set.of(
            "Mano de piedra", "Defensa feroz",
            "Toque de la Vida", "Vínculo Natural", "Canto del Bosque",
            "Curación Directa", "Neutralización de Efectos", "Reanimación");

    private static final List<Integer> TRAMOS = List.of(1, 4, 8);

    // ---------------------------------------------------------------- la semilla del repositorio

    @Test
    @DisplayName("la semilla completa es valida: los ocho prototipos de la Tabla 7 en los tres tramos de nivel, sin rechazos")
    void laSemillaCompletaEsValida() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = CatalogoDeEstrategiasDesdeSemilla.cargar();

        assertThat(catalogo.rechazadas()).isEmpty();
        assertThat(catalogo.version()).isNotBlank();
        assertThat(catalogo.todas()).hasSize(Tabla7Local.PROTOTIPOS.size() * TRAMOS.size());
        for (String prototipo : Tabla7Local.PROTOTIPOS) {
            for (int tramo : TRAMOS) {
                assertThat(catalogo.todas()).as("%s desde el nivel %d", prototipo, tramo)
                        .filteredOn(e -> e.prototipo().equals(prototipo) && e.desdeNivel() == tramo).hasSize(1);
            }
        }
        assertThat(catalogo.todas()).extracting(EstrategiaPredefinida::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("los nombres del archivo son los exactos de la Tabla 7, tal cual estan escritos (sin depender de la normalizacion)")
    void losNombresDelArchivoSonLosExactos() throws IOException {
        JsonNode raiz;
        try (InputStream entrada = getClass().getClassLoader()
                .getResourceAsStream(CatalogoDeEstrategiasDesdeSemilla.RECURSO)) {
            assertThat(entrada).as("el recurso %s", CatalogoDeEstrategiasDesdeSemilla.RECURSO).isNotNull();
            raiz = JsonMapper.builder().build().readTree(entrada);
        }

        for (JsonNode estrategia : raiz.get("estrategias")) {
            String prototipo = estrategia.get("prototipo").asString();
            List<String> exactos = Tabla7Local.accionesDe(prototipo);
            assertThat(exactos).as(prototipo).isNotEmpty();
            for (JsonNode rotacion : estrategia.get("rotaciones")) {
                for (JsonNode paso : rotacion) {
                    assertThat(exactos).as("%s: %s", estrategia.get("id").asString(), paso.asString())
                            .contains(paso.asString());
                }
            }
        }
    }

    @Test
    @DisplayName("cada estrategia tiene entre una y tres rotaciones y solo usa lo que el prototipo tiene desbloqueado en su tramo")
    void hastaTresRotacionesConLoDesbloqueado() {
        for (EstrategiaPredefinida e : CatalogoDeEstrategiasDesdeSemilla.cargar().todas()) {
            assertThat(e.rotaciones()).as(e.id()).hasSizeBetween(1, 3);
            List<String> desbloqueadas = Tabla7Local.desbloqueadasEn(e.prototipo(), e.desdeNivel());
            e.rotaciones().forEach(rotacion ->
                    assertThat(desbloqueadas).as(e.id()).containsAll(rotacion));
            // Ninguna habilidad se repite entre rotaciones: cada rotacion es una opcion distinta.
            List<String> pasos = e.rotaciones().stream().flatMap(List::stream).toList();
            assertThat(pasos).as(e.id()).doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("criterio de diseno: las habilidades de ataque se priorizan; defensa y sanacion nunca van antes de un ataque")
    void elAtaqueVaPrimero() {
        for (EstrategiaPredefinida e : CatalogoDeEstrategiasDesdeSemilla.cargar().todas()) {
            boolean vistaUnaQueNoAtaca = false;
            for (List<String> rotacion : e.rotaciones()) {
                boolean ataca = rotacion.stream().noneMatch(NO_SON_DE_ATAQUE::contains);
                if (ataca) {
                    assertThat(vistaUnaQueNoAtaca)
                            .as("%s: una rotacion de ataque (%s) va despues de una de defensa o sanacion", e.id(),
                                    rotacion)
                            .isFalse();
                } else {
                    vistaUnaQueNoAtaca = true;
                }
            }
        }
    }

    @Test
    @DisplayName("cada rotacion es de una sola familia: o ataca o defiende y sana, nunca las dos mezcladas")
    void unaRotacionNoMezclaFamilias() {
        for (EstrategiaPredefinida e : CatalogoDeEstrategiasDesdeSemilla.cargar().todas()) {
            e.rotaciones().forEach(rotacion -> {
                long sinAtaque = rotacion.stream().filter(NO_SON_DE_ATAQUE::contains).count();
                assertThat(sinAtaque).as("%s %s", e.id(), rotacion).isIn(0L, (long) rotacion.size());
            });
        }
    }

    @Test
    @DisplayName("el tanque ataca con su unica habilidad ofensiva y reserva las dos de defensa para cuando esa se recarga")
    void elTanque() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = CatalogoDeEstrategiasDesdeSemilla.cargar();

        assertThat(rotaciones(catalogo, "Guerrero Tanque", 1)).containsExactly(List.of("Golpe con escudo"));
        assertThat(rotaciones(catalogo, "Guerrero Tanque", 4))
                .containsExactly(List.of("Golpe con escudo"), List.of("Mano de piedra"));
        assertThat(rotaciones(catalogo, "Guerrero Tanque", 8)).containsExactly(
                List.of("Golpe con escudo"), List.of("Defensa feroz"), List.of("Mano de piedra"));
    }

    @Test
    @DisplayName("un mago de fuego en el nivel 8 abre con Vulcano, sigue con Misiles de magma y deja Pare de fuego para el final")
    void elMagoDeFuego() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = CatalogoDeEstrategiasDesdeSemilla.cargar();

        assertThat(rotaciones(catalogo, "Mago Fuego", 8)).containsExactly(
                List.of("Vulcano"), List.of("Misiles de magma"), List.of("Pare de fuego"));
        assertThat(rotaciones(catalogo, "Mago Fuego", 4))
                .containsExactly(List.of("Vulcano"), List.of("Misiles de magma"));
    }

    @Test
    @DisplayName("Reanimacion no entra: sana a un companero y en una mision el enemigo pelea solo")
    void medicoSinReanimacion() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = CatalogoDeEstrategiasDesdeSemilla.cargar();

        assertThat(rotaciones(catalogo, "Médico", 8))
                .containsExactly(List.of("Neutralización de Efectos"), List.of("Curación Directa"));
        assertThat(catalogo.todas().stream().flatMap(e -> e.rotaciones().stream()).flatMap(List::stream))
                .doesNotContain("Reanimación");
    }

    @Test
    @DisplayName("el tramo se busca por el nivel del enemigo: 1 a 3, 4 a 7 y 8 en adelante")
    void elTramoSeBuscaPorNivel() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = CatalogoDeEstrategiasDesdeSemilla.cargar();

        assertThat(catalogo.para("Mago Fuego", 0)).isEmpty();
        for (int nivel : List.of(1, 2, 3)) {
            assertThat(catalogo.para("Mago Fuego", nivel)).get().extracting(EstrategiaPredefinida::id)
                    .isEqualTo("mago-fuego-n1");
        }
        for (int nivel : List.of(4, 5, 6, 7)) {
            assertThat(catalogo.para("Mago Fuego", nivel)).get().extracting(EstrategiaPredefinida::id)
                    .isEqualTo("mago-fuego-n4");
        }
        for (int nivel : List.of(8, 9, 20)) {
            assertThat(catalogo.para("Mago Fuego", nivel)).get().extracting(EstrategiaPredefinida::id)
                    .isEqualTo("mago-fuego-n8");
        }
        assertThat(catalogo.para("Bardo", 5)).isEmpty();
        assertThat(catalogo.para(null, 5)).isEmpty();
    }

    // ---------------------------------------------------------------- una estrategia mala no tumba nada

    private static CatalogoDeEstrategiasDesdeSemilla leer(String json) {
        return CatalogoDeEstrategiasDesdeSemilla.leer(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "prueba.json");
    }

    private static String archivo(String... estrategias) {
        return "{\"version\": \"prueba\", \"estrategias\": [" + String.join(",", estrategias) + "]}";
    }

    private static String estrategia(String id, String prototipo, int desde, String rotaciones) {
        return "{\"id\": \"" + id + "\", \"prototipo\": \"" + prototipo + "\", \"desdeNivel\": " + desde
                + ", \"rotaciones\": " + rotaciones + "}";
    }

    private static List<List<String>> rotaciones(CatalogoDeEstrategiasDesdeSemilla catalogo, String prototipo,
                                                 int nivel) {
        return catalogo.para(prototipo, nivel).orElseThrow().rotaciones();
    }

    @Test
    @DisplayName("una estrategia con una habilidad que no es del prototipo se rechaza con su motivo; las buenas siguen")
    void habilidadDeOtroPrototipo() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer(archivo(
                estrategia("mago-fuego-n1", "Mago Fuego", 1, "[[\"Misiles de magma\"]]"),
                estrategia("mago-fuego-n4", "Mago Fuego", 4, "[[\"Golpe con escudo\"]]"),
                estrategia("guerrero-tanque-n1", "Guerrero Tanque", 1, "[[\"Golpe con escudo\"]]")));

        assertThat(catalogo.todas()).extracting(EstrategiaPredefinida::id)
                .containsExactly("mago-fuego-n1", "guerrero-tanque-n1");
        assertThat(catalogo.rechazadas()).singleElement().satisfies(r -> {
            assertThat(r.estrategia()).isEqualTo("mago-fuego-n4");
            assertThat(r.motivo()).contains("Golpe con escudo").contains("Mago Fuego");
        });
    }

    @Test
    @DisplayName("una estrategia rechazada deja a ese tramo sin estrategia: no hereda la del tramo anterior")
    void elTramoRechazadoNoHeredaElAnterior() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer(archivo(
                estrategia("mago-fuego-n1", "Mago Fuego", 1, "[[\"Misiles de magma\"]]"),
                estrategia("mago-fuego-n4", "Mago Fuego", 4, "[[\"Inventada\"]]")));

        assertThat(catalogo.para("Mago Fuego", 2)).isPresent();
        assertThat(catalogo.para("Mago Fuego", 5)).as("cae a la heuristica, no a la del nivel 1").isEmpty();
    }

    @Test
    @DisplayName("no se admite una habilidad que el prototipo todavia no desbloquea en el tramo")
    void habilidadBloqueadaEnElTramo() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer(archivo(
                estrategia("mago-fuego-n1", "Mago Fuego", 1, "[[\"Vulcano\"]]")));

        assertThat(catalogo.todas()).isEmpty();
        assertThat(catalogo.rechazadas()).singleElement()
                .satisfies(r -> assertThat(r.motivo()).contains("Vulcano").contains("nivel 1"));
    }

    @Test
    @DisplayName("mas de tres rotaciones, una rotacion vacia o sin rotaciones: se rechaza")
    void cantidadDeRotaciones() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer(archivo(
                estrategia("a", "Mago Fuego", 8,
                        "[[\"Vulcano\"],[\"Misiles de magma\"],[\"Pare de fuego\"],[\"Vulcano\"]]"),
                estrategia("b", "Mago Fuego", 4, "[[\"Vulcano\"],[]]"),
                estrategia("c", "Mago Fuego", 1, "[]")));

        assertThat(catalogo.todas()).isEmpty();
        assertThat(catalogo.rechazadas()).extracting(CatalogoDeEstrategiasDesdeSemilla.Rechazo::estrategia)
                .containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("un nivel que no es un desbloqueo (1, 4 u 8), un prototipo desconocido o el ataque basico como paso: se rechaza")
    void camposInvalidos() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer(archivo(
                estrategia("nivel-2", "Mago Fuego", 2, "[[\"Misiles de magma\"]]"),
                estrategia("bardo", "Bardo", 1, "[[\"Canto\"]]"),
                estrategia("basico", "Mago Fuego", 1, "[[\"Ataque básico\"]]")));

        assertThat(catalogo.todas()).isEmpty();
        assertThat(catalogo.rechazadas()).hasSize(3);
        assertThat(catalogo.rechazadas().get(0).motivo()).contains("1, 4 u 8");
        assertThat(catalogo.rechazadas().get(1).motivo()).contains("Bardo");
        assertThat(catalogo.rechazadas().get(2).motivo()).contains("Ataque básico");
    }

    @Test
    @DisplayName("dos estrategias para el mismo prototipo y tramo: queda la primera y la segunda se rechaza")
    void duplicada() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer(archivo(
                estrategia("primera", "Mago Fuego", 1, "[[\"Misiles de magma\"]]"),
                estrategia("segunda", "Mago Fuego", 1, "[[\"Misiles de magma\"]]")));

        assertThat(catalogo.todas()).extracting(EstrategiaPredefinida::id).containsExactly("primera");
        assertThat(catalogo.rechazadas()).singleElement()
                .satisfies(r -> assertThat(r.estrategia()).isEqualTo("segunda"));
    }

    @Test
    @DisplayName("un campo desconocido o un id repetido o ausente se rechaza: una errata no se publica a medias")
    void erratasEnLaEstrategia() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer(archivo(
                "{\"id\": \"con-errata\", \"prototipo\": \"Mago Fuego\", \"desdeNivel\": 1, "
                        + "\"rotacions\": [[\"Misiles de magma\"]], \"rotaciones\": [[\"Misiles de magma\"]]}",
                "{\"prototipo\": \"Mago Fuego\", \"desdeNivel\": 4, \"rotaciones\": [[\"Vulcano\"]]}"));

        assertThat(catalogo.todas()).isEmpty();
        assertThat(catalogo.rechazadas()).hasSize(2);
    }

    @Test
    @DisplayName("los nombres se aceptan sin tildes ni mayusculas y quedan con el nombre exacto de la Tabla 7")
    void normalizaAlNombreExacto() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer(archivo(
                estrategia("veneno-n4", "Pícaro Veneno", 4, "[[\"FLOR DE LOTO\"],[\"agonia\"]]")));

        assertThat(catalogo.rechazadas()).isEmpty();
        assertThat(rotaciones(catalogo, "Pícaro Veneno", 4))
                .containsExactly(List.of("Flor de loto"), List.of("Agonía"));
    }

    @Test
    @DisplayName("un archivo ilegible no tumba el arranque: catalogo vacio y el motivo anotado")
    void archivoIlegible() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer("esto no es JSON {");

        assertThat(catalogo.todas()).isEmpty();
        assertThat(catalogo.rechazadas()).singleElement()
                .satisfies(r -> assertThat(r.motivo()).contains("prueba.json"));
    }

    @Test
    @DisplayName("un archivo sin la lista de estrategias tampoco lo tumba")
    void sinLista() {
        CatalogoDeEstrategiasDesdeSemilla catalogo = leer("{\"version\": \"x\"}");

        assertThat(catalogo.todas()).isEmpty();
        assertThat(catalogo.rechazadas()).hasSize(1);
    }

    @Test
    @DisplayName("lo que se rechaza queda ademas en la bitacora")
    void quedaEnLaBitacora() {
        List<String> mensajes = new ArrayList<>();
        CatalogoDeEstrategiasDesdeSemilla catalogo = CatalogoDeEstrategiasDesdeSemilla.leer(
                new ByteArrayInputStream(archivo(estrategia("mala", "Mago Fuego", 1, "[[\"Inventada\"]]"))
                        .getBytes(StandardCharsets.UTF_8)), "prueba.json", mensajes::add);

        assertThat(catalogo.rechazadas()).hasSize(1);
        assertThat(mensajes).singleElement().asString().contains("mala").contains("Inventada");
        assertThat(mensajes.stream().collect(Collectors.joining())).doesNotContain("null");
    }
}
