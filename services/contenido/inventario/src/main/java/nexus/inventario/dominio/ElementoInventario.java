package nexus.inventario.dominio;

import java.util.Objects;

/**
 * Instancia propia del jugador que referencia un producto del catalogo.
 *
 * <h2>B4</h2>
 * <ul>
 *   <li>{@code origen} y {@code referencia}: el canal por el que llego
 *       ({@link OrigenDeEntrega}) y el hecho que lo causo (la orden, el cofre,
 *       el torneo...). Los pone {@code POST /entregas}; los elementos
 *       anteriores, o creados por {@code POST /elementos}, no los tienen.</li>
 *   <li>{@code nivel} y {@code experiencia}: solo en los HEROE. Es la
 *       persistencia de la progresion (seccion 6.1.1: "el nivel inicial de todos
 *       los personajes es uno y puede incrementarse hasta el nivel 8"); las
 *       formulas las calcula el servicio de heroes. Un heroe sin ellos
 *       —anterior a B4— se lee en nivel 1 con 0 de experiencia. Como se gana
 *       experiencia no se decide aqui: es B9 (misiones).</li>
 * </ul>
 *
 * <h2>B9 (inventario 1.6.0, misiones)</h2>
 * <ul>
 *   <li>{@code ejecucionMisionId}: solo en los HEROE, la ejecucion de mision que
 *       lo tiene bloqueado (seccion 7.8.10, «En mision»), o nulo. Mientras la
 *       tenga, el heroe no esta {@linkplain #disponible() disponible}: no se
 *       equipa, no se renombra, no se borra ni se publica en subasta.</li>
 *   <li>La mision no trae una progresion propia: hace avanzar el {@code nivel} y
 *       la {@code experiencia} de B4 —mismos campos, mismos valores por
 *       omision—. Al liberarlo ({@link #liberarDeMision}) se guardan el nivel y
 *       la experiencia dentro de su nivel que ya calculo la aplicacion.</li>
 * </ul>
 */
public record ElementoInventario(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio,
        ParteArmadura parteArmadura,
        String subastaId,
        OrigenDeEntrega origen,
        String referencia,
        Integer nivel,
        Double experiencia,
        String ejecucionMisionId) {

    public static final int NIVEL_INICIAL = 1;
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

    /** La forma anterior a B4: sin origen, sin referencia y, si es heroe, en nivel 1. */
    public ElementoInventario(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura,
            String subastaId) {
        this(id, productoId, tipo, nombrePropio, parteArmadura, subastaId, null, null, null, null);
    }

    /** La forma de B4: con origen, referencia y progresion, fuera de cualquier mision. */
    public ElementoInventario(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura,
            String subastaId,
            OrigenDeEntrega origen,
            String referencia,
            Integer nivel,
            Double experiencia) {
        this(id, productoId, tipo, nombrePropio, parteArmadura, subastaId, origen, referencia, nivel, experiencia,
                null);
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
        if (referencia != null && referencia.isBlank()) {
            throw new IllegalArgumentException("referencia no puede estar vacia");
        }
        if (tipo == TipoElementoInventario.HEROE) {
            nivel = nivel == null ? NIVEL_INICIAL : nivel;
            experiencia = experiencia == null ? 0d : experiencia;
            if (nivel < NIVEL_INICIAL || nivel > NIVEL_MAXIMO) {
                throw new IllegalArgumentException("El nivel de un heroe va de 1 a 8");
            }
            if (experiencia < 0) {
                throw new IllegalArgumentException("La experiencia no puede ser negativa");
            }
        } else if (nivel != null || experiencia != null) {
            throw new IllegalArgumentException("Solo un heroe tiene nivel y experiencia");
        }
        if (ejecucionMisionId != null) {
            if (tipo != TipoElementoInventario.HEROE) {
                throw new IllegalArgumentException("Solo un heroe puede estar en una mision");
            }
            if (ejecucionMisionId.isBlank()) {
                throw new IllegalArgumentException("ejecucionMisionId no puede estar vacio");
            }
        }
    }

    /** Un elemento recien entregado por un canal valido (B4). */
    public static ElementoInventario entregado(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura,
            OrigenDeEntrega origen,
            String referencia) {
        Objects.requireNonNull(origen, "origen no puede ser nulo");
        return new ElementoInventario(
                id, productoId, tipo, nombrePropio, parteArmadura, null, origen, referencia, null, null);
    }

    public ElementoInventario renombrar(String nuevoNombre) {
        exigirDisponible();
        return new ElementoInventario(id, productoId, tipo, nuevoNombre, parteArmadura, subastaId,
                origen, referencia, nivel, experiencia, ejecucionMisionId);
    }

    /**
     * La misma armadura con la parte que dice el catalogo (B4). La parte de una
     * armadura es del producto, no de quien la creo: los elementos anteriores
     * guardaron la que mando el cliente.
     */
    public ElementoInventario conParte(ParteArmadura parteDelCatalogo) {
        if (tipo != TipoElementoInventario.ARMADURA) {
            throw new IllegalArgumentException("Solo una armadura tiene parte");
        }
        return new ElementoInventario(id, productoId, tipo, nombrePropio, parteDelCatalogo, subastaId,
                origen, referencia, nivel, experiencia, ejecucionMisionId);
    }

    /** Libre para operar: ni en una subasta vigente ni en una mision (1.6.0). */
    public boolean disponible() {
        return subastaId == null && ejecucionMisionId == null;
    }

    public boolean enMision() {
        return ejecucionMisionId != null;
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
                id, productoId, tipo, nombrePropio, parteArmadura, nuevaSubastaId,
                origen, referencia, nivel, experiencia, ejecucionMisionId);
    }

    public ElementoInventario liberarBloqueoSubasta(String subastaQueFinalizo) {
        exigirTexto(subastaQueFinalizo, "subastaId");
        // Solo el bloqueo por subasta: un heroe en mision tampoco esta disponible,
        // pero el aviso de una subasta no le concierne.
        if (subastaId == null) {
            return this;
        }
        if (!subastaId.equals(subastaQueFinalizo)) {
            throw new ElementoNoDisponibleException(
                    "El aviso no corresponde a la subasta que mantiene el bloqueo.");
        }
        return new ElementoInventario(id, productoId, tipo, nombrePropio, parteArmadura, null,
                origen, referencia, nivel, experiencia, ejecucionMisionId);
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
                origen, referencia, nivel, experiencia, ejecucionId);
    }

    /**
     * El heroe vuelve de la mision con su progresion ya calculada (7.8.6, «el
     * heroe es liberado y regresa al inventario»): el mismo {@code nivel} y la
     * misma {@code experiencia} de B4, avanzados.
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
                origen, referencia, nuevoNivel, nuevaExperiencia, null);
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
