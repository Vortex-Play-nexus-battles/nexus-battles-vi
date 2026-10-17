package com.nexusbattles.ms_identidad.auth.sesion;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Renovacion de una sesion en uso — 7.3.9 (timeout por inactividad).
 *
 * <p>La consola renueva el token mientras la persona trabaja; si deja de
 * hacerlo, el token de 15 minutos caduca y el servidor lo rechaza. Renovar no
 * es volver a autenticarse: la sesion conserva su {@code auth_time} y su
 * {@code amr} (si paso el segundo factor, lo sigue diciendo), y no se renueva
 * nada que ya no deberia valer:
 *
 * <ul>
 *   <li>token caducado, mal firmado o de otro emisor;</li>
 *   <li>version de token superada (cambio de rol, sancion, contrasena o
 *       segundo factor restablecido);</li>
 *   <li>cuenta que ya no esta ACTIVA (suspendida, baneada, cerrada);</li>
 *   <li>sesion que ya agoto su duracion maxima desde que se autentico.</li>
 * </ul>
 */
@Service
public class RenovacionDeSesionService {

    private static final Logger BITACORA = LoggerFactory.getLogger(RenovacionDeSesionService.class);

    private final JwtService jwt;
    private final UsuarioRepository usuarios;
    private final Clock reloj;

    @Autowired
    public RenovacionDeSesionService(JwtService jwt, UsuarioRepository usuarios) {
        this(jwt, usuarios, Clock.systemUTC());
    }

    RenovacionDeSesionService(JwtService jwt, UsuarioRepository usuarios, Clock reloj) {
        this.jwt = jwt;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /** La sesion renovada: el token nuevo y cuando caduca. */
    public record SesionRenovada(String token, Instant expiraEn) {
    }

    @Transactional(readOnly = true)
    public SesionRenovada renovar(String autorizacion) {
        if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
            throw new SesionNoRenovableException("No hay una sesión que renovar.");
        }
        Claims claims;
        try {
            claims = jwt.validarYObtenerClaims(autorizacion.substring(7).trim());
        } catch (JwtException | IllegalArgumentException invalido) {
            throw new SesionNoRenovableException("Tu sesión terminó. Vuelve a iniciar sesión.");
        }

        Usuario usuario = titular(claims)
                .orElseThrow(() -> new SesionNoRenovableException("Tu sesión terminó. Vuelve a iniciar sesión."));
        if (!jwt.esVersionVigente(claims, usuario.getVersionToken())) {
            throw new SesionNoRenovableException("Tu sesión ya no es válida. Vuelve a iniciar sesión.");
        }
        if (!EstadoCuenta.ACTIVO.equals(EstadoCuenta.normalizado(usuario.getEstado()))) {
            throw new SesionNoRenovableException("Tu cuenta no puede continuar la sesión.");
        }

        String rol = usuario.getRol() == null ? null : usuario.getRol().getNombre();
        Instant autenticadoEn = JwtService.autenticadoEn(claims);
        Instant ahora = reloj.instant();
        if (!ahora.isBefore(autenticadoEn.plus(jwt.duracionMaximaDeSesion(rol)))) {
            throw new SesionNoRenovableException(
                    "Tu sesión alcanzó su duración máxima. Vuelve a iniciar sesión.");
        }

        String token = jwt.generarToken(usuario.getApodo(), rol, usuario.getVersionToken(), usuario.getPublicId(),
                JwtService.metodosDe(claims), autenticadoEn);
        Instant expiraEn = jwt.validarYObtenerClaims(token).getExpiration().toInstant();
        BITACORA.debug("SESION_RENOVADA usuarioId={} expiraEn={}", usuario.getId(), expiraEn);
        return new SesionRenovada(token, expiraEn);
    }

    /** La cuenta del token: por su {@code uid} y, en un token anterior al uid, por su apodo. */
    Optional<Usuario> titular(Claims claims) {
        String uid = claims.get("uid", String.class);
        if (uid != null && !uid.isBlank()) {
            try {
                return usuarios.findByPublicId(UUID.fromString(uid));
            } catch (IllegalArgumentException malFormado) {
                return Optional.empty();
            }
        }
        String apodo = claims.getSubject();
        return apodo == null || apodo.isBlank() ? Optional.empty() : usuarios.findByApodo(apodo);
    }
}
