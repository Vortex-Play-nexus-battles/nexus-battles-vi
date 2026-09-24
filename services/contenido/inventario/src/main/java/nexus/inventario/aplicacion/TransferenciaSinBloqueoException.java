package nexus.inventario.aplicacion;

/**
 * Se pidio transferir un elemento que no esta bloqueado por esa subasta.
 *
 * <p>Es una salvaguarda, no un tecnicismo: sin ella, cualquier servicio con
 * credencial podria cambiar de dueno un elemento ajeno inventandose un
 * identificador de subasta.
 */
public class TransferenciaSinBloqueoException extends RuntimeException {

    public TransferenciaSinBloqueoException() {
        super("El elemento no esta bloqueado por esa subasta");
    }
}
