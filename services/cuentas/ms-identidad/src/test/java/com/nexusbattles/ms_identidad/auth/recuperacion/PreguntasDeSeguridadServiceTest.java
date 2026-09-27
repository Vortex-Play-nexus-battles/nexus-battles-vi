package com.nexusbattles.ms_identidad.auth.recuperacion;

import com.nexusbattles.ms_identidad.auth.codigos.PreguntasConfiguradas;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.recuperacion.ConfigurarPreguntasRequest.NuevaPregunta;
import com.nexusbattles.ms_identidad.auth.recuperacion.RecuperacionRechazadaException.Motivo;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Preguntas de seguridad de la propia cuenta (B1, 7.1.1)")
class PreguntasDeSeguridadServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final LocalDateTime HOY = LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final UUID UID = UUID.fromString("9c9c9c9c-1111-4222-8333-444444444444");

    private final PasswordEncoder cifrador = new BCryptPasswordEncoder(4);
    private PreguntaSeguridadRepository preguntas;
    private UsuarioRepository usuarios;
    private IntentosFallidosService intentos;
    private ApplicationEventPublisher eventos;
    private PreguntasDeSeguridadService servicio;
    private Usuario cuenta;
    private final List<PreguntaSeguridad> guardadas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        preguntas = mock(PreguntaSeguridadRepository.class);
        usuarios = mock(UsuarioRepository.class);
        intentos = mock(IntentosFallidosService.class);
        eventos = mock(ApplicationEventPublisher.class);
        servicio = new PreguntasDeSeguridadService(preguntas, usuarios, intentos, eventos, 2, 3, cifrador,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        cuenta = new Usuario();
        cuenta.setId(5L);
        cuenta.setPublicId(UID);
        cuenta.setPassword(cifrador.encode("Actual.Clave-1"));
        when(usuarios.findById(5L)).thenReturn(Optional.of(cuenta));
        when(preguntas.save(any(PreguntaSeguridad.class))).thenAnswer(invocacion -> {
            PreguntaSeguridad pregunta = invocacion.getArgument(0);
            guardadas.add(pregunta);
            return pregunta;
        });
    }

    private static ConfigurarPreguntasRequest peticion(String clave, NuevaPregunta... nuevas) {
        return new ConfigurarPreguntasRequest(clave, List.of(nuevas));
    }

    @Test
    @DisplayName("reemplaza todas: borra, guarda en orden con la respuesta normalizada y resumida, y lo audita")
    void configura() {
        PreguntasDeRecuperacion respuesta = servicio.configurar(5L, peticion("Actual.Clave-1",
                new NuevaPregunta("  ¿Ciudad donde naciste?  ", "  Bogotá "),
                new NuevaPregunta("¿Nombre de tu primera mascota?", "Firulais")), "10.0.0.8");

        verify(usuarios).bloquear(5L);
        verify(preguntas).borrarDe(5L);
        assertThat(guardadas).extracting(PreguntaSeguridad::getOrden).containsExactly(1, 2);
        assertThat(guardadas.get(0).getTexto()).isEqualTo("¿Ciudad donde naciste?");
        assertThat(guardadas.get(0).getRespuestaHash()).doesNotContain("Bogot");
        assertThat(cifrador.matches(NormalizadorDeRespuestas.paraResumir("BOGOTA"), guardadas.get(0).getRespuestaHash()))
                .as("se compara normalizada: sin tildes ni mayusculas").isTrue();
        assertThat(guardadas).allSatisfy(p -> {
            assertThat(p.getUsuarioId()).isEqualTo(5L);
            assertThat(p.getCreadoEn()).isEqualTo(HOY);
            assertThat(p.getId()).isNotNull();
        });
        assertThat(respuesta.configuradas()).isTrue();
        assertThat(respuesta.preguntas()).extracting(PreguntasDeRecuperacion.Pregunta::texto)
                .containsExactly("¿Ciudad donde naciste?", "¿Nombre de tu primera mascota?");
        verify(eventos).publishEvent(new PreguntasConfiguradas(UID.toString(), 2, "10.0.0.8"));
    }

    @Test
    @DisplayName("contrasena actual mal: 422 y cuenta como intento fallido; cuenta bloqueada: 423 sin comparar")
    void contrasenaActual() {
        assertThatThrownBy(() -> servicio.configurar(5L, peticion("otra",
                new NuevaPregunta("¿Pregunta uno?", "uno"), new NuevaPregunta("¿Pregunta dos?", "dos")), null))
                .isInstanceOf(RecuperacionRechazadaException.class)
                .extracting(e -> ((RecuperacionRechazadaException) e).getMotivo()).isEqualTo(Motivo.ACTUAL_INCORRECTA);
        verify(intentos).registrarIntentoFallido(5L);

        cuenta.setBloqueadoHasta(HOY.plusMinutes(5));
        assertThatThrownBy(() -> servicio.configurar(5L, peticion("Actual.Clave-1",
                new NuevaPregunta("¿Pregunta uno?", "uno"), new NuevaPregunta("¿Pregunta dos?", "dos")), null))
                .extracting(e -> ((RecuperacionRechazadaException) e).getMotivo()).isEqualTo(Motivo.CUENTA_BLOQUEADA);
        verify(preguntas, never()).borrarDe(anyLong());
    }

    @Test
    @DisplayName("422 preguntas-invalidas: cantidad, largos, respuesta vacia o preguntas repetidas")
    void invalidas() {
        NuevaPregunta buena = new NuevaPregunta("¿Pregunta uno?", "uno");
        List<ConfigurarPreguntasRequest> malas = List.of(
                peticion("Actual.Clave-1", buena),
                peticion("Actual.Clave-1", buena, new NuevaPregunta("¿Dos?", "dos"), new NuevaPregunta("¿Tres!?", "tres"),
                        new NuevaPregunta("¿Cuatro?", "cuatro")),
                peticion("Actual.Clave-1", buena, new NuevaPregunta("¿?", "dos")),
                peticion("Actual.Clave-1", buena, new NuevaPregunta("x".repeat(201), "dos")),
                peticion("Actual.Clave-1", buena, new NuevaPregunta("¿Pregunta dos?", "   a   ")),
                peticion("Actual.Clave-1", buena, new NuevaPregunta("¿Pregunta dos?", "y".repeat(101))),
                peticion("Actual.Clave-1", buena, new NuevaPregunta("¿Pregunta dos?", null)),
                peticion("Actual.Clave-1", buena, new NuevaPregunta("  ¿PREGUNTA   úno?", "otra")),
                new ConfigurarPreguntasRequest("Actual.Clave-1", null));
        for (ConfigurarPreguntasRequest mala : malas) {
            assertThatThrownBy(() -> servicio.configurar(5L, mala, null))
                    .as(mala.toString())
                    .isInstanceOf(RecuperacionRechazadaException.class)
                    .extracting(e -> ((RecuperacionRechazadaException) e).getMotivo())
                    .isEqualTo(Motivo.PREGUNTAS_INVALIDAS);
        }
        verify(preguntas, never()).borrarDe(anyLong());
    }

    @Test
    @DisplayName("respuestas: todas bien -> si; una mal, una sin contestar o sin preguntas -> no")
    void respuestas() {
        PreguntaSeguridad ciudad = new PreguntaSeguridad(5L, "¿Ciudad?",
                cifrador.encode(NormalizadorDeRespuestas.paraResumir("Bogotá")), 1, HOY);
        PreguntaSeguridad mascota = new PreguntaSeguridad(5L, "¿Mascota?",
                cifrador.encode(NormalizadorDeRespuestas.paraResumir("Firulais")), 2, HOY);
        when(preguntas.findByUsuarioIdOrderByOrdenAsc(5L)).thenReturn(List.of(ciudad, mascota));

        assertThat(servicio.respuestasCorrectas(5L, List.of(
                new RespuestaDeSeguridad(ciudad.getId(), " bogota "),
                new RespuestaDeSeguridad(mascota.getId(), "FIRULAIS")))).isTrue();
        assertThat(servicio.respuestasCorrectas(5L, List.of(
                new RespuestaDeSeguridad(ciudad.getId(), "medellin"),
                new RespuestaDeSeguridad(mascota.getId(), "firulais")))).isFalse();
        assertThat(servicio.respuestasCorrectas(5L, List.of(
                new RespuestaDeSeguridad(ciudad.getId(), "bogota")))).as("falta una").isFalse();
        assertThat(servicio.respuestasCorrectas(5L, null)).isFalse();
        assertThat(servicio.respuestasCorrectas(5L, List.of(
                new RespuestaDeSeguridad(ciudad.getId(), "b".repeat(101)),
                new RespuestaDeSeguridad(mascota.getId(), "firulais")))).isFalse();

        when(preguntas.findByUsuarioIdOrderByOrdenAsc(6L)).thenReturn(List.of());
        assertThat(servicio.respuestasCorrectas(6L, List.of())).isFalse();
    }

    @Test
    @DisplayName("consulta: preguntas sin respuestas; y si tiene alguna configurada")
    void consulta() {
        PreguntaSeguridad ciudad = new PreguntaSeguridad(5L, "¿Ciudad?", "hash", 1, HOY);
        when(preguntas.findByUsuarioIdOrderByOrdenAsc(5L)).thenReturn(List.of(ciudad));
        when(preguntas.countByUsuarioId(5L)).thenReturn(1L);

        PreguntasDeRecuperacion vistas = servicio.deLaCuenta(5L);
        assertThat(vistas.configuradas()).isTrue();
        assertThat(vistas.preguntas()).containsExactly(new PreguntasDeRecuperacion.Pregunta(ciudad.getId(), "¿Ciudad?"));
        assertThat(servicio.tieneConfiguradas(5L)).isTrue();
        assertThat(servicio.tieneConfiguradas(6L)).isFalse();
    }

    @Test
    @DisplayName("una configuracion incoherente de minimas y maximas detiene el arranque")
    void configuracionIncoherente() {
        assertThatThrownBy(() -> new PreguntasDeSeguridadService(preguntas, usuarios, intentos, eventos, 3, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreguntasDeSeguridadService(preguntas, usuarios, intentos, eventos, 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
        PreguntasDeSeguridadService exacta = new PreguntasDeSeguridadService(preguntas, usuarios, intentos, eventos,
                2, 2, cifrador, Clock.fixed(AHORA, ZoneOffset.UTC));
        assertThatThrownBy(() -> exacta.configurar(5L, peticion("Actual.Clave-1",
                new NuevaPregunta("¿Pregunta uno?", "uno")), null))
                .hasMessage("Configura exactamente 2 preguntas.");
    }

    @Test
    @DisplayName("la cuenta sin uid se audita por su clave interna; y los toString no llevan secretos")
    void sinUidYSinSecretos() {
        cuenta.setPublicId(null);
        servicio.configurar(5L, peticion("Actual.Clave-1",
                new NuevaPregunta("¿Pregunta uno?", "respuesta-secreta"), new NuevaPregunta("¿Pregunta dos?", "dos")),
                null);
        ArgumentCaptor<PreguntasConfiguradas> evento = ArgumentCaptor.forClass(PreguntasConfiguradas.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().afectado()).isEqualTo("usuario-5");

        ConfigurarPreguntasRequest datos = peticion("Actual.Clave-1", new NuevaPregunta("¿P?", "respuesta-secreta"));
        assertThat(datos.toString()).doesNotContain("Actual.Clave-1", "respuesta-secreta");
        assertThat(datos.preguntas().get(0).toString()).doesNotContain("respuesta-secreta");
        assertThat(new RespuestaDeSeguridad(UUID.randomUUID(), "respuesta-secreta").toString())
                .doesNotContain("respuesta-secreta");

        when(usuarios.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.configurar(99L, datos, null)).isInstanceOf(IllegalStateException.class);
    }
}
