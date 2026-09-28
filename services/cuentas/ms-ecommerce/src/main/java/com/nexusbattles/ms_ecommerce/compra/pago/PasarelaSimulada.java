package com.nexusbattles.ms_ecommerce.compra.pago;

import com.nexusbattles.ms_ecommerce.precios.Moneda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * La pasarela de pagos simulada (7.5: «integracion con una pasarela de pagos
 * simulada»; el Project Charter excluye los pagos reales).
 *
 * <p>Vive dentro de la tienda y no guarda nada: decide por la tarjeta, como
 * hacen las tarjetas de prueba de las pasarelas reales. Las que publica el
 * contrato (1.3.0):
 *
 * <ul>
 *   <li>terminada en {@value #RECHAZO}: rechazo por fondos insuficientes;</li>
 *   <li>terminada en {@value #CAIDA}: la pasarela no responde (sirve para
 *       probar el reintento con la misma clave);</li>
 *   <li>cualquier otra que pase Luhn con vencimiento futuro: aprobada.</li>
 * </ul>
 *
 * <p>El cobro «existe» cuando la orden pasa a COBRADA en la base, en la misma
 * transaccion que saca lo comprado del carrito: la pasarela no tiene estado
 * propio que reconciliar. Los contadores solo sirven para verlo desde fuera
 * (las pruebas de duplicacion cuentan cobros aqui).
 */
@Component
public class PasarelaSimulada {

    private static final Logger log = LoggerFactory.getLogger(PasarelaSimulada.class);

    public static final String RECHAZO = "0002";
    public static final String CAIDA = "0069";

    private final AtomicLong aprobados = new AtomicLong();
    private final AtomicLong rechazados = new AtomicLong();
    private final AtomicLong caidas = new AtomicLong();
    private final AtomicLong reembolsos = new AtomicLong();

    /**
     * @param orden la orden que se cobra; la referencia la lleva en la bitacora
     */
    public ResultadoDeCobro cobrar(TarjetaValidada tarjeta, BigDecimal monto, Moneda moneda, UUID orden) {
        String ultimos4 = tarjeta.ultimos4();
        if (CAIDA.equals(ultimos4)) {
            caidas.incrementAndGet();
            log.warn("Pasarela simulada: no responde (tarjeta de prueba de caida) para la orden {}", orden);
            return new ResultadoDeCobro.NoDisponible();
        }
        if (RECHAZO.equals(ultimos4)) {
            rechazados.incrementAndGet();
            log.info("Pasarela simulada: cobro rechazado por fondos para la orden {}", orden);
            return new ResultadoDeCobro.Rechazado("La pasarela rechazó el pago: fondos insuficientes.");
        }
        aprobados.incrementAndGet();
        String referencia = "SIM-" + UUID.randomUUID();
        log.info("Pasarela simulada: cobro aprobado de {} {} para la orden {} ({})", monto, moneda, orden, referencia);
        return new ResultadoDeCobro.Aprobado(referencia);
    }

    /** Devuelve un cobro aprobado. En la simulacion no falla nunca. */
    public String reembolsar(String referencia, BigDecimal monto, Moneda moneda) {
        reembolsos.incrementAndGet();
        String reembolso = "SIMR-" + UUID.randomUUID();
        log.info("Pasarela simulada: reembolso de {} {} del cobro {} ({})", monto, moneda, referencia, reembolso);
        return reembolso;
    }

    public long cobrosAprobados() {
        return aprobados.get();
    }

    public long cobrosRechazados() {
        return rechazados.get();
    }

    public long caidas() {
        return caidas.get();
    }

    public long reembolsos() {
        return reembolsos.get();
    }
}
