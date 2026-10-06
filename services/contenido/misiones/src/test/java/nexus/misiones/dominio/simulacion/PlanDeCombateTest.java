package nexus.misiones.dominio.simulacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nexus.misiones.dominio.Misiones;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanDeCombateTest {

    private static Rival regular(String nombre) {
        return new Rival(nombre, TipoDeRival.REGULAR, "Guerrero Armas", 1, 10, 10, 8, List.of(), null);
    }

    @Test
    @DisplayName("los regulares van en su orden, el Master aparece entre ellos y el jefe siempre al final")
    void orden() {
        List<Rival> regulares = List.of(regular("A"), regular("B"), regular("C"));
        Rival master = new Rival("Sombra del Olvido", TipoDeRival.MASTER, "Pícaro Veneno", 3, 30, 8, 8,
                List.of(), Misiones.VELO_DE_SOMBRAS);
        Rival jefe = new Rival("Jefe", TipoDeRival.JEFE, "Guerrero Tanque", 1, 100, 11, 10, List.of(), null);

        for (long semilla = 0; semilla < 50; semilla++) {
            List<Rival> plan = PlanDeCombate.armar(regulares, List.of(master), jefe, new AzarConSemilla(semilla));

            assertThat(plan).hasSize(5);
            assertThat(plan.getLast()).isEqualTo(jefe);
            assertThat(plan.stream().filter(r -> r.tipo() == TipoDeRival.REGULAR).map(Rival::nombre))
                    .containsExactly("A", "B", "C");
            assertThat(plan).contains(master);
        }
    }

    @Test
    @DisplayName("criterio3_sinMasterEnLaTiradaElPlanNoTraeRivalesMasterConNingunaSemilla")
    void criterio3_sinMasterEnLaTiradaElPlanNoTraeRivalesMasterConNingunaSemilla() {
        // HU-SIM-005 C3: el plan solo lleva Master cuando la tirada los saco; sin ellos, ninguno.
        Rival jefe = new Rival("Jefe", TipoDeRival.JEFE, "Guerrero Tanque", 1, 100, 11, 10, List.of(), null);

        for (long semilla = 0; semilla < 50; semilla++) {
            List<Rival> plan = PlanDeCombate.armar(List.of(regular("A"), regular("B")), List.of(), jefe,
                    new AzarConSemilla(semilla));

            assertThat(plan).extracting(Rival::tipo).doesNotContain(TipoDeRival.MASTER);
        }
    }

    @Test
    @DisplayName("sin jefe ni Master, el plan son los regulares")
    void soloRegulares() {
        List<Rival> plan = PlanDeCombate.armar(List.of(regular("A")), List.of(), null, new AzarConSemilla(1));

        assertThat(plan).extracting(Rival::nombre).containsExactly("A");
    }
}
