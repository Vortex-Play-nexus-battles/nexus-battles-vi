package com.nexusbattles.ms_ecommerce.compra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La tarea programada que termina las compras a medias (B5): una orden
 * cobrada cuya entrega, asiento o correo fallaron porque un servicio no
 * respondia, se retoma aqui con las mismas claves de idempotencia hasta
 * completarse; y una PENDIENTE que nadie reintento, caduca.
 *
 * <p>Vale igual con varias instancias del servicio: cada orden se toma con su
 * concesion ({@link OrdenRepository#tomar}) y solo una la procesa.
 *
 * <p>Se apaga con {@code tienda.ordenes.reanudacion.habilitada=false} (las
 * pruebas que quieren decidir cuando se retoma, la llaman a mano).
 */
@Component
@ConditionalOnProperty(name = "tienda.ordenes.reanudacion.habilitada", havingValue = "true", matchIfMissing = true)
public class ReanudadorDeOrdenes {

    private static final Logger log = LoggerFactory.getLogger(ReanudadorDeOrdenes.class);

    private final CompraService compras;

    public ReanudadorDeOrdenes(CompraService compras) {
        this.compras = compras;
    }

    @Scheduled(fixedDelayString = "${tienda.ordenes.reanudacion.intervalo-ms:15000}",
            initialDelayString = "${tienda.ordenes.reanudacion.espera-inicial-ms:30000}")
    public void reanudar() {
        try {
            int retomadas = compras.reanudarPendientes();
            if (retomadas > 0) {
                log.info("Tarea de ordenes: {} orden(es) retomada(s)", retomadas);
            }
        } catch (RuntimeException fallo) {
            // Una vuelta que falla (la base no responde) no mata la tarea: la
            // siguiente lo vuelve a intentar.
            log.error("Tarea de ordenes: la vuelta fallo", fallo);
        }
    }
}
