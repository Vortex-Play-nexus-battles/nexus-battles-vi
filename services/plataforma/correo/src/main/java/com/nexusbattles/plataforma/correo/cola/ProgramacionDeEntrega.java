package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.plataforma.correo.envio.ClasificadorDeFallos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * Pone a trabajar al {@link TrabajadorDeEntrega} y a la purga de retencion.
 *
 * <p>Retraso fijo y no frecuencia fija: la pausa cuenta desde que termina una
 * ronda, asi que una ronda lenta (un proveedor que tarda) nunca se solapa con
 * la siguiente.
 *
 * <p>Las tareas se registran con los valores ya normalizados de
 * {@link ConfiguracionDeEntrega} y no con {@code @Scheduled("${...}")}: una
 * variable vacia en el {@code .env} ({@code CORREO_ENTREGA_INTERVALO_MS=})
 * llegaria al {@code @Scheduled} como cadena vacia e impediria arrancar.
 *
 * <p>Si la base se cae, cada ronda falla. Se avisa una vez al empezar la racha
 * y otra al terminar, en lugar de una traza completa cada dos segundos: el
 * aviso que se repite sin parar es el que nadie lee. Mientras tanto no se
 * pierde nada: lo aceptado sigue en la tabla, y lo que llegue responde 503.
 */
public class ProgramacionDeEntrega implements SchedulingConfigurer {

    private static final Logger BITACORA = LoggerFactory.getLogger(ProgramacionDeEntrega.class);

    private final TrabajadorDeEntrega trabajador;
    private final ConfiguracionDeEntrega configuracion;
    private volatile boolean enFallo;

    public ProgramacionDeEntrega(TrabajadorDeEntrega trabajador, ConfiguracionDeEntrega configuracion) {
        this.trabajador = trabajador;
        this.configuracion = configuracion;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registro) {
        Duration intervalo = Duration.ofMillis(configuracion.intervaloMs());
        Duration purga = Duration.ofMillis(configuracion.purgaIntervaloMs());
        registro.addFixedDelayTask(new FixedDelayTask(this::entregar, intervalo, intervalo));
        registro.addFixedDelayTask(new FixedDelayTask(this::purgar, purga, purga));
    }

    /** Una ronda del trabajador, sin dejar escapar nada al planificador. */
    public void entregar() {
        try {
            trabajador.procesarRonda();
            if (enFallo) {
                enFallo = false;
                BITACORA.info("La cola de correo vuelve a responder: se reanuda la entrega");
            }
        } catch (RuntimeException e) {
            if (!enFallo) {
                enFallo = true;
                BITACORA.warn("No se pudo procesar la cola de correo; se reintenta en cada ronda: {}",
                        ClasificadorDeFallos.resumen(e));
            }
        }
    }

    /** Una pasada de la purga de retencion. */
    public void purgar() {
        try {
            trabajador.purgar();
        } catch (RuntimeException e) {
            BITACORA.warn("No se pudo purgar la cola de correo; se intenta en la siguiente pasada: {}",
                    ClasificadorDeFallos.resumen(e));
        }
    }

    boolean enFallo() {
        return enFallo;
    }
}
