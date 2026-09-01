package com.nexusbattles.ms_identidad.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${app.jwt.clave-secreta}")
    private String claveSecretaTexto;

    @Value("${app.jwt.horas-expiracion:24}")
    private int horasExpiracion;

    private SecretKey obtenerClave() {
        return Keys.hmacShaKeyFor(claveSecretaTexto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Genera un JWT firmado, con el apodo del usuario como sujeto, y su rol
     * como un campo adicional (claim) — es lo que el SecurityInterceptor de
     * Andrés necesitaría leer y verificar en vez de confiar en X-User-Role.
     */
    public String generarToken(String apodo, String rol) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + horasExpiracion * 3600_000L);

        return Jwts.builder()
            .subject(apodo)
            .claim("rol", rol)
            .issuedAt(ahora)
            .expiration(expiracion)
            .signWith(obtenerClave())
            .compact();
    }

    /**
     * Valida la firma y expiración de un token, y devuelve sus datos.
     * Lanza una excepción de la propia librería (JwtException o alguna de
     * sus subclases) si el token es inválido, fue alterado, o expiró.
     */
    public Claims validarYObtenerClaims(String token) {
        return Jwts.parser()
            .verifyWith(obtenerClave())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
