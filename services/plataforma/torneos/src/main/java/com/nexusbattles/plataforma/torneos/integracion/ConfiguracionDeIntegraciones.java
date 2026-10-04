package com.nexusbattles.plataforma.torneos.integracion;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.nexusbattles.plataforma.torneos.torneo.AvisosAlJugador;
import com.nexusbattles.plataforma.torneos.torneo.ConsultaDeSanciones;
import com.nexusbattles.plataforma.torneos.torneo.EntregaDeInventario;
import com.nexusbattles.plataforma.torneos.torneo.FiltroDeNombres;
import com.nexusbattles.plataforma.torneos.torneo.LibroDeCreditos;
import com.nexusbattles.plataforma.torneos.torneo.PoliticaDePremio;
import com.nexusbattles.plataforma.torneos.torneo.PoliticaDeReintentos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/**
 * Los puertos de salida sobre RestClient, todos con:
 *
 * <ul>
 *   <li><b>Tiempos de espera</b> de conexion y de lectura
 *       ({@code torneos.http.*}). Sin ellos, un proveedor colgado dejaba el
 *       hilo esperando para siempre.</li>
 *   <li><b>Propagacion de la traza</b> (regla 5): {@link InterceptorDeTraza}
 *       solo se engancha solo a los constructores que arma Spring Boot; este
 *       servicio arma los suyos, asi que lo anade aqui.</li>
 *   <li><b>Credencial de servicio</b> (ADR-005) cuando esta configurada, salvo
 *       la lista negra, que es publica dentro de la red.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
public class ConfiguracionDeIntegraciones {

    @Bean
    public Clock relojDeTorneos() {
        return Clock.systemUTC();
    }

    /**
     * El premio del campeon (RF-TOR-007). Los valores por omision de
     * {@code application.yml} son PROVISIONALES (D-24): el documento no fija
     * ni el monto ni la epica.
     */
    @Bean
    public PoliticaDePremio politicaDePremio(@Value("${torneos.premio.creditos-por-integrante}") int creditos,
                                             @Value("${torneos.premio.epica-producto-id:}") String epica) {
        return new PoliticaDePremio(creditos, epica);
    }

    @Bean
    public PoliticaDeReintentos politicaDeReintentos(
            @Value("${torneos.operaciones.espera-base-s:15}") long base,
            @Value("${torneos.operaciones.espera-maxima-s:1800}") long maxima,
            @Value("${torneos.operaciones.intentos-maximos:12}") int intentos,
            @Value("${torneos.operaciones.plazo-bloqueo-s:120}") long plazo) {
        return new PoliticaDeReintentos(Duration.ofSeconds(base), Duration.ofSeconds(maxima), intentos,
                Duration.ofSeconds(plazo));
    }

    @Bean
    public SimpleClientHttpRequestFactory fabricaDePeticiones(
            @Value("${torneos.http.timeout-conexion-ms:2000}") int conexion,
            @Value("${torneos.http.timeout-lectura-ms:5000}") int lectura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(conexion));
        fabrica.setReadTimeout(Duration.ofMillis(lectura));
        return fabrica;
    }

    @Bean
    public LibroDeCreditos libroDeCreditos(@Value("${torneos.creditos.url}") String url,
                                           SimpleClientHttpRequestFactory fabrica,
                                           ObjectProvider<InterceptorDeTraza> traza,
                                           ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        return new ClienteCreditos(cliente(fabrica, traza, credencial), url);
    }

    @Bean
    public FiltroDeNombres filtroDeNombres(@Value("${torneos.lista-negra.url}") String url,
                                           SimpleClientHttpRequestFactory fabrica,
                                           ObjectProvider<InterceptorDeTraza> traza) {
        return new ClienteListaNegra(cliente(fabrica, traza, null), url);
    }

    @Bean
    public ConsultaDeSanciones consultaDeSanciones(@Value("${torneos.sanciones.url}") String url,
                                                  SimpleClientHttpRequestFactory fabrica,
                                                  ObjectProvider<InterceptorDeTraza> traza,
                                                  ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        return new ClienteSanciones(cliente(fabrica, traza, credencial), url);
    }

    @Bean
    public EntregaDeInventario entregaDeInventario(@Value("${torneos.inventario.url}") String url,
                                                   SimpleClientHttpRequestFactory fabrica,
                                                   ObjectProvider<InterceptorDeTraza> traza,
                                                   ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        return new ClienteInventario(cliente(fabrica, traza, credencial), url);
    }

    @Bean
    public AvisosAlJugador avisosAlJugador(@Value("${torneos.notificaciones.url}") String notificaciones,
                                           @Value("${torneos.identidad.url:}") String identidad,
                                           @Value("${torneos.correo.url:}") String correo,
                                           SimpleClientHttpRequestFactory fabrica,
                                           ObjectProvider<InterceptorDeTraza> traza,
                                           ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        return new ClienteAvisos(cliente(fabrica, traza, credencial), notificaciones, identidad, correo);
    }

    private static RestClient cliente(SimpleClientHttpRequestFactory fabrica, ObjectProvider<InterceptorDeTraza> traza,
                                      ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        RestClient.Builder constructor = RestClient.builder().requestFactory(fabrica);
        traza.ifAvailable(constructor::requestInterceptor);
        if (credencial != null) {
            credencial.ifAvailable(constructor::requestInterceptor);
        }
        return constructor.build();
    }
}
