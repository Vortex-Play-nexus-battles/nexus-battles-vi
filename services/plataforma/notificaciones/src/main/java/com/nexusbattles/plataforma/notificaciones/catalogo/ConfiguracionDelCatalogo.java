package com.nexusbattles.plataforma.notificaciones.catalogo;

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

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.notificaciones.bandeja.AvisosPorIncorporar;
import com.nexusbattles.plataforma.notificaciones.bandeja.ServicioDeNotificaciones;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;

/**
 * Enciende la importacion de avisos del catalogo (HU-NOT-001) cuando se
 * puede: con la credencial de servicio de notificaciones (productos la exige,
 * ADR-005) y una configuracion que productos acepte. En cualquier otro caso
 * queda apagada, se dice en la bitacora y la bandeja funciona como antes: una
 * variable mal puesta no deja a notificaciones sin arrancar.
 *
 * <p>Todo baja de variable de entorno (regla 10), con estos valores de
 * desarrollo local, que son constantes tecnicas y no reglas de negocio:
 *
 * <pre>
 * NOTIFICACIONES_CATALOGO_ACTIVO               true   interruptor de operacion
 * PRODUCTOS_BASE_URL                           http://localhost:8103 (sin /api/v1)
 * NOTIFICACIONES_CATALOGO_TIMEOUT_CONEXION_MS  1000
 * NOTIFICACIONES_CATALOGO_TIMEOUT_LECTURA_MS   2000   la llamada va dentro de una peticion interactiva
 * NOTIFICACIONES_CATALOGO_LIMITE               50     cambios por lote (productos admite 1..200)
 * NOTIFICACIONES_CATALOGO_INTERVALO_MINIMO_S   60     segundos entre consultas por jugador
 * NOTIFICACIONES_CATALOGO_ZONA                 America/Bogota  para la fecha legible del aviso
 * </pre>
 */
@Configuration
public class ConfiguracionDelCatalogo {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionDelCatalogo.class);

    /** El rango que admite {@code limite} en productos.yaml 1.6.0. */
    static final int LIMITE_MINIMO = 1;
    static final int LIMITE_MAXIMO = 200;

    @Bean
    public AvisosPorIncorporar avisosDelCatalogo(
            @Value("${notificaciones.catalogo.activo:true}") boolean activo,
            @Value("${notificaciones.catalogo.productos-url:http://localhost:8103}") String productosUrl,
            @Value("${notificaciones.catalogo.timeout-conexion-ms:1000}") int conexionMs,
            @Value("${notificaciones.catalogo.timeout-lectura-ms:2000}") int lecturaMs,
            @Value("${notificaciones.catalogo.limite:50}") int limite,
            @Value("${notificaciones.catalogo.intervalo-minimo-s:60}") long intervaloMinimoS,
            @Value("${notificaciones.catalogo.zona:America/Bogota}") String zona,
            ObjectProvider<InterceptorDeTraza> traza,
            ObjectProvider<InterceptorDePortadorDeServicio> credencial,
            ObjectProvider<Clock> reloj,
            CursorCatalogoRepository cursores,
            ServicioDeNotificaciones servicio) {

        if (!activo) {
            log.info("Avisos del catalogo apagados por configuracion (NOTIFICACIONES_CATALOGO_ACTIVO=false)");
            return AvisosPorIncorporar.ninguno();
        }
        InterceptorDePortadorDeServicio portador = credencial.getIfAvailable();
        if (portador == null) {
            log.warn("Avisos del catalogo apagados: notificaciones no tiene credencial de servicio "
                    + "(DIRECTORIO_ACTIVO_CLIENT_ID) y productos la exige");
            return AvisosPorIncorporar.ninguno();
        }
        ZoneId zonaDeLaFecha;
        try {
            zonaDeLaFecha = ZoneId.of(zona);
        } catch (DateTimeException zonaDesconocida) {
            log.warn("Avisos del catalogo apagados: zona horaria desconocida en NOTIFICACIONES_CATALOGO_ZONA");
            return AvisosPorIncorporar.ninguno();
        }
        if (limite < LIMITE_MINIMO || limite > LIMITE_MAXIMO || intervaloMinimoS < 0) {
            log.warn("Avisos del catalogo apagados: NOTIFICACIONES_CATALOGO_LIMITE debe estar entre {} y {} "
                    + "y NOTIFICACIONES_CATALOGO_INTERVALO_MINIMO_S no puede ser negativo", LIMITE_MINIMO, LIMITE_MAXIMO);
            return AvisosPorIncorporar.ninguno();
        }

        return new ImportadorDeAvisosDelCatalogo(
                cliente(productosUrl, conexionMs, lecturaMs, limite, traza.getIfAvailable(), portador),
                cursores,
                servicio,
                reloj.getIfUnique(Clock::systemUTC),
                Duration.ofSeconds(intervaloMinimoS),
                zonaDeLaFecha);
    }

    /**
     * El cliente de productos con sus tiempos de espera, la traza (regla 5:
     * {@link InterceptorDeTraza} solo se engancha solo a los constructores
     * que arma Spring Boot, y este se arma aqui) y la credencial de servicio.
     */
    static ClienteDeCambiosDelCatalogo cliente(String productosUrl, int conexionMs, int lecturaMs, int limite,
                                               InterceptorDeTraza traza,
                                               InterceptorDePortadorDeServicio credencial) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(conexionMs));
        fabrica.setReadTimeout(Duration.ofMillis(lecturaMs));
        RestClient.Builder constructor = RestClient.builder().requestFactory(fabrica);
        if (traza != null) {
            constructor.requestInterceptor(traza);
        }
        if (credencial != null) {
            constructor.requestInterceptor(credencial);
        }
        return new ClienteDeCambiosDelCatalogo(constructor.build(), productosUrl, limite);
    }
}
