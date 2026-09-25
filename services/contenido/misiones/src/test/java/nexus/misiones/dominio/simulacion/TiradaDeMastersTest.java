package nexus.misiones.dominio.simulacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Misiones;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Seccion 7.8.4: «aparicion aleatoria durante la mision segun probabilidad
 * definida», «pueden aparecer multiples Master en misiones largas», «cada tipo
 * de heroe tiene Master asociados con epicas especificas» (Tabla 20).
 */
class TiradaDeMastersTest {

    private static final EpicaDeTabla20 TANQUE = new EpicaDeTabla20("Guerrero Tanque",
            new Epica("Golpe de defensa", "+1 al ataque", "+4 al daño, +2% de crítico", "81af272d"), 0.04);
    private static final EpicaDeTabla20 ARMAS = new EpicaDeTabla20("Guerrero Armas",
            new Epica("Segundo impulso", "Recupera 1d4 de vida", "+3 a la vida, +5% de crítico", "4481eb34"), 0.01);

    @Test
    @DisplayName("la Tabla 20 da el Master del tipo del heroe y su porcentaje pasa a proporcion")
    void masterDeTabla20() {
        MasterDeMision master = TANQUE.comoMaster();

        assertThat(master.prototipo()).isEqualTo("Guerrero Tanque");
        assertThat(master.probabilidad()).isEqualTo(0.0004);
        assertThat(master.epica().nombre()).isEqualTo("Golpe de defensa");
    }

    @Test
    @DisplayName("con probabilidad 1 aparece el de la mision y el del tipo del heroe, y el de otro tipo no")
    void candidatos() {
        MasterDeMision seguro = new MasterDeMision("Sombra", "Pícaro Veneno", 1.0,
                new Epica("Velo de Sombras", null, null, null));
        EpicaDeTabla20 tanqueSeguro = new EpicaDeTabla20("Guerrero Tanque", TANQUE.epica(), 100);

        List<MasterDeMision> aparecen = TiradaDeMasters.quienesAparecen(
                Misiones.historia("mision-m", List.of(seguro)), "Guerrero Tanque",
                List.of(tanqueSeguro, ARMAS), new AzarConSemilla(1));

        assertThat(aparecen).extracting(MasterDeMision::nombre)
                .containsExactly("Sombra", "Master afin a Guerrero Tanque");
    }

    @Test
    @DisplayName("la probabilidad se respeta: con 15 % aparece en torno al 15 % de las misiones")
    void probabilidadSeRespeta() {
        MasterDeMision sombra = new MasterDeMision("Sombra del Olvido", "Pícaro Veneno", 0.15,
                new Epica("Velo de Sombras", null, null, null));
        AzarConSemilla azar = new AzarConSemilla(2026);

        long apariciones = IntStream.range(0, 20_000)
                .filter(i -> !TiradaDeMasters.quienesAparecen(Misiones.historia("mision-m", List.of(sombra)),
                        "Mago Fuego", List.of(), azar).isEmpty())
                .count();

        assertThat(apariciones / 20_000.0).isBetween(0.14, 0.16);
    }

    @Test
    @DisplayName("la misma semilla da las mismas apariciones")
    void reproducible() {
        MasterDeMision sombra = new MasterDeMision("Sombra del Olvido", "Pícaro Veneno", 0.5,
                new Epica("Velo de Sombras", null, null, null));

        List<Integer> primera = serie(new AzarConSemilla(42), sombra);
        List<Integer> segunda = serie(new AzarConSemilla(42), sombra);

        assertThat(primera).isEqualTo(segunda);
    }

    @Test
    @DisplayName("una exploracion tira una vez por cada 24 horas: pueden aparecer varios")
    void exploracionTiraVariasVeces() {
        MasterDeMision seguro = new MasterDeMision("Sombra", "Pícaro Veneno", 1.0,
                new Epica("Velo de Sombras", null, null, null));

        assertThat(TiradaDeMasters.tiradas(Misiones.historia("mision-h", List.of(seguro)))).isEqualTo(1);
        assertThat(TiradaDeMasters.tiradas(Misiones.exploracion("mision-e", 72))).isEqualTo(3);
        assertThat(TiradaDeMasters.quienesAparecen(Misiones.exploracion("mision-e", 48, seguro), "Mago Fuego",
                List.of(), new AzarConSemilla(3))).hasSize(2);
    }

    @Test
    @DisplayName("el Master esta dos niveles por encima del heroe, sin pasar del 8")
    void nivelDelMaster() {
        assertThat(MasterDeMision.nivelFrente(1)).isEqualTo(3);
        assertThat(MasterDeMision.nivelFrente(7)).isEqualTo(8);
        assertThat(MasterDeMision.nivelFrente(8)).isEqualTo(8);
    }

    private static List<Integer> serie(Azar azar, MasterDeMision master) {
        return IntStream.range(0, 50)
                .map(i -> TiradaDeMasters.quienesAparecen(Misiones.historia("mision-m", List.of(master)),
                        "Mago Fuego", List.of(), azar).size())
                .boxed()
                .toList();
    }
}
