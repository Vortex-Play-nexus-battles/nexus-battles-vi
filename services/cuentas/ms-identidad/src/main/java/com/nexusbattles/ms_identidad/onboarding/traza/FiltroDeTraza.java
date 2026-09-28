package com.nexusbattles.ms_identidad.onboarding.traza;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Abre la traza W3C de cada peticion entrante (regla 5 de plataforma).
 *
 * <p>Hasta B1 solo el alta del jugador llevaba traza a los servicios que
 * llama. Desde B1 ms-identidad llama a correo, a la lista negra y a
 * moderacion-sanciones en medio de una peticion; con este filtro toda llamada
 * saliente hecha con {@link InterceptorDeTraza} comparte el trace-id de la
 * peticion que la origino (el del {@code traceparent} del cliente, o uno
 * nuevo), y la bitacora lo lleva en el MDC.
 *
 * <p>Si la peticion no trae un {@code traceparent} valido se empieza una
 * traza nueva: nunca se propaga basura recibida de fuera.
 */
@Component
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
