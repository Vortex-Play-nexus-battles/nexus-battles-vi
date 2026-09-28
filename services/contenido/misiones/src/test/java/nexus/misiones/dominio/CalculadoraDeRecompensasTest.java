package nexus.misiones.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import nexus.misiones.dominio.simulacion.AzarConSemilla;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Seccion 7.8.3 (sistema de recompensas) y 7.8.14 (el ejemplo): creditos base
 * al completar, primera vez una sola vez, botin con probabilidad, epica del
 * Master y experiencia. Lo que el documento da y el catalogo no tiene se
 * informa como no entregado: no se inventan productos.
 */
class CalculadoraDeRecompensasTest {

    private static final ParametrosDeRecompensa SIN_EXTRAS = ParametrosDeRecompensa.provisionales();

    private static ResultadoDeMision resultado(boolean exito, int vidaMinima,
                                               List<ResultadoDeMision.MasterEnfrentado> masters) {
        return new ResultadoDeMision(exito, exito, exito ? 19 : 3, exito, 400, 30, 90, 3,
                List.of(), List.of(new ResultadoDeMision.EnemigoDerrotado("Sombras Corrompidas", exito ? 10 : 3)),
                masters, vidaMinima, 250.0, List.of());
    }

    @Test
    @DisplayName("completada la primera vez: 50 creditos base mas 10 de primera vez, y la experiencia")
    void primeraVezEnElTemplo() {
        RecompensasDeEjecucion recompensas = CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(true, 80, List.of()), true, SIN_EXTRAS, new AzarConSemilla(1));

