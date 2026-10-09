package nexus.misiones.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import nexus.misiones.dominio.simulacion.AzarConSemilla;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HuMis007AceptacionTest {

    @Test
    @DisplayName("calcula créditos, experiencia y botín de una misión completada")
    void calculaRecompensasBase() {
        RecompensasDeEjecucion recompensas = calcular(
                Escalon.NORMAL,
                resultado(true, true, List.of()),
                ParametrosDeRecompensa.provisionales());

        assertThat(recompensas.creditos()).isEqualTo(50);
        assertThat(recompensas.experiencia()).isEqualTo(250);
        assertThat(recompensas.sinEntregar()).isNotEmpty();
    }

    @Test
    @DisplayName("entrega la épica únicamente al derrotar el Máster y completar la misión")
    void entregaEpicaDelMaster() {
        ResultadoDeMision.MasterEnfrentado master = new ResultadoDeMision.MasterEnfrentado(
                "Sombra del Olvido", Misiones.VELO_DE_SOMBRAS, true);
        ParametrosDeRecompensa exigeCompletar = new ParametrosDeRecompensa(Map.of(), Map.of(), true);

        RecompensasDeEjecucion completada = calcular(
                Escalon.NORMAL, resultado(true, true, List.of(master)), exigeCompletar);
        RecompensasDeEjecucion fallida = calcular(
                Escalon.NORMAL, resultado(false, false, List.of(master)), exigeCompletar);

        assertThat(completada.epicas()).extracting(RecompensasDeEjecucion.EpicaGanada::nombre)
                .containsExactly("Velo de Sombras");
        assertThat(fallida.epicas()).isEmpty();
    }

    @Test
    @DisplayName("el escalón de dificultad incrementa los créditos configurados")
    void aplicaMultiplicadorDeDificultad() {
        RecompensasDeEjecucion normal = calcular(
                Escalon.NORMAL, resultado(true, true, List.of()), ParametrosDeRecompensa.provisionales());
        RecompensasDeEjecucion heroico = calcular(
                Escalon.HEROICO, resultado(true, true, List.of()), ParametrosDeRecompensa.provisionales());
        RecompensasDeEjecucion legendario = calcular(
                Escalon.LEGENDARIO, resultado(true, true, List.of()), ParametrosDeRecompensa.provisionales());

        assertThat(normal.creditos()).isEqualTo(50);
        assertThat(heroico.creditos()).isEqualTo(75);
        assertThat(legendario.creditos()).isEqualTo(100);
    }

    @Test
    @DisplayName("suma únicamente las bonificaciones de objetivos cumplidos")
    void aplicaBonificacionesCumplidas() {
        Mision base = Misiones.minima("bonificaciones", Categoria.HISTORIA, 1, List.of(), List.of(), null);
        List<Objetivo> objetivos = List.of(
                new Objetivo("Derrotar al jefe", true, TipoDeObjetivo.DERROTAR_JEFE, null, null),
                new Objetivo("Derrotar al Máster", false, TipoDeObjetivo.DERROTAR_MASTER, null, null));
        Mision conBonificaciones = new Mision(
                base.id(), base.origen(), base.nombre(), base.categoria(), base.descripcionBreve(), base.imagen(),
                base.dificultad(), base.duracionHoras(), base.nivelRecomendado(), base.requisitosPrevios(),
                base.narrativa(), base.escenario(), objetivos, base.enemigos(), base.jefe(), base.masters(),
                new RecompensasDeMision(
                        5,
                        List.of(),
                        List.of(),
                        List.of(
                                new RecompensasDeMision.BonificacionPorObjetivo(0, 7),
                                new RecompensasDeMision.BonificacionPorObjetivo(1, 11)),
                        null),
                base.destacada(), base.disponibleHasta(), base.intentos());

        RecompensasDeEjecucion recompensas = CalculadoraDeRecompensas.calcular(
                conBonificaciones,
                Escalon.NORMAL,
                resultado(true, true, List.of()),
                false,
                ParametrosDeRecompensa.provisionales(),
                new AzarConSemilla(7));

        assertThat(recompensas.creditos()).isEqualTo(12);
        assertThat(recompensas.objetivos()).containsExactly(
                new RecompensasDeEjecucion.ObjetivoEvaluado("Derrotar al jefe", true, "+7 créditos"),
                new RecompensasDeEjecucion.ObjetivoEvaluado("Derrotar al Máster", false, null));
    }

    private static RecompensasDeEjecucion calcular(
            Escalon escalon,
            ResultadoDeMision resultado,
            ParametrosDeRecompensa parametros) {
        return CalculadoraDeRecompensas.calcular(
                Misiones.templo(), escalon, resultado, false, parametros, new AzarConSemilla(7));
    }

    private static ResultadoDeMision resultado(
            boolean exito,
            boolean jefeDerrotado,
            List<ResultadoDeMision.MasterEnfrentado> masters) {
        return new ResultadoDeMision(
                exito,
                jefeDerrotado,
                exito ? 19 : 3,
                exito,
                400,
                30,
                90,
                3,
                List.of(),
                List.of(new ResultadoDeMision.EnemigoDerrotado("Sombras Corrompidas", exito ? 10 : 3)),
                masters,
                exito ? 80 : 0,
                250,
                List.of());
    }
}
