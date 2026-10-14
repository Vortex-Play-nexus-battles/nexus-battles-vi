package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.rbac.model.JerarquiaDeRoles;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Emision y verificacion de los tokens de acceso.
 *
 * <p><b>Firma RSA (RS256), no HMAC.</b> Estos tokens ya no los consume solo
 * este servicio: la plataforma entera los verifica para aplicar sus reglas por
 * rol. Con HMAC habria que repartir la clave secreta, y esa misma clave sirve
 * para emitir: cualquier servicio que la tuviera podria fabricar un token de
 * cualquier usuario. Con RSA los demas solo reciben la clave publica, por el
 * JWKS. Ver {@link ClavesDeFirma} y
 * {@code docs/gobierno/ADR-002-identidad-de-usuario.md}.
 *
 * <p><b>Inactividad de las sesiones administrativas (7.3.9).</b> Un token de
 * Moderador, Administrador o Super Administrador vive
 * {@code app.jwt.minutos-sesion-administrativa} (15 min por omision) y se
 * renueva solo mientras la persona usa la consola ({@code POST
 * /auth/sesion/renovacion}). Sin actividad, el token caduca en el servidor: el
 * timeout no depende de que la pestana siga abierta ni de JavaScript. Toda
 * renovacion conserva el instante en que se autentico ({@code auth_time}, OIDC
 * Core §2) y ninguna sesion administrativa pasa de
 * {@code app.jwt.horas-maximas-sesion-administrativa} (12 h) sin volver a
 * escribir la contrasena y el segundo factor. Los jugadores siguen con 24 h.
 */
@Service
public class JwtService {

    /** {@code amr} de una sesion abierta solo con la contrasena (RFC 8176 §2: «pwd»). */
    public static final List<String> AMR_CONTRASENA = List.of("pwd");

    /**
     * {@code amr} de una sesion con segundo factor: contrasena y un codigo de un
     * solo uso (TOTP o de recuperacion; RFC 8176 §2: «otp»).
     */
    public static final List<String> AMR_CON_SEGUNDO_FACTOR = List.of("pwd", "otp");

    private final ClavesDeFirma claves;

    @Value("${app.jwt.horas-expiracion:24}")
    private int horasExpiracion;

    @Value("${app.jwt.minutos-sesion-administrativa:15}")
    private int minutosSesionAdministrativa = 15;

    @Value("${app.jwt.horas-maximas-sesion-administrativa:12}")
    private int horasMaximasSesionAdministrativa = 12;

    @Value("${app.jwt.emisor:ms-identidad}")
    private String emisor;

    public JwtService(ClavesDeFirma claves) {
        this.claves = claves;
    }

    /**
     * Genera un JWT firmado, con el apodo del usuario como sujeto, su rol,
     * y la versión de token vigente al momento de generarlo (HU-RBAC-003).
     * Es lo que el SecurityInterceptor de Andrés necesitaría leer y
     * verificar en vez de confiar en X-User-Role.
     *
     * <p>El sujeto sigue siendo el apodo, y eso es deliberado: el
     * {@code SecurityInterceptor} lo lee como identidad y lo propaga a los
     * controladores de administración como {@code usuarioActual}. Cambiarlo por
     * el UUID rompería esa cadena, así que el identificador estable se añade
     * como un claim más ({@code uid}) en lugar de sustituir al sujeto.
     *
     * @param identificadorPublico UUID estable del usuario, para que otros
     *        servicios lo referencien sin depender del apodo, que es mutable.
     *        Puede ser nulo en usuarios creados antes de que el campo
     *        existiera: entonces el token se emite sin ese claim, porque no
     *        poder identificar al usuario de cara a otros servicios no es razón
     *        para negarle el acceso al suyo.
     */
    public String generarToken(String apodo, String rol, int versionToken, UUID identificadorPublico) {
        return generarToken(apodo, rol, versionToken, identificadorPublico, AMR_CONTRASENA);
    }

