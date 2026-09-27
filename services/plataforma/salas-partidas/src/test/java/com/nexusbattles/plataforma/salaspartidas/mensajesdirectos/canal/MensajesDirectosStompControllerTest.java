package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.canal;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.EnviarMensajeDirecto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirectoRechazado;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MotivoDeRechazo;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Remitente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("MensajesDirectosStompController · el remitente es el del CONNECT (B6)")
class MensajesDirectosStompControllerTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final EnviarMensajeDirecto enviar = mock(EnviarMensajeDirecto.class);
    private final MensajesDirectosStompController controlador = new MensajesDirectosStompController(enviar);

    /** Como lo deja AutenticacionStomp con un token de ms-identidad: sub = apodo, uid aparte. */
    private static Principal sesionDe(UUID uid, String apodo, String rol) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(apodo)
                .claim("uid", uid.toString()).claim("rol", rol)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + rol)), uid.toString());
    }

    @Test
    @DisplayName("el remitente sale de la sesion, el destino solo dice a quien")
    void remitenteDelToken() {
        controlador.enviar(BRUNO.toString(), new MensajeSalienteRequest("hola", "cli-1"),
                sesionDe(ANA, "ana", "JUGADOR"));

        verify(enviar).enviar(new Remitente(ANA, "ana"), BRUNO, "hola", "cli-1");
    }

    @Test
    @DisplayName("un destino que no es un uid no nombra a nadie")
    void destinoMalFormado() {
        MensajeDirectoRechazado r = assertThrows(MensajeDirectoRechazado.class, () -> controlador.enviar(
                "no-soy-un-uid", new MensajeSalienteRequest("hola", "cli-2"), sesionDe(ANA, "ana", "JUGADOR")));
        assertAll(
                () -> assertEquals(MotivoDeRechazo.DESTINATARIO_INEXISTENTE, r.motivo()),
                () -> assertEquals("cli-2", r.idCliente()));
    }

    @Test
    @DisplayName("sin cuerpo es un texto invalido")
    void sinCuerpo() {
        MensajeDirectoRechazado r = assertThrows(MensajeDirectoRechazado.class, () -> controlador.enviar(
                BRUNO.toString(), null, sesionDe(ANA, "ana", "JUGADOR")));
        assertEquals(MotivoDeRechazo.TEXTO_INVALIDO, r.motivo());
        verify(enviar, never()).enviar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("un token de servicio, o una sesion que no es JWT, no escriben mensajes privados")
    void soloPersonas() {
        assertAll(
                () -> assertThrows(AccessDeniedException.class, () -> controlador.enviar(BRUNO.toString(),
                        new MensajeSalienteRequest("hola", null), sesionDe(ANA, "salas-partidas", "SERVICIO"))),
                () -> assertThrows(AccessDeniedException.class, () -> controlador.enviar(BRUNO.toString(),
                        new MensajeSalienteRequest("hola", null), () -> ANA.toString())));
    }

    @Test
    @DisplayName("un rechazo vuelve como {tipo: RECHAZO, motivo, idCliente}")
    void rechazo() {
        EnvioRechazadoPayload payload = controlador.rechazado(
                new MensajeDirectoRechazado(MotivoDeRechazo.TEXTO_NO_PERMITIDO, "cli-3"));
        assertEquals(new EnvioRechazadoPayload("RECHAZO", "TEXTO_NO_PERMITIDO", "cli-3"), payload);
    }

    @Test
    @DisplayName("un cuerpo que no es el JSON del contrato es TEXTO_INVALIDO, no un 500")
    void ilegible() {
        assertEquals(new EnvioRechazadoPayload("RECHAZO", "TEXTO_INVALIDO", null),
                controlador.ilegible(new MessageConversionException("no es JSON")));
    }
}
