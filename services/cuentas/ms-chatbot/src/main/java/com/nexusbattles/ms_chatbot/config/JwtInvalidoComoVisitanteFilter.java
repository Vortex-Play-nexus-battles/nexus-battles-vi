package com.nexusbattles.ms_chatbot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

// HU-CHA-008: /chat/** debe seguir funcionando 24/7 incluso si el usuario
// trae un JWT vencido o invalido (por ejemplo, su sesion expiro a mitad de
// una conversacion). Sin este filtro, el filtro de Resource Server de
// Spring Security responde 401 antes de que "permitAll" importe, cortando
// el chat para alguien que deberia poder seguir usandolo como visitante.
//
// Estrategia: decodificar el JWT nosotros mismos, antes de que la cadena de
// seguridad lo intente. Si es invalido, se envuelve la peticion quitando la
// cabecera Authorization, para que el resto de la cadena la trate igual que
// una peticion sin token (visitante). Si es valido, no se toca nada y sigue
// el flujo normal (ChatController.resolverIdentidad la reconoce como
// autenticada).
public class JwtInvalidoComoVisitanteFilter extends OncePerRequestFilter {

    private static final String CABECERA_AUTORIZACION = "Authorization";
    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public JwtInvalidoComoVisitanteFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String cabecera = request.getHeader(CABECERA_AUTORIZACION);

        if (cabecera != null && cabecera.startsWith(PREFIJO_BEARER)) {
            String token = cabecera.substring(PREFIJO_BEARER.length());
            if (!esValido(token)) {
                filterChain.doFilter(new PeticionSinAutorizacion(request), response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean esValido(String token) {
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (JwtException excepcion) {
            return false;
        }
    }

    // Envoltorio que oculta la cabecera Authorization, para que el resto de
    // la cadena de filtros vea la peticion igual que la de un visitante sin
    // token.
    private static class PeticionSinAutorizacion extends HttpServletRequestWrapper {

        PeticionSinAutorizacion(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String nombre) {
            if (CABECERA_AUTORIZACION.equalsIgnoreCase(nombre)) {
                return null;
            }
            return super.getHeader(nombre);
        }

        @Override
        public Enumeration<String> getHeaders(String nombre) {
            if (CABECERA_AUTORIZACION.equalsIgnoreCase(nombre)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(nombre);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(
                Collections.list(super.getHeaderNames()).stream()
                    .filter(nombre -> !CABECERA_AUTORIZACION.equalsIgnoreCase(nombre))
                    .toList()
            );
        }
    }
}
