package com.nexusbattles.ms_subastas.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

/**
 * Valida el JWT que emite ms-identidad: firma y expiracion, nada mas.
 *
 * <p><b>Que NO hace, a proposito.</b> No comprueba si el rol del token sigue
 * vigente. ms-identidad guarda una version de rol por usuario y la mete en el
 * claim {@code ver}; comparar esa version contra la actual exige leer su tabla
 * de usuarios, que es de otro dominio (ArchUnit prohibe importarla) o llamarlo
 * por HTTP dentro del lock pesimista de la subasta, que es justo el riesgo de
 * rendimiento que este servicio evita. Consecuencia aceptada: si a alguien le
 * revocan el rol, su token sigue sirviendo aqui hasta que expire solo. El
 * acuerdo, el riesgo y el diseno de la version siguiente estan en el README del
 * servicio, "Asunciones tomadas" punto 4.
 *
 * <p><b>Pensado para mudarse.</b> No depende de Spring ni del dominio de
 * subastas: solo de jjwt y de {@code java.time}. Vive aqui porque
 * {@code shared/libs/} todavia no existe como modulo y crearlo fijaria la
 * estructura para los tres equipos sin haberlo acordado. Cuando se acuerde,
 * mudarlo es mover el paquete y declarar la dependencia.
 *
 * <p>El reloj se inyecta, como en el resto del servicio: con el reloj del
 * sistema la expiracion solo se puede probar durmiendo hilos.
 */
public class ValidadorDeToken {

    private static final String PREFIJO_BEARER = "bearer ";

    private final SecretKey clave;
    private final io.jsonwebtoken.Clock relojParaJjwt;

    /**
     * @param claveSecreta la misma con la que firma ms-identidad
     *                     ({@code app.jwt.clave-secreta}). Si difiere, todo
     *                     token legitimo se rechaza por firma invalida.
     * @param reloj        reloj con el que se juzga la expiracion.
     */
    public ValidadorDeToken(String claveSecreta, Clock reloj) {
        if (claveSecreta == null || claveSecreta.isBlank()) {
            throw new IllegalArgumentException("la clave de firma es obligatoria: sin ella no se puede validar ningun token");
        }
        this.clave = Keys.hmacShaKeyFor(claveSecreta.getBytes(StandardCharsets.UTF_8));
        this.relojParaJjwt = () -> Date.from(reloj.instant());
    }

    /**
     * Valida el contenido de un encabezado {@code Authorization}, con su
     * prefijo {@code Bearer}. Es la forma en que llega desde una peticion HTTP.
     */
    public IdentidadDelSolicitante validarEncabezado(String encabezadoAuthorization) {
        if (encabezadoAuthorization == null || encabezadoAuthorization.isBlank()) {
            throw new TokenInvalidoException("falta el encabezado Authorization");
        }
        String encabezado = encabezadoAuthorization.strip();
        if (!encabezado.toLowerCase().startsWith(PREFIJO_BEARER)) {
            throw new TokenInvalidoException("el encabezado Authorization debe empezar por 'Bearer '");
        }
        return validar(encabezado.substring(PREFIJO_BEARER.length()));
    }

    /** Valida un token ya sin el prefijo {@code Bearer}. */
    public IdentidadDelSolicitante validar(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException("falta el token");
        }

        Claims claims = leerClaims(token.strip());

        String apodo = claims.getSubject();
        if (apodo == null || apodo.isBlank()) {
            throw new TokenInvalidoException("el token no identifica a ningun usuario");
        }

        String rol = claims.get("rol", String.class);
        if (rol == null || rol.isBlank()) {
            throw new TokenInvalidoException("el token no trae rol: sin rol no se autoriza nada");
        }

        Integer version = claims.get("ver", Integer.class);
        if (version == null) {
            throw new TokenInvalidoException("el token no trae version de rol");
        }

        return new IdentidadDelSolicitante(apodo, rol, version);
    }

    /**
     * Traduce los fallos de jjwt a un motivo propio. Se captura
     * {@link ExpiredJwtException} antes que {@link JwtException} porque es una
     * subclase suya y expirado merece un motivo distinto a "no valida".
     */
    private Claims leerClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(clave)
                    .clock(relojParaJjwt)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException expirado) {
            throw new TokenInvalidoException("el token expiro", expirado);
        } catch (SignatureException firmaInvalida) {
            throw new TokenInvalidoException("la firma del token no valida", firmaInvalida);
        } catch (JwtException | IllegalArgumentException malformado) {
            throw new TokenInvalidoException("el token no es valido", malformado);
        }
    }
}
