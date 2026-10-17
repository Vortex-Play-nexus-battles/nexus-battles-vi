package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Quien actua y desde donde, leido de la peticion en curso — §7.3.5.
 *
 * <p><b>La direccion es la que fija el borde.</b> nginx escribe
 * {@code X-Real-IP $remote_addr} en toda peticion que entra por el
 * (borde-dev.conf), sustituyendo lo que trajera el cliente: es la unica
 * cabecera de direccion que no se puede falsear desde fuera.
 * {@code X-Forwarded-For} no sirve para esto, porque el borde le anade la
 * direccion que ve detras de lo que el cliente ya hubiera escrito.
 *
 * <p><b>Sin borde, la accion no es de una persona desde la consola.</b> Una
 * peticion sin {@code X-Real-IP} llega desde dentro de la red de servicios:
 * hoy, ms-identidad cuando un administrador suspende, banea o reactiva desde
 * la gestion de usuarios. Esa accion ya la registra ms-identidad con su
 * motivo; si la registrara tambien este servicio, la bitacora la tendria dos
 * veces. Desde fuera no hay forma de quitar la cabecera, asi que no hay forma
 * de que una accion de la consola se quede sin registrar.
 */
public final class OrigenDeLaAccion {

    /** La forma textual mas larga de una IPv6, el largo de la columna de ms-cumplimiento. */
    static final int LARGO_MAXIMO_DE_IP = 45;

    private OrigenDeLaAccion() {
    }

    /** La direccion que fijo el borde, si la peticion entro por el y la cabecera parece una IP. */
    public static Optional<String> ipDelBorde() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (!(atributos instanceof ServletRequestAttributes servlet)) {
            return Optional.empty();
        }
        return ipDelBorde(servlet.getRequest());
    }

    static Optional<String> ipDelBorde(HttpServletRequest peticion) {
        String real = peticion.getHeader("X-Real-IP");
        if (real == null) {
            return Optional.empty();
        }
        String limpia = real.strip();
        return pareceUnaIp(limpia) ? Optional.of(limpia) : Optional.empty();
    }

    /** El {@code uid} de quien actua, del token de la peticion en curso. */
    public static Optional<UUID> actor() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(IdentidadDelToken.idDe(autenticacion));
        } catch (RuntimeException sinUid) {
            return Optional.empty();
        }
    }

    /** Solo caracteres de una IP ({@code 0-9 a-f A-F : .}), como mucho 45. */
    static boolean pareceUnaIp(String valor) {
        return valor != null
                && !valor.isEmpty()
                && valor.length() <= LARGO_MAXIMO_DE_IP
                && valor.chars().allMatch(c -> (c >= '0' && c <= '9')
                        || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                        || c == ':' || c == '.');
    }
}
