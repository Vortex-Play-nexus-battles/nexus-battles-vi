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
        Double experiencia) {

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
                origen, referencia, nivel, experiencia);
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
                origen, referencia, nivel, experiencia);
    }

    public boolean disponible() {
        return subastaId == null;
    }

    public ElementoInventario bloquearEnSubasta(String nuevaSubastaId) {
        exigirTexto(nuevaSubastaId, "subastaId");
        if (nuevaSubastaId.equals(subastaId)) {
            return this;
        }
        if (!disponible()) {
            throw new ElementoNoDisponibleException(
                    "El producto ya esta bloqueado por otra subasta vigente.");
        }
        return new ElementoInventario(
                id, productoId, tipo, nombrePropio, parteArmadura, nuevaSubastaId,
                origen, referencia, nivel, experiencia);
    }

    public ElementoInventario liberarBloqueoSubasta(String subastaQueFinalizo) {
        exigirTexto(subastaQueFinalizo, "subastaId");
        if (disponible()) {
            return this;
        }
        if (!subastaId.equals(subastaQueFinalizo)) {
            throw new ElementoNoDisponibleException(
                    "El aviso no corresponde a la subasta que mantiene el bloqueo.");
        }
        return new ElementoInventario(id, productoId, tipo, nombrePropio, parteArmadura, null,
                origen, referencia, nivel, experiencia);
    }

    public void exigirDisponible() {
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
