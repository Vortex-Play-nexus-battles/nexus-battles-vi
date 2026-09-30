package com.nexusbattles.plataforma.torneos.torneo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las piezas puras de B10: claves, transiciones, espera, estado de pagos y premio, avisos. */
@DisplayName("Torneos · operaciones por participante (B10)")
class OperacionesTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private final UUID torneoId = UUID.randomUUID();
    private final UUID capitan = UUID.randomUUID();
    private final UUID companero = UUID.randomUUID();

    @Test
    @DisplayName("la clave es torneo-<id>-jugador-<uid>-<que> y cabe en los limites de libro (128) e inventario (100)")
    void claves() {
        Operacion cobro = Operacion.cobro(torneoId, UUID.randomUUID(), capitan, UUID.randomUUID(), 10, AHORA);
        assertThat(cobro.clave()).isEqualTo("torneo-" + torneoId + "-jugador-" + capitan + "-cobro");
        assertThat(Operacion.clave(torneoId, capitan, "inscripcion")).hasSizeLessThanOrEqualTo(128);
        assertThat(Operacion.clave(torneoId, capitan, "devolucion")).hasSizeLessThanOrEqualTo(128);
        Operacion premio = Operacion.premio(torneoId, UUID.randomUUID(), capitan, 100, " epica-1 ", AHORA);
        assertThat(premio.productoId()).isEqualTo("epica-1");
        assertThat(premio.claveDeEpica()).endsWith("-epica").hasSizeLessThanOrEqualTo(100);
        assertThat(Operacion.premio(torneoId, null, capitan, 0, " ", AHORA).productoId()).isNull();
        assertThat(Operacion.aviso(torneoId, null, capitan, "inicio", "t", "c", AHORA).clave()).endsWith("-aviso-inicio");
        assertThat(Operacion.correo(torneoId, null, capitan, "inicio", "t", "c", AHORA).clave()).endsWith("-correo-inicio");
    }

    @Test
    @DisplayName("transiciones: hecha, reintentable, fallida, excluida, omitida; solo una FALLIDA se reabre")
    void transiciones() {
        Operacion op = Operacion.cobro(torneoId, null, capitan, UUID.randomUUID(), 10, AHORA);
        assertThat(op.estado()).isEqualTo(Operacion.Estado.PENDIENTE);
        assertThat(op.estado().abierta()).isTrue();
        op.reintentable("x".repeat(600), AHORA.plusSeconds(15), AHORA);
        assertThat(op.estado()).isEqualTo(Operacion.Estado.REINTENTABLE);
        assertThat(op.ultimoError()).hasSize(500);
        assertThat(op.proximoIntento()).isEqualTo(AHORA.plusSeconds(15));
        op.reabrir(AHORA);
        assertThat(op.estado()).as("solo una FALLIDA se reabre").isEqualTo(Operacion.Estado.REINTENTABLE);
        op.fallida("rechazada", AHORA);
        assertThat(op.estado().abierta()).isFalse();
        op.reabrir(AHORA.plusSeconds(5));
        assertThat(op.estado()).isEqualTo(Operacion.Estado.PENDIENTE);
        assertThat(op.intentos()).isZero();
        assertThat(op.proximoIntento()).isEqualTo(AHORA.plusSeconds(5));
        op.hecha("ok", AHORA);
        assertThat(op.estado()).isEqualTo(Operacion.Estado.HECHA);
        assertThat(op.ultimoError()).isNull();
        Operacion excluida = Operacion.premio(torneoId, null, capitan, 1, null, AHORA);
        excluida.excluida("sancion", AHORA);
        assertThat(excluida.estado()).isEqualTo(Operacion.Estado.EXCLUIDA);
        Operacion omitida = Operacion.correo(torneoId, null, capitan, "inicio", "t", "c", AHORA);
        omitida.omitida("sin correo", AHORA);
        assertThat(omitida.estado()).isEqualTo(Operacion.Estado.OMITIDA);
        assertThat(omitida.resultado()).isEqualTo("sin correo");
    }

    @Test
    @DisplayName("espera exponencial con techo; se agota al llegar a los intentos maximos")
    void espera() {
        PoliticaDeReintentos politica = new PoliticaDeReintentos(Duration.ofSeconds(15), Duration.ofMinutes(30), 5,
                Duration.ofMinutes(2));
        assertThat(politica.siguiente(1, AHORA)).isEqualTo(AHORA.plusSeconds(15));
        assertThat(politica.siguiente(2, AHORA)).isEqualTo(AHORA.plusSeconds(30));
        assertThat(politica.siguiente(4, AHORA)).isEqualTo(AHORA.plusSeconds(120));
        assertThat(politica.siguiente(40, AHORA)).isEqualTo(AHORA.plusMinutes(30));
        assertThat(politica.agotada(4)).isFalse();
        assertThat(politica.agotada(5)).isTrue();
        assertThatThrownBy(() -> new PoliticaDeReintentos(Duration.ZERO, Duration.ZERO, 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("premio: negativo no vale; cero y sin epica es vacio")
    void politicaDePremio() {
        assertThat(new PoliticaDePremio(0, " ").premio().vacio()).isTrue();
        assertThat(new PoliticaDePremio(0, "epica").premio().vacio()).isFalse();
        assertThat(new PoliticaDePremio(5, null).premio().vacio()).isFalse();
        assertThatThrownBy(() -> new PoliticaDePremio(-1, null)).isInstanceOf(IllegalArgumentException.class);
        Torneo anterior = new Torneo(torneoId, "Copa", capitan, AHORA, AHORA.plusDays(1), 0);
        assertThat(anterior.premio(new PoliticaDePremio(7, "e"))).as("sin premio anunciado manda la configuracion")
                .isEqualTo(new PoliticaDePremio.Premio(7, "e"));
    }

    @Test
    @DisplayName("estado del pago de un equipo segun sus operaciones")
    void estadoDelPago() {
        Equipo equipo = Equipo.deJugadores(UUID.randomUUID(), torneoId, "Los Dos", "av", capitan, companero, AHORA);
        assertThat(EstadoDeOperaciones.pago(equipo, List.of())).as("sin inscribir").isNull();
        UUID reserva = UUID.randomUUID();
        equipo.inscribir(1, capitan, reserva);
        assertThat(EstadoDeOperaciones.pago(equipo, List.of())).isEqualTo(EstadoDeOperaciones.Pago.RESERVADO);

        Operacion cobro = Operacion.cobro(torneoId, equipo.id(), capitan, reserva, 10, AHORA);
        assertThat(EstadoDeOperaciones.pago(equipo, List.of(cobro))).isEqualTo(EstadoDeOperaciones.Pago.COBRO_PENDIENTE);
        cobro.hecha("ok", AHORA);
        assertThat(EstadoDeOperaciones.pago(equipo, List.of(cobro))).isEqualTo(EstadoDeOperaciones.Pago.COBRADO);
        cobro.fallida("409", AHORA);
        assertThat(EstadoDeOperaciones.pago(equipo, List.of(cobro))).isEqualTo(EstadoDeOperaciones.Pago.REQUIERE_REVISION);

        Operacion devolucion = Operacion.devolucion(torneoId, equipo.id(), capitan, reserva, 10, AHORA);
        assertThat(EstadoDeOperaciones.pago(equipo, List.of(devolucion))).isEqualTo(EstadoDeOperaciones.Pago.DEVOLUCION_PENDIENTE);
        devolucion.hecha("ok", AHORA);
        assertThat(EstadoDeOperaciones.pago(equipo, List.of(devolucion))).isEqualTo(EstadoDeOperaciones.Pago.DEVUELTO);

        Equipo gratis = Equipo.deJugadores(UUID.randomUUID(), torneoId, "Gratis", "av", UUID.randomUUID(), UUID.randomUUID(), AHORA);
        gratis.inscribir(2, gratis.capitanUid(), null);
        assertThat(EstadoDeOperaciones.pago(gratis, List.of())).isEqualTo(EstadoDeOperaciones.Pago.SIN_COSTO);
        assertThat(EstadoDeOperaciones.pago(Equipo.deLaMaquina(UUID.randomUUID(), torneoId, 1, 3, AHORA), List.of())).isNull();
    }

    @Test
    @DisplayName("hitos: comprobante de inscripcion a los dos; el correo solo si esta configurado")
    void hitos() {
        Torneo torneo = new Torneo(torneoId, "Copa Otono", capitan, AHORA, AHORA.plusDays(1), 10);
        Equipo equipo = Equipo.deJugadores(UUID.randomUUID(), torneoId, "Los Dos", "av", capitan, companero, AHORA);
        equipo.inscribir(3, capitan, UUID.randomUUID());
        CanalesDePrueba canales = new CanalesDePrueba();
        Hitos hitos = new Hitos(canales);

        List<Operacion> sinCorreo = hitos.inscripcion(torneo, equipo, AHORA);
        assertThat(sinCorreo).hasSize(2).allSatisfy(op -> {
            assertThat(op.tipo()).isEqualTo(Operacion.Tipo.AVISO);
            assertThat(op.cuerpo()).contains("posición 3").contains("10 créditos");
        });
        canales.conCorreo = true;
        assertThat(hitos.inicio(torneo, equipo, 2, AHORA)).hasSize(4)
                .filteredOn(op -> op.tipo() == Operacion.Tipo.CORREO).hasSize(2);
        assertThat(hitos.inicio(torneo, equipo, null, AHORA).getFirst().cuerpo()).doesNotContain("encuentro");
        Torneo gratis = new Torneo(UUID.randomUUID(), "Copa Libre", capitan, AHORA, AHORA.plusDays(1), 0);
        assertThat(hitos.inscripcion(gratis, equipo, AHORA).getFirst().cuerpo()).contains("gratuita");

        Operacion premio = Operacion.premio(torneoId, equipo.id(), capitan, 100, "epica", AHORA);
        premio.creditosEntregados(AHORA);
        premio.epicaEntregada(AHORA);
        assertThat(hitos.premioEntregado(torneo, equipo, premio, AHORA).getFirst().cuerpo())
                .contains("100 créditos").contains("épica");
        Operacion soloEpica = Operacion.premio(torneoId, equipo.id(), capitan, 0, "epica", AHORA);
        soloEpica.epicaEntregada(AHORA);
        assertThat(hitos.premioEntregado(torneo, equipo, soloEpica, AHORA).getFirst().cuerpo()).doesNotContain("créditos");
    }

    /** Canales sin red: solo interesa si el correo esta configurado. */
    private static final class CanalesDePrueba implements AvisosAlJugador {
        boolean conCorreo;

        @Override
        public void notificar(UUID destinatario, String id, String titulo, String cuerpo, OffsetDateTime creadaEn) {
        }

        @Override
        public boolean correoConfigurado() {
            return conCorreo;
        }

        @Override
        public Correo enviarCorreo(UUID destinatario, UUID torneoId, String asunto, String mensaje, String clave) {
            return Correo.ENVIADO;
        }
    }
}
