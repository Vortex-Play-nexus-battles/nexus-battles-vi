package nexus.misiones.configuracion;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import java.security.SecureRandom;
import java.util.HexFormat;
import nexus.misiones.aplicacion.TrabajoDeMisiones;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Da cuerda al trabajo en segundo plano (7.8.12): cada
 * {@code MISIONES_INTERVALO_MS} simula lo que vencio y liquida lo pendiente.
 *
 * <p>Cada vuelta abre su propia traza: el trabajo no nace de una peticion HTTP,
 * y sin esto las llamadas de una vuelta a inventario, ms-finanzas y correo
 * saldrian sin {@code traceparent} (regla 5) y no se podrian seguir juntas.
 *
 * <p>Se apaga con {@code misiones.trabajo.activo=false}: las pruebas lo
 * ejecutan a mano, en el instante que les conviene.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "misiones.trabajo.activo", havingValue = "true", matchIfMissing = true)
public class TrabajoProgramado {

    private static final SecureRandom AZAR = new SecureRandom();

    private final TrabajoDeMisiones trabajo;

    public TrabajoProgramado(TrabajoDeMisiones trabajo) {
        this.trabajo = trabajo;
    }

    @Scheduled(fixedDelayString = "${misiones.trabajo.intervalo-ms:30000}",
            initialDelayString = "${misiones.trabajo.intervalo-ms:30000}")
    public void darUnaVuelta() {
        byte[] traza = new byte[16];
        AZAR.nextBytes(traza);
        MDC.put(FiltroDeTraza.CLAVE_MDC, HexFormat.of().formatHex(traza));
        try {
            trabajo.ejecutar();
        } finally {
            MDC.remove(FiltroDeTraza.CLAVE_MDC);
        }
    }
}