    /**
     * HU-AUT-007 — como {@link #generarToken(String, String, int, UUID)}, con
     * los metodos de autenticacion de la sesion en el claim {@code amr} (RFC
     * 8176): {@link #AMR_CONTRASENA} o {@link #AMR_CON_SEGUNDO_FACTOR}. Es lo
     * que leen los servicios que exigen doble factor (D-09: ms-cumplimiento
     * con {@code AUDITORIA_EXIGIR_2FA=true} busca {@code otp}).
     */
    public String generarToken(String apodo, String rol, int versionToken, UUID identificadorPublico,
                               List<String> metodos) {
        return generarToken(apodo, rol, versionToken, identificadorPublico, metodos, null);
    }

    /**
     * Como el anterior, conservando el instante de la autenticacion original
     * ({@code auth_time}): lo usa la renovacion de una sesion administrativa.
     * Con {@code autenticadoEn} nulo es una autenticacion nueva (ahora).
     */
    public String generarToken(String apodo, String rol, int versionToken, UUID identificadorPublico,
                               List<String> metodos, Instant autenticadoEn) {
        Date ahora = new Date();
        Instant autenticacion = autenticadoEn == null ? ahora.toInstant() : autenticadoEn;
        Date expiracion = Date.from(expiracionPara(rol, ahora.toInstant(), autenticacion));

        JwtBuilder constructor = Jwts.builder()
            .subject(apodo)
            .claim("rol", rol)
            .claim("ver", versionToken)
            .claim("auth_time", autenticacion.getEpochSecond())
            .claim("amr", metodos == null || metodos.isEmpty() ? AMR_CONTRASENA : List.copyOf(metodos));

        if (identificadorPublico != null) {
            constructor.claim("uid", identificadorPublico.toString());
        }

        return constructor
            .header().add(Map.of("kid", claves.identificador())).and()
            .issuer(emisor)
            .issuedAt(ahora)
            .expiration(expiracion)
            .signWith(claves.privada(), Jwts.SIG.RS256)
            .compact();
    }

    /**
     * Caducidad de un token emitido ahora: 15 minutos (renovables) para un
     * rol administrativo y 24 h para un jugador, sin pasar nunca del maximo de
     * la sesion contado desde que se autentico. Para un jugador las dos cifras
     * coinciden: renovar no le alarga la sesion.
     */
    public Instant expiracionPara(String rol, Instant ahora, Instant autenticadoEn) {
        Duration vida = JerarquiaDeRoles.esAdministrativo(rol)
            ? Duration.ofMinutes(minutosSesionAdministrativa)
            : Duration.ofHours(horasExpiracion);
        Instant porVida = ahora.plus(vida);
        Instant maximo = autenticadoEn.plus(duracionMaximaDeSesion(rol));
        return porVida.isBefore(maximo) ? porVida : maximo;
    }

    /** Lo maximo que puede durar una sesion de este rol desde que se autentico. */
    public Duration duracionMaximaDeSesion(String rol) {
        return JerarquiaDeRoles.esAdministrativo(rol)
            ? Duration.ofHours(horasMaximasSesionAdministrativa)
            : Duration.ofHours(horasExpiracion);
    }

    /** Minutos de inactividad que cierran una sesion administrativa. */
    public int minutosSesionAdministrativa() {
        return minutosSesionAdministrativa;
    }

    /**
     * Cuando se autentico la sesion de este token: su {@code auth_time} o, en
     * un token anterior a 7.3.9, su {@code iat}.
     */
    public static Instant autenticadoEn(Claims claims) {
        Object autenticacion = claims.get("auth_time");
        if (autenticacion instanceof Number segundos) {
            return Instant.ofEpochSecond(segundos.longValue());
        }
        return claims.getIssuedAt() == null ? Instant.EPOCH : claims.getIssuedAt().toInstant();
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
            .verifyWith(claves.publica())
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

    /**
     * Los metodos de autenticacion de un token ya validado ({@code amr}). Un
     * token anterior a HU-AUT-007 no lo trae: lista vacia, que equivale a
     * «sin segundo factor».
     */
    public static List<String> metodosDe(Claims claims) {
        Object amr = claims == null ? null : claims.get("amr");
        if (amr instanceof Collection<?> lista) {
            return lista.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        return List.of();
    }

    /** Si esos metodos incluyen un segundo factor ({@code otp}). */
    public static boolean conSegundoFactor(List<String> metodos) {
        return metodos != null && metodos.contains("otp");
    }
}
