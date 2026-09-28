package com.nexusbattles.plataforma.torneos.torneo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El reintento seguro de B10: cada {@code torneos.operaciones.intervalo-ms}
 * intenta las operaciones que ya tocan (cobros, devoluciones, premios y avisos
 * que la peticion no pudo completar, o que quedaron a medias porque el
 * servicio murio). Reintentar es seguro porque cada operacion es unica por su
 * clave y cada llamada al proveedor es idempotente.
 *
 * <p>Se puede apagar ({@code torneos.operaciones.tarea-activa=false}) para las
 * pruebas que quieren decidir ellas cuando se procesa.
 */
@Component
@ConditionalOnProperty(name = "torneos.operaciones.tarea-activa", havingValue = "true", matchIfMissing = true)
public class TareaDeOperaciones {

    private static final Logger BITACORA = LoggerFactory.getLogger(TareaDeOperaciones.class);

    private final ProcesadorDeOperaciones procesador;

    public TareaDeOperaciones(ProcesadorDeOperaciones procesador) {
        this.procesador = procesador;
    }

    @Scheduled(fixedDelayString = "${torneos.operaciones.intervalo-ms:15000}",
            initialDelayString = "${torneos.operaciones.intervalo-ms:15000}")
    public void reintentar() {
        try {
            int intentadas = procesador.procesarPendientes();
            if (intentadas > 0) {
                BITACORA.info("Tarea de operaciones: {} intentada(s)", intentadas);
            }
        } catch (RuntimeException fallo) {
            // La tarea no puede morir: la siguiente vuelta lo vuelve a intentar.
            BITACORA.error("La tarea de operaciones fallo; se reintenta en la siguiente vuelta", fallo);
        }
    }
}
