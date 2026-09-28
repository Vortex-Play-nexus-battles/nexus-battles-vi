package com.nexusbattles.ms_finanzas.partidas;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;

/**
 * Cableado de los cofres (cofres.yaml 1.1.0, B7). Toda la configuración baja
 * de variables de entorno con un valor por omisión seguro (regla 10).
 *
 * <pre>
 * FINANZAS_COFRES_ZONA               zona de la semana ISO (America/Bogota)
 * FINANZAS_COFRES_CONSERVAR_SOBRANTE conservar lo que pasa de 20 (true, D-B7-17)
 * FINANZAS_COFRES_TABLA              tabla del contenido, productoId=peso;...
 *                                    (vacia: la provisional de desarrollo)
 * FINANZAS_COFRES_TABLA_VERSION      su version (PROVISIONAL-DEV-1)
 * FINANZAS_COFRES_REINTENTO_MS       cada cuanto se reintentan entregas (60000)
 * FINANZAS_COFRES_ESPERA_MAXIMA_MINUTOS espera maxima entre reintentos (30)
 * INVENTARIO_BASE_URL                inventario (vacia: los cofres quedan PENDIENTES)
 * DIRECTORIO_ACTIVO_*                credencial de servicio para inventario (ADR-005)
 * </pre>
 */
@Configuration
@EnableScheduling
public class ConfiguracionDeCofres {

    private static final Logger BITACORA = LoggerFactory.getLogger(ConfiguracionDeCofres.class);

    /** La tabla provisional de desarrollo que viaja con el servicio (D-B7-18). */
    static final String TABLA_PROVISIONAL = "cofres/tabla-provisional-dev-1.txt";
    static final String VERSION_PROVISIONAL = "PROVISIONAL-DEV-1";

    @Bean
    public ReglasDeCofres reglasDeCofres(
            @Value("${finanzas.cofres.zona:America/Bogota}") String zona,
            @Value("${finanzas.cofres.conservar-sobrante:true}") boolean conservarSobrante,
            @Value("${finanzas.cofres.tabla:}") String tabla,
            @Value("${finanzas.cofres.tabla-version:" + VERSION_PROVISIONAL + "}") String version,
            @Value("${finanzas.cofres.espera-maxima-minutos:30}") int esperaMaximaMinutos) {
        TablaDeCofre laTabla = tablaDe(tabla, version);
        if (laTabla.esProvisional()) {
            BITACORA.warn("El contenido de los cofres sale de la tabla PROVISIONAL DE DESARROLLO {} ({} premios): "
                    + "la definitiva es decision del Product Owner (D-B7-18)", laTabla.version(),
                    laTabla.entradas().size());
        }
        return new ReglasDeCofres(ZoneId.of(zona), conservarSobrante, laTabla, esperaMaximaMinutos);
    }

    /**
     * La tabla configurada o, si no hay, la provisional del classpath. Una
     * tabla propia con la version provisional se rechaza al arrancar: cada
     * cofre guarda la version con la que se sorteo y dos tablas distintas no
     * pueden llamarse igual.
     */
    static TablaDeCofre tablaDe(String tabla, String version) {
        if (tabla == null || tabla.isBlank()) {
            return TablaDeCofre.desde(VERSION_PROVISIONAL, leer(TABLA_PROVISIONAL));
        }
        if (VERSION_PROVISIONAL.equals(version)) {
            throw new IllegalStateException("FINANZAS_COFRES_TABLA viene sin su propia FINANZAS_COFRES_TABLA_VERSION: "
                    + "los cofres guardan la version con la que se sortearon");
        }
        return TablaDeCofre.desde(version, tabla);
    }

    private static String leer(String recurso) {
        try (InputStream entrada = new ClassPathResource(recurso).getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException noEsta) {
            throw new IllegalStateException("No se encontro la tabla provisional del cofre " + recurso, noEsta);
        }
    }

    @Bean
    public CofreService cofreService(ContadorDeCofresRepository contadores, CofreEntregadoRepository cofres,
                                     ReglasDeCofres reglas, Clock reloj) {
        return new CofreService(contadores, cofres, reglas, reloj, new SecureRandom());
    }

    /**
     * Cliente de inventario con la credencial de servicio de ms-finanzas
     * (cuando esta configurada), la traza propagada (regla 5) y tiempos de
     * espera acotados.
     */
    @Bean
    public InventarioDeCofres inventarioDeCofres(
            @Value("${finanzas.cofres.inventario-url:}") String urlInventario,
            @Value("${finanzas.cofres.tiempo-conexion-ms:2000}") long tiempoConexion,
            @Value("${finanzas.cofres.tiempo-respuesta-ms:5000}") long tiempoRespuesta,
            ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(tiempoConexion));
        fabrica.setReadTimeout(Duration.ofMillis(tiempoRespuesta));
        RestClient.Builder constructor = RestClient.builder()
                .requestFactory(fabrica)
                .requestInterceptor(new InterceptorDeTraza());
        credencial.ifAvailable(constructor::requestInterceptor);
        if (urlInventario == null || urlInventario.isBlank()) {
            BITACORA.warn("INVENTARIO_BASE_URL no esta configurada: los cofres se ganan pero quedan PENDIENTES "
                    + "de entrega hasta que lo este");
        }
        return new ClienteInventarioDeCofres(constructor.build(), urlInventario);
    }

    /**
     * Hilos virtuales para la entrega que se lanza al confirmar. No es un bean
     * a proposito: un {@code Executor} en el contexto haria que Spring Boot no
     * creara su {@code applicationTaskExecutor}, y eso no es decision de los
     * cofres. Al apagar se interrumpe lo que este en vuelo: esos cofres quedan
     * PENDIENTES y los entrega el reintento.
     */
    private final ExecutorService ejecutorDeEntregas = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    void cerrarEjecutorDeEntregas() {
        ejecutorDeEntregas.shutdownNow();
    }

    @Bean
    public EntregaDeCofres entregaDeCofres(CofreEntregadoRepository cofres, InventarioDeCofres inventario,
                                           ReglasDeCofres reglas, Clock reloj) {
        return new EntregaDeCofres(cofres, inventario, reglas, reloj, ejecutorDeEntregas);
    }

    @Bean
    public ReintentoDeEntregas reintentoDeEntregas(EntregaDeCofres entregas) {
        return new ReintentoDeEntregas(entregas);
    }

    /** Reintenta las entregas pendientes cada {@code finanzas.cofres.reintento-ms}. */
    public static class ReintentoDeEntregas {

        private final EntregaDeCofres entregas;

        ReintentoDeEntregas(EntregaDeCofres entregas) {
            this.entregas = entregas;
        }

        @Scheduled(fixedDelayString = "${finanzas.cofres.reintento-ms:60000}",
                   initialDelayString = "${finanzas.cofres.reintento-ms:60000}")
        public void reintentar() {
            try {
                entregas.reintentarPendientes();
            } catch (RuntimeException fallo) {
                BITACORA.warn("No se pudieron reintentar las entregas de cofres pendientes: {}", fallo.getMessage());
            }
        }
    }
}
