package com.nexusbattles.ms_identidad.rbac.security;

import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.auth.servicio.EmisorDeTokensDeServicio;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * El camino de las credenciales de SERVICIO, solo para {@code /api/v1/internal/**} (B2).
 *
 * <p>{@link SecurityInterceptor} autoriza personas: busca la cuenta del
 * {@code sub} y compara la version de token, y un token de servicio (sin
 * cuenta ni {@code ver}) nunca pasa por ahi. Estas rutas son lo contrario:
 * solo las llama otro servicio. Se registra UNICAMENTE sobre
 * {@code /api/v1/internal/**} ({@code WebSecurityConfig}), asi que no abre nada
 * mas, y es <b>fail-closed</b>: cualquier ruta bajo ese prefijo exige una
 * credencial de servicio valida aunque su controlador olvide la anotacion.
 *
 * <p>Que exige:
 * <ul>
 *   <li>{@code Authorization: Bearer} con un JWT firmado por la clave de ESTE
 *       servicio (el emisor de ADR-005) y no caducado;</li>
 *   <li>emisor ({@code iss}) el de este servicio;</li>
 *   <li>{@code rol=SERVICIO} —un token de persona, aunque sea de un Super
 *       Administrador, no sirve—;</li>
 *   <li>si la ruta lo pide ({@link SoloServicio#azp()}), que el servicio que
 *       llama ({@code azp}) sea uno de los permitidos.</li>
 * </ul>
 * Deja el {@code client_id} en el atributo {@code servicioActual}.
 */
@Component
public class InterceptorDeServicio implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InterceptorDeServicio.class);

    public static final String ATRIBUTO_SERVICIO = "servicioActual";

    private final JwtService jwtService;
    private final AuditoriaEventClient auditoria;
    private final String emisor;

    public InterceptorDeServicio(JwtService jwtService, AuditoriaEventClient auditoria,
                                 @Value("${app.jwt.emisor:ms-identidad}") String emisor) {
        this.jwtService = jwtService;
        this.auditoria = auditoria;
        this.emisor = emisor;
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(peticion.getMethod())) {
            return true;
        }
        String cabecera = peticion.getHeader("Authorization");
        if (cabecera == null || !cabecera.startsWith("Bearer ")) {
            return denegar(peticion, respuesta, "ANONYMOUS", "CREDENCIAL_DE_SERVICIO_AUSENTE");
        }
        Claims claims;
        try {
            claims = jwtService.validarYObtenerClaims(cabecera.substring(7).trim());
        } catch (JwtException | IllegalArgumentException invalido) {
            return denegar(peticion, respuesta, "INVALID_JWT", "JWT_INVALIDO_O_CADUCADO");
        }
        String azp = claims.get("azp", String.class);
        String rol = claims.get("rol", String.class);
        if (!emisor.equals(claims.getIssuer())) {
            return denegar(peticion, respuesta, azp, "EMISOR_DESCONOCIDO");
        }
        if (!EmisorDeTokensDeServicio.ROL_SERVICIO.equals(rol) || azp == null || azp.isBlank()) {
            return denegar(peticion, respuesta, claims.getSubject(), "NO_ES_CREDENCIAL_DE_SERVICIO");
        }
        String[] permitidos = permitidos(manejador);
        if (permitidos.length > 0 && Arrays.stream(permitidos).noneMatch(azp::equals)) {
            return denegar(peticion, respuesta, azp, "SERVICIO_NO_AUTORIZADO");
        }
        peticion.setAttribute(ATRIBUTO_SERVICIO, azp);
        return true;
    }

    private static String[] permitidos(Object manejador) {
        if (!(manejador instanceof HandlerMethod metodo)) {
            return new String[0];
        }
        SoloServicio anotacion = metodo.getMethodAnnotation(SoloServicio.class);
        if (anotacion == null) {
            anotacion = metodo.getBeanType().getAnnotation(SoloServicio.class);
        }
        return anotacion == null ? new String[0] : anotacion.azp();
    }

    private boolean denegar(HttpServletRequest peticion, HttpServletResponse respuesta, String quien,
                            String motivo) throws Exception {
        log.warn("Ruta interna denegada: {} {} quien={} motivo={}", peticion.getMethod(), peticion.getRequestURI(),
                quien, motivo);
        if (auditoria != null) {
            auditoria.registrarBypassAsync(quien, "SERVICIO", "INTERNO " + peticion.getRequestURI(), motivo,
                    peticion.getRemoteAddr());
        }
        respuesta.setStatus(HttpStatus.FORBIDDEN.value());
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.setContentType("application/problem+json;charset=UTF-8");
        respuesta.getWriter().write(String.format(
                "{\"type\": \"https://nexusbattles.upb.edu.co/errors/forbidden\", \"title\": \"Acceso denegado\","
                        + " \"status\": 403, \"detail\": \"Esta ruta solo atiende a servicios autorizados\","
                        + " \"instance\": \"%s\"}", peticion.getRequestURI()));
        return false;
    }
}
