package com.nexusbattles.plataforma.comentarios.catalogo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoDeProductos.Existencia;

/**
 * Memoria corta de lo que el catalogo ya contesto — B3.
 *
 * <p>Sin ella cada comentario, cada calificacion y cada lectura del resumen
 * costarian una llamada al host de contenido, y un hilo con actividad
 * repetiria la misma pregunta decenas de veces por minuto. Las dos respuestas
 * se recuerdan distinto tiempo a proposito:
 *
 * <ul>
 *   <li><b>Existe</b> (5 min por omision): un producto no desaparece del
 *       catalogo a cada rato, y si se suspende sigue existiendo.</li>
 *   <li><b>No existe</b> (30 s por omision): se recuerda poco, porque el
 *       producto puede darse de alta justo despues y nadie tiene que esperar
 *       cinco minutos para comentarlo.</li>
 * </ul>
 *
 * <p>Lo que no se recuerda nunca es un fallo: si el catalogo no contesto, la
 * siguiente peticion vuelve a preguntar. Recordar la caida seria alargarla.
 *
 * <p>Tiene tope: los identificadores los elige quien llama, y sin limite
 * bastaria con pedir productos inventados para llenar la memoria del servicio.
 * Al llegar al tope se tiran primero las entradas vencidas y, si no basta,
 * todas: lo peor que pasa es volver a preguntar.
 */
final class CacheDeExistencia {

    /** Entradas como maximo. Holgado para un catalogo de decenas de productos. */
    static final int CAPACIDAD = 10_000;

    private record Entrada(Existencia existencia, Instant venceEn) {
    }

    private final ConcurrentHashMap<String, Entrada> entradas = new ConcurrentHashMap<>();
    private final Duration vidaDeLosPositivos;
    private final Duration vidaDeLosNegativos;
    private final Clock reloj;

    CacheDeExistencia(Duration vidaDeLosPositivos, Duration vidaDeLosNegativos, Clock reloj) {
        this.vidaDeLosPositivos = vidaDeLosPositivos;
        this.vidaDeLosNegativos = vidaDeLosNegativos;
        this.reloj = reloj;
    }

    /** Lo que se recuerda de ese producto, si aun no ha vencido. */
    Optional<Existencia> consultar(String productoId) {
        Entrada entrada = entradas.get(productoId);
        if (entrada == null) {
            return Optional.empty();
        }
        if (!reloj.instant().isBefore(entrada.venceEn())) {
            entradas.remove(productoId, entrada);
            return Optional.empty();
        }
        return Optional.of(entrada.existencia());
    }

    /** Guarda una respuesta del catalogo. Una {@link Existencia#DESCONOCIDA} no se guarda. */
    void recordar(String productoId, Existencia existencia) {
        if (existencia == Existencia.DESCONOCIDA) {
            return;
        }
        if (entradas.size() >= CAPACIDAD) {
            hacerSitio();
        }
        Duration vida = existencia == Existencia.EXISTE ? vidaDeLosPositivos : vidaDeLosNegativos;
        entradas.put(productoId, new Entrada(existencia, reloj.instant().plus(vida)));
    }

    int tamano() {
        return entradas.size();
    }

    private void hacerSitio() {
        Instant ahora = reloj.instant();
        entradas.values().removeIf(entrada -> !ahora.isBefore(entrada.venceEn()));
        if (entradas.size() >= CAPACIDAD) {
            entradas.clear();
        }
    }
}
