package nexus.inventario.aplicacion;

import java.util.Objects;
import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;

/** Consulta una unidad concreta para la integracion Inventario-Subastas. */
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

        return new DetalleElementoInventario(
                elemento.id(),
                comoUuid(elemento.productoId()),
                comoUuid(inventario.propietarioId()),
                inventario.estaEnUso(elemento.id()),
                elemento.disponible(),
                elemento.subastaId() == null ? null : comoUuid(elemento.subastaId()));
    }

    private UUID comoUuid(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException error) {
            throw new IdentificadorHistoricoException();
        }
    }
}
