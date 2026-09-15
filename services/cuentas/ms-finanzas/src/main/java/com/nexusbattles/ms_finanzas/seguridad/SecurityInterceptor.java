package com.nexusbattles.ms_finanzas.seguridad;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Interceptor que aplica el patrón fail-closed de HU-RBAC-004 en los
 * endpoints marcados con {@link RequireAutenticacion}: sin credencial válida
 * la respuesta es 403 en formato RFC 7807.
 *
 * <p>Diferencias respecto al {@code SecurityInterceptor} de ms-identidad:
 * <ul>
 *   <li>NO hay {@code RbacAuthorizationService} — este servicio no gobierna
 *       roles. La autorización fina (por rol) se resuelve en el método del
 *       controller si hace falta, leyendo {@link #ATTR_ROL} del request.</li>
 *   <li>NO hay {@code UsuarioRepository} — verificar la versión del token
 *       exigiría leer la BD de ms-identidad, lo cual rompe la regla 7 de
 *       plataforma. Aquí se acepta el token si la firma y la expiración
 *       son válidas.</li>
 *   <li>NO hay {@code AuditoriaEventClient} — los intentos denegados se
 *       registran en el log estructurado del propio servicio en vez de en
 *       ms-cumplimiento (ese acoplamiento se puede agregar después si la
 *       auditoría global lo requiere).</li>
 * </ul>
 *
 * <p>Respaldo de desarrollo: el flag {@code app.seguridad.permitir-header-rol}
 * habilita aceptar {@code X-User-Uid} + {@code X-User-Role} en vez del JWT.
 * En producción queda en {@code false} — la única credencial válida es el
 * Bearer JWT emitido por ms-identidad.
 */
@Component
public class SecurityInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SecurityInterceptor.class);

    /** Clave del request attribute con el UUID público del usuario (claim {@code uid} del JWT). */
    public static final String ATTR_UID = "uidActual";

    /** Clave del request attribute con el apodo (claim {@code sub} del JWT). */
    public static final String ATTR_APODO = "apodoActual";

    /** Clave del request attribute con el rol vigente (claim {@code rol} del JWT). */
    public static final String ATTR_ROL = "rolActual";

    private final JwtService jwtService;
    private final boolean permitirHeaderRol;

    public SecurityInterceptor(
            JwtService jwtService,
            @Value("${app.seguridad.permitir-header-rol:false}") boolean permitirHeaderRol) {
        this.jwtService = jwtService;
        this.permitirHeaderRol = permitirHeaderRol;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws java.io.IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (!requiereAutenticacion(handlerMethod)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = jwtService.validarYObtenerClaims(token);
                String uid = claims.get("uid", String.class);
                String apodo = claims.getSubject();
                String rol = claims.get("rol", String.class);

                if (uid == null || uid.isBlank() || rol == null || rol.isBlank()) {
                    return denegar(response, request, "CLAIMS_INCOMPLETOS",
                            "Token sin claims uid o rol");
                }

                request.setAttribute(ATTR_UID, uid);
                request.setAttribute(ATTR_APODO, apodo);
                request.setAttribute(ATTR_ROL, rol);
                return true;
            } catch (JwtException e) {
                return denegar(response, request, "JWT_INVALIDO_O_EXPIRADO", e.getMessage());
            }
        }

        String uidHeader = request.getHeader("X-User-Uid");
        String rolHeader = request.getHeader("X-User-Role");
        if (uidHeader != null && !uidHeader.isBlank() && rolHeader != null && !rolHeader.isBlank()) {
            if (!permitirHeaderRol) {
                return denegar(response, request, "HEADER_ROL_DESHABILITADO",
                        "El respaldo por headers X-User-* solo se acepta en desarrollo");
            }
            request.setAttribute(ATTR_UID, uidHeader.trim());
            request.setAttribute(ATTR_APODO, request.getHeader("X-User-Name"));
            request.setAttribute(ATTR_ROL, rolHeader.trim());
            return true;
        }

        return denegar(response, request, "CREDENCIAL_FALTANTE",
                "Falta el header Authorization: Bearer <token>");
    }

    private boolean requiereAutenticacion(HandlerMethod handler) {
        return handler.getMethodAnnotation(RequireAutenticacion.class) != null
                || handler.getBeanType().getAnnotation(RequireAutenticacion.class) != null;
    }

    private boolean denegar(HttpServletResponse response, HttpServletRequest request,
                            String razon, String detalle) throws java.io.IOException {
        log.warn("AUDIT_TRAIL: {\"event\":\"SECURITY_BYPASS_ATTEMPT\",\"timestamp\":\"{}\",\"uri\":\"{}\",\"razon\":\"{}\"}",
                Instant.now(), request.getRequestURI(), razon);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"type\":\"https://nexusbattles.upb.edu.co/errors/forbidden\",\"title\":\"Acceso denegado\",\"status\":403,\"detail\":\"%s\",\"instance\":\"%s\"}",
                detalle != null ? detalle.replace("\"", "'") : "No tienes permiso para esta acción",
                request.getRequestURI()));
        return false;
    }
}
