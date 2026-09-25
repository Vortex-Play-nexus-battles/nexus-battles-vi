package nexus.inventario.dominio;

import java.util.Objects;

/**
 * Instancia propia del jugador que referencia un producto del catalogo.
 *
 * <p>1.6.0 (B9, misiones): un HEROE lleva ademas su progresion —{@code nivel}
 * y {@code experiencia}, seccion 6.1.1— y, mientras esta en una mision, la
 * ejecucion que lo bloquea ({@code ejecucionMisionId}, seccion 7.8.10). Los tres
 * son nulos en cualquier otro tipo. NOTA DE FUSION: la fase B4 anade
 * {@code nivel} y {@code experiencia} con estos mismos nombres y tipos.
 *
 * @param nivel             nulo en un heroe que aun no ha progresado = nivel 1
 * @param experiencia       la acumulada dentro de su nivel; nula = 0
 * @param ejecucionMisionId la ejecucion de mision que lo tiene bloqueado, o nulo
 */
public record ElementoInventario(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio,
        ParteArmadura parteArmadura,
        String subastaId,
        Integer nivel,
        Double experiencia,
        String ejecucionMisionId) {

    /** Seccion 6.1.1: el nivel de un heroe va de 1 a 8. */
    public static final int NIVEL_MINIMO = 1;
    public static final int NIVEL_MAXIMO = 8;

    public ElementoInventario(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio) {
        this(id, productoId, tipo, nombrePropio, null, null);
    }

    public ElementoInventario(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura) {
        this(id, productoId, tipo, nombrePropio, parteArmadura, null);
    }

    public ElementoInventario(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura,
            String subastaId) {
        this(id, productoId, tipo, nombrePropio, parteArmadura, subastaId, null, null, null);
    }

    public ElementoInventario {
        exigirTexto(id, "id");
        exigirTexto(productoId, "productoId");
        Objects.requireNonNull(tipo, "tipo no puede ser nulo");
        exigirTexto(nombrePropio, "nombrePropio");
        if (tipo != TipoElementoInventario.ARMADURA && parteArmadura != null) {
            throw new IllegalArgumentException("Solo una armadura puede declarar una parte");
        }
        if (subastaId != null && subastaId.isBlank()) {
            throw new IllegalArgumentException("subastaId no puede estar vacio");
        }
        if (tipo != TipoElementoInventario.HEROE
                && (nivel != null || experiencia != null || ejecucionMisionId != null)) {
            throw new IllegalArgumentException("Solo un heroe tiene nivel, experiencia o mision");
        }
        if (nivel != null && (nivel < NIVEL_MINIMO || nivel > NIVEL_MAXIMO)) {
            throw new IllegalArgumentException("El nivel de un heroe va de 1 a 8");
        }
        if (experiencia != null && (experiencia < 0 || experiencia.isNaN())) {
            throw new IllegalArgumentException("La experiencia no puede ser negativa");
        }
        if (ejecucionMisionId != null && ejecucionMisionId.isBlank()) {
            throw new IllegalArgumentException("ejecucionMisionId no puede estar vacio");
        }
    }

    public ElementoInventario renombrar(String nuevoNombre) {
        exigirDisponible();
        return new ElementoInventario(id, productoId, tipo, nuevoNombre, parteArmadura, subastaId,
                nivel, experiencia, ejecucionMisionId);
    }

    /** Libre para operar: ni en una subasta vigente ni en una mision (1.6.0). */
    public boolean disponible() {
        return subastaId == null && ejecucionMisionId == null;
    }

    public boolean enMision() {
        return ejecucionMisionId != null;
    }

    /** El nivel del heroe, 1 si todavia no ha progresado. */
    public int nivelActual() {
        return nivel == null ? NIVEL_MINIMO : nivel;
    }

    /** La experiencia dentro de su nivel, 0 si todavia no ha progresado. */
    public double experienciaActual() {
        return experiencia == null ? 0 : experiencia;
    }

    public ElementoInventario bloquearEnSubasta(String nuevaSubastaId) {
        exigirTexto(nuevaSubastaId, "subastaId");
        if (nuevaSubastaId.equals(subastaId)) {
            return this;
        }
        if (enMision()) {
            throw new HeroeEnMisionException("El heroe esta en una mision y no se puede publicar en subasta.");
        }
        if (!disponible()) {
            throw new ElementoNoDisponibleException(
                    "El producto ya esta bloqueado por otra subasta vigente.");
        }
        return new ElementoInventario(
                id, productoId, tipo, nombrePropio, parteArmadura, nuevaSubastaId, nivel, experiencia, null);
    }

    public ElementoInventario liberarBloqueoSubasta(String subastaQueFinalizo) {
        exigirTexto(subastaQueFinalizo, "subastaId");
        if (subastaId == null) {
            return this;
        }
        if (!subastaId.equals(subastaQueFinalizo)) {
            throw new ElementoNoDisponibleException(
                    "El aviso no corresponde a la subasta que mantiene el bloqueo.");
        }
        return new ElementoInventario(id, productoId, tipo, nombrePropio, parteArmadura, null,
                nivel, experiencia, ejecucionMisionId);
    }

    /**
     * El heroe sale a una mision (7.8.6, «queda bloqueado y no puede usarse en
     * otros modos»). Idempotente para la misma ejecucion.
     *
     * @throws NoEsUnHeroeException     si no es un heroe
     * @throws ElementoNoDisponibleException si esta en una subasta vigente
     * @throws HeroeEnMisionException   si ya esta en OTRA mision
     */
    public ElementoInventario bloquearEnMision(String ejecucionId) {
        exigirTexto(ejecucionId, "ejecucionId");
        if (tipo != TipoElementoInventario.HEROE) {
            throw new NoEsUnHeroeException();
        }
        if (ejecucionId.equals(ejecucionMisionId)) {
            return this;
        }
        if (subastaId != null) {
            throw new ElementoNoDisponibleException("El heroe esta bloqueado por una subasta vigente.");
        }
        if (enMision()) {
            throw new HeroeEnMisionException("El heroe ya esta en otra mision.");
        }
        return new ElementoInventario(id, productoId, tipo, nombrePropio, parteArmadura, null,
                nivel, experiencia, ejecucionId);
    }

    /**
     * El heroe vuelve de la mision con su progresion ya calculada (7.8.6, «el
     * heroe es liberado y regresa al inventario»).
     *
     * @throws HeroeEnMisionException si lo bloquea otra ejecucion
     */
    public ElementoInventario liberarDeMision(String ejecucionId, int nuevoNivel, double nuevaExperiencia) {
        exigirTexto(ejecucionId, "ejecucionId");
        if (!enMision()) {
            return this;
        }
        if (!ejecucionMisionId.equals(ejecucionId)) {
            throw new HeroeEnMisionException("El heroe esta bloqueado por otra ejecucion de mision.");
        }
        return new ElementoInventario(id, productoId, tipo, nombrePropio, parteArmadura, subastaId,
                nuevoNivel, nuevaExperiencia, null);
    }

    public void exigirDisponible() {
        if (enMision()) {
            throw new HeroeEnMisionException("El heroe esta en una mision y no se puede modificar hasta que vuelva.");
        }
        if (!disponible()) {
            throw new ElementoNoDisponibleException(
                    "El producto esta bloqueado por una subasta vigente.");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio");
        }
    }
}
