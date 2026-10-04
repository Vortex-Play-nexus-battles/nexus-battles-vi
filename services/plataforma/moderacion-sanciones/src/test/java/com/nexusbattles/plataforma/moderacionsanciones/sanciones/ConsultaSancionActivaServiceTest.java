package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La consulta que hacen chat, salas, comentarios, torneos y subastas: traduce
 * la sancion que restringe hoy (si hay) a la forma del contrato 1.4.0, con
 * {@code tipo} y {@code sancionId}, y solo a quien puede verla.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultaSancionActivaService · 1.4.0")
class ConsultaSancionActivaServiceTest {

    @Mock SancionesService sanciones;

    private static final UUID JUGADOR = UUID.randomUUID();

    @Test
    @DisplayName("sin sancion que restrinja responde sancionActiva=false sin motivo, tipo ni sancion")
    void sinSancion() {
        when(sanciones.activaDe(JUGADOR)).thenReturn(Optional.empty());

        var resultado = new ConsultaSancionActivaService(sanciones).consultar(JUGADOR);

        assertThat(resultado.sancionActiva()).isFalse();
        assertThat(resultado.motivo()).isNull();
        assertThat(resultado.tipo()).isNull();
        assertThat(resultado.sancionId()).isNull();
    }

    @Test
    @DisplayName("con suspension vigente responde el motivo, la fecha fin, el tipo y el id de la sancion")
    void conSuspension() {
        OffsetDateTime fin = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        Sancion suspension = new Sancion(UUID.randomUUID(), JUGADOR, Sancion.Tipo.SUSPENSION, "Reincidencia", null,
                null, UUID.randomUUID(), "MODERADOR", OffsetDateTime.now(ZoneOffset.UTC), fin);
        when(sanciones.activaDe(JUGADOR)).thenReturn(Optional.of(suspension));

        var resultado = new ConsultaSancionActivaService(sanciones).consultar(JUGADOR);

        assertThat(resultado.sancionActiva()).isTrue();
        assertThat(resultado.motivo()).isEqualTo("Reincidencia");
        assertThat(resultado.vigenteHasta()).isEqualTo(fin);
        assertThat(resultado.tipo()).isEqualTo("SUSPENSION");
        assertThat(resultado.sancionId()).isEqualTo(suspension.id());
    }

    @Test
    @DisplayName("un jugador consulta la suya; la de otro es PERMISO_INSUFICIENTE sin leer nada")
    void jugador() {
        when(sanciones.activaDe(JUGADOR)).thenReturn(Optional.empty());
        ConsultaSancionActivaService servicio = new ConsultaSancionActivaService(sanciones);

        assertThat(servicio.consultar(new ConsultaSancionActivaService.Consultante(JUGADOR, false, false), JUGADOR)
                .sancionActiva()).isFalse();
        assertThatThrownBy(() -> servicio.consultar(
                new ConsultaSancionActivaService.Consultante(UUID.randomUUID(), false, false), JUGADOR))
                .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
        assertThatThrownBy(() -> servicio.consultar(
                new ConsultaSancionActivaService.Consultante(null, false, false), JUGADOR))
                .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
    }

    @Test
    @DisplayName("un servicio o quien modera consulta la de cualquiera")
    void servicioYModeracion() {
        when(sanciones.activaDe(JUGADOR)).thenReturn(Optional.empty());
        ConsultaSancionActivaService servicio = new ConsultaSancionActivaService(sanciones);

        assertThat(servicio.consultar(new ConsultaSancionActivaService.Consultante(null, true, false), JUGADOR))
                .isNotNull();
        assertThat(servicio.consultar(new ConsultaSancionActivaService.Consultante(UUID.randomUUID(), false, true),
                JUGADOR)).isNotNull();
    }

    @Test
    @DisplayName("las formas anteriores del resultado siguen construyendose (compatibilidad de las pruebas)")
    void formasAnteriores() {
        assertThat(new ConsultaSancionActivaService.ResultadoSancion(true, "m", null).tipo()).isNull();
        assertThat(new ConsultaSancionActivaService.ResultadoSancion(true, "m", null, "BANEO").sancionId()).isNull();
        verifyNoInteractions(sanciones);
    }
}
