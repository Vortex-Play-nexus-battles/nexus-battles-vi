package nexus.misiones.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.EstadoDePaso;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.PasoDeLiquidacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La lectura de una ejecucion guardada no se cae por un paso que esta version
 * no conoce: lo escribio una version mas nueva y el despliegue se revirtio.
 */
class EjecucionDocumentoTest {

    private static final Instant INICIO = Instant.parse("2026-10-04T10:00:00Z");
    private static final HeroeEnMision HEROE = new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-1", 1, 0, 8,
            44, 11);

    @Test
    @DisplayName("un paso desconocido se ignora, con su motivo; los conocidos se leen tal cual")
    void pasoDesconocido() {
        Ejecucion cancelada = Ejecucion.nueva(UUID.randomUUID(), "templo-olvidado", "uid-1", HEROE, List.of(),
                Escalon.NORMAL, INICIO, Duration.ofHours(12), 7L, null);
        cancelada.cancelar(INICIO.plusSeconds(60));
        EjecucionDocumento guardado = EjecucionDocumento.de(cancelada, 3);

        Map<String, String> pasos = new TreeMap<>(guardado.pasos());
        pasos.put("PASO_DEL_FUTURO", "PENDIENTE");
        Map<String, String> motivos = new TreeMap<>(guardado.motivos());
        motivos.put("PASO_DEL_FUTURO", "lo escribio otra version");
        EjecucionDocumento conOtraVersion = new EjecucionDocumento(guardado.id(), guardado.misionId(),
                guardado.jugadorUid(), guardado.heroe(), guardado.estrategia(), guardado.escalon(),
                guardado.iniciadaEn(), guardado.terminaEn(), guardado.semilla(), guardado.claveIdempotencia(),
                guardado.estado(), guardado.terminadaEn(), guardado.resultado(), guardado.recompensas(), pasos,
                motivos, guardado.intentosDeLiquidacion(), guardado.proximoIntento(), guardado.ultimoError(),
                guardado.nivelAlcanzado(), guardado.experienciaAcumulada(), true, guardado.version(),
                guardado.intentosDeSimulacion(), guardado.sinPenalizacion());

        Ejecucion leida = conOtraVersion.aDominio();

        assertThat(leida.pasos()).containsExactly(Map.entry(PasoDeLiquidacion.LIBERACION, EstadoDePaso.PENDIENTE));
        assertThat(leida.motivos()).isEmpty();
        assertThat(leida.pasosPendientes()).containsExactly(PasoDeLiquidacion.LIBERACION);
    }

    @Test
    @DisplayName("HU-SIM-007: los intentos de simulacion y el arriendo (proximoIntento) sobreviven a guardar y leer")
    void reservaIdaYVuelta() {
        Ejecucion ejecucion = Ejecucion.nueva(UUID.randomUUID(), "templo-olvidado", "uid-1", HEROE, List.of(),
                Escalon.NORMAL, INICIO, Duration.ofHours(1), 99L, null);
        Instant vence = INICIO.plus(Duration.ofHours(1));
        ejecucion.reservarParaSimular(vence);

        Ejecucion leida = EjecucionDocumento.de(ejecucion, 3).aDominio();

        assertThat(leida.intentosDeSimulacion()).isEqualTo(1);
        assertThat(leida.proximoIntento()).isEqualTo(vence.plus(Ejecucion.ARRIENDO_DE_SIMULACION));
        assertThat(leida.reservaVigente(vence.plusSeconds(1))).isTrue();
        assertThat(leida.version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("HU-SIM-007: una ejecucion guardada antes de este cambio, sin el campo, se lee con cero intentos y lista para simular")
    void documentoViejo() {
        Ejecucion ejecucion = Ejecucion.nueva(UUID.randomUUID(), "templo-olvidado", "uid-1", HEROE, List.of(),
                Escalon.NORMAL, INICIO, Duration.ofHours(1), 99L, null);
        EjecucionDocumento nuevo = EjecucionDocumento.de(ejecucion, 0);
        EjecucionDocumento viejo = new EjecucionDocumento(nuevo.id(), nuevo.misionId(), nuevo.jugadorUid(),
                nuevo.heroe(), nuevo.estrategia(), nuevo.escalon(), nuevo.iniciadaEn(), nuevo.terminaEn(),
                nuevo.semilla(), nuevo.claveIdempotencia(), nuevo.estado(), nuevo.terminadaEn(), nuevo.resultado(),
                nuevo.recompensas(), nuevo.pasos(), nuevo.motivos(), nuevo.intentosDeLiquidacion(),
                nuevo.proximoIntento(), nuevo.ultimoError(), nuevo.nivelAlcanzado(), nuevo.experienciaAcumulada(),
                nuevo.liquidacionPendiente(), nuevo.version(), null, null);

        Ejecucion leida = viejo.aDominio();

        assertThat(leida.intentosDeSimulacion()).isZero();
        assertThat(leida.listaParaSimular(INICIO.plus(Duration.ofHours(1)))).isTrue();
    }

    @Test
    @DisplayName("los pasos de aviso se guardan y se leen como cualquier otro")
    void pasosDeAvisoIdaYVuelta() {
        assertThat(EjecucionDocumento.conocido("AVISO")).contains(PasoDeLiquidacion.AVISO);
        assertThat(EjecucionDocumento.conocido("AVISO_DESBLOQUEO")).contains(PasoDeLiquidacion.AVISO_DESBLOQUEO);
        assertThat(EjecucionDocumento.conocido("NO_EXISTE")).isEmpty();
        assertThat(EjecucionDocumento.conocido(null)).isEmpty();
    }

    @Test
    @DisplayName("cancelar sin penalizacion sobrevive a guardar y leer; lo guardado antes, sin el campo, se lee como con penalizacion")
    void sinPenalizacionIdaYVuelta() {
        Instant vence = INICIO.plus(Duration.ofHours(1));
        Ejecucion fallando = Ejecucion.nueva(UUID.randomUUID(), "templo-olvidado", "uid-1", HEROE, List.of(),
                Escalon.NORMAL, INICIO, Duration.ofHours(1), 99L, null);
        fallando.simulacionAplazada(vence, Duration.ofSeconds(30), "motor caido");
        fallando.cancelar(vence.plusSeconds(1));

        EjecucionDocumento guardado = EjecucionDocumento.de(fallando, 2);

        assertThat(guardado.sinPenalizacion()).isTrue();
        assertThat(guardado.aDominio().canceladaSinPenalizacion()).isTrue();

        Ejecucion conPenalizacion = Ejecucion.nueva(UUID.randomUUID(), "templo-olvidado", "uid-1", HEROE, List.of(),
                Escalon.NORMAL, INICIO, Duration.ofHours(1), 99L, null);
        conPenalizacion.cancelar(INICIO.plusSeconds(5));
        EjecucionDocumento sana = EjecucionDocumento.de(conPenalizacion, 2);
        EjecucionDocumento vieja = new EjecucionDocumento(sana.id(), sana.misionId(), sana.jugadorUid(),
                sana.heroe(), sana.estrategia(), sana.escalon(), sana.iniciadaEn(), sana.terminaEn(), sana.semilla(),
                sana.claveIdempotencia(), sana.estado(), sana.terminadaEn(), sana.resultado(), sana.recompensas(),
                sana.pasos(), sana.motivos(), sana.intentosDeLiquidacion(), sana.proximoIntento(), sana.ultimoError(),
                sana.nivelAlcanzado(), sana.experienciaAcumulada(), sana.liquidacionPendiente(), sana.version(),
                null, null);

        assertThat(vieja.aDominio().canceladaSinPenalizacion()).isFalse();
    }
}
