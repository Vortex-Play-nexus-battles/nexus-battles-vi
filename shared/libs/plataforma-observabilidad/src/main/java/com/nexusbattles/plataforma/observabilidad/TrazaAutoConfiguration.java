package com.nexusbattles.plataforma.observabilidad;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

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
 *       HTTP. Se conecta por {@link RestClient.Builder}, que es de donde
 *       salen los clientes de los veinte servicios.</li>
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
     * Engancha el interceptor a TODO {@code RestClient.Builder} del contexto.
     *
     * <p>Se hace por el personalizador y no pidiendole a cada servicio que
     * anada {@code .requestInterceptor(...)} por la misma razon de siempre:
     * hay cuarenta clases con cliente HTTP en el monorepo y la que se olvide
     * rompe la traza sin que nada falle. Un personalizador se aplica a los
     * cuarenta, incluidos los que se escriban manana.
     *
     * <p>Convive con {@code InterceptorDePortadorDeServicio} (ADR-005): son
     * dos interceptores distintos sobre el mismo constructor, uno pone
     * {@code Authorization} y el otro {@code traceparent}.
     */
    @Bean
    public RestClientCustomizerDeTraza restClientCustomizerDeTraza(InterceptorDeTraza interceptor) {
        return new RestClientCustomizerDeTraza(interceptor);
    }

    /**
     * Personalizador con nombre propio (en vez de una lambda) para que un
     * servicio que necesite excluirlo pueda declarar el suyo y sustituirlo.
     */
    public static final class RestClientCustomizerDeTraza
            implements org.springframework.boot.restclient.RestClientCustomizer {

        private final ClientHttpRequestInterceptor interceptor;

        RestClientCustomizerDeTraza(ClientHttpRequestInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public void customize(RestClient.Builder constructor) {
            constructor.requestInterceptor(interceptor);
        }
    }
}
