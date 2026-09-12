package com.nexusbattles.plataforma.observabilidad;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enciende la instrumentacion de latencia en cualquier servicio del monorepo
 * (HU-REN-001, CA-01).
 *
 * <p>Esta es la pieza que hace que CA-01 se cumpla <b>sin copiar el filtro
 * servicio por servicio</b>: la biblioteca se reparte desde
 * {@code buildSrc/src/main/groovy/nexus.spring-conventions.gradle}, que ya
 * aplican los veinte modulos, y esta autoconfiguracion la activa sola. Un
 * servicio nuevo queda instrumentado por existir, sin tocar su
 * {@code build.gradle} ni su codigo.
 *
 * <p>Si alguien necesita otra cosa, cada bean es {@code @ConditionalOnMissingBean}:
 * declarar el suyo lo reemplaza, no hay que desactivar nada.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnProperty(prefix = "latencia", name = "activa", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PropiedadesDeLatencia.class)
public class ObservabilidadDeLatenciaAutoConfiguration {

    /**
     * Reloj del sistema, solo para la marca de tiempo de la muestra.
     *
     * <p>La <i>duracion</i> nunca sale de aqui: se mide con
     * {@link System#nanoTime()} dentro del filtro, porque el reloj de pared
     * puede saltar hacia atras con un ajuste de NTP.
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock relojDeLatencia() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistroDeLatencia registroDeLatencia(
            PropiedadesDeLatencia propiedades,
            @Value("${spring.application.name:servicio-sin-nombre}") String nombreDeLaAplicacion) {

        String servicio = propiedades.getServicio() == null || propiedades.getServicio().isBlank()
                ? nombreDeLaAplicacion
                : propiedades.getServicio();

        return new RegistroDeLatencia(servicio, propiedades.getCapacidad());
    }

    /**
     * El filtro va lo mas afuera posible de la cadena.
     *
     * <p>{@link FiltroDeLatencia} implementa {@link Ordered} justamente para
     * esto: un {@code Filter} declarado como bean sin orden queda el ultimo, y
     * medir desde ahi dejaria fuera el tiempo de los filtros de seguridad y de
     * conversion —tiempo que el jugador espera igual—. El requisito habla de
     * latencia extremo a extremo, no de latencia del controlador.
     */
    @Bean
    @ConditionalOnMissingBean
    public FiltroDeLatencia filtroDeLatencia(RegistroDeLatencia registro, Clock relojDeLatencia) {
        return new FiltroDeLatencia(registro, relojDeLatencia, Ordered.HIGHEST_PRECEDENCE + 10);
    }
}
