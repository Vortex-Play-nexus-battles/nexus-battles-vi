package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 7.4.9 (ms-chatbot.yaml 1.9.0): los temas de la versión en producción, leídos
 * de la base una vez por versión y no en cada mensaje.
 *
 * <p>Hasta 1.6.0 el motor cargaba los temas de producción (todos sus textos,
 * más las intenciones adicionales en una segunda consulta) con cada pregunta.
 * Los temas de una versión publicada no cambian —el panel solo edita la
 * candidata; desplegar o revertir cambia QUÉ versión está en producción, no
 * sus temas—, así que basta con saber cuál es la vigente:
 *
 * <ol>
 *   <li>en cada mensaje, una consulta de una fila: el id de la versión en
 *       producción ({@code buscarIdDeLaDeEstado});</li>
 *   <li>si es la misma que hay en memoria, se usan esos temas;</li>
 *   <li>si cambió (un despliegue, una reversión o la publicación programada,
 *       en esta instancia o en otra), se leen los temas de la nueva y la
 *       anterior se suelta.</li>
 * </ol>
 *
 * <p>Por eso no hace falta avisar a las demás instancias al desplegar: cada
 * una lo nota en su siguiente mensaje. En memoria hay como mucho una versión
 * (59 temas en V11, unos cientos de KB). Los temas guardados son entidades
 * ya desligadas de la sesión, con sus intenciones adicionales cargadas
 * (EAGER); el motor solo los lee.
 *
 * <p>La lectura de una versión nueva va con un {@link ReentrantLock} y no con
 * {@code synchronized}: la hacen también los hilos virtuales del canal en
 * tiempo real, y un {@code synchronized} alrededor de una consulta JDBC los
 * fijaría a su hilo portador.
 */
@Component
public class TemasEnProduccion implements FuenteDeLaBase {

    private final VersionBaseConocimientoRepository versiones;
    private final TemaConocimientoRepository temas;
    private final Counter cargas;
    private final ReentrantLock cargando = new ReentrantLock();

    /** La última versión leída; null hasta el primer mensaje. */
    private volatile BaseEnProduccion enMemoria;

    public TemasEnProduccion(VersionBaseConocimientoRepository versiones, TemaConocimientoRepository temas,
                             MeterRegistry metricas) {
        this.versiones = versiones;
        this.temas = temas;
        this.cargas = Counter.builder("chatbot.cache.temas.cargas")
            .description("Veces que se leyeron de la base los temas de la version en produccion (7.4.9)")
            .register(metricas);
        Gauge.builder("chatbot.cache.temas.tamano", this, TemasEnProduccion::temasEnMemoria)
            .description("Temas de la version en produccion que hay en memoria (7.4.9)")
            .register(metricas);
    }

    @Override
    public BaseEnProduccion enProduccion() {
        Optional<UUID> vigente = versiones.buscarIdDeLaDeEstado(EstadoVersion.PRODUCCION);
        if (vigente.isEmpty()) {
            enMemoria = null;
            return BaseEnProduccion.vacia();
        }
        BaseEnProduccion actual = enMemoria;
        if (actual != null && actual.versionId().equals(vigente.get())) {
            return actual;
        }
        return cargar(vigente.get());
    }

    // Dos mensajes que notan a la vez la version nueva la leen una sola vez.
    private BaseEnProduccion cargar(UUID versionId) {
        cargando.lock();
        try {
            BaseEnProduccion actual = enMemoria;
            if (actual != null && actual.versionId().equals(versionId)) {
                return actual;
            }
            List<TemaConocimiento> activos = temas.findByVersionIdAndActivoTrue(versionId);
            BaseEnProduccion nueva = new BaseEnProduccion(versionId, activos);
            enMemoria = nueva;
            cargas.increment();
            return nueva;
        } finally {
            cargando.unlock();
        }
    }

    int temasEnMemoria() {
        BaseEnProduccion actual = enMemoria;
        return actual == null ? 0 : actual.temas().size();
    }
}
