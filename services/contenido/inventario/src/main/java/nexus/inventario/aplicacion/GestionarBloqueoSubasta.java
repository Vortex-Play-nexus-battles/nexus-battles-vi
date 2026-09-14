package nexus.inventario.aplicacion;

import java.util.Objects;
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
            String identidad,
            String elementoId,
            String subastaId,
            String claveIdempotencia) {
        String propietarioId = exigirTexto(identidad, "identidad");
        exigirTexto(claveIdempotencia, "claveIdempotencia");
        Inventario inventario = repositorio.buscarPorElementoId(elementoId)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventario.propietarioId().equalsIgnoreCase(propietarioId)) {
            throw new InventarioAjenoException();
        }
        Inventario guardado = repositorio.guardar(
                inventario.bloquearEnSubasta(elementoId, exigirTexto(subastaId, "subastaId")));
        return guardado.elemento(elementoId);
    }

    private String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            if ("identidad".equals(campo)) {
                throw new IdentidadRequeridaException();
            }
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
        return valor.trim();
    }
}