        assertThat(recompensas.creditos()).isEqualTo(60);
        assertThat(recompensas.experiencia()).isEqualTo(250.0);
        assertThat(recompensas.primeraVez()).isTrue();
    }

    @Test
    @DisplayName("repetida ya completada: la recompensa de primera vez no se vuelve a dar (HU-MIS-003)")
    void repetida() {
        RecompensasDeEjecucion recompensas = CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(true, 80, List.of()), false, SIN_EXTRAS, new AzarConSemilla(1));

        assertThat(recompensas.creditos()).isEqualTo(50);
        assertThat(recompensas.sinEntregar()).extracting(RecompensasDeEjecucion.SinEntregar::nombre)
                .doesNotContain("Título «Explorador del Templo»");
    }

    @Test
    @DisplayName("fallida: sin creditos ni botin, pero la experiencia de los enemigos derrotados si (6.1.1)")
    void fallida() {
        RecompensasDeEjecucion recompensas = CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(false, 0, List.of()), true, SIN_EXTRAS, new AzarConSemilla(1));

        assertThat(recompensas.creditos()).isZero();
        assertThat(recompensas.productos()).isEmpty();
        assertThat(recompensas.sinEntregar()).isEmpty();
        assertThat(recompensas.experiencia()).isEqualTo(250.0);
    }

    @Test
    @DisplayName("la experiencia por completar sale de la dificultad (parametro del PO; 0 por omision)")
    void experienciaPorDificultad() {
        ParametrosDeRecompensa conValores = new ParametrosDeRecompensa(Map.of(Dificultad.NORMAL, 40.0),
                Map.of(), false);

        assertThat(CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(true, 80, List.of()), false, conValores, new AzarConSemilla(1)).experiencia())
                .isEqualTo(290.0);
        assertThat(CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(false, 0, List.of()), false, conValores, new AzarConSemilla(1)).experiencia())
                .isEqualTo(250.0);
    }

    @Test
    @DisplayName("el cofre, los fragmentos, la armadura, la espada y el titulo no estan en el catalogo: se informan")
    void loQueNoEstaEnElCatalogoSeInforma() {
        RecompensasDeEjecucion recompensas = CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(true, 80, List.of()), true, SIN_EXTRAS, new AzarConSemilla(7));

        assertThat(recompensas.productos()).isEmpty();
        assertThat(recompensas.sinEntregar()).extracting(RecompensasDeEjecucion.SinEntregar::nombre)
                .contains("1 Cofre de Bronce", "Título «Explorador del Templo»");
        assertThat(recompensas.sinEntregar()).allSatisfy(s -> assertThat(s.motivo()).isNotBlank());
    }

    @Test
    @DisplayName("un botin con producto en el catalogo se entrega, sorteado unidad por unidad")
    void botinEntregable() {
        Mision conProducto = conBotin(new RecompensasDeMision.ObjetoPotencial("Espada de una mano", 0.6, 3,
                null, "1647b2ea-096d-37e7-b580-0172e4c62313"));

        double media = IntStream.range(0, 4000)
                .mapToDouble(semilla -> CalculadoraDeRecompensas.calcular(conProducto, Escalon.NORMAL,
                                resultado(true, 80, List.of()), false, SIN_EXTRAS, new AzarConSemilla(semilla))
                        .productos().stream().mapToInt(RecompensasDeEjecucion.ObjetoGanado::cantidad).sum())
                .average().orElseThrow();

        assertThat(media).isBetween(1.7, 1.9);
    }

    @Test
    @DisplayName("el objetivo de los fragmentos solo se cumple si salieron los tres")
    void objetivoDeBotin() {
        Mision templo = Misiones.templo();
        for (int semilla = 0; semilla < 200; semilla++) {
            RecompensasDeEjecucion r = CalculadoraDeRecompensas.calcular(templo, Escalon.NORMAL,
                    resultado(true, 80, List.of()), false, SIN_EXTRAS, new AzarConSemilla(semilla));
            long fragmentos = r.sinEntregar().stream()
                    .filter(s -> s.nombre().endsWith("Fragmento del Sello Antiguo")).count();
            int unidades = r.sinEntregar().stream()
                    .filter(s -> s.nombre().endsWith("Fragmento del Sello Antiguo"))
                    .mapToInt(s -> Integer.parseInt(s.nombre().split(" ")[0])).sum();
            boolean cumplido = objetivo(r, "Encontrar los 3 fragmentos del Sello Antiguo.").cumplido();
            assertThat(cumplido).as("semilla %d con %d fragmentos", semilla, unidades).isEqualTo(unidades == 3);
            assertThat(fragmentos).isLessThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("los objetivos del ejemplo se evaluan contra la simulacion")
    void objetivos() {
        RecompensasDeEjecucion conVida = CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(true, 60, List.of()), false, SIN_EXTRAS, new AzarConSemilla(1));
        RecompensasDeEjecucion sinVida = CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(true, 40, List.of()), false, SIN_EXTRAS, new AzarConSemilla(1));

        assertThat(objetivo(conVida, "Derrotar al Guardián del Templo (Jefe final).").cumplido()).isTrue();
        assertThat(objetivo(conVida, "Explorar las 5 cámaras del templo.").cumplido()).isTrue();
        assertThat(objetivo(conVida, "Completar la misión sin que la vida del héroe baje del 50%.").cumplido())
                .isTrue();
        assertThat(objetivo(sinVida, "Completar la misión sin que la vida del héroe baje del 50%.").cumplido())
                .isFalse();
        assertThat(objetivo(conVida, "Derrotar al Máster si aparece.").cumplido()).isFalse();
    }

    @Test
    @DisplayName("derrotar al Master da su epica; sin producto en el catalogo va a la coleccion y se informa")
    void epicaDelMaster() {
        RecompensasDeEjecucion recompensas = CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(true, 80, List.of(new ResultadoDeMision.MasterEnfrentado(
                        "Sombra del Olvido", Misiones.VELO_DE_SOMBRAS, true))),
                false, SIN_EXTRAS, new AzarConSemilla(1));

        assertThat(recompensas.epicas()).containsExactly(
                new RecompensasDeEjecucion.EpicaGanada("Velo de Sombras", "Sombra del Olvido", null));
        assertThat(recompensas.tieneEpicaEntregable()).isFalse();
        assertThat(recompensas.sinEntregar()).extracting(RecompensasDeEjecucion.SinEntregar::nombre)
                .contains("Épica «Velo de Sombras»");
        assertThat(objetivo(recompensas, "Derrotar al Máster si aparece.").cumplido()).isTrue();
    }

    @Test
    @DisplayName("la epica de la Tabla 20 tiene producto: se entrega; tambien si la mision falla, salvo que el PO lo exija")
    void epicaDeTabla20() {
        Epica golpe = new Epica("Golpe de defensa", "+1 al ataque", "+4 al daño", "81af272d-74fb-3dc1-b6ff-01fdc99a1c1d");
        ResultadoDeMision fallidaConMaster = resultado(false, 0,
                List.of(new ResultadoDeMision.MasterEnfrentado("Master afin a Guerrero Tanque", golpe, true)));

        RecompensasDeEjecucion segunElDocumento = CalculadoraDeRecompensas.calcular(Misiones.templo(),
                Escalon.NORMAL, fallidaConMaster, false, SIN_EXTRAS, new AzarConSemilla(1));
        RecompensasDeEjecucion exigiendoCompletar = CalculadoraDeRecompensas.calcular(Misiones.templo(),
                Escalon.NORMAL, fallidaConMaster, false, new ParametrosDeRecompensa(Map.of(), Map.of(), true),
                new AzarConSemilla(1));

        assertThat(segunElDocumento.epicas()).extracting(RecompensasDeEjecucion.EpicaGanada::nombre)
                .containsExactly("Golpe de defensa");
        assertThat(segunElDocumento.tieneEpicaEntregable()).isTrue();
        assertThat(exigiendoCompletar.epicas()).isEmpty();
    }

    @Test
    @DisplayName("un Master que aparecio y no cayo no da epica")
    void masterNoDerrotado() {
        RecompensasDeEjecucion recompensas = CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.NORMAL,
                resultado(false, 0, List.of(new ResultadoDeMision.MasterEnfrentado(
                        "Sombra del Olvido", Misiones.VELO_DE_SOMBRAS, false))),
                false, SIN_EXTRAS, new AzarConSemilla(1));

        assertThat(recompensas.epicas()).isEmpty();
    }

    @Test
    @DisplayName("el escalon multiplica los creditos base (provisional: el mismo factor que las estadisticas)")
    void escalon() {
        RecompensasDeEjecucion heroico = CalculadoraDeRecompensas.calcular(Misiones.templo(), Escalon.HEROICO,
                resultado(true, 80, List.of()), false, SIN_EXTRAS, new AzarConSemilla(1));

        assertThat(heroico.creditos()).isEqualTo(75);
    }

    @Test
    @DisplayName("una bonificacion por objetivo se suma solo si el objetivo se cumplio")
    void bonificacionPorObjetivo() {
        Mision base = Misiones.minima("con-bono", Categoria.HISTORIA, 1, List.of(), List.of(), null);
        Mision conBono = new Mision(base.id(), base.origen(), base.nombre(), base.categoria(),
                base.descripcionBreve(), null, base.dificultad(), base.duracionHoras(), null, List.of(),
                base.narrativa(), null, base.objetivos(), base.enemigos(), base.jefe(), List.of(),
                new RecompensasDeMision(5, List.of(), List.of(),
                        List.of(new RecompensasDeMision.BonificacionPorObjetivo(0, 7)), null),
                false, null, null);

        RecompensasDeEjecucion cumplido = CalculadoraDeRecompensas.calcular(conBono, Escalon.NORMAL,
                resultado(true, 80, List.of()), false, SIN_EXTRAS, new AzarConSemilla(1));

        assertThat(cumplido.creditos()).isEqualTo(12);
        assertThat(cumplido.objetivos().getFirst().bonificacion()).isEqualTo("+7 créditos");
    }

    private static RecompensasDeEjecucion.ObjetivoEvaluado objetivo(RecompensasDeEjecucion r, String texto) {
        return r.objetivos().stream().filter(o -> o.texto().equals(texto)).findFirst().orElseThrow();
    }

    private static Mision conBotin(RecompensasDeMision.ObjetoPotencial botin) {
        Mision base = Misiones.minima("con-botin", Categoria.HISTORIA, 1, List.of(), List.of(), null);
        return new Mision(base.id(), base.origen(), base.nombre(), base.categoria(), base.descripcionBreve(),
                null, base.dificultad(), base.duracionHoras(), null, List.of(), base.narrativa(), null,
                base.objetivos(), base.enemigos(), base.jefe(), List.of(),
                new RecompensasDeMision(5, List.of(), List.of(botin), List.of(), null), false, null, null);
    }
}
