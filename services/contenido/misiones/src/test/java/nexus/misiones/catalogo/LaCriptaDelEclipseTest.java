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

/** HU-MIS-013: contenido aprobado y verificable de la segunda mision del equipo. */
class LaCriptaDelEclipseTest {

    private static final String TOMA_Y_LLEVA = "ac841a0a-132a-392f-8cf5-5a9cfa9add0f";
    private static final String VENENO_LACERANTE = "ae461eab-cdbd-370d-8241-99f875417657";
    private static final String DAGA_PURULENTA = "d7d1836f-e45e-33f4-a033-6e467017f1df";
    private static final String MANO_DEL_DESTERRADO = "14413b85-7d68-3e83-a258-9044147440d6";

    private final CatalogoDeMisionesDesdeSemilla catalogo = CatalogoDeMisionesDesdeSemilla.cargar(false);

    private Mision cripta() {
        return catalogo.buscar("la-cripta-del-eclipse").orElseThrow();
    }

    @Test
    @DisplayName("criterios 1 y 2: la segunda mision tiene detalle propio y sigue a La Forja Sumergida")
    void publicadaConContenidoDistinto() {
        Mision forja = catalogo.buscar("la-forja-sumergida").orElseThrow();
        Mision cripta = cripta();

        assertThat(catalogo.todas()).extracting(Mision::id)
                .containsSubsequence("la-forja-sumergida", "la-cripta-del-eclipse");
        assertThat(cripta.origen()).isEqualTo(Origen.EQUIPO);
        assertThat(cripta.nombre()).isEqualTo("La Cripta del Eclipse");
        assertThat(cripta.categoria()).isEqualTo(Categoria.HISTORIA);
        assertThat(cripta.dificultad()).isEqualTo(Dificultad.EXTREMO);
        assertThat(cripta.duracionHoras()).isEqualTo(24);
        assertThat(cripta.nivelRecomendado()).isEqualTo(8);
        assertThat(cripta.requisitosPrevios()).containsExactly("la-forja-sumergida");
        assertThat(cripta.descripcionBreve()).isNotBlank();
        assertThat(cripta.narrativa()).contains("Guerra del Eclipse", "Reina Sin Rostro");

        assertThat(cripta.nombre()).isNotEqualTo(forja.nombre());
        assertThat(cripta.narrativa()).isNotEqualTo(forja.narrativa());
        assertThat(cripta.objetivos()).isNotEqualTo(forja.objetivos());
        assertThat(cripta.enemigos()).isNotEqualTo(forja.enemigos());
    }

    @Test
    @DisplayName("criterios 3 y 4: define requisitos, objetivos, encuentros regulares y enfrentamiento final")
    void recorridoCompletoDefinido() {
        Mision cripta = cripta();

        assertThat(cripta.objetivos()).extracting(Objetivo::texto, Objetivo::principal, Objetivo::tipo,
                        Objetivo::valor, Objetivo::botin)
                .containsExactly(
                        tuple("Derrotar a la Reina Sin Rostro (jefe final).", true,
                                TipoDeObjetivo.DERROTAR_JEFE, null, null),
                        tuple("Romper los 4 sellos de la cripta.", true,
                                TipoDeObjetivo.COMPLETAR_ENCUENTROS, null, null),
                        tuple("Completar la misión sin que la vida del héroe baje del 30 %.", false,
                                TipoDeObjetivo.VIDA_MINIMA, 30, null),
                        tuple("Derrotar al Máster si aparece.", false,
                                TipoDeObjetivo.DERROTAR_MASTER, null, null),
                        tuple("Recuperar los 3 fragmentos del Sello del Eclipse.", false,
                                TipoDeObjetivo.OBTENER_BOTIN, 3, "Fragmento del Sello del Eclipse"));

        assertThat(cripta.enemigos()).extracting("nombre", "cantidad", "prototipo", "vida", "defensa")
                .containsExactly(
                        tuple("Centinelas de Obsidiana", 8, "Guerrero Tanque", 36, 82),
                        tuple("Oráculos de Ceniza", 2, "Mago Fuego", 18, 68),
                        tuple("Acechadores sin Rostro", 2, "Pícaro Veneno", 18, 60));
        assertThat(cripta.encuentrosRegulares()).isEqualTo(12);
        assertThat(cripta.encuentros()).isEqualTo(13);
        assertThat(cripta.jefe().nombre()).isEqualTo("La Reina Sin Rostro");
        assertThat(cripta.jefe().prototipo()).isEqualTo("Pícaro Machete");
        assertThat(cripta.jefe().vida()).isEqualTo(180);
    }

