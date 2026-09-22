package com.nexusbattles.comun.seguridad.servicio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import java.time.Clock;

/**
 * Deja lista la credencial de servicio en cualquier modulo que declare esta
 * biblioteca y tenga configuradas sus credenciales — ADR-001.
 *
 * <p>Lee las variables que ya existen en {@code .env.example} y en los secretos
 * del despliegue, sin inventar otras:
 *
 * <pre>
 * DIRECTORIO_ACTIVO_URL            URL del realm (emisor)
 * DIRECTORIO_ACTIVO_CLIENT_ID      identidad del servicio en el emisor
 * DIRECTORIO_ACTIVO_CLIENT_SECRET  su secreto
 * </pre>
 *
 * <p>Se pueden sobreescribir por propiedad ({@code seguridad.servicio.url},
 * {@code seguridad.servicio.client-id}, {@code seguridad.servicio.client-secret}),
 * que es lo que usan las pruebas. Sin {@code client-id} no se crea ningun bean:
 * un servicio que solo es servidor de recursos no necesita credencial propia y
 * no debe arrancar con una a medias.
 */
@AutoConfiguration
@ConditionalOnClass(ClientRegistration.class)
@ConditionalOnExpression("!'${seguridad.servicio.client-id:${DIRECTORIO_ACTIVO_CLIENT_ID:}}'.isBlank()")
public class CredencialesDeServicioAutoConfiguration {

    /**
     * Reloj para la caducidad del token cacheado. Por TIPO, no por nombre: un
     * servicio con su propio {@code Clock} (ms-subastas lo define para las
     * pruebas de tiempo) lo usa tambien aqui; si esta biblioteca creara otro,
     * cualquier bean que pida {@code Clock} a secas —el filtro de latencia de
     * plataforma-observabilidad— encontraria dos y el servicio no arrancaria
     * (lo destapo el banco E2E al meter ms-subastas, #572).
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock relojDeCredenciales() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenDeServicio tokenDeServicio(
            @Value("${seguridad.servicio.url:${DIRECTORIO_ACTIVO_URL:}}") String urlDelEmisor,
            @Value("${seguridad.servicio.client-id:${DIRECTORIO_ACTIVO_CLIENT_ID:}}") String clientId,
            @Value("${seguridad.servicio.client-secret:${DIRECTORIO_ACTIVO_CLIENT_SECRET:}}") String clientSecret,
            Clock relojDeCredenciales) {
        return new TokenDeServicioOAuth2(urlDelEmisor, clientId, clientSecret, relojDeCredenciales);
    }

    @Bean
    @ConditionalOnMissingBean
    public InterceptorDePortadorDeServicio interceptorDePortadorDeServicio(TokenDeServicio token) {
        return new InterceptorDePortadorDeServicio(token);
    }
}
