package com.nexusbattles.ms_subastas.pujas.config;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientHttp;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientResiliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

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

    /**
     * @param saldoInicial creditos con los que aparece cualquier jugador en
     *        modo doble. Tiene que ser mayor que cero para poder ver la
     *        funcionalidad: sin ms-finanzas no existe ningun sitio desde donde
     *        acreditar, asi que con cero toda puja muere en SALDO_INSUFICIENTE
     *        y la historia no se puede demostrar. El dia que exista el cliente
     *        real, este bean deja de aplicar y el valor se ignora.
     */
    @Bean
    @ConditionalOnProperty(name = "app.finanzas.modo", havingValue = "fake", matchIfMissing = true)
    public CreditoClient creditoClientFake(
            @Value("${app.finanzas.saldo-inicial-doble:5000}") BigDecimal saldoInicial) {
        log.warn("ms-subastas arranca con el doble EN MEMORIA de creditos (app.finanzas.modo=fake). "
                + "No hay integracion real con ms-finanzas: ningun credito se mueve de verdad. "
                + "Cada jugador aparece con {} creditos ficticios.", saldoInicial);
        // true: corriendo en local, las pujas viven en PostgreSQL y las reservas
        // solo en memoria, asi que tras un reinicio hay pujas apuntando a
        // reservas que ya no existen. Sin esto, la primera puja sobre una
        // subasta que ya tenia oferta devuelve 500 y no se puede ni demostrar.
        return new CreditoClientResiliente(new CreditoClientFake(saldoInicial, true));
    }

    /**
     * Cliente real contra ms-finanzas. Se activa con
     * {@code app.finanzas.modo=http}.
     *
     * <p>No es el modo por defecto todavia, y ya no por los errores: desde el
     * 15/09/2026 ms-finanzas distingue el saldo insuficiente (422) de la
     * reserva inexistente (404) con su {@code type} URI, y este cliente los
     * traduce. Lo que falta es mas basico: <b>no hay ninguna forma de acreditar
     * creditos a una cuenta</b>. Reservar y debitar exigen saldo, y el unico
     * abono es consumir al vendedor, que exige una reserva previa; toda cuenta
     * nace en cero, asi que con el modo real toda puja moriria en
     * SALDO_INSUFICIENTE y la historia no se podria demostrar.
     *
     * <p>El dia que exista un endpoint de abono, esto pasa a ser el valor por
     * defecto sin tocar el motor de pujas.
     */
    @Bean
    @ConditionalOnProperty(name = "app.finanzas.modo", havingValue = "http")
    public CreditoClient creditoClientHttp(
            @Value("${app.finanzas.base-url:http://localhost:8093/api/v1}") String baseUrl,
            @Value("${app.finanzas.pujas.timeout-ms:1000}") long timeoutMs,
            ObjectMapper objectMapper) {
        log.info("ms-subastas arranca con el cliente HTTP real de creditos (app.finanzas.modo=http): {}", baseUrl);
        return new CreditoClientResiliente(new CreditoClientHttp(baseUrl, timeoutMs, objectMapper));
    }
}
