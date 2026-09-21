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
import static org.mockito.Mockito.when;

/**
 * La consulta que hacen chat, comentarios y subastas: traduce la sancion que
 * restringe hoy (si hay) a la forma del contrato 1.1.0, con {@code tipo}.
 */
@ExtendWith(MockitoExtension.class)
class ConsultaSancionActivaServiceTest {

    @Mock SancionesService sanciones;

    @Test
    @DisplayName("sin sancion que restrinja responde sancionActiva=false sin motivo ni tipo")
    void sinSancion() {
        UUID usuario = UUID.randomUUID();
        when(sanciones.activaDe(usuario)).thenReturn(Optional.empty());

        var resultado = new ConsultaSancionActivaService(sanciones).consultar(usuario);

        assertThat(resultado.sancionActiva()).isFalse();
        assertThat(resultado.motivo()).isNull();
        assertThat(resultado.tipo()).isNull();
    }

    @Test
    @DisplayName("con suspension vigente responde el motivo, la fecha fin y el tipo")
    void conSuspension() {
        UUID usuario = UUID.randomUUID();
        OffsetDateTime fin = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        when(sanciones.activaDe(usuario)).thenReturn(Optional.of(new Sancion(UUID.randomUUID(), usuario,
                Sancion.Tipo.SUSPENSION, "Reincidencia", null, null, UUID.randomUUID(), "MODERADOR",
                OffsetDateTime.now(ZoneOffset.UTC), fin)));

        var resultado = new ConsultaSancionActivaService(sanciones).consultar(usuario);

        assertThat(resultado.sancionActiva()).isTrue();
        assertThat(resultado.motivo()).isEqualTo("Reincidencia");
        assertThat(resultado.vigenteHasta()).isEqualTo(fin);
        assertThat(resultado.tipo()).isEqualTo("SUSPENSION");
    }
}
