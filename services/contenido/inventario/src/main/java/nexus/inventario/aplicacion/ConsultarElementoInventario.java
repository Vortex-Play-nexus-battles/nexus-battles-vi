package nexus.inventario.aplicacion;

import java.util.Objects;
import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.stereotype.Service;

/** Consulta una unidad concreta para las integraciones con Subastas y, desde 1.6.0, Misiones. */
@Service
public class ConsultarElementoInventario {

    private final RepositorioDeInventarios repositorio;

    public ConsultarElementoInventario(RepositorioDeInventarios repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio es obligatorio");
    }

    public DetalleElementoInventario consultar(String elementoId) {
        if (elementoId == null || elementoId.isBlank()) {
            throw new IllegalArgumentException("elementoId no puede estar vacio");
        }
        Inventario inventario = repositorio.buscarPorElementoId(elementoId.trim())
                .orElseThrow(ElementoNoEncontradoException::new);
        ElementoInventario elemento = inventario.elemento(elementoId.trim());

        boolean heroe = elemento.tipo() == TipoElementoInventario.HEROE;
        return new DetalleElementoInventario(
                elemento.id(),
                comoUuid(elemento.productoId()),
                comoUuid(inventario.propietarioId()),
                inventario.estaEnUso(elemento.id()),
                elemento.disponible(),
                elemento.subastaId() == null ? null : comoUuid(elemento.subastaId()),
                // 1.6.0 (B9): lo que misiones comprueba antes de matricular.
                elemento.tipo(),
                elemento.nombrePropio(),
                heroe ? elemento.nivelActual() : null,
                heroe ? elemento.experienciaActual() : null,
                elemento.ejecucionMisionId() == null ? null : comoUuid(elemento.ejecucionMisionId()));
    }

    private UUID comoUuid(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException error) {
            throw new IdentificadorHistoricoException();
        }
    }
}
