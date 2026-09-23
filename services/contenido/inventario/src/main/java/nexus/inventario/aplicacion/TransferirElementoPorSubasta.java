package nexus.inventario.aplicacion;

import java.util.Objects;
import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambia de dueno un elemento al concluir una subasta (HU-SUB-004).
 *
 * <p>No es un cambio de campo. El inventario esta modelado POR DUENO, asi que
 * transferir es mover el elemento de un agregado a otro: sale del inventario
 * del vendedor y entra en el del comprador. Por eso va en un servicio propio y
 * en una sola transaccion — si se guardara uno y fallara el otro, el elemento
 * existiria dos veces o ninguna.
 *
 * <p>Solo transfiere quien bloqueo: se exige que el elemento este bloqueado por
 * ESA subasta. Sin esa comprobacion, cualquier servicio con credencial podria
 * mover un elemento ajeno inventandose un identificador de subasta.
 */
@Service
public class TransferirElementoPorSubasta {

    private final RepositorioDeInventarios repositorio;

    public TransferirElementoPorSubasta(RepositorioDeInventarios repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio es obligatorio");
    }

    /**
     * @param claveIdempotencia hoy no se persiste; la idempotencia la da el
     *        estado: si el elemento ya es del nuevo dueno, la operacion
     *        termina bien sin volver a moverlo. El cierre por vencimiento de
     *        ms-subastas corre en transaccion y reintenta la misma subasta cada
     *        30 s, asi que repetir la llamada TIENE que ser inofensivo.
     */
    @Transactional
    public ElementoInventario transferir(
            String elementoId,
            UUID nuevoPropietarioUid,
            UUID subastaId,
            String claveIdempotencia) {
        Objects.requireNonNull(nuevoPropietarioUid, "nuevoPropietarioUid es obligatorio");
        Objects.requireNonNull(subastaId, "subastaId es obligatorio");
        String elementoQueSeTransfiere = exigirTexto(elementoId, "elementoId");
        String comprador = nuevoPropietarioUid.toString();

        Inventario origen = repositorio.buscarPorElementoId(elementoQueSeTransfiere)
                .orElseThrow(ElementoNoEncontradoException::new);

        // Reintento de una transferencia ya aplicada: el elemento ya esta donde
        // tiene que estar. Terminar bien es lo correcto, no fallar.
        if (origen.propietarioId().equals(comprador)) {
            return origen.elemento(elementoQueSeTransfiere);
        }

        ElementoInventario actual = origen.elemento(elementoQueSeTransfiere);
        if (!subastaId.toString().equals(actual.subastaId())) {
            throw new TransferenciaSinBloqueoException();
        }

        // El elemento sale libre y sin equipar: se vende, ya no lo lleva nadie
        // del vendedor. Liberar el bloqueo aqui evita que ms-subastas tenga que
        // hacer dos llamadas y quedarse a medias entre ellas.
        Inventario liberado = origen.liberarBloqueoSubasta(elementoQueSeTransfiere, subastaId.toString());
        // El elemento se relee DESPUES de soltar el bloqueo: 'actual' es la foto
        // de antes y todavia lleva la subasta puesta. Anadir esa foto dejaria al
        // comprador con el objeto bloqueado por una subasta ya cerrada, sin
        // poder usarlo y sin nadie que vaya a soltarlo.
        ElementoInventario yaLibre = liberado.elemento(elementoQueSeTransfiere);
        Inventario vendedorSinElemento = liberado.eliminarElemento(elementoQueSeTransfiere);

        Inventario destino = repositorio.buscarPorPropietario(comprador)
                .orElseGet(() -> Inventario.vacio(comprador));

        repositorio.guardar(vendedorSinElemento);
        Inventario guardado = repositorio.guardar(destino.agregar(yaLibre));
        return guardado.elemento(elementoQueSeTransfiere);
    }

    private String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
        return valor.trim();
    }
}
