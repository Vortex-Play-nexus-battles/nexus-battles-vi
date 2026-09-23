package com.nexusbattles.ms_subastas.subastas.realtime;

import java.util.Objects;

import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Quien puede hacer que en el canal del listado de subastas — R9.6b.
 *
 * <h2>Por que no es simplemente {@code AutenticacionStomp}</h2>
 *
 * El interceptor compartido de plataforma rechaza TODO CONNECT sin token:
 * "ningun canal de la plataforma admite anonimos, porque cada mensaje se
 * atribuye a un usuario y las sanciones son por persona". Eso es exacto para
 * el chat de sala y para la bandeja de notificaciones, donde cada frame lleva
 * el nombre de alguien.
 *
 * <p>Este canal es otra cosa, y conviene decirlo con las dos evidencias que lo
 * demuestran en vez de suponerlo:
 *
 * <ol>
 *   <li>La vista es <b>publica por decision de producto</b>:
 *       {@code frontend/app-web/src/comun/matriz-acceso.js} declara
 *       {@code pujas: { acceso: ACCESO.PUBLICA }}. Un visitante sin cuenta
 *       puede abrir el listado de subastas y verlo. Exigir token en el CONNECT
 *       le quitaria la actualizacion en vivo a quien tiene derecho a verla, y
 *       la pantalla degradaria al sondeo de 5 s sin que nadie entendiera por
 *       que.</li>
 *   <li>El canal es <b>solo de difusion</b>: en todo ms-subastas no existe ni
 *       un {@code @MessageMapping}. El servidor publica en
 *       {@code /topic/subastas/listado} desde {@code SubastaRealtimePublisher}
 *       y el cliente unicamente se suscribe. Ningun frame del navegador entra
 *       al dominio.</li>
 * </ol>
 *
 * <h2>Lo que si se cierra, y es mas de lo que pedia el molde</h2>
 *
 * <ul>
 *   <li><b>Un token invalido, caducado o firmado por otro rechaza la
 *       conexion.</b> Sin ambiguedad: presentar una credencial rota nunca es
 *       lo mismo que no presentar ninguna. Quien no manda nada es un visitante;
 *       quien manda basura esta intentando algo.</li>
 *   <li><b>Con token valido la sesion queda identificada</b> por el
 *       identificador estable ({@code uid}, ADR-002), con el mismo conversor
 *       que la cadena HTTP. El {@code sub} sigue siendo el apodo y nadie lo
 *       interpreta como UUID.</li>
 *   <li><b>Suscribirse a cualquier destino que no sea el listado publico exige
 *       estar identificado.</b> Hoy no existe ningun otro destino; la regla
 *       esta puesta para que el dia que alguien anada
 *       {@code /user/**} o un topico por subasta, el destino nazca cerrado en
 *       vez de abierto.</li>
 *   <li><b>Ningun SEND del cliente se acepta.</b> No hay {@code @MessageMapping}
 *       que lo atienda, asi que un SEND solo puede ser un intento de encontrar
 *       uno. Rechazarlo de plano es la garantia anti-suplantacion mas fuerte
 *       posible aqui: el usuario A no puede ni intentar actuar como B, porque
 *       no hay ningun camino por el que un dato del payload se convierta en
 *       identidad.</li>
 * </ul>
 *
 * <p>Si algun dia este canal acepta mensajes del cliente, la regla cambia: el
 * actor se lee del {@code Principal} de la sesion y NUNCA de un campo del
 * cuerpo. Queda dicho aqui para que quien anada el primer
 * {@code @MessageMapping} lo lea antes de escribirlo.
 */
public class PoliticaDelCanalDeSubastas implements ChannelInterceptor {

    public static final String CABECERA = "Authorization";
    public static final String PREFIJO = "Bearer ";

    /** El unico destino publico: el listado que publica SubastaRealtimePublisher. */
    private final String destinoPublico;

    private final JwtDecoder decodificador;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> conversor;

    public PoliticaDelCanalDeSubastas(
            JwtDecoder decodificador,
            Converter<Jwt, ? extends AbstractAuthenticationToken> conversor,
            String destinoPublico) {
        this.decodificador = Objects.requireNonNull(decodificador, "decodificador");
        this.conversor = Objects.requireNonNull(conversor, "conversor");
        this.destinoPublico = Objects.requireNonNull(destinoPublico, "destinoPublico");
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabeceras =
                MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (cabeceras == null || cabeceras.getCommand() == null) {
            return mensaje;
        }

        return switch (cabeceras.getCommand()) {
            case CONNECT -> identificar(mensaje, cabeceras);
            case SUBSCRIBE -> comprobarSuscripcion(mensaje, cabeceras);
            case SEND -> rechazarEnvio();
            default -> mensaje;
        };
    }

    /**
     * Sin cabecera, visitante. Con cabecera, tiene que ser un token de verdad:
     * una credencial rota no se ignora, se rechaza.
     */
    private Message<?> identificar(Message<?> mensaje, StompHeaderAccessor cabeceras) {
        String valor = cabeceras.getFirstNativeHeader(CABECERA);
        if (valor == null || valor.isBlank()) {
            return mensaje;
        }
        if (!valor.startsWith(PREFIJO)) {
            throw new AccessDeniedException(
                    "La credencial del canal tiene que ser un token Bearer.");
        }
        try {
            Jwt jwt = decodificador.decode(valor.substring(PREFIJO.length()).strip());
            AbstractAuthenticationToken usuario = conversor.convert(jwt);
            if (usuario == null) {
                throw new AccessDeniedException("El token de acceso no identifica a nadie.");
            }
            cabeceras.setUser(usuario);
        } catch (JwtException ex) {
            throw new AccessDeniedException("El token de acceso no es valido.");
        }
        return mensaje;
    }

    /** El listado, para todos. Cualquier otro destino, solo identificado. */
    private Message<?> comprobarSuscripcion(Message<?> mensaje, StompHeaderAccessor cabeceras) {
        String destino = cabeceras.getDestination();
        if (destinoPublico.equals(destino)) {
            return mensaje;
        }
        if (cabeceras.getUser() == null) {
            throw new AccessDeniedException(
                    "Ese canal necesita una sesion; el listado publico es " + destinoPublico + ".");
        }
        return mensaje;
    }

    /**
     * ms-subastas no tiene ni un {@code @MessageMapping}: nada del cliente
     * llega al dominio por aqui. Un SEND solo puede ser un tanteo, y se
     * contesta como tal.
     */
    private Message<?> rechazarEnvio() {
        throw new AccessDeniedException("Este canal no acepta mensajes del cliente.");
    }
}
