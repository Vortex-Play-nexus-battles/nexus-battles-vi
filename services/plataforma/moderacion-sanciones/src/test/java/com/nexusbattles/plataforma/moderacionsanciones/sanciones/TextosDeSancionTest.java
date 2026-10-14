package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §7.3.7 «Cada notificacion debe incluir»: accion tomada, motivo especifico,
 * politica violada, proceso de apelacion y fecha de vigencia — escritos para
 * el jugador, con tildes y en su hora.
 */
@DisplayName("Textos de los avisos de sancion (§7.3.7)")
class TextosDeSancionTest {

    /** Viernes 16 de octubre de 2026, 4:02 p. m. en Colombia. */
    private static final OffsetDateTime EMITIDA = OffsetDateTime.of(2026, 10, 16, 21, 2, 0, 0, ZoneOffset.UTC);
    private static final UUID JUGADOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static Sancion sancion(Sancion.Tipo tipo, Causal causal, String politica) {
        return new Sancion(UUID.randomUUID(), JUGADOR, tipo, causal, "Insultó a otro jugador en el chat de la sala 12",
                politica, null, ADMIN, "ADMINISTRADOR", EMITIDA,
                tipo == Sancion.Tipo.SUSPENSION ? EMITIDA.plusDays(3) : null);
    }

    @Test
    @DisplayName("las fechas van en la hora de Colombia, escritas como se leen y con espacios normales")
    void fecha() {
        assertThat(TextosDeSancion.fecha(EMITIDA)).isEqualTo("16 de octubre de 2026, 4:02 p. m.");
        assertThat(TextosDeSancion.fecha(OffsetDateTime.of(2026, 1, 2, 13, 5, 0, 0, ZoneOffset.UTC)))
                .isEqualTo("2 de enero de 2026, 8:05 a. m.");
        assertThat(TextosDeSancion.fecha(EMITIDA)).doesNotContain(" ").doesNotContain(" ");
    }

    @Nested
    @DisplayName("la emision")
    class Emision {

        @Test
        @DisplayName("advertencia: causal, motivo, politica, que significa, hasta cuando apelar y por donde")
        void advertencia() {
            Sancion s = sancion(Sancion.Tipo.ADVERTENCIA, Causal.LENGUAJE_OFENSIVO, "Normas de convivencia");

            assertThat(TextosDeSancion.tituloDeLaEmision(s)).isEqualTo("Recibiste una advertencia");
            assertThat(TextosDeSancion.cuerpoDeLaEmision(s, EMITIDA.plusDays(30))).isEqualTo(
                    "Causal: Uso de lenguaje ofensivo o abusivo en comentarios o chat. "
                            + "Motivo: Insultó a otro jugador en el chat de la sala 12. "
                            + "Política: Normas de convivencia. "
                            + "La advertencia queda registrada y no restringe tu acceso. "
                            + "Puedes apelar hasta el 15 de noviembre de 2026, 4:02 p. m. desde «Mis sanciones», "
                            + "en el menú de tu cuenta; el panel te responderá en un plazo de 15 días hábiles.");
        }

        @Test
        @DisplayName("suspension: hasta cuando, que se conserva, y sin prometer una pantalla que no puede abrir")
        void suspension() {
            Sancion s = sancion(Sancion.Tipo.SUSPENSION, Causal.ACOSO, null);

            assertThat(TextosDeSancion.tituloDeLaEmision(s))
                    .isEqualTo("Tu cuenta está suspendida hasta el 19 de octubre de 2026, 4:02 p. m.");
            assertThat(TextosDeSancion.cuerpoDeLaEmision(s, EMITIDA.plusDays(30)))
                    .startsWith("Causal: Acoso a otros jugadores. Motivo: Insultó")
                    .contains("No podrás entrar hasta el 19 de octubre de 2026, 4:02 p. m.; tu inventario y tu "
                            + "progreso se mantienen intactos.")
                    .contains("Puedes apelar hasta el 15 de noviembre de 2026, 4:02 p. m.;")
                    .doesNotContain("Política:")
                    .doesNotContain("Mis sanciones");
        }

        @Test
        @DisplayName("baneo: inhabilitacion definitiva, inventario congelado y el plazo del panel")
        void baneo() {
            Sancion s = sancion(Sancion.Tipo.BANEO, Causal.FRAUDE, "Reglamento de juego limpio");

            assertThat(TextosDeSancion.tituloDeLaEmision(s)).isEqualTo("Tu cuenta fue inhabilitada de forma definitiva");
            assertThat(TextosDeSancion.cuerpoDeLaEmision(s, EMITIDA.plusDays(30)))
                    .contains("Causal: Intentos de fraude o manipulación del sistema.")
                    .contains("Política: Reglamento de juego limpio.")
                    .contains("Pierdes el acceso a todos los servicios y tu inventario queda congelado.")
                    .endsWith("el panel te responderá en un plazo de 15 días hábiles.");
        }

