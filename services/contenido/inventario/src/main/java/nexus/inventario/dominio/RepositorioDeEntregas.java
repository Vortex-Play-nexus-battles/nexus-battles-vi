package nexus.inventario.dominio;

import java.time.Instant;
import java.util.Optional;

/** Las entregas registradas (coleccion {@code entregas}), una por clave de idempotencia — B4. */
public interface RepositorioDeEntregas {

    Optional<Entrega> buscarPorClave(String clave);

    /**
     * Registra una entrega nueva.
     *
     * @throws ClaveDeEntregaOcupadaException si otra peticion registro la misma
     *         clave antes (indice unico)
     */
    void registrar(Entrega entrega);

    /** Marca la entrega como completada; repetirlo no cambia nada. */
    void completar(String entregaId, Instant entregadaEn);
}
