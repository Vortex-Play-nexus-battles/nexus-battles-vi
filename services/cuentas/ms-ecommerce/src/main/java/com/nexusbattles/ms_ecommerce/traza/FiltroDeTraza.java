package com.nexusbattles.ms_ecommerce.traza;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Abre la traza de cada peticion con el {@code traceparent} que traiga (o una
 * nueva) y la cierra al terminar, pase lo que pase (regla 5).
 *
 * <p>Va antes que la cadena de seguridad: un 401 tambien queda trazado.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroDeTraza extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        Traza.abrir(Traza.traceIdDe(peticion.getHeader(InterceptorDeTraza.CABECERA)).orElse(null));
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            Traza.cerrar();
        }
    }
}
