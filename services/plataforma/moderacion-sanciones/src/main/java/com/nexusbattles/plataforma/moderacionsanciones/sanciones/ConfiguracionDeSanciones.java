package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Cableado de sanciones: reloj, limites, los tres destinos de las salidas
 * (notificaciones, ms-identidad y correo, todos con la credencial de servicio
 * de ADR-005 y con tiempos de espera) y el entregador con su reintento.
 */
@Configuration
@EnableScheduling
public class ConfiguracionDeSanciones {

    private static final Logger BITACORA = LoggerFactory.getLogger(ConfiguracionDeSanciones.class);

    @Bean
    public Clock relojDeSanciones() {
        return Clock.systemUTC();
    }

    /**
     * El lector del catalogo de parametros (HU-ADM-001).
     *
     * <p>Se construye SIEMPRE, tambien cuando {@code PARAMETROS_URL} viene
     * vacia: en ese caso {@link LectorDeParametros#desde} devuelve un lector
     * sin catalogo que no hace ni una peticion y sirve solo respaldos. Antes
     * este cableado tenia el {@code if} aqui y devolvia otro objeto distinto
     * segun el entorno, con lo que el camino «sin catalogo» no pasaba por el
     * mismo codigo que el de produccion y no lo cubria ninguna prueba.
     */
    @Bean
    public LectorDeParametros lectorDeParametros(
            @Value("${sanciones.parametros.url:}") String urlDeParametros,
            @Value("${sanciones.parametros.cache-segundos:30}") long cacheSegundos,
            Clock reloj) {
        return LectorDeParametros.desde(RestClient.builder().build(), urlDeParametros, reloj,
                Duration.ofSeconds(cacheSegundos));
    }

    /**
     * HU-ADM-001 (CA-04): el rango de la suspension y el plazo de apelacion se
     * leen de admin-parametros por API; las variables de entorno quedan como
     * respaldo cuando no responde o el PO no ha fijado el valor.
     */
    @Bean
    public LimitesDeSancion limitesDeSancion(
            LectorDeParametros parametros,
            @Value("${sanciones.suspension.minima-horas:1}") long minimaHoras,
            @Value("${sanciones.suspension.maxima-dias:30}") long maximaDias,
            @Value("${sanciones.apelacion.plazo-dias:30}") long plazoDias) {
        return new LimitesDesdeParametros(parametros,
                LimitesDeSancion.Fijos.de(minimaHoras, maximaDias, plazoDias));
    }

    /**
     * El cliente HTTP de las salidas: credencial de servicio si esta
     * configurada ({@code DIRECTORIO_ACTIVO_*}), traza (regla 5) y tiempos de
     * espera de conexion y de lectura, para que un destino colgado no tenga
     * parado al entregador.
     */
    static RestClient clienteDeSalidas(ObjectProvider<InterceptorDePortadorDeServicio> credencial,
                                       Duration conexion, Duration lectura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(conexion);
        fabrica.setReadTimeout(lectura);
        RestClient.Builder constructor = RestClient.builder()
                .requestFactory(fabrica)
                .requestInterceptor(new InterceptorDeTraza());
        credencial.ifAvailable(constructor::requestInterceptor);
        return constructor.build();
    }

    @Bean
    public RestClient restClientDeSalidas(
            ObjectProvider<InterceptorDePortadorDeServicio> credencial,
            @Value("${sanciones.salidas.timeout-conexion-ms:2000}") long conexionMs,
            @Value("${sanciones.salidas.timeout-lectura-ms:5000}") long lecturaMs) {
        if (credencial.getIfAvailable() == null) {
            BITACORA.warn("Sin credencial de servicio (DIRECTORIO_ACTIVO_CLIENT_ID vacio): notificaciones, "
                    + "ms-identidad y correo rechazaran las salidas de las sanciones, que quedan pendientes");
        }
        return clienteDeSalidas(credencial, Duration.ofMillis(conexionMs), Duration.ofMillis(lecturaMs));
    }

    @Bean
    public ClienteNotificaciones clienteNotificaciones(RestClient restClientDeSalidas,
                                                       @Value("${sanciones.notificaciones.url}") String url) {
        return new ClienteNotificaciones(restClientDeSalidas, url);
    }

    @Bean
    public ClienteIdentidad clienteIdentidad(RestClient restClientDeSalidas,
                                             @Value("${sanciones.identidad.url}") String url) {
        return new ClienteIdentidad(restClientDeSalidas, url);
    }

    @Bean
    public ClienteCorreo clienteCorreo(RestClient restClientDeSalidas,
                                       @Value("${sanciones.correo.url}") String url) {
        return new ClienteCorreo(restClientDeSalidas, url);
    }

    @Bean
    public ProyeccionEnIdentidad proyeccionEnIdentidad(ClienteIdentidad identidad, SancionesService sanciones,
                                                       SancionRepository repositorio) {
        return new ProyeccionEnIdentidad(identidad, sanciones, repositorio);
    }

    @Bean
    public CorreoDeSancion correoDeSancion(ClienteIdentidad identidad, ClienteCorreo correo,
                                           SancionRepository sanciones, ApelacionRepository apelaciones,
                                           LimitesDeSancion limites) {
        return new CorreoDeSancion(identidad, correo, sanciones, apelaciones, limites);
    }

    /**
     * El entregador: la espera base es el intervalo de siempre
     * ({@code sanciones.avisos.reintento-ms}, 15 s) y crece con cada fallo
     * hasta {@code sanciones.salidas.espera-maxima-ms} (15 min por omision).
     */
    @Bean
    public EntregadorDeSalidas entregadorDeSalidas(
            SalidaPendienteRepository salidas, List<DestinoDeSalidas> destinos, Clock reloj,
            @Value("${sanciones.avisos.reintento-ms:15000}") long reintentoMs,
            @Value("${sanciones.salidas.espera-maxima-ms:900000}") long esperaMaximaMs) {
        return new EntregadorDeSalidas(salidas, destinos, reloj, Duration.ofMillis(reintentoMs),
                Duration.ofMillis(Math.max(reintentoMs, esperaMaximaMs)));
    }
}
