package com.nexusbattles.plataforma.observabilidad;

import java.io.IOException;
import java.time.Clock;

import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mide el tiempo extremo a extremo de cada peticion (HU-REN-001, CA-01).
 *
 * <p>Es un filtro y no codigo repartido por los controladores: asi la medida
 * abarca todo el viaje que atiende el servicio —validacion, negocio, base de
 * datos y serializacion de la respuesta— y no solo el metodo del controlador.
 * Vive en la biblioteca compartida para que los servicios del bloque la
 * hereden en una linea, en vez de copiar el mismo filtro ocho veces.
 *
 * <p>El tiempo se toma con {@link System#nanoTime()} y no con el reloj de
 * pared: el reloj de pared puede saltar hacia atras con un ajuste de NTP y
 * producir duraciones negativas. El reloj inyectado solo pone la marca de
 * tiempo de la muestra, que si debe ser una fecha real.
 */
public class FiltroDeLatencia extends OncePerRequestFilter implements Ordered {

    /** Casi el primero de la cadena: ver {@link #getOrder()}. */
    public static final int ORDEN_POR_OMISION = Ordered.HIGHEST_PRECEDENCE + 10;

    private final RegistroDeLatencia registro;
    private final Clock reloj;
    private final int orden;

    public FiltroDeLatencia(RegistroDeLatencia registro, Clock reloj) {
        this(registro, reloj, ORDEN_POR_OMISION);
    }

    public FiltroDeLatencia(RegistroDeLatencia registro, Clock reloj, int orden) {
        this.registro = registro;
        this.reloj = reloj;
        this.orden = orden;
    }

    /**
     * Sin esto el filtro quedaria el ultimo de la cadena.
     *
     * <p>Un {@code Filter} declarado como bean y sin orden se registra con la
     * precedencia mas baja. Medir desde ahi dejaria fuera el tiempo que tardan
     * los filtros de seguridad y de conversion, que es tiempo que el jugador
     * espera igual: RNF-REN-001 pide latencia extremo a extremo, no latencia
     * del controlador.
     */
    @Override
    public int getOrder() {
        return orden;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {

        long inicio = System.nanoTime();
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            // En finally: una peticion que termina en excepcion es justo la
            // que interesa medir. Si solo se midiera el camino feliz, los
            // fallos lentos —los que mas molestan al jugador— no apareceran
            // en ningun percentil.
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            registro.registrar(new MuestraDeLatencia(
                    registro.servicio(),
                    peticion.getMethod(),
                    rutaDe(peticion),
                    respuesta.getStatus(),
                    duracionMs,
                    reloj.instant()));
        }
    }

    /**
     * Plantilla de la ruta cuando Spring la conoce, y si no la URI.
     *
     * <p>Agrupar por plantilla —{@code /api/v1/salas/{id}}— y no por URI
     * concreta es lo que hace util el informe: con la URI, cada sala seria una
     * operacion distinta y no habria percentil que calcular.
     */
    private static String rutaDe(HttpServletRequest peticion) {
        Object plantilla = peticion.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return plantilla != null ? plantilla.toString() : peticion.getRequestURI();
    }
}
