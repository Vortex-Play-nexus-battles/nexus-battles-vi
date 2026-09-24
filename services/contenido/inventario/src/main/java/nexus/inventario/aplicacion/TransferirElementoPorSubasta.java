package nexus.inventario.aplicacion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.springframework.stereotype.Service;

/**
 * Cambia de dueno un elemento al concluir una subasta (HU-SUB-004).
 *
 * <p>No es un cambio de campo. El inventario esta modelado POR DUENO, asi que
 * transferir es mover el elemento de un agregado a otro: sale del inventario del
 * vendedor y entra en el del comprador. Son dos escrituras en dos documentos
 * distintos, y este servicio <b>no</b> tiene transacciones de Mongo
 * configuradas: no declara ningun {@code MongoTransactionManager} y Spring Boot
 * no lo anade solo.
 *
 * <h2>Por que el orden de las dos escrituras importa tanto</h2>
 *
 * La primera version de esta clase guardaba <b>primero</b> al vendedor sin el
 * elemento y <b>despues</b> al comprador con el, y lo decia en su javadoc: si el
 * segundo guardado fallaba, el elemento quedaba fuera de los dos inventarios y
 * cada reintento recibia 404 porque ya no habia elemento que buscar. O sea: el
 * objeto se perdia, el vendedor lo habia entregado y el comprador no lo tenia, y
 * no habia forma automatica de recuperarlo.
 *
 * <p>Ahora se guarda <b>primero el destino</b> y despues el origen. La ventana
 * de fallo deja de perder el elemento y pasa a duplicarlo, que es un estado
 * <i>detectable y reparable</i>: el elemento sigue existiendo, y el reintento
 * sabe que sobra una copia porque puede ver las dos. Duplicar es malo; perder es
 * peor, y solo uno de los dos se arregla solo.
 *
 * <p>Para poder verlo hace falta {@link
 * RepositorioDeInventarios#buscarTodosPorElementoId}: {@code buscarPorElementoId}
 * devuelve uno cualquiera de los dos y con eso no se puede decidir nada.
 *
 * <h2>Idempotencia</h2>
 *
 * Por estado, no por clave. La operacion converge desde las tres situaciones
 * posibles: solo el vendedor lo tiene (transferencia normal), lo tienen los dos
 * (transferencia interrumpida: se termina quitando la copia que sobra), o solo lo
 * tiene el comprador (ya estaba hecha: termina bien sin mover nada). Hace falta
 * porque el cierre por vencimiento de ms-subastas reintenta la misma subasta cada
 * 30 s.
 *
 * <h2>El bloqueo se conserva</h2>
 *
 * Solo transfiere quien bloqueo: se exige que el elemento este bloqueado por ESA
 * subasta. Sin esa comprobacion, cualquier servicio con credencial podria mover
 * un elemento ajeno inventandose un identificador de subasta.
 *
 * <p>Y el bloqueo <b>viaja con el elemento</b> en vez de soltarse aqui. La
 * primera version lo liberaba dentro, para ahorrarle a ms-subastas una segunda
 * llamada, y eso rompia la compensacion: cuando el cobro falla despues de haber
 * transferido, ms-subastas intenta devolver el elemento al vendedor con esta
 * misma operacion, y sin bloqueo la precondicion de arriba la rechaza con 409.
 * La compensacion existia, estaba escrita, y no podia funcionar nunca: el ganador
 * se quedaba el objeto sin pagarlo y el unico rastro era una linea de registro.
 *
 * <p>Conservarlo tiene ademas un efecto que el producto quiere: el comprador no
 * puede equipar ni revender un objeto que todavia no ha pagado. Lo libera
 * {@code DELETE .../bloqueo-subasta/{subastaId}} cuando la venta es definitiva,
 * y esa operacion ya es idempotente y no mira quien es el dueno.
 */
@Service
public class TransferirElementoPorSubasta {

    private final RepositorioDeInventarios repositorio;

    public TransferirElementoPorSubasta(RepositorioDeInventarios repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio es obligatorio");
    }

    /**
     * @param claveIdempotencia se acepta y no se persiste: la idempotencia la da
     *        el estado, que es mas fiable que una tabla de claves y no caduca.
     */
    public ElementoInventario transferir(
            String elementoId,
            UUID nuevoPropietarioUid,
            UUID subastaId,
            String claveIdempotencia) {
        Objects.requireNonNull(nuevoPropietarioUid, "nuevoPropietarioUid es obligatorio");
        Objects.requireNonNull(subastaId, "subastaId es obligatorio");
        String elementoQueSeTransfiere = exigirTexto(elementoId, "elementoId");
        String comprador = nuevoPropietarioUid.toString();

        List<Inventario> conElElemento = repositorio.buscarTodosPorElementoId(elementoQueSeTransfiere);
        if (conElElemento.isEmpty()) {
            throw new ElementoNoEncontradoException();
        }

        Optional<Inventario> yaEnDestino = conElElemento.stream()
                .filter(inventario -> inventario.propietarioId().equals(comprador))
                .findFirst();

        if (yaEnDestino.isPresent()) {
            // El destino ya lo tiene. Si alguien mas tambien, es una
            // transferencia que se quedo a medias: se termina el trabajo
            // quitando la copia que sobra, en vez de devolver «ya esta» y dejar
            // el elemento duplicado para siempre.
            for (Inventario sobrante : conElElemento) {
                if (!sobrante.propietarioId().equals(comprador)) {
                    repositorio.guardar(sinElElemento(sobrante, elementoQueSeTransfiere, subastaId));
                }
            }
            return yaEnDestino.get().elemento(elementoQueSeTransfiere);
        }

        Inventario origen = conElElemento.get(0);
        ElementoInventario conSuBloqueo = origen.elemento(elementoQueSeTransfiere);
        if (!subastaId.toString().equals(conSuBloqueo.subastaId())) {
            throw new TransferenciaSinBloqueoException();
        }

        Inventario destino = repositorio.buscarPorPropietario(comprador)
                .orElseGet(() -> Inventario.vacio(comprador));

        // El destino primero, con el bloqueo intacto. Si la segunda escritura
        // falla, el elemento esta duplicado y el reintento lo arregla; al
        // contrario, se perderia.
        Inventario guardado = repositorio.guardar(destino.agregar(conSuBloqueo));
        repositorio.guardar(sinElElemento(origen, elementoQueSeTransfiere, subastaId));
        return guardado.elemento(elementoQueSeTransfiere);
    }

    /**
     * El inventario sin ese elemento.
     *
     * <p>Hay que soltarle el bloqueo antes de quitarlo porque
     * {@code eliminarElemento} se niega a borrar un elemento no disponible, y con
     * razon: nadie deberia poder deshacerse de algo que esta comprometido en una
     * subasta. Aqui la subasta es justamente la que ordena el movimiento, y el
     * bloqueo se suelta solo sobre la copia que va a desaparecer — la que se
     * queda el comprador lo conserva.
     */
    private Inventario sinElElemento(Inventario inventario, String elementoId, UUID subastaId) {
        return inventario
                .liberarBloqueoSubasta(elementoId, subastaId.toString())
                .eliminarElemento(elementoId);
    }

    private String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
        return valor.trim();
    }
}
