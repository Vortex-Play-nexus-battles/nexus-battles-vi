package nexus.misiones.dominio;

import java.util.List;

/**
 * El sistema de recompensas de una mision (seccion 7.8.3).
 *
 * @param creditos     «Recompensa garantizada: creditos base por completar»
 * @param garantizadas objetos garantizados («1 Cofre de Bronce»)
 * @param potenciales  «Botin aleatorio: productos con probabilidad variable»
 * @param porObjetivos «Bonificaciones por cumplir metas especificas»
 * @param primeraVez   la «recompensa especial unica» de la primera vez (7.8.9)
 */
public record RecompensasDeMision(
        int creditos,
        List<ObjetoDeRecompensa> garantizadas,
        List<ObjetoPotencial> potenciales,
        List<BonificacionPorObjetivo> porObjetivos,
        PrimeraVez primeraVez) {

    public RecompensasDeMision {
        if (creditos < 0) {
            throw new IllegalArgumentException("Los creditos de una mision no pueden ser negativos.");
        }
        garantizadas = garantizadas == null ? List.of() : List.copyOf(garantizadas);
        potenciales = potenciales == null ? List.of() : List.copyOf(potenciales);
        porObjetivos = porObjetivos == null ? List.of() : List.copyOf(porObjetivos);
        primeraVez = primeraVez == null ? new PrimeraVez(0, List.of()) : primeraVez;
    }

    /**
     * Un objeto de recompensa. {@code productoId} nulo: el documento lo nombra
     * pero no esta en el catalogo oficial, y no se entrega (se informa).
     */
    public record ObjetoDeRecompensa(String nombre, String detalle, int cantidad, String productoId) {

        public ObjetoDeRecompensa {
            if (nombre == null || nombre.isBlank()) {
                throw new IllegalArgumentException("Un objeto de recompensa necesita nombre.");
            }
            if (cantidad < 1) {
                throw new IllegalArgumentException("«" + nombre + "» necesita al menos una unidad.");
            }
        }

        public boolean entregable() {
            return productoId != null && !productoId.isBlank();
        }
    }

    /**
     * Botin con probabilidad. Cada unidad se sortea por separado («Fragmento
     * del Sello Antiguo (60% de probabilidad c/u - 3 en total)»).
     */
    public record ObjetoPotencial(String nombre, double probabilidad, int cantidad, String detalle, String productoId) {

        public ObjetoPotencial {
            if (nombre == null || nombre.isBlank()) {
                throw new IllegalArgumentException("Un botin necesita nombre.");
            }
            if (probabilidad < 0 || probabilidad > 1) {
                throw new IllegalArgumentException("La probabilidad de «" + nombre + "» va de 0 a 1.");
            }
            if (cantidad < 1) {
                throw new IllegalArgumentException("«" + nombre + "» necesita al menos una unidad.");
            }
        }

        public boolean entregable() {
            return productoId != null && !productoId.isBlank();
        }
    }

    /** Creditos extra por cumplir el objetivo en la posicion {@code objetivo} (desde cero). */
    public record BonificacionPorObjetivo(int objetivo, int creditos) {

        public BonificacionPorObjetivo {
            if (objetivo < 0 || creditos < 1) {
                throw new IllegalArgumentException("Una bonificacion necesita objetivo y creditos positivos.");
            }
        }
    }

    /**
     * «Recompensa por primera vez: 10 creditos adicionales. Titulo: Explorador
     * del Templo». {@code otras} son las que no son creditos: no hay servicio
     * de titulos ni insignias (7.8.11), asi que se informan sin entregar.
     */
    public record PrimeraVez(int creditos, List<String> otras) {

        public PrimeraVez {
            if (creditos < 0) {
                throw new IllegalArgumentException("Los creditos de primera vez no pueden ser negativos.");
            }
            otras = otras == null ? List.of() : List.copyOf(otras);
        }
    }
}
