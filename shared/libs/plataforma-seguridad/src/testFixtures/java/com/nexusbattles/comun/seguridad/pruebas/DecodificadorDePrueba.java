package com.nexusbattles.comun.seguridad.pruebas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * {@code JwtDecoder} real apuntando al JWKS de {@link EmisorDeTokensDePrueba}.
 *
 * <p>Para importar en un {@code @WebMvcTest} junto al {@code SecurityConfig}
 * del servicio. Boot deja de construir su propio decodificador cuando ya hay
 * uno en el contexto, asi que la cadena de seguridad del servicio queda
 * intacta y solo cambia de donde sale la clave publica.
 *
 * <p>No es un doble: verifica firma y caducidad como en produccion. Un token
 * de {@link EmisorDeTokensDePrueba#tokenFirmadoPorOtro} o
 * {@link EmisorDeTokensDePrueba#tokenCaducado} sale con 401.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DecodificadorDePrueba {

    @Bean
    public JwtDecoder jwtDecoder() {
        return EmisorDeTokensDePrueba.emisor().decodificador();
    }
}
