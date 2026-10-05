package nexus.misiones.configuracion;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import nexus.misiones.aplicacion.AvisosDeMisiones;
import nexus.misiones.aplicacion.CatalogoDeProductos;
import nexus.misiones.aplicacion.CorreoDeMisiones;
import nexus.misiones.aplicacion.DirectorioDeJugadores;
import nexus.misiones.aplicacion.InventarioDeHeroes;
import nexus.misiones.aplicacion.LibroDeCreditos;
import nexus.misiones.aplicacion.ServicioDeHeroes;
import nexus.misiones.dominio.simulacion.MotorDeCombate;
import nexus.misiones.integracion.ClienteCorreo;
import nexus.misiones.integracion.ClienteCreditos;
import nexus.misiones.integracion.ClienteHeroes;
import nexus.misiones.integracion.ClienteIdentidad;
import nexus.misiones.integracion.ClienteInventario;
import nexus.misiones.integracion.ClienteMotor;
import nexus.misiones.integracion.ClienteNotificaciones;
import nexus.misiones.integracion.ClienteProductos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Las ocho dependencias de misiones, cada una con su cliente, su corta
 * circuitos (HU-DIS-003) y los mismos tiempos de espera acotados.
 *
 * <p>Todas por REST sincrono y con la credencial de servicio de misiones
 * (ADR-001 via el emisor de ADR-005) cuando esta configurada: sin bus de
 * mensajes (no hay ninguno desplegado) y sin importar clases de otros
 * servicios. Todas propagan el trace id (regla 5).
 *
 * <p><b>Dependencia y seccion.</b> La dependencia es el nombre interno, para la
 * bitacora y el panel de degradaciones; la seccion es lo que lee el jugador en
 * el aviso de seccion limitada.
 */
@Configuration
public class ConfiguracionDeIntegraciones {

    @Bean
    public Umbrales umbralesDeMisiones(
            @Value("${resiliencia.fallos-para-abrir:3}") int fallosParaAbrir,
            @Value("${resiliencia.reintentar-en-segundos:30}") long reintentarEnSegundos) {
        return new Umbrales(fallosParaAbrir, Duration.ofSeconds(reintentarEnSegundos));
    }

    /**
     * Conexion corta y respuesta con margen, muy por debajo de los 60 s del
     * borde, para que quien conteste sea siempre este servicio con su problem
     * detail. HTTP/1.1 explicito: los servicios de la red de compose no hablan
     * HTTP/2 en claro y el intento de mejora solo anade una ida y vuelta.
     */
    @Bean
    public ClientHttpRequestFactory fabricaDePeticionesConTiempos(
            @Value("${resiliencia.tiempo-conexion-ms:2000}") long conexionMs,
            @Value("${resiliencia.tiempo-respuesta-ms:10000}") long respuestaMs) {
        HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(conexionMs))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofMillis(respuestaMs));
        return fabrica;
    }

    /** Un solo cliente HTTP para las ocho: traza, tiempos y credencial de servicio. */
    @Bean
    public RestClient restClientDeMisiones(ClientHttpRequestFactory fabrica,
                                           ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        RestClient.Builder constructor = RestClient.builder()
                .requestFactory(fabrica)
                .requestInterceptor(new InterceptorDeTraza());
        credencial.ifAvailable(constructor::requestInterceptor);
        return constructor.build();
    }

    @Bean
    public InventarioDeHeroes inventarioDeHeroes(RestClient restClientDeMisiones,
                                                 @Value("${misiones.inventario.url}") String url,
                                                 Umbrales umbrales, RegistroDeDegradacion registro) {
        return new ClienteInventario(restClientDeMisiones, url,
                umbrales.para("inventario", "Inventario", registro),
                umbrales.para("inventario-entregas", "Entrega de recompensas", registro));
    }

    @Bean
    public CatalogoDeProductos catalogoDeProductos(RestClient restClientDeMisiones,
                                                   @Value("${misiones.productos.url}") String url,
                                                   Umbrales umbrales, RegistroDeDegradacion registro) {
        return new ClienteProductos(restClientDeMisiones, url,
                umbrales.para("productos", "Catálogo de productos", registro));
    }

    @Bean
    public ServicioDeHeroes servicioDeHeroes(RestClient restClientDeMisiones,
                                             @Value("${misiones.heroes.url}") String url,
                                             Umbrales umbrales, RegistroDeDegradacion registro) {
        return new ClienteHeroes(restClientDeMisiones, url, umbrales.para("heroes", "Reglas de los héroes", registro));
    }

    @Bean
    public MotorDeCombate motorDeCombate(RestClient restClientDeMisiones,
                                               @Value("${misiones.motor.url}") String url,
                                               Umbrales umbrales, RegistroDeDegradacion registro) {
        return new ClienteMotor(restClientDeMisiones, url, umbrales.para("motor-combate", "Motor de combate", registro));
    }

    @Bean
    public LibroDeCreditos libroDeCreditos(RestClient restClientDeMisiones,
                                           @Value("${misiones.creditos.url}") String url,
                                           Umbrales umbrales, RegistroDeDegradacion registro) {
        return new ClienteCreditos(restClientDeMisiones, url, umbrales.para("ms-finanzas", "Créditos", registro));
    }

    @Bean
    public CorreoDeMisiones correoDeMisiones(RestClient restClientDeMisiones,
                                             @Value("${misiones.correo.url}") String url,
                                             Umbrales umbrales, RegistroDeDegradacion registro) {
        return new ClienteCorreo(restClientDeMisiones, url, umbrales.para("correo", "Correo", registro));
    }

    /** La bandeja del jugador (notificaciones.yaml 1.2.0): avisos de misiones, RF-NOT-004. */
    @Bean
    public AvisosDeMisiones avisosDeMisiones(RestClient restClientDeMisiones,
                                            @Value("${misiones.notificaciones.url}") String url,
                                            Umbrales umbrales, RegistroDeDegradacion registro) {
        return new ClienteNotificaciones(restClientDeMisiones, url,
                umbrales.para("notificaciones", "Notificaciones", registro));
    }

    @Bean
    public DirectorioDeJugadores directorioDeJugadores(RestClient restClientDeMisiones,
                                                       @Value("${misiones.identidad.url}") String url,
                                                       Umbrales umbrales, RegistroDeDegradacion registro) {
        return new ClienteIdentidad(restClientDeMisiones, url, umbrales.para("ms-identidad", "Cuentas", registro));
    }

    /**
     * Los mismos umbrales para todas las dependencias. {@code reintentar-en-segundos}
     * es a la vez lo que dura abierto el circuito y lo que se promete en
     * {@code Retry-After}.
     */
    public record Umbrales(int fallosParaAbrir, Duration esperaAntesDeReintentar) {

        CortaCircuitos para(String dependencia, String seccion, RegistroDeDegradacion registro) {
            return new CortaCircuitos(dependencia, seccion, fallosParaAbrir, esperaAntesDeReintentar,
                    Clock.systemUTC(), registro);
        }
    }
}
