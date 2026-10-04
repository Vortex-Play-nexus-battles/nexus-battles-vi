package nexus.misiones.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.Objetivo;
import nexus.misiones.dominio.Origen;
import nexus.misiones.dominio.RecompensasDeMision;
import nexus.misiones.dominio.TipoDeObjetivo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-MIS-012: la primera mision que disena el equipo, «La Forja Sumergida»,
 * con el nivel de detalle de «El Templo Olvidado» (RF-MIS-56 a 60, RG-108).
 * Lee la semilla real, la que se publica.
 */
class LaForjaSumergidaTest {

    /** Los ids de services/contenido/productos/docs/catalogo-inicial-identificadores.md. */
    private static final String ESPADA_DE_DOS_MANOS = "37154b84-2ea0-3076-ae7b-656f7b4ebf11";
    private static final String DEFENSA_DEL_ENFURECIDO = "fbce687b-7496-3526-aef0-500b3fe2135e";
    private static final String FRIO_CONCENTRADO = "978446ae-c979-349b-b620-f4215852ab5f";

    private final CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.cargar(false);

    private Mision forja() {
        return catalogo.buscar("la-forja-sumergida").orElseThrow();
    }

    // ----------------------------------------------------------- criterio 1

    @Test
    @DisplayName("la semilla publica «La Forja Sumergida» despues de «El Templo Olvidado», como mision del equipo")
    void estaPublicada() {
        assertThat(catalogo.todas()).extracting(Mision::id).containsSubsequence("templo-olvidado", "la-forja-sumergida");

        Mision forja = forja();
        assertThat(forja.origen()).isEqualTo(Origen.EQUIPO);
        assertThat(forja.nombre()).isEqualTo("La Forja Sumergida");
        assertThat(forja.categoria()).isEqualTo(Categoria.HISTORIA);
        assertThat(forja.dificultad()).isEqualTo(Dificultad.DIFICIL);
        assertThat(forja.duracionHoras()).isEqualTo(18);
        assertThat(forja.nivelRecomendado()).as("sigue al Templo (nivel 8), el ultimo de la progresion (6.1.1)")
                .isEqualTo(8).isBetween(Mision.NIVEL_MINIMO, Mision.NIVEL_MAXIMO);
        assertThat(forja.requisitosPrevios()).as("sigue a «El Templo Olvidado» en la cadena de historia")
                .containsExactly("templo-olvidado");
        assertThat(forja.descripcionBreve()).isNotBlank();
        assertThat(forja.destacada()).as("la del banner sigue siendo El Templo Olvidado").isFalse();
        assertThat(forja.disponibleHasta()).isNull();
        assertThat(forja.intentos()).isNull();
    }

    @Test
    @DisplayName("criterio 1: trae historia y narrativa, objetivos, enemigos regulares y jefe final")
    void contenidoCompleto() {
        Mision forja = forja();

        assertThat(forja.narrativa())
                .startsWith("Bajo las aguas del Lago de Cristal Negro duerme una forja que los antiguos herreros "
                        + "sellaron hace siglos.")
                .contains("Desde que el sello del Templo Olvidado se rompió")
                .endsWith("Alguien debe bajar, apagar los hornos y descubrir qué se está forjando.");

        assertThat(forja.objetivos()).extracting(Objetivo::texto, Objetivo::principal, Objetivo::tipo,
                        Objetivo::valor, Objetivo::botin)
                .containsExactly(
                        tuple("Derrotar al Herrero Ahogado (jefe final).", true,
                                TipoDeObjetivo.DERROTAR_JEFE, null, null),
                        tuple("Apagar los 3 hornos de la forja.", true,
                                TipoDeObjetivo.COMPLETAR_ENCUENTROS, null, null),
                        tuple(
                                "Completar la misión sin que la vida del héroe baje del 40 %.", false,
                                TipoDeObjetivo.VIDA_MINIMA, 40, null),
                        tuple("Derrotar al Máster si aparece.", false,
                                TipoDeObjetivo.DERROTAR_MASTER, null, null),
                        tuple("Recuperar los 2 lingotes de acero frío.", false,
                                TipoDeObjetivo.OBTENER_BOTIN, 2, "Lingote de acero frío"));

        // Cantidad, vida y defensa son valores de equilibrio PROVISIONALES (BalanceDeMisionesTest, motor-combate).
        assertThat(forja.enemigos()).extracting("nombre", "cantidad", "prototipo", "descripcion", "vida", "defensa")
                .containsExactly(
                        tuple("Autómatas de Escoria", 8, "Guerrero Tanque",
                                "Enemigos con alta defensa.", 40, 84),
                        tuple("Salamandras de la Forja", 3, "Mago Fuego",
                                "Enemigos con ataques mágicos.", 20, 70),
                        tuple("Ladrones de Lingotes", 2, "Pícaro Machete",
                                "Enemigos con ataques rápidos.", 20, 60));
        assertThat(forja.encuentrosRegulares()).isEqualTo(13);
        assertThat(forja.encuentros()).as("regulares mas el jefe").isEqualTo(14);

        assertThat(forja.jefe().nombre()).isEqualTo("El Herrero Ahogado");
        assertThat(forja.jefe().prototipo()).isEqualTo("Guerrero Armas");
        assertThat(forja.jefe().vida()).isEqualTo(120);
        assertThat(forja.jefe().defensa()).as("la del prototipo en el nivel del heroe").isNull();
    }

