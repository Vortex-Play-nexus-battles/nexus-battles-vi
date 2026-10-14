package com.nexusbattles.ms_chatbot.chat.preferencias;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.15.0 (7.4.5 «adaptar el nivel de detalle segun la
// experiencia», D-CHA-13): el nivel automatico, segun las preguntas suyas que
// el asistente ya resolvio.
class ExperienciaDelChatTest {

    private static final IdentidadDelChat JUGADOR =
        IdentidadDelChat.usuario(UUID.fromString("11111111-2222-4333-8444-555555555555"), "token");

    private final MensajeRepository mensajes = mock(MensajeRepository.class);
    private final ExperienciaDelChat experiencia = new ExperienciaDelChat(mensajes);

    private void conResueltas(long cuantas) {
        when(mensajes.countByConversacionIdentificadorSesionAndRemitenteAndResultadoIn(
            eq(JUGADOR.claveDeConversacion()), eq(Remitente.BOT), any())).thenReturn(cuantas);
    }

    @Test
    void quienEmpiezaRecibeElDetalleQuienSigueLaCompletaYQuienConoceLaBreve() {
        assertThat(ExperienciaDelChat.nivelPara(0)).isEqualTo(NivelDeDetalle.DETALLADO);
        assertThat(ExperienciaDelChat.nivelPara(4)).isEqualTo(NivelDeDetalle.DETALLADO);
        assertThat(ExperienciaDelChat.nivelPara(5)).isEqualTo(NivelDeDetalle.NORMAL);
        assertThat(ExperienciaDelChat.nivelPara(29)).isEqualTo(NivelDeDetalle.NORMAL);
        assertThat(ExperienciaDelChat.nivelPara(30)).isEqualTo(NivelDeDetalle.BREVE);
        assertThat(ExperienciaDelChat.nivelPara(500)).isEqualTo(NivelDeDetalle.BREVE);
    }

    @Test
    void elNivelAutomaticoSeTraduceConLoQueYaResolvioEnSuConversacion() {
        conResueltas(12);

        assertThat(experiencia.efectivas(JUGADOR, PreferenciasDeRespuesta.SIN_ELEGIR))
            .isEqualTo(new PreferenciasDeRespuesta(IdiomaPreferido.AUTOMATICO, NivelDeDetalle.NORMAL));
        assertThat(experiencia.resueltas(JUGADOR)).isEqualTo(12);
    }

    @Test
    void cuentanSoloLasQueResuelvenSinPersonaYSoloSuConversacion() {
        conResueltas(3);
        experiencia.resueltas(JUGADOR);

        verify(mensajes).countByConversacionIdentificadorSesionAndRemitenteAndResultadoIn(
            JUGADOR.claveDeConversacion(), Remitente.BOT, ResultadoDeRespuesta.queResuelven());
        assertThat(ResultadoDeRespuesta.queResuelven()).containsExactlyInAnyOrder(ResultadoDeRespuesta.TEMA,
            ResultadoDeRespuesta.CONSULTA_EN_VIVO, ResultadoDeRespuesta.NAVEGACION);
    }

    @Test
    void unNivelElegidoManda() {
        PreferenciasDeRespuesta elegidas = new PreferenciasDeRespuesta(IdiomaPreferido.EN, NivelDeDetalle.BREVE);

        assertThat(experiencia.efectivas(JUGADOR, elegidas)).isSameAs(elegidas);
        verifyNoInteractions(mensajes);
    }

    @Test
    void sinIdentidadCuentaComoQuienEmpieza() {
        assertThat(experiencia.efectivas(null, PreferenciasDeRespuesta.SIN_ELEGIR).nivelDetalle())
            .isEqualTo(NivelDeDetalle.DETALLADO);
        verifyNoInteractions(mensajes);
    }

    @Test
    void conNivelCambiaSoloElNivel() {
        assertThat(PreferenciasDeRespuesta.SIN_ELEGIR.conNivel(NivelDeDetalle.BREVE))
            .isEqualTo(new PreferenciasDeRespuesta(IdiomaPreferido.AUTOMATICO, NivelDeDetalle.BREVE));
    }
}
