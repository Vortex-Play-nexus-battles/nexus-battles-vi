package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Objects;

/**
 * 7.4.9 — el token de una conexion del canal, verificado igual que en la
 * cadena HTTP: el mismo {@link JwtDecoder} (el JWKS de ms-identidad, firma,
 * caducidad) y el mismo conversor de roles. Se verifica en el CONNECT
 * ({@link PoliticaDelCanal}) y otra vez en cada mensaje
 * ({@link ConversacionEnVivo}), porque un token caduca mientras la conexion
 * sigue abierta.
 *
 * <p>Un token que no vale (o el JWKS que no responde) da {@code null}: igual
 * que {@code JwtInvalidoComoVisitanteFilter} en {@code /chat/**}, quien lo
 * trae pasa a ser un visitante, no se le corta el chat.
 */
public final class TokenDelCanal {

    static final String PREFIJO_BEARER = "Bearer ";

    private final JwtDecoder decodificador;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> conversor;

    public TokenDelCanal(JwtDecoder decodificador, Converter<Jwt, ? extends AbstractAuthenticationToken> conversor) {
        this.decodificador = Objects.requireNonNull(decodificador, "decodificador");
        this.conversor = Objects.requireNonNull(conversor, "conversor");
    }

    /** El token de una cabecera {@code Authorization: Bearer ...}, o null. */
    static String deLaCabecera(String cabecera) {
        if (cabecera == null || !cabecera.startsWith(PREFIJO_BEARER)) {
            return null;
        }
        String token = cabecera.substring(PREFIJO_BEARER.length()).strip();
        return token.isEmpty() ? null : token;
    }

    /** La autenticacion de ese token, o null si no hay token o no vale. */
    Authentication autenticar(String token) {
        if (token == null) {
            return null;
        }
        try {
            return conversor.convert(decodificador.decode(token));
        } catch (JwtException tokenQueNoVale) {
            return null;
        }
    }
}
