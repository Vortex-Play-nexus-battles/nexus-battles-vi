package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.6.0: las transiciones de version se hacen en fila. Contra
// PostgreSQL de verdad lo prueba TransicionesDeVersionIT.
class BloqueoDeVersionesTest {

    private final VersionBaseConocimientoRepository versiones = mock(VersionBaseConocimientoRepository.class);
    private final BloqueoDeVersiones bloqueo = new BloqueoDeVersiones(versiones);

    @Test
    void devuelveLaVersionBloqueada() {
        VersionBaseConocimiento produccion = VersionBaseConocimiento.nuevaCandidata(1, null);
        when(versiones.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));

        assertThat(bloqueo.bloquear(EstadoVersion.PRODUCCION)).containsSame(produccion);
        verify(versiones, never()).existsByEstado(any());
    }

    @Test
    void vacioSiNoHayNingunaEnEseEstado() {
        when(versiones.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());
        when(versiones.existsByEstado(EstadoVersion.BORRADOR)).thenReturn(false);

        assertThat(bloqueo.bloquear(EstadoVersion.BORRADOR)).isEmpty();
    }

    // La fila cambio de estado mientras se esperaba (PostgreSQL ya no la
    // devuelve) y ahora hay otra en ese estado: la operacion venia de un
    // estado que ya no existe.
    @Test
    void siCambioMientrasEsperabaYHayOtra_esUnCambioConcurrente() {
        when(versiones.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.empty());
        when(versiones.existsByEstado(EstadoVersion.PRODUCCION)).thenReturn(true);

        assertThatThrownBy(() -> bloqueo.bloquear(EstadoVersion.PRODUCCION))
            .isInstanceOf(CambioConcurrenteException.class)
            .hasMessageContaining("Otro administrador cambio la base de conocimiento al mismo tiempo");
    }
}
