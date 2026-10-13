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
                guardado.nivelAlcanzado(), guardado.experienciaAcumulada(), true, guardado.version());

        Ejecucion leida = conOtraVersion.aDominio();

        assertThat(leida.pasos()).containsExactly(Map.entry(PasoDeLiquidacion.LIBERACION, EstadoDePaso.PENDIENTE));
        assertThat(leida.motivos()).isEmpty();
        assertThat(leida.pasosPendientes()).containsExactly(PasoDeLiquidacion.LIBERACION);
    }

    @Test
    @DisplayName("los pasos de aviso se guardan y se leen como cualquier otro")
    void pasosDeAvisoIdaYVuelta() {
        assertThat(EjecucionDocumento.conocido("AVISO")).contains(PasoDeLiquidacion.AVISO);
        assertThat(EjecucionDocumento.conocido("AVISO_DESBLOQUEO")).contains(PasoDeLiquidacion.AVISO_DESBLOQUEO);
        assertThat(EjecucionDocumento.conocido("NO_EXISTE")).isEmpty();
        assertThat(EjecucionDocumento.conocido(null)).isEmpty();
    }
}