    @Test
    @DisplayName("las recompensas: 80 creditos y un Cofre de Bronce, botin con producto del catalogo oficial, primera vez")
    void recompensas() {
        RecompensasDeMision recompensas = forja().recompensas();

        assertThat(recompensas.creditos()).isEqualTo(80);
        assertThat(recompensas.garantizadas()).singleElement().satisfies(cofre -> {
            assertThat(cofre.nombre()).isEqualTo("Cofre de Bronce");
            assertThat(cofre.cantidad()).isEqualTo(1);
            assertThat(cofre.entregable()).as("sin producto en el catalogo oficial: se informa").isFalse();
        });
        assertThat(recompensas.potenciales()).extracting("nombre", "probabilidad", "cantidad", "productoId")
                .containsExactly(
                        tuple("Lingote de acero frío", 0.5, 2, null),
                        tuple("Espada de dos manos", 0.2, 1, ESPADA_DE_DOS_MANOS),
                        tuple("Defensa del enfurecido", 0.15, 1,
                                DEFENSA_DEL_ENFURECIDO));
        assertThat(recompensas.primeraVez().creditos()).isEqualTo(15);
        assertThat(recompensas.primeraVez().otras()).containsExactly("Título «Forjador del Lago»");
        assertThat(forja().recompensasDestacadas())
                .containsExactly("80 créditos", "1 Cofre de Bronce", "Lingote de acero frío (50 %)");
    }

    // ----------------------------------------------------------- criterio 2

    @Test
    @DisplayName("criterio 2: su Master tiene una epica con efecto general y potenciado, la de Mago Hielo de la Tabla 20")
    void masterConEpica() {
        assertThat(forja().masters()).singleElement().satisfies(master -> {
            assertThat(master.nombre()).isEqualTo("Hija de la Escarcha");
            assertThat(master.prototipo()).isEqualTo("Mago Hielo");
            assertThat(master.epica().nombre()).isEqualTo("Frio concentrado");
            assertThat(master.epica().efectoGeneral()).isEqualTo("-1 de poder al oponente");
            assertThat(master.epica().efectoPotenciado()).isEqualTo("No recibe ningún daño en el siguiente turno");
            assertThat(master.epica().entregable()).as("se entrega con su producto EPICA").isTrue();
            assertThat(master.epica().productoId()).isEqualTo(FRIO_CONCENTRADO);
        });
    }

    @Test
    @DisplayName("criterio 2: su epica es exactamente la de la fila de Mago Hielo de la Tabla 20 (mismo producto)")
    void epicaDeLaTabla20() {
        EpicaDeTabla20 fila = catalogo.tabla20().stream().filter(f -> f.prototipo().equals("Mago Hielo"))
                .findFirst().orElseThrow();
        MasterDeMision master = forja().masters().get(0);

        assertThat(master.epica()).isEqualTo(fila.epica());
        assertThat(catalogo.tabla20()).extracting(f -> f.epica().productoId()).contains(master.epica().productoId());
    }

    // ----------------------------------------------------------- criterio 3

    @Test
    @DisplayName("criterio 3: la probabilidad del Master es la de la dificultad declarada, Dificil, 20 %")
    void probabilidadSegunDificultad() {
        Mision forja = forja();
        double declarada = CatalogoDeMisionesDesdeSemilla.leer(CatalogoDeMisionesDesdeSemilla.DEL_DOCUMENTO)
                .probabilidadDeMasterPorDificultad().get(forja.dificultad());

        assertThat(forja.dificultad()).isEqualTo(Dificultad.DIFICIL);
        assertThat(declarada).isEqualTo(0.20);
        assertThat(forja.masters()).extracting(MasterDeMision::probabilidad).containsExactly(0.20);
    }

    // --------------------------------------------------------- RG-108 / 7.8.14

    @Test
    @DisplayName("el equipo disena dos misiones completas: esta es la primera del equipo y no desplaza a la del documento")
    void convivenConLaDelDocumento() {
        List<Mision> delEquipo = catalogo.todas().stream().filter(m -> m.origen() == Origen.EQUIPO).toList();

        assertThat(delEquipo).extracting(Mision::id).containsExactly("la-forja-sumergida");
        assertThat(catalogo.todas().stream().filter(Mision::destacada)).extracting(Mision::id)
                .containsExactly("templo-olvidado");
    }
}
