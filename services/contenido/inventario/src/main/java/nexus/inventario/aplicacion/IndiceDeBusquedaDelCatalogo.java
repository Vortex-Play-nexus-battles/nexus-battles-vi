package nexus.inventario.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * El índice de cuatrigramas del catálogo, en memoria y renovado solo
 * (HU-INV-002, inventario.yaml 1.7.0).
 *
 * <ul>
 *   <li><b>Primera búsqueda:</b> se construye en el hilo de la petición, una sola
 *       vez (unas diez lecturas internas al catálogo y a los héroes).</li>
 *   <li><b>Vigencia:</b> pasada la vigencia ({@code inventario.busqueda.vigencia-del-catalogo},
 *       5 minutos por omisión) se sigue sirviendo el índice que hay y se renueva en
 *       segundo plano: un administrador que cambia un producto lo ve buscable en
 *       minutos, y ninguna búsqueda espera por ello.</li>
 *   <li><b>Catálogo caído:</b> la búsqueda no falla. Sin índice todavía, se
 *       contesta sin coincidencias de catálogo (el caso de uso sigue buscando en el
 *       nombre propio, el tipo y la parte de cada elemento) y no se vuelve a
 *       intentar hasta pasado un respiro, para no martillear un servicio caído.
 *       Lo mismo si falla una renovación: se sigue con el índice anterior y la
 *       siguiente no se intenta hasta pasado el respiro.</li>
 *   <li><b>Hilos virtuales:</b> la primera construcción hace lecturas HTTP. Se
 *       serializa con un {@link ReentrantLock} y no con {@code synchronized}: en
 *       Java 21 un hilo virtual que se bloquea dentro de {@code synchronized} fija
 *       su hilo portador, y con tantas primeras búsquedas como núcleos el servicio
 *       entero se quedaba esperando a la construcción (segunda auditoría, 13-oct).</li>
 * </ul>
 */
@Component
public class IndiceDeBusquedaDelCatalogo implements BuscadorEnElCatalogo {

    private static final Logger BITACORA = LoggerFactory.getLogger(IndiceDeBusquedaDelCatalogo.class);

    private final FuenteDelCatalogo fuente;
    private final Clock reloj;
    private final Duration vigencia;
    private final Duration respiroTrasFallo;
    private final Executor segundoPlano;
    private final AtomicBoolean renovando = new AtomicBoolean();
    private final ReentrantLock primeraConstruccion = new ReentrantLock();

    private volatile Instantanea actual;
    private volatile Instant ultimoFallo;

    @Autowired
    public IndiceDeBusquedaDelCatalogo(
            FuenteDelCatalogo fuente,
            Clock reloj,
            @Value("${inventario.busqueda.vigencia-del-catalogo:PT5M}") Duration vigencia) {
        this(fuente, reloj, vigencia, Duration.ofSeconds(30),
                tarea -> Thread.ofVirtual().name("indice-catalogo").start(tarea));
    }

    IndiceDeBusquedaDelCatalogo(
            FuenteDelCatalogo fuente,
            Clock reloj,
            Duration vigencia,
            Duration respiroTrasFallo,
            Executor segundoPlano) {
        this.fuente = Objects.requireNonNull(fuente, "fuente es obligatoria");
        this.reloj = Objects.requireNonNull(reloj, "reloj es obligatorio");
        this.vigencia = Objects.requireNonNull(vigencia, "vigencia es obligatoria");
        this.respiroTrasFallo = Objects.requireNonNull(respiroTrasFallo, "respiro es obligatorio");
        this.segundoPlano = Objects.requireNonNull(segundoPlano, "ejecutor es obligatorio");
    }

    @Override
    public Set<String> productosQueCoinciden(String criterio) {
        return indiceVigente().buscar(criterio);
    }

    IndiceDeNgramas indiceVigente() {
        Instantanea instantanea = actual;
        if (instantanea == null) {
            return construirPorPrimeraVez();
        }
        if (instantanea.construidoEn().plus(vigencia).isBefore(reloj.instant())) {
            renovarEnSegundoPlano();
        }
        return instantanea.indice();
    }

    private IndiceDeNgramas construirPorPrimeraVez() {
        primeraConstruccion.lock();
        try {
            if (actual != null) {
                return actual.indice();
            }
            Instant ahora = reloj.instant();
            if (enRespiro(ahora)) {
                return IndiceDeNgramas.vacio();
            }
            try {
                actual = construir();
                ultimoFallo = null;
                return actual.indice();
            } catch (RuntimeException fallo) {
                ultimoFallo = ahora;
                BITACORA.warn("Indice del catalogo no disponible: la busqueda se limita a los datos propios"
                        + " de cada elemento ({})", fallo.getMessage());
                return IndiceDeNgramas.vacio();
            }
        } finally {
            primeraConstruccion.unlock();
        }
    }

    private void renovarEnSegundoPlano() {
        if (enRespiro(reloj.instant()) || !renovando.compareAndSet(false, true)) {
            return;
        }
        segundoPlano.execute(() -> {
            try {
                actual = construir();
                ultimoFallo = null;
            } catch (RuntimeException fallo) {
                ultimoFallo = reloj.instant();
                BITACORA.warn("No se pudo renovar el indice del catalogo; se sigue usando el anterior ({})",
                        fallo.getMessage());
            } finally {
                renovando.set(false);
            }
        });
    }

    /** Tras un fallo (primera construcción o renovación) no se reintenta hasta pasado el respiro. */
    private boolean enRespiro(Instant ahora) {
        Instant fallo = ultimoFallo;
        return fallo != null && fallo.plus(respiroTrasFallo).isAfter(ahora);
    }

    private Instantanea construir() {
        IndiceDeNgramas indice = IndiceDeNgramas.construir(fuente.textosBuscables());
        BITACORA.info("Indice del catalogo construido con {} productos", indice.tamanio());
        return new Instantanea(indice, reloj.instant());
    }

    private record Instantanea(IndiceDeNgramas indice, Instant construidoEn) {
    }
}
