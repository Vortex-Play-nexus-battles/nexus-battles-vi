package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 7.4.9 (ms-chatbot.yaml 1.9.0): los temas de produccion se leen una vez por
// version, no en cada mensaje.
class TemasEnProduccionTest {

    private static final UUID V1 = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID V2 = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private final VersionBaseConocimientoRepository versiones = mock(VersionBaseConocimientoRepository.class);
    private final TemaConocimientoRepository temas = mock(TemaConocimientoRepository.class);
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private TemasEnProduccion fuente;

    private static TemaConocimiento tema(String clave) {
        return new TemaConocimiento(null, clave, Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA, clave, clave, null,
            "Respuesta de " + clave, null, 0, true);
    }

    @BeforeEach
    void configurar() {
        fuente = new TemasEnProduccion(versiones, temas, metricas);
        when(temas.findByVersionIdAndActivoTrue(V1)).thenReturn(List.of(tema("a"), tema("b")));
        when(temas.findByVersionIdAndActivoTrue(V2)).thenReturn(List.of(tema("c")));
    }

    @Test
    void mientrasLaVersionNoCambiaLosTemasSeLeenUnaSolaVez() {
        when(versiones.buscarIdDeLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(V1));

        BaseEnProduccion primera = fuente.enProduccion();
        BaseEnProduccion segunda = fuente.enProduccion();
        fuente.enProduccion();

        assertThat(primera.versionId()).isEqualTo(V1);
        assertThat(primera.temas()).extracting(TemaConocimiento::getClave).containsExactly("a", "b");
        assertThat(segunda).isSameAs(primera);
        verify(temas, times(1)).findByVersionIdAndActivoTrue(V1);
        verify(versiones, times(3)).buscarIdDeLaDeEstado(EstadoVersion.PRODUCCION);
        assertThat(metricas.counter("chatbot.cache.temas.cargas").count()).isEqualTo(1.0);
        assertThat(metricas.get("chatbot.cache.temas.tamano").gauge().value()).isEqualTo(2.0);
    }

    // Un despliegue o una reversion (en esta instancia o en otra) cambia el id
    // de la version en produccion: el siguiente mensaje lee la nueva.
    @Test
    void cuandoCambiaLaVersionEnProduccionSeLeeLaNueva() {
        when(versiones.buscarIdDeLaDeEstado(EstadoVersion.PRODUCCION))
            .thenReturn(Optional.of(V1), Optional.of(V2), Optional.of(V2));

        fuente.enProduccion();
        BaseEnProduccion nueva = fuente.enProduccion();
        fuente.enProduccion();

        assertThat(nueva.versionId()).isEqualTo(V2);
        assertThat(nueva.temas()).extracting(TemaConocimiento::getClave).containsExactly("c");
        verify(temas, times(1)).findByVersionIdAndActivoTrue(V2);
        assertThat(metricas.get("chatbot.cache.temas.tamano").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void sinVersionEnProduccionLaBaseEstaVaciaYNoSeLeenTemas() {
        when(versiones.buscarIdDeLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.empty());

        BaseEnProduccion base = fuente.enProduccion();

        assertThat(base.versionId()).isNull();
        assertThat(base.temas()).isEmpty();
        verify(temas, never()).findByVersionIdAndActivoTrue(any());
        assertThat(metricas.get("chatbot.cache.temas.tamano").gauge().value()).isZero();
    }
}
