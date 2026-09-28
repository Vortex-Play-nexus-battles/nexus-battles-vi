package com.nexusbattles.ms_subastas;

import com.nexusbattles.ms_subastas.notificaciones.DrenadorDeCorreosJob;
import com.nexusbattles.ms_subastas.notificaciones.DrenadorDeNotificacionesJob;
import com.nexusbattles.ms_subastas.notificaciones.RecordatorioDeCierreJob;
import com.nexusbattles.ms_subastas.panel.service.VencimientoDePendientesJob;
import com.nexusbattles.ms_subastas.pujas.service.CierreDeSubastasVencidasJob;
import com.nexusbattles.ms_subastas.pujas.service.EmisionDePujasAutomaticasJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Los trabajos programados esperan un intervalo antes de su primera pasada.
 *
 * <p>Con {@code fixedDelay} a secas la primera pasada es inmediata al arrancar.
 * El planificador tiene un solo hilo, asi que esa primera pasada de cada
 * trabajo cae unos segundos despues, en mitad de las pruebas de integracion,
 * aunque estas alejen el intervalo a una hora: el recordatorio de cierre
 * (B8) marco una subasta que {@code SubastaSpecificationsIT} estaba borrando
 * y la limpieza fallo con {@code ObjectOptimisticLockingFailureException}.
 * Con el retardo inicial igual al intervalo, alejar el intervalo aleja
 * tambien la primera pasada. En produccion el primer cierre llega un
 * intervalo despues de arrancar, no al instante: nada que el jugador note.
 */
@DisplayName("Trabajos programados: la primera pasada espera un intervalo")
class TrabajosProgramadosTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {
            CierreDeSubastasVencidasJob.class,
            EmisionDePujasAutomaticasJob.class,
            DrenadorDeNotificacionesJob.class,
            DrenadorDeCorreosJob.class,
            RecordatorioDeCierreJob.class,
            VencimientoDePendientesJob.class
    })
    void elRetardoInicialEsElIntervalo(Class<?> trabajo) {
        List<Scheduled> programados = Arrays.stream(trabajo.getDeclaredMethods())
                .map((Method m) -> m.getAnnotation(Scheduled.class))
                .filter(anotacion -> anotacion != null)
                .toList();

        assertFalse(programados.isEmpty(), trabajo.getSimpleName() + " no tiene ningun @Scheduled");
        for (Scheduled programado : programados) {
            assertFalse(programado.fixedDelayString().isBlank(), "se programa por intervalo");
            assertEquals(programado.fixedDelayString(), programado.initialDelayString(),
                    "la primera pasada espera el mismo intervalo que las siguientes");
        }
    }
}