        @Test
        @DisplayName("sin causal (emitida por la API antes de SA-4): empieza por el motivo")
        void sinCausal() {
            Sancion s = sancion(Sancion.Tipo.ADVERTENCIA, null, null);

            assertThat(TextosDeSancion.cuerpoDeLaEmision(s, EMITIDA.plusDays(30)))
                    .startsWith("Motivo: Insultó a otro jugador en el chat de la sala 12. La advertencia")
                    .doesNotContain("Causal:");
        }

        @Test
        @DisplayName("un motivo que ya termina en punto, exclamacion o pregunta no se repite")
        void puntuacion() {
            Sancion conPunto = new Sancion(UUID.randomUUID(), JUGADOR, Sancion.Tipo.ADVERTENCIA, null,
                    "  Spam en el chat.  ", "Normas!", null, ADMIN, "ADMINISTRADOR", EMITIDA, null);

            assertThat(TextosDeSancion.cuerpoDeLaEmision(conPunto, EMITIDA.plusDays(30)))
                    .startsWith("Motivo: Spam en el chat. Política: Normas! La advertencia");
        }
    }

    @Test
    @DisplayName("apelacion recibida: quien la revisa y la fecha limite de su decision")
    void apelacionRecibida() {
        Sancion s = sancion(Sancion.Tipo.SUSPENSION, Causal.ACOSO, null);

        assertThat(TextosDeSancion.tituloDeLaApelacionRecibida()).isEqualTo("Recibimos tu apelación");
        assertThat(TextosDeSancion.cuerpoDeLaApelacionRecibida(s,
                OffsetDateTime.of(2026, 11, 6, 23, 59, 59, 999_000_000, ZoneOffset.ofHours(-5))))
                .isEqualTo("Un panel de moderadores y administradores revisará tu apelación sobre tu suspensión "
                        + "y te notificará su decisión a más tardar el 6 de noviembre de 2026, 11:59 p. m.");
    }

    @Test
    @DisplayName("resolucion: cada decision con su titulo; la reducida dice la nueva fecha")
    void resolucion() {
        Sancion s = sancion(Sancion.Tipo.SUSPENSION, Causal.ACOSO, null);

        assertThat(TextosDeSancion.tituloDeLaResolucion(Apelacion.Estado.MANTENIDA))
                .isEqualTo("Tu apelación fue resuelta: la sanción se mantiene");
        assertThat(TextosDeSancion.tituloDeLaResolucion(Apelacion.Estado.REDUCIDA))
                .isEqualTo("Tu apelación fue resuelta: la sanción se redujo");
        assertThat(TextosDeSancion.tituloDeLaResolucion(Apelacion.Estado.REVERTIDA))
                .isEqualTo("Tu apelación fue resuelta: la sanción se revirtió");
        assertThat(TextosDeSancion.tituloDeLaResolucion(Apelacion.Estado.PENDIENTE))
                .isEqualTo("Tu apelación sigue en revisión");
        assertThat(TextosDeSancion.cuerpoDeLaResolucion(s, Apelacion.Estado.REDUCIDA, "Primera falta",
                EMITIDA.plusDays(1)))
                .isEqualTo("Decisión del panel sobre tu suspensión: Primera falta. Tu suspensión termina ahora el "
                        + "17 de octubre de 2026, 4:02 p. m.");
        assertThat(TextosDeSancion.cuerpoDeLaResolucion(s, Apelacion.Estado.MANTENIDA, "Las pruebas son claras",
                null)).isEqualTo("Decisión del panel sobre tu suspensión: Las pruebas son claras.");
    }

    @Test
    @DisplayName("levantamiento y nombres: como se dicen, con tilde")
    void levantamiento() {
        Sancion s = sancion(Sancion.Tipo.SUSPENSION, null, null);

        assertThat(TextosDeSancion.tituloDelLevantamiento(s)).isEqualTo("Tu suspensión fue levantada");
        assertThat(TextosDeSancion.cuerpoDelLevantamiento(s, "Error de moderación"))
                .isEqualTo("Un administrador levantó tu suspensión. Motivo: Error de moderación.");
        assertThat(TextosDeSancion.nombreDe(Sancion.Tipo.ADVERTENCIA)).isEqualTo("advertencia");
        assertThat(TextosDeSancion.nombreDe(Sancion.Tipo.BANEO)).isEqualTo("inhabilitación definitiva");
    }

    @Test
    @DisplayName("las ocho causales de §7.3.2, en su orden y con el texto del documento")
    void causales() {
        assertThat(Causal.values()).extracting(Causal::etiqueta).containsExactly(
                "Uso de lenguaje ofensivo o abusivo en comentarios o chat",
                "Acoso a otros jugadores",
                "Intentos de fraude o manipulación del sistema",
                "Uso de software no autorizado o trampas",
                "Suplantación de identidad",
                "Violación de políticas de privacidad",
                "Incumplimiento de términos y condiciones del servicio",
                "Terminación de la relación legal entre las partes");
    }
}
