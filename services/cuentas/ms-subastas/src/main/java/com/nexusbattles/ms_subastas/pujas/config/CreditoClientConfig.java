package com.nexusbattles.ms_subastas.pujas.config;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientResiliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mientras no exista el endpoint real de ms-finanzas (HU-PAG-001, Juan Diego),
 * el unico CreditoClient disponible es el doble en memoria. El dia que exista
 * un CreditoClientHttp se anade aqui con app.finanzas.modo=http y este bean
 * deja de aplicar, sin tocar la logica de pujas.
 *
 * El doble esta escrito a mano, lo que NO cumple la regla de
 * backend-spring.md ("si el proveedor no existe, desarrollar contra un doble
 * generado desde el mismo contrato, nunca uno escrito a mano"). Es deuda
 * consciente: todavia no existe un contrato acordado de ms-finanzas del que
 * generarlo. En cuanto su dueno publique uno, se genera el doble desde ahi.
 */
@Configuration
public class CreditoClientConfig {

    private static final Logger log = LoggerFactory.getLogger(CreditoClientConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.finanzas.modo", havingValue = "fake", matchIfMissing = true)
    public CreditoClient creditoClientFake() {
        log.warn("ms-subastas arranca con el doble EN MEMORIA de creditos (app.finanzas.modo=fake). "
                + "No hay integracion real con ms-finanzas: ningun credito se mueve de verdad.");
        return new CreditoClientResiliente(new CreditoClientFake());
    }
}
