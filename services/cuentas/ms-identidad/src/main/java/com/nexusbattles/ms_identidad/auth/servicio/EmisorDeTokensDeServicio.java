package com.nexusbattles.ms_identidad.auth.servicio;

import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Emision de credenciales de servicio (ADR-001 implementado segun ADR-005).
 *
 * <p>Un token de servicio dice <b>que servicio habla</b>, nunca que usuario:
 * sujeto y {@code azp} llevan el {@code client_id}, el rol es
 * {@value #ROL_SERVICIO} y no hay {@code uid} ni {@code ver}. Los servidores
 * de recursos lo distinguen de un token de usuario por el rol
 * ({@code hasRole("SERVICIO")}) y, cuando les importa cual servicio es, por
 * {@code azp} ({@code ActorDeServicio} en plataforma-seguridad).
 *
 * <p>Se firma con la misma clave RSA que los tokens de usuario y se verifica
 * por el mismo JWKS: para un servidor de recursos no hay nada nuevo que
 * configurar. Que el emisor sea este servicio y no Keycloak es una decision
 * transitoria documentada en ADR-005; el dia que exista un realm, los
 * clientes cambian {@code DIRECTORIO_ACTIVO_URL} y nada mas.
 *
 * <p>Vigencia corta ({@code app.servicios.minutos-vigencia}, 15 por defecto):
 * el cliente la renueva solo ({@code TokenDeServicioOAuth2}) y un token
 * filtrado vale poco tiempo.
 */
@Service
public class EmisorDeTokensDeServicio {

    /** Rol unico de los tokens de servicio; da {@code ROLE_SERVICIO} en los servidores de recursos. */
    public static final String ROL_SERVICIO = "SERVICIO";

    /** Un token emitido y su vigencia, con la forma que responde el endpoint de token. */
    public record TokenEmitido(String valor, long expiraEnSegundos) {
    }

    private final ClavesDeFirma claves;
    private final String emisor;
    private final Duration vigencia;
    private final Clock reloj;

    @Autowired
    public EmisorDeTokensDeServicio(
            ClavesDeFirma claves,
            @Value("${app.jwt.emisor:ms-identidad}") String emisor,
            @Value("${app.servicios.minutos-vigencia:15}") int minutosVigencia) {
        this(claves, emisor, minutosVigencia, Clock.systemUTC());
    }

    EmisorDeTokensDeServicio(ClavesDeFirma claves, String emisor, int minutosVigencia, Clock reloj) {
        if (minutosVigencia < 1) {
            throw new IllegalArgumentException("app.servicios.minutos-vigencia debe ser al menos 1");
        }
        this.claves = claves;
        this.emisor = emisor;
        this.vigencia = Duration.ofMinutes(minutosVigencia);
        this.reloj = reloj;
    }

    /** Emite la credencial del servicio {@code clientId}, ya autenticado por quien llama. */
    public TokenEmitido emitir(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("El client_id es obligatorio para emitir una credencial de servicio.");
        }
        Instant ahora = reloj.instant();
        Instant expiracion = ahora.plus(vigencia);

        String token = Jwts.builder()
                .header().add(Map.of("kid", claves.identificador())).and()
                .issuer(emisor)
                .subject(clientId)
                .claim("azp", clientId)
                .claim("rol", ROL_SERVICIO)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiracion))
                .signWith(claves.privada(), Jwts.SIG.RS256)
                .compact();

        return new TokenEmitido(token, vigencia.toSeconds());
    }
}
