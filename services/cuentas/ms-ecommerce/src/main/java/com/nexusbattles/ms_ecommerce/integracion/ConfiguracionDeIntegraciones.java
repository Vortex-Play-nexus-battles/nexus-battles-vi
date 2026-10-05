package com.nexusbattles.ms_ecommerce.integracion;

import com.nexusbattles.ms_ecommerce.catalogo.PropiedadesDelCatalogo;
import com.nexusbattles.ms_ecommerce.seguridad.CredencialDeServicio;
import com.nexusbattles.ms_ecommerce.seguridad.CredencialPorClientCredentials;
import com.nexusbattles.ms_ecommerce.seguridad.InterceptorDeCredencial;
import com.nexusbattles.ms_ecommerce.seguridad.SinCredencialDeServicio;
import com.nexusbattles.ms_ecommerce.traza.InterceptorDeTraza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Los clientes HTTP con los que la compra habla con los demas servicios (B5).
 *
 * <p>Todos salen del mismo constructor ({@link #constructor}): los tiempos de
 * espera de {@code tienda.http.*} y la traza en cada peticion (regla 5). Los
 * que actuan en nombre de la tienda —reservar tiraje, entregar, registrar el
 * pago, pedir el contacto del jugador, enviar el correo, consultar el
 * inventario de un jugador— llevan ademas su credencial de servicio (ADR-005).
 * admin-parametros no: su lectura de valores es publica.
 *
 * <p>Ninguno reintenta solo. Cada operacion que se repite lleva su clave de
 * idempotencia y la repite la orden, con la misma clave, cuando le toca: un
 * reintento automatico dentro del cliente repetiria una operacion sin que la
 * orden lo supiera.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesDeLaTienda.class)
public class ConfiguracionDeIntegraciones {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionDeIntegraciones.class);

    /*
     * Nombres de los RestClient: distintos de los de los componentes que los
     * usan (ClienteDeInventario es el bean "clienteDeInventario").
     */
    public static final String RESERVAS = "httpDeReservasDeTiraje";
    public static final String INVENTARIO = "httpDeInventario";
    public static final String FINANZAS = "httpDeFinanzas";
    public static final String CORREO = "httpDeCorreo";
    public static final String IDENTIDAD = "httpDeIdentidad";
    public static final String PARAMETROS = "httpDeParametros";

    @Bean
    CredencialDeServicio credencialDeServicio(PropiedadesDeLaTienda propiedades, Clock reloj) {
        PropiedadesDeLaTienda.Credencial credencial = propiedades.credencial();
        if (credencial.clientId() == null || credencial.clientId().isBlank()) {
            log.warn("La tienda arranca sin credencial de servicio (DIRECTORIO_ACTIVO_CLIENT_ID vacio): "
                    + "la vitrina y el carrito funcionan, la compra responde 503 hasta que se configure.");
            return new SinCredencialDeServicio();
        }
        RestClient cliente = constructor("", propiedades.http()).build();
        return new CredencialPorClientCredentials(cliente, credencial.url(), credencial.clientId(),
                credencial.clientSecret(), reloj);
    }

    @Bean(RESERVAS)
    RestClient clienteDeReservasDeTiraje(PropiedadesDelCatalogo catalogo, PropiedadesDeLaTienda propiedades,
                                         CredencialDeServicio credencial) {
        return conCredencial(catalogo.url(), propiedades, credencial);
    }

    @Bean(INVENTARIO)
    RestClient clienteDeInventario(PropiedadesDeLaTienda propiedades, CredencialDeServicio credencial) {
        return conCredencial(propiedades.servicios().inventario(), propiedades, credencial);
    }

    @Bean(FINANZAS)
    RestClient clienteDeFinanzas(PropiedadesDeLaTienda propiedades, CredencialDeServicio credencial) {
        return conCredencial(propiedades.servicios().finanzas(), propiedades, credencial);
    }

    @Bean(CORREO)
    RestClient clienteDeCorreo(PropiedadesDeLaTienda propiedades, CredencialDeServicio credencial) {
        return conCredencial(propiedades.servicios().correo(), propiedades, credencial);
    }

    @Bean(IDENTIDAD)
    RestClient clienteDeIdentidad(PropiedadesDeLaTienda propiedades, CredencialDeServicio credencial) {
        return conCredencial(propiedades.servicios().identidad(), propiedades, credencial);
    }

    @Bean(PARAMETROS)
    RestClient clienteDeParametros(PropiedadesDeLaTienda propiedades) {
        return constructor(propiedades.servicios().parametros(), propiedades.http()).build();
    }

    private static RestClient conCredencial(String base, PropiedadesDeLaTienda propiedades,
                                            CredencialDeServicio credencial) {
        return constructor(base, propiedades.http())
                .requestInterceptor(new InterceptorDeCredencial(credencial))
                .build();
    }

    /**
     * El constructor comun, sin construir: las pruebas lo reutilizan para que
     * el cliente que prueban sea el que se despliega.
     */
    public static RestClient.Builder constructor(String base, PropiedadesDeLaTienda.Http http) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(http.timeoutConexion());
        fabrica.setReadTimeout(http.timeoutLectura());
        RestClient.Builder constructor = RestClient.builder()
                .requestFactory(fabrica)
                .requestInterceptor(new InterceptorDeTraza());
        if (base != null && !base.isBlank()) {
            constructor.baseUrl(base);
        }
        return constructor;
    }
}
