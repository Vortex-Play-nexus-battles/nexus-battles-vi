package com.nexusbattles.plataforma.notificaciones.politicas;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.nexusbattles.plataforma.notificaciones.bandeja.AvisosPorIncorporar;
import com.nexusbattles.plataforma.notificaciones.bandeja.ServicioDeNotificaciones;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;

/**
 * Enciende los avisos de cambios en las politicas de uso (SA-11, §7.3.7)
 * cuando la configuracion es valida; si no, quedan apagados, se dice en la
 * bitacora y la bandeja funciona como antes. Todo baja de variable de entorno
 * (regla 10), con estos valores de desarrollo local, constantes tecnicas y no
 * reglas de negocio:
 *
 * <pre>
 * NOTIFICACIONES_POLITICAS_ACTIVO               true   interruptor de operacion
 * PARAMETROS_URL                                http://localhost:8088/api/v1 (con /api/v1, como en el resto)
 * NOTIFICACIONES_POLITICAS_TIMEOUT_CONEXION_MS  1000
 * NOTIFICACIONES_POLITICAS_TIMEOUT_LECTURA_MS   2000   la llamada va dentro de una peticion interactiva
 * NOTIFICACIONES_POLITICAS_LIMITE               50     cambios por lote (admin-parametros admite 1..100)
 * NOTIFICACIONES_POLITICAS_INTERVALO_MINIMO_S   60     segundos entre consultas por jugador
 * NOTIFICACIONES_POLITICAS_VENTANA_DIAS         30     lo publicado que alcanza a quien aun no tiene cursor
 * NOTIFICACIONES_POLITICAS_ZONA                 America/Bogota  para la fecha legible del aviso
 * </pre>
 */
@Configuration
public class ConfiguracionDePoliticas {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionDePoliticas.class);

    /** El rango que admite {@code limite} en admin-parametros.yaml 1.1.0. */
    static final int LIMITE_MINIMO = 1;
    static final int LIMITE_MAXIMO = 100;

    @Bean
    public AvisosPorIncorporar avisosDePoliticas(
            @Value("${notificaciones.politicas.activo:true}") boolean activo,
            @Value("${notificaciones.politicas.parametros-url:http://localhost:8088/api/v1}") String parametrosUrl,
            @Value("${notificaciones.politicas.timeout-conexion-ms:1000}") int conexionMs,
            @Value("${notificaciones.politicas.timeout-lectura-ms:2000}") int lecturaMs,
            @Value("${notificaciones.politicas.limite:50}") int limite,
            @Value("${notificaciones.politicas.intervalo-minimo-s:60}") long intervaloMinimoS,
            @Value("${notificaciones.politicas.ventana-dias:30}") long ventanaDias,
            @Value("${notificaciones.politicas.zona:America/Bogota}") String zona,
            ObjectProvider<InterceptorDeTraza> traza,
            ObjectProvider<Clock> reloj,
            CursorPoliticasRepository cursores,
            ServicioDeNotificaciones servicio) {

        if (!activo) {
            log.info("Avisos de cambios en las politicas apagados por configuracion (NOTIFICACIONES_POLITICAS_ACTIVO=false)");
            return AvisosPorIncorporar.ninguno();
        }
        ZoneId zonaDeLaFecha;
        try {
            zonaDeLaFecha = ZoneId.of(zona);
        } catch (DateTimeException zonaDesconocida) {
            log.warn("Avisos de cambios en las politicas apagados: zona horaria desconocida en "
                    + "NOTIFICACIONES_POLITICAS_ZONA");
            return AvisosPorIncorporar.ninguno();
        }
        if (limite < LIMITE_MINIMO || limite > LIMITE_MAXIMO || intervaloMinimoS < 0 || ventanaDias < 1) {
            log.warn("Avisos de cambios en las politicas apagados: NOTIFICACIONES_POLITICAS_LIMITE debe estar entre "
                    + "{} y {}, el intervalo no puede ser negativo y la ventana es de al menos un dia",
                    LIMITE_MINIMO, LIMITE_MAXIMO);
            return AvisosPorIncorporar.ninguno();
        }
        if (parametrosUrl == null || parametrosUrl.isBlank()) {
            log.warn("Avisos de cambios en las politicas apagados: falta PARAMETROS_URL");
            return AvisosPorIncorporar.ninguno();
        }

        return new ImportadorDeCambiosDePolitica(
                cliente(parametrosUrl, conexionMs, lecturaMs, limite, traza.getIfAvailable()),
                cursores,
                servicio,
                reloj.getIfUnique(Clock::systemUTC),
                Duration.ofSeconds(intervaloMinimoS),
                Duration.ofDays(ventanaDias),
                zonaDeLaFecha);
    }

    /** El cliente de admin-parametros con sus tiempos de espera y la traza (regla 5). */
    static ClienteDeCambiosDePolitica cliente(String parametrosUrl, int conexionMs, int lecturaMs, int limite,
                                              InterceptorDeTraza traza) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(conexionMs));
        fabrica.setReadTimeout(Duration.ofMillis(lecturaMs));
        RestClient.Builder constructor = RestClient.builder().requestFactory(fabrica);
        if (traza != null) {
            constructor.requestInterceptor(traza);
        }
        return new ClienteDeCambiosDePolitica(constructor.build(), parametrosUrl.strip(), limite);
    }
}
