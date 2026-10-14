package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §7.3.2 «notificacion de la decision final en un plazo de 15 dias habiles»:
 * hasta cuando tiene el panel, contado en la hora de Colombia.
 */
@DisplayName("Plazo de decision de una apelacion · 15 dias habiles (§7.3.2)")
class PlazoDeDecisionTest {

    private static final ZoneOffset BOGOTA = ZoneOffset.ofHours(-5);

    private final PlazoDeDecision deLunesAViernes = PlazoDeDecision.deLunesAViernes();

    @Test
    @DisplayName("presentada un lunes: el plazo acaba al final del lunes tres semanas despues")
    void desdeUnLunes() {
        // Lunes 21 de septiembre de 2026, 5:00 a. m. en Colombia.
        OffsetDateTime presentada = OffsetDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC);

        // Del martes 22 de septiembre al lunes 12 de octubre: quince dias de lunes a viernes.
        assertThat(deLunesAViernes.limitePara(presentada))
                .isEqualTo(OffsetDateTime.of(2026, 10, 12, 23, 59, 59, 999_000_000, BOGOTA))
                .isEqualTo(OffsetDateTime.parse("2026-10-13T04:59:59.999Z"));
    }

    @Test
    @DisplayName("el dia en que se presenta no cuenta, aunque se presente a primera hora")
    void elPrimerDiaNoCuenta() {
        OffsetDateTime aLaMedianoche = OffsetDateTime.of(2026, 9, 21, 0, 0, 0, 0, BOGOTA);
        OffsetDateTime alFinal = OffsetDateTime.of(2026, 9, 21, 23, 59, 0, 0, BOGOTA);

        assertThat(deLunesAViernes.limitePara(aLaMedianoche)).isEqualTo(deLunesAViernes.limitePara(alFinal));
    }

    @Test
    @DisplayName("cuenta en la hora de Colombia: el viernes a las 9 p. m. ya es sabado en UTC, y sigue siendo viernes")
    void enLaHoraDeColombia() {
        // Viernes 25 de septiembre, 9:00 p. m. en Colombia = sabado 26, 02:00 UTC.
        OffsetDateTime viernesPorLaNoche = OffsetDateTime.of(2026, 9, 26, 2, 0, 0, 0, ZoneOffset.UTC);

        // Del lunes 28 de septiembre al viernes 16 de octubre.
        assertThat(deLunesAViernes.limitePara(viernesPorLaNoche))
                .isEqualTo(OffsetDateTime.of(2026, 10, 16, 23, 59, 59, 999_000_000, BOGOTA));
    }

    @Test
    @DisplayName("presentada un sabado o un domingo: se empieza a contar el lunes")
    void enFinDeSemana() {
        OffsetDateTime sabado = OffsetDateTime.of(2026, 9, 26, 12, 0, 0, 0, BOGOTA);
        OffsetDateTime domingo = OffsetDateTime.of(2026, 9, 27, 12, 0, 0, 0, BOGOTA);
        OffsetDateTime viernes = OffsetDateTime.of(2026, 9, 25, 12, 0, 0, 0, BOGOTA);

        assertThat(deLunesAViernes.limitePara(sabado))
                .isEqualTo(deLunesAViernes.limitePara(domingo))
                .isEqualTo(deLunesAViernes.limitePara(viernes))
                .isEqualTo(OffsetDateTime.of(2026, 10, 16, 23, 59, 59, 999_000_000, BOGOTA));
    }

    @Test
    @DisplayName("un festivo configurado no cuenta: el plazo se corre un dia habil")
    void conFestivos() {
        // El 12 de octubre de 2026 es festivo en Colombia.
        PlazoDeDecision conElDoceDeOctubre = new PlazoDeDecision(Set.of(LocalDate.of(2026, 10, 12)));
        OffsetDateTime presentada = OffsetDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC);

        assertThat(conElDoceDeOctubre.limitePara(presentada))
                .isEqualTo(OffsetDateTime.of(2026, 10, 13, 23, 59, 59, 999_000_000, BOGOTA));
    }

    @Test
    @DisplayName("un festivo en fin de semana no cambia nada: ese dia ya no contaba")
    void festivoEnFinDeSemana() {
        PlazoDeDecision conUnSabado = new PlazoDeDecision(Set.of(LocalDate.of(2026, 10, 3)));
        OffsetDateTime presentada = OffsetDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC);

        assertThat(conUnSabado.limitePara(presentada)).isEqualTo(deLunesAViernes.limitePara(presentada));
    }

    @Test
    @DisplayName("vencida solo despues del ultimo milisegundo del plazo")
    void vencida() {
        OffsetDateTime presentada = OffsetDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime limite = deLunesAViernes.limitePara(presentada);

        assertThat(deLunesAViernes.vencida(presentada, presentada)).isFalse();
        assertThat(deLunesAViernes.vencida(presentada, limite)).isFalse();
        assertThat(deLunesAViernes.vencida(presentada, limite.plusNanos(1_000_000))).isTrue();
    }

    @Test
    @DisplayName("el numero lo fija el documento oficial: quince, en la hora de Bogota")
    void constantes() {
        assertThat(PlazoDeDecision.DIAS_HABILES).isEqualTo(15);
        assertThat(PlazoDeDecision.ZONA.getId()).isEqualTo("America/Bogota");
    }

    @Test
    @DisplayName("SANCIONES_FESTIVOS: fechas ISO separadas por coma; vacia, de lunes a viernes")
    void festivosDeLaConfiguracion() {
        ConfiguracionDeSanciones configuracion = new ConfiguracionDeSanciones();
        OffsetDateTime presentada = OffsetDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneOffset.UTC);

        assertThat(configuracion.plazoDeDecision(" 2026-10-12 , 2026-12-25,").limitePara(presentada))
                .isEqualTo(OffsetDateTime.of(2026, 10, 13, 23, 59, 59, 999_000_000, BOGOTA));
        assertThat(configuracion.plazoDeDecision("").limitePara(presentada))
                .isEqualTo(deLunesAViernes.limitePara(presentada));
        assertThatThrownBy(() -> configuracion.plazoDeDecision("12/10/2026"))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
    }

    @Test
    @DisplayName("sin fecha de presentacion o sin festivos no hay plazo que calcular")
    void nulos() {
        assertThatThrownBy(() -> deLunesAViernes.limitePara(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> deLunesAViernes.vencida(OffsetDateTime.now(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlazoDeDecision(null)).isInstanceOf(NullPointerException.class);
    }
}