    @Test
    @DisplayName("criterios 5 a 7: el Master puede aparecer al 25 % y usa la epica aprobada de Picaro Veneno")
    void masterConEpicaYProbabilidadAprobadas() {
        Mision cripta = cripta();
        MasterDeMision master = cripta.masters().getFirst();
        EpicaDeTabla20 fila = catalogo.tabla20().stream()
                .filter(f -> f.prototipo().equals("Pícaro Veneno"))
                .findFirst().orElseThrow();

        assertThat(cripta.masters()).hasSize(1);
        assertThat(master.nombre()).isEqualTo("El Recaudador de Ecos");
        assertThat(master.prototipo()).isEqualTo("Pícaro Veneno");
        assertThat(master.probabilidad()).isEqualTo(0.25);
        assertThat(master.epica()).isEqualTo(fila.epica());
        assertThat(master.epica().nombre()).isEqualTo("Toma y lleva");
        assertThat(master.epica().efectoGeneral()).isEqualTo("+1 al ataque");
        assertThat(master.epica().efectoPotenciado())
                .isEqualTo("Disminuye a la mitad del daño causado por el oponente y se lo retorna");
        assertThat(master.epica().productoId()).isEqualTo(TOMA_Y_LLEVA);
    }

    @Test
    @DisplayName("criterio 8: las recompensas aumentan con la dificultad y la duracion frente a la primera mision")
    void recompensasBalanceadas() {
        Mision forja = catalogo.buscar("la-forja-sumergida").orElseThrow();
        Mision cripta = cripta();
        RecompensasDeMision recompensas = cripta.recompensas();

        assertThat(cripta.dificultad()).isEqualTo(Dificultad.EXTREMO);
        assertThat(cripta.duracionHoras()).isGreaterThan(forja.duracionHoras());
        assertThat(recompensas.creditos()).isEqualTo(100).isGreaterThan(forja.recompensas().creditos());
        assertThat(recompensas.garantizadas()).singleElement().satisfies(item -> {
            assertThat(item.nombre()).isEqualTo("Veneno lacerante");
            assertThat(item.productoId()).isEqualTo(VENENO_LACERANTE);
            assertThat(item.entregable()).isTrue();
        });
        assertThat(recompensas.potenciales()).extracting("nombre", "probabilidad", "cantidad", "productoId")
                .containsExactly(
                        tuple("Fragmento del Sello del Eclipse", 0.55, 3, null),
                        tuple("Daga purulenta", 0.25, 1, DAGA_PURULENTA),
                        tuple("Mano del desterrado", 0.15, 1, MANO_DEL_DESTERRADO));
        assertThat(recompensas.primeraVez().creditos()).isEqualTo(20);
        assertThat(recompensas.primeraVez().otras()).containsExactly("Título «Guardián del Eclipse»");
    }

    @Test
    @DisplayName("RG-108: el equipo publica exactamente dos misiones completas y encadenadas")
    void dosMisionesDelEquipo() {
        List<Mision> delEquipo = catalogo.todas().stream().filter(m -> m.origen() == Origen.EQUIPO).toList();

        assertThat(delEquipo).extracting(Mision::id)
                .containsExactly("la-forja-sumergida", "la-cripta-del-eclipse");
        assertThat(delEquipo).allSatisfy(m -> {
            assertThat(m.enemigos()).isNotEmpty();
            assertThat(m.jefe()).isNotNull();
            assertThat(m.masters()).isNotEmpty();
            assertThat(m.recompensas()).isNotNull();
        });
    }
}
