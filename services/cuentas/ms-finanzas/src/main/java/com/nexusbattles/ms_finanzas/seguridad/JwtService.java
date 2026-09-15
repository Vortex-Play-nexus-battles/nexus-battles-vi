package com.nexusbattles.ms_finanzas.seguridad;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Validador de JWT emitido por ms-identidad. Es una versión reducida del
 * {@code JwtService} de ese servicio: ms-finanzas SOLO consume tokens, no los
 * emite. Por lo mismo tampoco expone {@code esVersionVigente} — verificar la
 * versión del token contra la fila del usuario en ms-identidad implicaría
 * acceder a la base de datos de otro servicio, y eso rompe la regla 7 de
 * plataforma. Aquí se acepta el token mientras la firma y la expiración sean
 * correctas.
 *
 * <p>La clave secreta se comparte con ms-identidad por variable de entorno
 * {@code JWT_CLAVE_SECRETA}. El {@link SecurityInterceptor} traduce las
 * excepciones de {@code io.jsonwebtoken} a respuestas 403 en formato RFC 7807.
 */
@Service
public class JwtService {

    private final String claveSecretaTexto;

    public JwtService(@Value("${app.jwt.clave-secreta}") String claveSecretaTexto) {
        this.claveSecretaTexto = claveSecretaTexto;
    }

    private SecretKey obtenerClave() {
        return Keys.hmacShaKeyFor(claveSecretaTexto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Valida firma y expiración; devuelve los claims si el token es correcto.
     * Lanza {@code io.jsonwebtoken.JwtException} (o alguna subclase) si el
     * token es inválido, fue alterado o expiró.
     */
    public Claims validarYObtenerClaims(String token) {
        return Jwts.parser()
                .verifyWith(obtenerClave())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
