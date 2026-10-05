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
 * <p>El <b>nivel</b>, desde B7, es el del elemento de inventario cuando
 * inventario lo publica, y 1 cuando no: todo heroe empieza en el nivel 1
 * (§6.1.1) y es el nivel con el que combate mientras nadie lo suba. Antes de
 * B7 se dejaba nulo porque ningun servicio lo persistia y el combate no lo
 * usaba; ahora el motor lo usa (acciones desbloqueadas y multiplicador), asi
 * que el que se muestra es el mismo con el que se combate.
 *
 * <p>El <b>perfil</b> (B7) es lo que el heroe lleva al combate ademas de la
 * vida: nivel, estadisticas con el equipo aplicado, nombres del equipamiento y
 * epicas. Nulo en fichas anteriores a V14 y en las pruebas que no lo usan: el
 * motor resuelve entonces con el catalogo en nivel 1 y sin equipo.
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
 * @param nivel       nivel del heroe, o {@code null} en fichas anteriores a B7.
 *                    Ver la nota de arriba
 * @param vidaActual  vida con la que llega a la sala
 * @param vidaMaxima  vida maxima con su equipamiento aplicado
 * @param defensa     defensa del prototipo, o {@code null} si no se conoce.
 *                    <b>No es la vida.</b> El motor acierta si la tirada de
 *                    ataque supera la defensa: mandando la vida en su lugar
 *                    —44 en «Guerrero Tanque», contra un ataque maximo de 16—
 *                    ningun golpe podia acertar nunca. Anulable por lo mismo
 *                    que el prototipo: filas anteriores a V8 y catalogo que no
 *                    contesta.
 * @param perfil      lo que lleva al combate (B7), o {@code null}
 */
public record HeroeDeCombate(
        String id,
        String nombre,
        String prototipo,
        String retratoUrl,
        Integer nivel,
        int vidaActual,
        int vidaMaxima,
        Integer defensa,
        PerfilDeCombate perfil) {

    /** El heroe sin perfil de combate: fichas anteriores a V14 y pruebas que no lo usan. */
    public HeroeDeCombate(String id, String nombre, String prototipo, String retratoUrl, Integer nivel,
                          int vidaActual, int vidaMaxima, Integer defensa) {
        this(id, nombre, prototipo, retratoUrl, nivel, vidaActual, vidaMaxima, defensa, null);
    }

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
                Math.max(0, Math.min(vidaActual, vidaMaxima)), vidaMaxima, defensa, perfil);
    }

    /**
     * El mismo heroe con la vida y la vida maxima que resolvio el motor (B7).
     * La maxima puede cambiar respecto a la de la sala: el motor la calcula en
     * el nivel del heroe, y la ficha de la sala la trae de inventario.
     */
    public HeroeDeCombate conVida(int vidaActual, int vidaMaxima) {
        int maxima = Math.max(1, vidaMaxima);
        return new HeroeDeCombate(id, nombre, prototipo, retratoUrl, nivel,
                Math.max(0, Math.min(vidaActual, maxima)), maxima, defensa, perfil);
    }

    /** El mismo heroe con la vida al maximo. */
    public HeroeDeCombate aPlenaVida() {
        return conVida(vidaMaxima);
    }

    /**
     * Un rival de la maquina del mismo prototipo y nivel que este heroe, como
     * los del catalogo (D-B7-11): su propio identificador, el nombre del
     * prototipo, sin retrato y sin equipo ni epicas ({@link PerfilDeCombate#delCatalogo}).
     *
     * <p>Es el respaldo cuando el catalogo de heroes no contesta al empezar.
     * Antes la maquina combatia con una COPIA exacta del heroe del anfitrion
     * —mismo nombre, mismo retrato y su equipo, Pinchos de escudo incluidos— y
     * el registro decia «Aquiles golpea a Aquiles (tu)»: el jugador veia que su
     * ataque le quitaba vida a el mismo.
     */
    public HeroeDeCombate comoRivalDeLaMaquina() {
        int nivelDelRival = nivelDeCombate();
        String nombreDelRival = prototipo != null && !prototipo.isBlank() ? prototipo : NOMBRE_DE_LA_MAQUINA;
        return new HeroeDeCombate(java.util.UUID.randomUUID().toString(), nombreDelRival, prototipo, null,
                nivelDelRival, vidaMaxima, vidaMaxima, defensa, PerfilDeCombate.delCatalogo(nivelDelRival));
    }

    /** Nombre del rival de la maquina cuando no se conoce su prototipo. */
    public static final String NOMBRE_DE_LA_MAQUINA = "Rival de la máquina";

    /** El mismo heroe con lo que lleva al combate (B7). */
    public HeroeDeCombate conPerfil(PerfilDeCombate perfil) {
        return new HeroeDeCombate(id, nombre, prototipo, retratoUrl,
                perfil == null ? nivel : Integer.valueOf(perfil.nivel()), vidaActual, vidaMaxima, defensa, perfil);
    }

    /** El nivel con el que combate: el del perfil, el publicado, o 1 (§6.1.1). */
    public int nivelDeCombate() {
        if (perfil != null) {
            return perfil.nivel();
        }
        return nivel == null ? PerfilDeCombate.NIVEL_MINIMO : nivel;
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
     * <p>El {@code nivel} no se pasa aqui: llega con el perfil de combate
     * ({@link #conPerfil}), que es donde se decide de donde sale (B7).
     */
    public static HeroeDeCombate aPleno(String id, String nombre, String prototipo,
                                        int vidaMaxima, Integer defensa, String retratoUrl) {
        return new HeroeDeCombate(
                id, nombre, prototipo, retratoUrl, null, vidaMaxima, vidaMaxima, defensa);
    }
}
