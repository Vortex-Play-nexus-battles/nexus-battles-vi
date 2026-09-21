package nexus.inventario.aplicacion;

import java.util.Objects;
import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;

@Service
public class GestionarBloqueoSubasta {

    private final RepositorioDeInventarios repositorio;

    public GestionarBloqueoSubasta(RepositorioDeInventarios repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio es obligatorio");
    }

    public ElementoInventario bloquear(
            UUID propietarioUid,
            String elementoId,
            UUID subastaId,
            String claveIdempotencia) {
        Objects.requireNonNull(propietarioUid, "propietarioUid es obligatorio");
        Objects.requireNonNull(subastaId, "subastaId es obligatorio");
        exigirTexto(claveIdempotencia, "claveIdempotencia");
        String elementoQueSeBloquea = exigirTexto(elementoId, "elementoId");
        Inventario inventario = repositorio.buscarPorElementoId(elementoQueSeBloquea)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventario.propietarioId().equals(propietarioUid.toString())) {
            throw new InventarioAjenoException();
        }
        Inventario guardado = repositorio.guardar(
                inventario.bloquearEnSubasta(elementoQueSeBloquea, subastaId.toString()));
        return guardado.elemento(elementoQueSeBloquea);
    }

    public ElementoInventario liberar(
            String elementoId,
            UUID subastaId,
            String claveIdempotencia) {
        exigirTexto(claveIdempotencia, "claveIdempotencia");
        Objects.requireNonNull(subastaId, "subastaId es obligatorio");
        String subastaQueFinalizo = subastaId.toString();
        String elementoQueSeLibera = exigirTexto(elementoId, "elementoId");
        Inventario inventario = repositorio.buscarPorElementoId(elementoQueSeLibera)
                .orElseThrow(ElementoNoEncontradoException::new);
        ElementoInventario actual = inventario.elemento(elementoQueSeLibera);
        if (actual.disponible()) {
            return actual;
        }
        Inventario guardado = repositorio.guardar(
                inventario.liberarBloqueoSubasta(elementoQueSeLibera, subastaQueFinalizo));
        return guardado.elemento(elementoQueSeLibera);
    }

    private String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
        return valor.trim();
    }
}
