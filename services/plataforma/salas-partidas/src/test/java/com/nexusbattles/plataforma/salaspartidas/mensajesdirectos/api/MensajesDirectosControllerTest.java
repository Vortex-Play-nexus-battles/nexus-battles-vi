package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.api;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.BandejaDeMensajesDirectos;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.BloqueosDeMensajes;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.ConsultaInvalida;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Conversacion;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.EnviarMensajeDirecto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.EstadoDeConversacion;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirectoRechazado;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MotivoDeRechazo;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Remitente;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.ResumenDeConversacion;
import com.nexusbattles.plataforma.salaspartidas.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La via REST de los mensajes privados — B6, {@code /mensajes-directos/**} de
 * {@code salas-partidas.yaml} 1.6.x; el bloqueo (D-40), 1.8.0.
 *
 * <p>Se prueba lo que solo se puede probar aqui: la seguridad por rol, que el
 * uid propio sale del token y no de la ruta, las formas del contrato y la
 * traduccion de cada rechazo a su estado, su {@code type} y su Retry-After.
 */
@WebMvcTest(controllers = MensajesDirectosController.class)
@Import(SecurityConfig.class)
@DisplayName("MensajesDirectosController · REST de respaldo y lectura (B6)")
class MensajesDirectosControllerTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE = "/api/v1/mensajes-directos/conversaciones";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnviarMensajeDirecto enviarMensajeDirecto;

    @MockitoBean
    private BandejaDeMensajesDirectos bandeja;

    @MockitoBean
    private BloqueosDeMensajes bloqueos;

    /** Token de ms-identidad tras ADR-002: el sub es el apodo, el uid va aparte. */
    private static RequestPostProcessor como(UUID uid, String apodo, String rol) {
        return jwt().jwt(token -> token.subject(apodo).claim("uid", uid.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static MensajeDirecto mensaje(UUID de, UUID a, String texto, String idCliente) {
        return new MensajeDirecto(UUID.fromString("99999999-9999-9999-9999-999999999999"), Conversacion.entre(de, a),
                de, de.equals(ANA) ? "ana" : "bruno", a, a.equals(ANA) ? "ana" : "bruno", texto,
                Instant.parse("2026-09-25T18:00:00Z"), null, idCliente);
    }

    @Test
    @DisplayName("sin token no hay nada: 401")
    void sinToken() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token de servicio no lee ni escribe mensajes privados: 403")
    void tokenDeServicio() throws Exception {
        mockMvc.perform(get(BASE).with(como(ANA, "salas-partidas", "SERVICIO"))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + BRUNO + "/mensajes").with(como(ANA, "x", "SERVICIO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"hola\"}"))
                .andExpect(status().isForbidden());
        verify(enviarMensajeDirecto, never()).enviar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("mis conversaciones: las del uid del token, con el esquema ResumenDeConversacion")
    void misConversaciones() throws Exception {
        UUID carla = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(bandeja.conversacionesDe(ANA)).thenReturn(List.of(
                new ResumenDeConversacion(BRUNO, "bruno", mensaje(BRUNO, ANA, "hola ana", null), 3),
                new ResumenDeConversacion(carla, "carla", mensaje(ANA, BRUNO, "otra", null), 0,
                        EstadoDeConversacion.NO_ADMITE)));

        mockMvc.perform(get(BASE).with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"))
                .andExpect(jsonPath("$[1].estado").value("NO_ADMITE"))
                .andExpect(jsonPath("$[0].uidOtro").value(BRUNO.toString()))
                .andExpect(jsonPath("$[0].apodoOtro").value("bruno"))
                .andExpect(jsonPath("$[0].noLeidos").value(3))
                .andExpect(jsonPath("$[0].ultimoMensaje.texto").value("hola ana"))
                .andExpect(jsonPath("$[0].ultimoMensaje.conversacion").value("dm:" + ANA + ":" + BRUNO))
                .andExpect(jsonPath("$[0].ultimoMensaje.leido").value(false))
                .andExpect(jsonPath("$[0].ultimoMensaje.fecha").value("2026-09-25T18:00:00Z"));
    }

    @Test
    @DisplayName("un moderador tambien es una persona: lee sus conversaciones")
    void moderadorEsPersona() throws Exception {
        when(bandeja.conversacionesDe(ANA)).thenReturn(List.of());
        mockMvc.perform(get(BASE).with(como(ANA, "ana", "MODERADOR"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("el historial es el del token con uidOtro, con antesDe y limite tal cual")
    void historial() throws Exception {
        when(bandeja.historial(ANA, BRUNO, Instant.parse("2026-09-25T18:00:00Z"), 20))
                .thenReturn(List.of(mensaje(ANA, BRUNO, "mio", "cli-1")));

        mockMvc.perform(get(BASE + "/" + BRUNO + "/mensajes")
                        .param("antesDe", "2026-09-25T18:00:00Z").param("limite", "20")
                        .with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remitente").value(ANA.toString()))
                .andExpect(jsonPath("$[0].apodoRemitente").value("ana"))
                .andExpect(jsonPath("$[0].destinatario").value(BRUNO.toString()))
                .andExpect(jsonPath("$[0].leido").value(true))
                .andExpect(jsonPath("$[0].idCliente").value("cli-1"));
    }

    @Test
    @DisplayName("sin antesDe ni limite, los ultimos con el limite por omision del caso de uso")
    void historialPorOmision() throws Exception {
        when(bandeja.historial(ANA, BRUNO, null, null)).thenReturn(List.of());
        mockMvc.perform(get(BASE + "/" + BRUNO + "/mensajes").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isOk());
        verify(bandeja).historial(ANA, BRUNO, null, null);
    }

    @Test
    @DisplayName("un uidOtro que no es un UUID, o un antesDe que no es una fecha, son 400 con el campo")
    void consultaInvalida() throws Exception {
        mockMvc.perform(get(BASE + "/dm:" + ANA + ":" + BRUNO + "/mensajes").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/parametros-invalidos"))
                .andExpect(jsonPath("$.errores[0].campo").value("uidOtro"));
        mockMvc.perform(get(BASE + "/" + BRUNO + "/mensajes").param("antesDe", "ayer")
                        .with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("antesDe"));
    }

    @Test
    @DisplayName("un limite fuera de rango o la conversacion con uno mismo tambien son 400")
    void historialRechazado() throws Exception {
        when(bandeja.historial(ANA, BRUNO, null, 500)).thenThrow(new ConsultaInvalida("limite", "de 1 a 100"));
        when(bandeja.historial(ANA, ANA, null, null))
                .thenThrow(new MensajeDirectoRechazado(MotivoDeRechazo.DESTINATARIO_PROPIO, null));

        mockMvc.perform(get(BASE + "/" + BRUNO + "/mensajes").param("limite", "500")
                        .with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("limite"));
        mockMvc.perform(get(BASE + "/" + ANA + "/mensajes").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/destinatario-propio"));
    }

    @Test
    @DisplayName("enviar: 201 con el mensaje; el remitente es el del token aunque el cuerpo diga otra cosa")
    void enviar() throws Exception {
        when(enviarMensajeDirecto.enviar(new Remitente(ANA, "ana"), BRUNO, "hola", "cli-1"))
                .thenReturn(mensaje(ANA, BRUNO, "hola", "cli-1"));

        mockMvc.perform(post(BASE + "/" + BRUNO + "/mensajes").with(como(ANA, "ana", "JUGADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"hola\",\"idCliente\":\"cli-1\",\"remitente\":\"" + BRUNO + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remitente").value(ANA.toString()))
                .andExpect(jsonPath("$.idCliente").value("cli-1"))
                .andExpect(jsonPath("$.leido").value(true));
    }

    @Test
    @DisplayName("cada rechazo sale con su estado y su type; el limite con Retry-After")
    void rechazos() throws Exception {
        when(enviarMensajeDirecto.enviar(any(), eq(BRUNO), eq("feo"), any()))
                .thenThrow(new MensajeDirectoRechazado(MotivoDeRechazo.TEXTO_NO_PERMITIDO, "c1"));
        when(enviarMensajeDirecto.enviar(any(), eq(BRUNO), eq("rapido"), any()))
                .thenThrow(new MensajeDirectoRechazado(MotivoDeRechazo.DEMASIADO_RAPIDO, null, Duration.ofMillis(4_200)));
        when(enviarMensajeDirecto.enviar(any(), eq(BRUNO), eq("sancion"), any()))
                .thenThrow(new MensajeDirectoRechazado(MotivoDeRechazo.SANCIONADO, null));
        when(enviarMensajeDirecto.enviar(any(), eq(BRUNO), eq("caido"), any()))
                .thenThrow(new MensajeDirectoRechazado(MotivoDeRechazo.MODERACION_NO_DISPONIBLE, null));
        when(enviarMensajeDirecto.enviar(any(), eq(BRUNO), eq("bloqueado"), any()))
                .thenThrow(new MensajeDirectoRechazado(MotivoDeRechazo.CONVERSACION_BLOQUEADA, null));
        when(enviarMensajeDirecto.enviar(any(), eq(BRUNO), eq("noadmite"), any()))
                .thenThrow(new MensajeDirectoRechazado(MotivoDeRechazo.NO_ADMITE, null));

        mockMvc.perform(enviarTexto("feo"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/contenido-bloqueado"))
                .andExpect(jsonPath("$.title").value("Mensaje bloqueado"));
        mockMvc.perform(enviarTexto("rapido"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/demasiados-mensajes"));
        mockMvc.perform(enviarTexto("sancion"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/jugador-sancionado"));
        mockMvc.perform(enviarTexto("caido"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/moderacion-no-disponible"));
        mockMvc.perform(enviarTexto("bloqueado"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/conversacion-bloqueada"))
                .andExpect(jsonPath("$.title").value("Bloqueaste a este jugador"));
        mockMvc.perform(enviarTexto("noadmite"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/destinatario-no-admite"))
                .andExpect(jsonPath("$.title").value("Este jugador no recibe tus mensajes"));
    }

    private org.springframework.test.web.servlet.RequestBuilder enviarTexto(String texto) {
        return post(BASE + "/" + BRUNO + "/mensajes").with(como(ANA, "ana", "JUGADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + texto + "\"}");
    }

    @Test
    @DisplayName("enviar a un uid mal formado es DESTINATARIO_INEXISTENTE, como por STOMP; sin cuerpo, TEXTO_INVALIDO")
    void enviarMalFormado() throws Exception {
        mockMvc.perform(post(BASE + "/nadie/mensajes").with(como(ANA, "ana", "JUGADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"hola\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/destinatario-inexistente"));
        mockMvc.perform(post(BASE + "/" + BRUNO + "/mensajes").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/mensaje-invalido"));
        verify(enviarMensajeDirecto, never()).enviar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("marcar leido: 204, sobre la conversacion del token")
    void marcarLeido() throws Exception {
        mockMvc.perform(post(BASE + "/" + BRUNO + "/leido").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isNoContent());
        verify(bandeja).marcarLeida(ANA, BRUNO);
    }

    @Test
    @DisplayName("D-40: GET del bloqueo dice si puedo escribirle, sin que haga falta una conversacion")
    void estadoDelBloqueo() throws Exception {
        when(bloqueos.estado(ANA, BRUNO)).thenReturn(EstadoDeConversacion.NO_ADMITE);

        mockMvc.perform(get(BASE + "/" + BRUNO + "/bloqueo").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uidOtro").value(BRUNO.toString()))
                .andExpect(jsonPath("$.estado").value("NO_ADMITE"));
    }

    @Test
    @DisplayName("D-40: PUT bloquea y DELETE desbloquea, siempre en nombre del token, con el estado resultante")
    void bloquearYDesbloquear() throws Exception {
        when(bloqueos.bloquear(ANA, BRUNO)).thenReturn(EstadoDeConversacion.BLOQUEADA);
        when(bloqueos.desbloquear(ANA, BRUNO)).thenReturn(EstadoDeConversacion.ACTIVA);

        mockMvc.perform(put(BASE + "/" + BRUNO + "/bloqueo").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uidOtro").value(BRUNO.toString()))
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"));
        mockMvc.perform(delete(BASE + "/" + BRUNO + "/bloqueo").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
        verify(bloqueos).bloquear(ANA, BRUNO);
        verify(bloqueos).desbloquear(ANA, BRUNO);
    }

    @Test
    @DisplayName("D-40: nadie se bloquea a si mismo ni a un uid mal formado; un token de servicio no bloquea")
    void bloqueoRechazado() throws Exception {
        when(bloqueos.bloquear(ANA, ANA))
                .thenThrow(new MensajeDirectoRechazado(MotivoDeRechazo.DESTINATARIO_PROPIO, null));

        mockMvc.perform(put(BASE + "/" + ANA + "/bloqueo").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/destinatario-propio"));
        mockMvc.perform(get(BASE + "/" + ANA + "/bloqueo").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/destinatario-propio"));
        mockMvc.perform(delete(BASE + "/nadie/bloqueo").with(como(ANA, "ana", "JUGADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("uidOtro"));
        mockMvc.perform(put(BASE + "/" + BRUNO + "/bloqueo").with(como(ANA, "x", "SERVICIO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/" + BRUNO + "/bloqueo")).andExpect(status().isUnauthorized());
        verify(bloqueos, never()).bloquear(ANA, BRUNO);
        verify(bloqueos, never()).estado(any(), any());
    }

    @Test
    @DisplayName("un token sin uid utilizable no identifica a nadie")
    void tokenSinUid() throws Exception {
        mockMvc.perform(get(BASE).with(jwt().jwt(token -> token.subject("solo-apodo"))
                        .authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
                .andExpect(status().isForbidden());
        verify(bandeja, never()).conversacionesDe(any());
    }
}
