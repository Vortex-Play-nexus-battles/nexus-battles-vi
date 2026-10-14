package nexus.auditoria;

/**
 * Un servicio que no es la bitácora central pidió las operaciones
 * administrativas del catálogo — SA-18. Llevan quién hizo cada cosa y desde
 * qué dirección IP: solo las lee quien las guarda (403).
 */
public class LectorNoAutorizadoException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public LectorNoAutorizadoException(String mensaje) {
                super(mensaje);
        }
}
