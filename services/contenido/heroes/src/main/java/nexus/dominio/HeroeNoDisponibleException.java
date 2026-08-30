package nexus.dominio;

/**
 * El mensaje es apto para mostrarse al usuario: sin codigos de protocolo ni
 * detalles internos (regla del cliente, acta 2026-08-13: "uno como usuario
 * jamas deberia ver un status de HTML").
 */
public class HeroeNoDisponibleException extends RuntimeException {

    public HeroeNoDisponibleException() {
        super("El héroe solicitado no está disponible en el catálogo.");
    }
}
