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
     * Genera un JWT firmado, con el apodo del usuario como sujeto, su rol,
     * y la versión de token vigente al momento de generarlo (HU-RBAC-003).
     * Es lo que el SecurityInterceptor de Andrés necesitaría leer y
     * verificar en vez de confiar en X-User-Role.
     */
    public String generarToken(String apodo, String rol, int versionToken) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + horasExpiracion * 3600_000L);

        return Jwts.builder()
            .subject(apodo)
            .claim("rol", rol)
            .claim("ver", versionToken)
            .issuedAt(ahora)
            .expiration(expiracion)
            .signWith(obtenerClave())
            .compact();
    }

    /**
     * Valida la firma y expiración de un token, y devuelve sus datos.
     * Lanza una excepción de la propia librería (JwtException o alguna de
     * sus subclases) si el token es inválido, fue alterado, o expiró.
     * NOTA: esta validación NO comprueba la versión — eso se hace aparte,
     * con esVersionVigente, porque requiere consultar el usuario actual.
     */
    public Claims validarYObtenerClaims(String token) {
        return Jwts.parser()
            .verifyWith(obtenerClave())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Compara la versión de token que trae el JWT contra la versión actual
     * del usuario en base de datos. Si no coinciden, el token fue emitido
     * antes de un cambio de rol y ya no debe considerarse válido, aunque
     * su firma siga siendo correcta y no haya expirado.
     */
    public boolean esVersionVigente(Claims claims, int versionActualDelUsuario) {
        Integer versionDelToken = claims.get("ver", Integer.class);
        return versionDelToken != null && versionDelToken == versionActualDelUsuario;
    }
}
