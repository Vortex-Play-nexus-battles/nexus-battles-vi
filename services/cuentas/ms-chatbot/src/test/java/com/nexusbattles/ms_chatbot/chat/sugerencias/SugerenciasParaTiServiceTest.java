package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.15.0 (7.4.3 «sugerencias proactivas», D-CHA-14): lo que se
// propone, a quien y por que, con lo que esa persona ya pregunto.
class SugerenciasParaTiServiceTest {

    private static final IdentidadDelChat JUGADOR =
        IdentidadDelChat.usuario(UUID.fromString("11111111-2222-4333-8444-555555555555"), "token");

    private final TemaConocimientoRepository temas = mock(TemaConocimientoRepository.class);
    private final MensajeRepository mensajes = mock(MensajeRepository.class);
    private final SugerenciasService frecuentes = mock(SugerenciasService.class);
    private final SugerenciasParaTiService servicio = new SugerenciasParaTiService(temas, mensajes, frecuentes);

    private static TemaConocimiento tema(String clave, Categoria categoria, String titulo, int prioridad) {
        return new TemaConocimiento(null, clave, categoria, TipoRespuesta.DIRECTA, titulo, clave, null, "respuesta",
            null, prioridad, true);
    }

    private static final TemaConocimiento SUBASTA =
        tema("subasta", Categoria.SUBASTA_Y_COMERCIO, "Cómo funciona la Subasta", 9);
    private static final TemaConocimiento PUJAR = tema("pujar", Categoria.SUBASTA_Y_COMERCIO, "Cómo pujar", 8);
    private static final TemaConocimiento COMISION =
        tema("comision", Categoria.SUBASTA_Y_COMERCIO, "Comisión de la subasta", 7);
    private static final TemaConocimiento CANCELAR =
        tema("cancelar", Categoria.SUBASTA_Y_COMERCIO, "Cancelar una subasta", 6);
    private static final TemaConocimiento TORNEO = tema("torneo", Categoria.MODALIDAD_JUEGO, "Cómo funciona el Torneo", 9);
    private static final TemaConocimiento MISION = tema("mision", Categoria.MODALIDAD_JUEGO, "Qué es una misión", 5);
    private static final TemaConocimiento INVENTARIO = tema("inventario", Categoria.PRODUCTO, "Mi inventario", 5);
    private static final TemaConocimiento SALUDO = tema("saludo", Categoria.FAQ_GENERAL, "Saludo", 10);

    @BeforeEach
    void preparar() {
        when(temas.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION))
            .thenReturn(List.of(COMISION, SALUDO, MISION, SUBASTA, PUJAR, TORNEO, CANCELAR, INVENTARIO));
    }

    private void leRespondieron(String... claves) {
        when(mensajes.temasQueLeRespondieron(eq(JUGADOR.claveDeConversacion()), eq(Remitente.BOT),
            eq(ResultadoDeRespuesta.TEMA), any())).thenReturn(List.of(claves));
    }

    private static List<String> claves(List<SugerenciasParaTiService.SugerenciaParaTi> sugerencias) {
        return sugerencias.stream().map(s -> s.tema().getClave()).toList();
    }

    @Test
    void porLoQuePreguntoSaleLoQueSigueDeSuCategoriaSinRepetirNiLoYaRespondido() {
        // La subasta (y otra vez la subasta) y, antes, el torneo.
        leRespondieron("subasta", "subasta", "pujar", "torneo");

        List<SugerenciasParaTiService.SugerenciaParaTi> sugerencias = servicio.para(JUGADOR, null, null);

        // Tres como mucho; dos por tema; nunca lo ya respondido (subasta, pujar, torneo).
        assertThat(claves(sugerencias)).containsExactly("comision", "cancelar", "mision");
        assertThat(sugerencias).allMatch(s -> s.motivo() == MotivoDeSugerencia.POR_LO_QUE_PREGUNTASTE);
        assertThat(sugerencias.get(0).relacionadoCon()).isEqualTo("Cómo funciona la Subasta");
        assertThat(sugerencias.get(2).relacionadoCon()).isEqualTo("Cómo funciona el Torneo");
        verify(mensajes).temasQueLeRespondieron(JUGADOR.claveDeConversacion(), Remitente.BOT,
            ResultadoDeRespuesta.TEMA, PageRequest.of(0, SugerenciasParaTiService.RESPUESTAS_QUE_SE_MIRAN));
        verifyNoInteractions(frecuentes);
    }

    @Test
    void siFaltanSalenLasDeDondeEstaYUnTemaRetiradoNoCuenta() {
        leRespondieron("retirado", "mision");

        List<SugerenciasParaTiService.SugerenciaParaTi> sugerencias =
            servicio.para(JUGADOR, VistaDelChat.INVENTARIO, 3);

        assertThat(claves(sugerencias)).containsExactly("torneo", "inventario");
        assertThat(sugerencias.get(0).motivo()).isEqualTo(MotivoDeSugerencia.POR_LO_QUE_PREGUNTASTE);
        assertThat(sugerencias.get(1).motivo()).isEqualTo(MotivoDeSugerencia.POR_DONDE_ESTAS);
        assertThat(sugerencias.get(1).relacionadoCon()).isNull();
    }

    @Test
    void quienNoPreguntoNadaRecibeLasDeSuSeccionYLasMasConsultadas() {
        leRespondieron();
        when(frecuentes.sugerir(isNull(), isNull(), anyInt())).thenReturn(List.of(SUBASTA, TORNEO, MISION));

        List<SugerenciasParaTiService.SugerenciaParaTi> sugerencias = servicio.para(JUGADOR, VistaDelChat.SUBASTAS, 4);

        assertThat(claves(sugerencias)).containsExactly("subasta", "pujar", "comision", "cancelar");
        assertThat(sugerencias).allMatch(s -> s.motivo() == MotivoDeSugerencia.POR_DONDE_ESTAS);

        List<SugerenciasParaTiService.SugerenciaParaTi> enInicio = servicio.para(JUGADOR, VistaDelChat.INICIO, 2);
        assertThat(claves(enInicio)).containsExactly("subasta", "torneo");
        assertThat(enInicio).allMatch(s -> s.motivo() == MotivoDeSugerencia.PARA_EMPEZAR);
    }

    @Test
    void sinIdentidadNoSeMiraNingunaConversacionYDeLasFrecuentesSoloLasQueSiguenYNoSonDeCortesia() {
        TemaConocimiento retirado = tema("retirado", Categoria.FAQ_GENERAL, "Un tema retirado", 9);
        when(frecuentes.sugerir(isNull(), isNull(), anyInt())).thenReturn(List.of(retirado, TORNEO, SALUDO));

        List<SugerenciasParaTiService.SugerenciaParaTi> sugerencias = servicio.para(null, null, 9);

        assertThat(claves(sugerencias)).containsExactly("torneo");
        verify(mensajes, never()).temasQueLeRespondieron(any(), any(), any(), any());
    }

    @Test
    void ningunTemaDeCortesiaNiMasDelLimite() {
        leRespondieron("saludo");

        assertThat(claves(servicio.para(JUGADOR, VistaDelChat.SUBASTAS, 1))).containsExactly("subasta");
        assertThat(claves(servicio.para(JUGADOR, null, 0))).hasSize(0);
    }
}
