package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;

/**
 * El heroe con el que un jugador entraria a combatir.
 *
 * <p>Espejo del esquema {@code HeroeEnPartida} de
 * {@code contracts/openapi/salas-partidas.yaml}: solo lo que la sala y la vista
 * de batalla pintan. La ficha completa del heroe pertenece al modulo de
 * contenido y no se copia aqui — este servicio no es su dueno y no la guarda.
 *
 * <p>{@code retratoUrl} y {@code nivel} pueden faltar, y por razones
 * distintas — conviene no confundirlas.
 *
 * <p>El <b>retrato</b> si existe: es {@code imagen} del producto del catalogo,
 * a dos saltos del inventario ({@code elemento.productoId -> producto.imagen}).
 * Desde R8 se propaga, y lo hace el servidor en la llamada a productos que ya
 * hacia para resolver el prototipo: el navegador no pide nada de mas. Sigue
 * siendo anulable porque productos puede no contestar, o el producto puede no
 * tener imagen.
 *
 * <p>El <b>nivel</b> no existe. Ningun servicio lo persiste: en el catalogo de
 * heroes es un parametro de ruta ({@code /heroes/{nombre}/niveles/{nivel}}) y
 * no un atributo guardado, y el documento del inventario
 * ({@code ElementoDocumento}) no tiene columna de nivel. Asi que se deja en
 * {@code null} a proposito. Rellenarlo con un 1, o con cualquier otra cifra,
 * seria ensenarle al jugador un dato que el servidor no conoce.
 *
 * @param id          identificador del heroe en el inventario del jugador
 * @param nombre      nombre propio que le puso su dueno
 * @param prototipo   prototipo del catalogo del que sale este heroe, o
 *                    {@code null} si no se conoce. <b>No es lo mismo que el
 *                    nombre</b>, y confundirlos es lo que rompia el combate: el
 *                    nombre es de quien lo compro («Aquiles»), el prototipo es
 *                    la entrada del catalogo de heroes («Guerrero Tanque»), que
 *                    es lo unico que el motor de combate sabe buscar. Anulable
 *                    porque las filas anteriores a V8 no lo guardaron y porque
 *                    productos puede no contestar.
 * @param retratoUrl  retrato para la vista de batalla, o {@code null} si
 *                    productos no contesta o el producto no tiene imagen
 * @param nivel       siempre {@code null}: no hay nivel persistido en ningun
 *                    servicio. Ver la nota de arriba
 * @param vidaActual  vida con la que llega a la sala
 * @param vidaMaxima  vida maxima con su equipamiento aplicado
 * @param defensa     defensa del prototipo, o {@code null} si no se conoce.
 *                    <b>No es la vida.</b> El motor acierta si la tirada de
 *                    ataque supera la defensa: mandando la vida en su lugar
 *                    —44 en «Guerrero Tanque», contra un ataque maximo de 16—
 *                    ningun golpe podia acertar nunca. Anulable por lo mismo
 *                    que el prototipo: filas anteriores a V8 y catalogo que no
 *                    contesta.
 */
public record HeroeDeCombate(
        String id,
        String nombre,
        String prototipo,
        String retratoUrl,
        Integer nivel,
        int vidaActual,
        int vidaMaxima,
        Integer defensa) {

    /**
     * El heroe sin prototipo conocido.
     *
     * <p>Existe para los sitios que nunca lo supieron —las fichas anteriores a
     * V8 y las pruebas a las que el prototipo no les dice nada—, y para que
     * anadirlo no obligara a tocar dos docenas de llamadas que no tienen
     * opinion sobre el. Un heroe construido asi combate como se combatia antes
     * de V8: mandando su nombre al motor.
     */
    public HeroeDeCombate(String id, String nombre, String retratoUrl, Integer nivel,
                          int vidaActual, int vidaMaxima) {
        this(id, nombre, null, retratoUrl, nivel, vidaActual, vidaMaxima, null);
    }

    public HeroeDeCombate {
        Objects.requireNonNull(id, "Un heroe sin identificador no se puede llevar a una sala.");
        Objects.requireNonNull(nombre, "El dialogo de verificacion nombra al heroe: hace falta su nombre.");
        if (vidaMaxima < 1) {
            throw new IllegalArgumentException("Un heroe con vida maxima menor que uno no puede combatir.");
        }
        if (vidaActual < 0) {
            throw new IllegalArgumentException("La vida actual no puede ser negativa.");
        }
    }

    /**
     * El mismo heroe con otra vida actual.
     *
     * <p>El record es inmutable a proposito: un golpe no muta al heroe, produce
     * uno nuevo. Asi el estado anterior sigue siendo valido mientras se anuncia
     * el cambio, y no hay forma de que dos hilos se pisen la vida.
     *
     * <p>La vida no baja de cero: cero es muerto, y un numero negativo no
     * significa nada que la barra sepa pintar.
     */
    public HeroeDeCombate conVida(int vidaActual) {
        return new HeroeDeCombate(id, nombre, prototipo, retratoUrl, nivel,
                Math.max(0, Math.min(vidaActual, vidaMaxima)), vidaMaxima, defensa);
    }

    /** El mismo heroe con la vida al maximo. */
    public HeroeDeCombate aPlenaVida() {
        return conVida(vidaMaxima);
    }

    /** True cuando ya no puede seguir combatiendo. */
    public boolean derrotado() {
        return vidaActual == 0;
    }

    /**
     * Heroe a pleno: antes de empezar la partida la vida actual es la maxima.
     *
     * <p>La vida que se muestra en el dialogo previo no es la de un combate en
     * curso —todavia no hay combate—, asi que las dos coinciden. La barra de
     * HU-SAL-005 muestra siempre el numero junto al color (RF-JUE-009), y para
     * eso necesita las dos cifras aunque de momento sean la misma.
     */
    public static HeroeDeCombate aPleno(String id, String nombre, int vidaMaxima) {
        return aPleno(id, nombre, null, vidaMaxima);
    }

    /** Igual, con el prototipo del catalogo ya resuelto. */
    public static HeroeDeCombate aPleno(String id, String nombre, String prototipo,
                                        int vidaMaxima) {
        return aPleno(id, nombre, prototipo, vidaMaxima, null);
    }

    /** Igual, con la defensa del prototipo tambien resuelta. */
    public static HeroeDeCombate aPleno(String id, String nombre, String prototipo,
                                        int vidaMaxima, Integer defensa) {
        return aPleno(id, nombre, prototipo, vidaMaxima, defensa, null);
    }

    /**
     * Igual, con el retrato del producto (R8).
     *
     * <p>El {@code nivel} se queda en {@code null} y no hay sobrecarga que lo
     * acepte, a proposito: no existe como estado persistido en ningun
     * servicio, y una firma que lo pidiera invitaria a rellenarlo con algo
     * inventado.
     */
    public static HeroeDeCombate aPleno(String id, String nombre, String prototipo,
                                        int vidaMaxima, Integer defensa, String retratoUrl) {
        return new HeroeDeCombate(
                id, nombre, prototipo, retratoUrl, null, vidaMaxima, vidaMaxima, defensa);
    }
}
