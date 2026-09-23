package com.nexusbattles.plataforma.observabilidad;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Enciende la propagacion del identificador de traza en todo el monorepo —
 * regla 5 de plataforma.
 *
 * <h2>Por que esto es una autoconfiguracion y no un bean por servicio</h2>
 *
 * La regla 5 dice que el trace id se propaga «en toda llamada entre servicios
 * y todo mensaje de cola». Estaba escrita desde agosto y la cumplia <b>un
 * servicio de veinte</b>: {@code FiltroDeTraza} existia, pero solo
 * salas-partidas lo declaraba como bean, a mano, en su propia configuracion.
 * Los otros diecinueve ni generaban trace id.
 *
 * <p>Repartirlo a mano habria significado diecinueve copias del mismo
 * {@code @Bean} en diecinueve archivos de tres equipos distintos, y la
 * garantia de que el siguiente servicio naciera sin el. Asi que sigue el
 * mismo camino que la instrumentacion de latencia: la biblioteca ya la
 * reparte {@code nexus.spring-conventions.gradle} a los veinte modulos, y
 * esta clase la activa sola. <b>Un servicio nuevo queda trazado por
 * existir.</b>
 *
 * <h2>Las dos mitades</h2>
 *
 * <ul>
 *   <li><b>Entrada:</b> {@link FiltroDeTraza} lee o crea el
 *       {@code traceparent} y lo deja en el MDC. Va en el primer lugar de la
 *       cadena de filtros, antes que seguridad, para que hasta un 401 salga
 *       con traza.</li>
 *   <li><b>Salida:</b> {@link InterceptorDeTraza} lo reenvia en cada llamada
 *       HTTP. Donde Spring Boot construye el {@code RestClient.Builder}, se
 *       engancha solo; donde el servicio se arma el suyo con
 *       {@code RestClient.builder()}, lo anade el propio servicio (el caso
 *       de salas-partidas, que habla con seis modulos).</li>
 * </ul>
 *
 * <h2>Lo que esto todavia NO cubre</h2>
 *
 * Los mensajes STOMP. {@code contracts/websocket/salas-partidas.yaml} afirma
 * que «todo mensaje propaga el identificador de traza» y hoy eso es falso:
 * los publicadores usan {@code convertAndSend(destino, payload)} sin
 * cabeceras. Se deja anotado aqui, donde se ve, en vez de darlo por hecho.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(FiltroDeTraza.class)
public class TrazaAutoConfiguration {

    /**
     * El filtro de entrada, en el primer lugar de la cadena.
     *
     * <p>{@code HIGHEST_PRECEDENCE} no es decoracion: si fuera despues de la
     * cadena de seguridad, una peticion rechazada con 401 —que es
     * precisamente de las que uno quiere seguir— saldria sin traza.
     */
    @Bean
    @ConditionalOnMissingBean(name = "filtroDeTraza")
    public FilterRegistrationBean<FiltroDeTraza> filtroDeTraza() {
        FilterRegistrationBean<FiltroDeTraza> registro =
                new FilterRegistrationBean<>(new FiltroDeTraza());
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registro.addUrlPatterns("/*");
        return registro;
    }

    @Bean
    @ConditionalOnMissingBean(InterceptorDeTraza.class)
    public InterceptorDeTraza interceptorDeTraza() {
        return new InterceptorDeTraza();
    }

    /**
     * Engancha el interceptor a todo {@code RestClient.Builder} que Spring
     * Boot construya — solo donde ese mecanismo existe.
     *
     * <h3>Por que esto va en una configuracion anidada y condicionada</h3>
     *
     * {@code RestClientCustomizer} vive en {@code spring-boot-restclient},
     * que en Spring Boot 4 es un modulo aparte. Esta biblioteca la reciben
     * los veinte servicios, y varios de ellos —productos, inventario,
     * metricas-plataforma— no llaman a nadie por HTTP y no lo tienen en el
     * classpath.
     *
     * <p>La primera version tenia el {@code @Bean} y el personalizador en la
     * clase de fuera, y esos servicios reventaron al arrancar con
     * {@code NoClassDefFoundError: RestClientCustomizer}: Spring introspecta
     * la autoconfiguracion entera —firmas de metodo y clases anidadas
     * incluidas— antes de evaluar nada, asi que un tipo ausente en una firma
     * tumba el contexto aunque ese bean no se fuera a crear nunca.
     *
     * <p>La condicion se declara por NOMBRE y no con el literal de clase, que
     * es la unica forma de que la propia anotacion no obligue a cargarlo: las
     * condiciones se evaluan leyendo el bytecode, sin ClassLoader.
     *
     * <p>Donde si existe, convive con {@code InterceptorDePortadorDeServicio}
     * (ADR-005): son dos interceptores sobre el mismo constructor, uno pone
     * {@code Authorization} y el otro {@code traceparent}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.restclient.RestClientCustomizer")
    public static class ConClientesRest {

        @Bean
        @ConditionalOnMissingBean(name = "restClientCustomizerDeTraza")
        public org.springframework.boot.restclient.RestClientCustomizer restClientCustomizerDeTraza(
                InterceptorDeTraza interceptor) {
            return constructor -> constructor.requestInterceptor(interceptor);
        }
    }
}
