package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.ConfiguracionDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.disponibilidad.MonitorDeDisponibilidad;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.ClienteDeModeracion;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.ClienteDeReportes;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.ClienteDeUsuarios;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.FuenteDeModeracion;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.FuenteDeReportes;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.FuenteDeUsuarios;
import com.nexusbattles.plataforma.metricasplataforma.moderacion.UmbralesDeModeracion;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ConfiguracionDelSondeo;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.ResultadoReciente;
import com.nexusbattles.plataforma.metricasplataforma.sondeo.RondaEnParalelo;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.nexusbattles.plataforma.observabilidad.PropiedadesDeLatencia;
import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/**
 * Recolector de Actuator, tablero en vivo y fuentes del tablero de moderacion (sanciones, cuentas, reportes y sus
 * umbrales), con tiempos cortos: un tablero no espera a nadie.
 */
@Configuration
public class ConfiguracionDeMetricasTecnicas {

    /**
     * Cliente con plazos y con la traza de la peticion (regla 5): este servicio se arma sus clientes con
     * {@code RestClient.builder()}, asi que el interceptor no se engancha solo y se pone aqui, una vez para todos.
     */
    private static RestClient conTiempos(Duration conexion, Duration respuesta) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(conexion);
        fabrica.setReadTimeout(respuesta);
        return RestClient.builder().requestFactory(fabrica).requestInterceptor(new InterceptorDeTraza()).build();
    }

    /**
     * Las lecturas de Actuator del tablero usan los plazos del sondeo
     * interactivo (RFINAL-08, {@code sondeo.*}): es una peticion de pantalla.
     */
    @Bean
    RecolectorDeMetricas recolectorDeMetricas(ConfiguracionDelSondeo sondeo) {
        return new RecolectorDeActuator(conTiempos(sondeo.plazoDeConexion(), sondeo.plazoDeRespuesta()));
    }

    @Bean
    TableroEnVivo tableroEnVivo(ConfiguracionDeDisponibilidad configuracion, RecolectorDeMetricas recolector,
                                MonitorDeDisponibilidad monitor, PropiedadesDeLatencia latencia,
                                RondaEnParalelo ronda, ConfiguracionDelSondeo sondeo, Clock reloj) {
        return new TableroEnVivo(configuracion, recolector, monitor, latencia, ronda,
                new ResultadoReciente<>(sondeo.vigencia()), reloj);
    }

    /** Sin cambios en RFINAL-08: es una sola llamada y no forma parte de la ronda. */
    @Bean
    FuenteDeModeracion fuenteDeModeracion(@Value("${metricas.moderacion.url}") String url) {
        return new ClienteDeModeracion(conTiempos(Duration.ofMillis(2000), Duration.ofMillis(3000)), url);
    }

    /** Cuentas por estado y altas por dia (HU-MET-001): una sola llamada a ms-identidad, con el token del administrador. */
    @Bean
    FuenteDeUsuarios fuenteDeUsuarios(@Value("${metricas.usuarios.url}") String url) {
        return new ClienteDeUsuarios(conTiempos(Duration.ofMillis(2000), Duration.ofMillis(3000)), url);
    }

    /**
     * Frecuencia de reportes (§7.3.8, comentarios 1.15.0): una sola llamada a comentarios, con el token de quien mira
     * el tablero y los mismos plazos que la de identidad.
     */
    @Bean
    FuenteDeReportes fuenteDeReportes(@Value("${metricas.reportes.url}") String url) {
        return new ClienteDeReportes(conTiempos(Duration.ofMillis(2000), Duration.ofMillis(3000)), url);
    }

    /**
     * Umbrales de alta frecuencia (D-25): del catalogo de admin-parametros, con la variable de entorno de respaldo.
     * Sin {@code PARAMETROS_URL} (local, pruebas) no se hace ni una peticion y todo sale del respaldo. La cache corta
     * hace que un cambio desde el panel se note en el tablero en menos de un minuto sin preguntar en cada apertura.
     */
    @Bean
    UmbralesDeModeracion umbralesDeModeracion(
            @Value("${metricas.parametros.url:}") String catalogo,
            @Value("${metricas.parametros.vigencia-segundos:30}") long vigencia,
            @Value("${metricas.moderacion.umbral-sanciones-por-dia:#{null}}") Integer respaldoSanciones,
            @Value("${metricas.moderacion.umbral-reportes-por-dia:#{null}}") Integer respaldoReportes,
            Clock reloj) {
        LectorDeParametros lector = LectorDeParametros.desde(
                conTiempos(Duration.ofMillis(1000), Duration.ofMillis(2000)), catalogo, reloj,
                Duration.ofSeconds(vigencia));
        return new UmbralesDeModeracion(lector, respaldoSanciones, respaldoReportes);
    }
}
