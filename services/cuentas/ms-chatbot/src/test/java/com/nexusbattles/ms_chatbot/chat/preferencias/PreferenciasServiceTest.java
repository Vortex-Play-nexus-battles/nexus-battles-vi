package com.nexusbattles.ms_chatbot.chat.preferencias;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.3.6 (7.4.5): preferencias de respuesta por conversacion.
@ExtendWith(MockitoExtension.class)
class PreferenciasServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-28T12:00:00Z");
    private static final IdentidadDelChat JUGADOR =
        IdentidadDelChat.usuario(UUID.fromString("11111111-2222-4333-8444-555555555555"), "token");

    @Mock
    private PreferenciasDelChatRepository repositorio;

    private PreferenciasService servicio;

    @BeforeEach
    void preparar() {
        servicio = new PreferenciasService(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void de_sinPreferenciasGuardadas_devuelveLasDePorDefecto() {
        when(repositorio.findById(JUGADOR.claveDeConversacion())).thenReturn(Optional.empty());

        assertThat(servicio.de(JUGADOR)).isEqualTo(PreferenciasDeRespuesta.POR_DEFECTO);
        assertThat(PreferenciasDeRespuesta.POR_DEFECTO.sonPorDefecto()).isTrue();
    }

    @Test
    void de_conPreferenciasGuardadas_lasDevuelve() {
        PreferenciasDeRespuesta guardadas = new PreferenciasDeRespuesta(IdiomaPreferido.EN, NivelDeDetalle.BREVE);
        when(repositorio.findById(JUGADOR.claveDeConversacion()))
            .thenReturn(Optional.of(new PreferenciasDelChat(JUGADOR.claveDeConversacion(), guardadas, AHORA)));

        assertThat(servicio.de(JUGADOR)).isEqualTo(guardadas);
        assertThat(guardadas.sonPorDefecto()).isFalse();
    }

    @Test
    void guardar_laPrimeraVez_creaLaFilaConLaClaveDeLaConversacion() {
        when(repositorio.findById(JUGADOR.claveDeConversacion())).thenReturn(Optional.empty());
        when(repositorio.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        PreferenciasDeRespuesta nuevas = new PreferenciasDeRespuesta(IdiomaPreferido.EN, NivelDeDetalle.BREVE);

        assertThat(servicio.guardar(JUGADOR, nuevas)).isEqualTo(nuevas);

        ArgumentCaptor<PreferenciasDelChat> fila = ArgumentCaptor.forClass(PreferenciasDelChat.class);
        verify(repositorio).save(fila.capture());
        assertThat(fila.getValue().getClave()).isEqualTo(JUGADOR.claveDeConversacion());
        assertThat(fila.getValue().getIdioma()).isEqualTo(IdiomaPreferido.EN);
        assertThat(fila.getValue().getNivelDetalle()).isEqualTo(NivelDeDetalle.BREVE);
        assertThat(fila.getValue().getActualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    void guardar_siYaExistian_lasCambia() {
        PreferenciasDelChat existente = new PreferenciasDelChat(JUGADOR.claveDeConversacion(),
            PreferenciasDeRespuesta.POR_DEFECTO, Instant.parse("2026-01-01T00:00:00Z"));
        when(repositorio.findById(JUGADOR.claveDeConversacion())).thenReturn(Optional.of(existente));
        when(repositorio.save(existente)).thenReturn(existente);
        PreferenciasDeRespuesta nuevas = new PreferenciasDeRespuesta(IdiomaPreferido.ES, NivelDeDetalle.DETALLADO);

        servicio.guardar(JUGADOR, nuevas);

        assertThat(existente.comoPreferencias()).isEqualTo(nuevas);
        assertThat(existente.getActualizadoEn()).isEqualTo(AHORA);
    }
}
