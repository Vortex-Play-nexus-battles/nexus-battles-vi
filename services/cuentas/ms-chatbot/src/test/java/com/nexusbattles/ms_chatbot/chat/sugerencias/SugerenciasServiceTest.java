package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.analitica.ConteoDeTema;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.3.3: preguntas rapidas, temas frecuentes y autocompletado.
@ExtendWith(MockitoExtension.class)
class SugerenciasServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-28T18:00:00Z");

    @Mock
    private TemaConocimientoRepository temas;
    @Mock
    private MensajeRepository mensajes;

    private SugerenciasService servicio;

    private static TemaConocimiento tema(String clave, Categoria categoria, String titulo, String variantes,
                                         int prioridad) {
        return new TemaConocimiento(null, clave, categoria, TipoRespuesta.DIRECTA, titulo, variantes,
            "auction, bid", "respuesta", "answer", prioridad, true);
    }

    private final TemaConocimiento subasta = tema("k-subasta", Categoria.SUBASTA_Y_COMERCIO,
        "Cómo publicar un producto en subasta", "subasta, vender un producto", 0);
    private final TemaConocimiento torneo = tema("k-torneo", Categoria.MODALIDAD_JUEGO,
        "Cómo funciona el Torneo", "torneo, inscribirse a un torneo", 0);
    private final TemaConocimiento contrasena = tema("k-clave", Categoria.CUENTA_Y_REGISTRO,
        "Recuperar mi contraseña", "olvide mi contraseña, no recuerdo mi clave", 5);
    private final TemaConocimiento saludo = tema("k-hola", Categoria.FAQ_GENERAL, "Saludo", "hola, buenas", 9);

    @BeforeEach
    void configurar() {
        servicio = new SugerenciasService(temas, mensajes, Clock.fixed(AHORA, ZoneOffset.UTC));
        lenient().when(temas.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION))
            .thenReturn(List.of(subasta, torneo, contrasena, saludo));
    }

    @Test
    void sinTextoOrdenaPorConsultasDeLosUltimos30DiasYLuegoPorPrioridad() {
        when(mensajes.contarRespuestasPorTemaEntre(eq(Remitente.BOT), eq(AHORA.minus(SugerenciasService.VENTANA_DE_FRECUENCIA)),
            eq(AHORA), any())).thenReturn(List.of(new ConteoDeTema("k-torneo", 7L), new ConteoDeTema("k-hola", 50L)));

        List<TemaConocimiento> sugeridos = servicio.sugerir(null, null, null);

        // El saludo es el mas consultado, pero es de cortesia: no se sugiere.
        assertThat(sugeridos).containsExactly(torneo, contrasena, subasta);
    }

    @Test
    void conCategoriaSoloLosDeEsaCategoria() {
        when(mensajes.contarRespuestasPorTemaEntre(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(servicio.sugerir("", Categoria.CUENTA_Y_REGISTRO, 6)).containsExactly(contrasena);
    }

    @Test
    void elLimiteSeRespetaYSeAcota() {
        when(mensajes.contarRespuestasPorTemaEntre(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(servicio.sugerir(null, null, 1)).hasSize(1);
        assertThat(servicio.sugerir(null, null, 0)).hasSize(1);
        assertThat(servicio.sugerir(null, null, 99)).hasSize(3);
    }

    @Test
    void autocompletaPrimeroElTituloQueEmpiezaLuegoElQueContieneLuegoLasVariantes() {
        TemaConocimiento comoMeRegistro = tema("k-registro", Categoria.CUENTA_Y_REGISTRO,
            "Crear una cuenta", "como me registro", 0);
        when(temas.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION))
            .thenReturn(List.of(subasta, torneo, comoMeRegistro, contrasena));

        List<TemaConocimiento> sugeridos = servicio.sugerir("  CÓMO  ", null, 10);

        // A igual relevancia y prioridad, por titulo: «Cómo f…» antes que «Cómo p…».
        assertThat(sugeridos).containsExactly(torneo, subasta, comoMeRegistro);
        verify(mensajes, never()).contarRespuestasPorTemaEntre(any(), any(), any(), any());
    }

    @Test
    void autocompletaSinTildesPorLoQueContieneElTitulo() {
        assertThat(servicio.sugerir("contrasena", null, 6)).containsExactly(contrasena);
        assertThat(servicio.sugerir("clave", null, 6)).containsExactly(contrasena);
    }

    @Test
    void tambienBuscaEnLasVariantesEnIngles() {
        assertThat(servicio.sugerir("bid", null, 2)).hasSize(2);
    }

    @Test
    void unaSolaLetraEquivaleANoEscribirNada() {
        when(mensajes.contarRespuestasPorTemaEntre(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(servicio.sugerir("c", null, 6)).containsExactly(contrasena, torneo, subasta);
    }

    @Test
    void sinCoincidenciasDevuelveLaListaVacia() {
        assertThat(servicio.sugerir("xyzw", null, 6)).isEmpty();
    }

    @Test
    void relevanciaDeUnTema() {
        assertThat(SugerenciasService.relevancia(torneo, "como")).isEqualTo(3);
        assertThat(SugerenciasService.relevancia(torneo, "torneo")).isEqualTo(2);
        assertThat(SugerenciasService.relevancia(torneo, "inscribirse")).isEqualTo(1);
        assertThat(SugerenciasService.relevancia(torneo, "subasta")).isZero();
    }

    @Test
    void laRespuestaUsaElTituloComoPregunta() {
        SugerenciaResponse respuesta = SugerenciaResponse.desde(torneo);

        assertThat(respuesta.pregunta()).isEqualTo("Cómo funciona el Torneo");
        assertThat(respuesta.clave()).isEqualTo("k-torneo");
        assertThat(respuesta.categoria()).isEqualTo(Categoria.MODALIDAD_JUEGO);
    }
}
