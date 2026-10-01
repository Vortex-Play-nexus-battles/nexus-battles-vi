package nexus.misiones.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.Misiones;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El documento de Mongo guarda lo que la continuidad en segundo plano necesita (HU-SIM-007): la reserva de la
 * simulacion, los intentos y el ultimo error. Sin Docker: es solo el mapeo entre el dominio y el documento.
 */
class EjecucionDocumentoTest {

    private static final Instant INICIO = Instant.parse("2026-10-01T10:00:00Z");

    private static Ejecucion enCurso() {
        return Ejecucion.nueva(UUID.randomUUID(), "templo-olvidado", "uid-1", Misiones.HEROE, List.of(),
                Escalon.NORMAL, INICIO, Duration.ofHours(1), 99L, null);
    }

    @Test
    @DisplayName("la reserva, los intentos y el ultimo error de la simulacion sobreviven a guardar y leer")
    void idaYVuelta() {
        Ejecucion ejecucion = enCurso();
        Instant vence = INICIO.plus(Duration.ofHours(1));
        ejecucion.reservarParaSimular(vence);
        ejecucion.simulacionFallida(vence, Duration.ofSeconds(30), "heroes no responde");

        Ejecucion leida = EjecucionDocumento.de(ejecucion, 3).aDominio();

        assertThat(leida.intentosDeSimulacion()).isEqualTo(1);
        assertThat(leida.simulacionReservadaHasta()).isEqualTo(vence.plusSeconds(30));
        assertThat(leida.ultimoErrorDeSimulacion()).isEqualTo("heroes no responde");
        assertThat(leida.version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("una ejecucion guardada antes de este cambio, sin esos campos, se lee con cero intentos y sin reserva")
    void documentoViejo() {
        EjecucionDocumento nuevo = EjecucionDocumento.de(enCurso(), 0);
        EjecucionDocumento viejo = new EjecucionDocumento(nuevo.id(), nuevo.misionId(), nuevo.jugadorUid(),
                nuevo.heroe(), nuevo.estrategia(), nuevo.escalon(), nuevo.iniciadaEn(), nuevo.terminaEn(),
                nuevo.semilla(), nuevo.claveIdempotencia(), nuevo.estado(), nuevo.terminadaEn(), nuevo.resultado(),
                nuevo.recompensas(), nuevo.pasos(), nuevo.motivos(), nuevo.intentosDeLiquidacion(),
                nuevo.proximoIntento(), nuevo.ultimoError(), nuevo.nivelAlcanzado(), nuevo.experienciaAcumulada(),
                nuevo.liquidacionPendiente(), nuevo.version(), null, null, null);

        Ejecucion leida = viejo.aDominio();

        assertThat(leida.intentosDeSimulacion()).isZero();
        assertThat(leida.simulacionReservadaHasta()).isNull();
        assertThat(leida.ultimoErrorDeSimulacion()).isNull();
        assertThat(leida.reclamable(INICIO.plus(Duration.ofHours(1)))).isTrue();
    }
}
