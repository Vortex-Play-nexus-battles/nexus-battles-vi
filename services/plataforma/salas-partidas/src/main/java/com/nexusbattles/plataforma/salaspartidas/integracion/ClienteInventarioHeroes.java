package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroeDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.JugadorAutenticado;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Adaptador hacia inventario para la verificacion de heroe — HU-SAL-003.
 *
 * <p>Habla con {@code contracts/openapi/inventario.yaml}, del equipo de
 * contenido, con tres llamadas y ninguna regla propia:
 *
 * <ol>
 *   <li>{@code GET /api/v1/inventario/elementos} — la vitrina del jugador, de
 *       donde salen sus heroes y si estan disponibles (HU-INV-001, HU-INV-010).</li>
 *   <li>{@code GET /api/v1/inventario/heroes/{id}/equipamiento} — que lleva
 *       puesto. Sin nada puesto, no esta equipado (RF-JUE-003).</li>
 *   <li>{@code GET /api/v1/inventario/heroes/{id}/estadisticas} — de donde sale
 *       la vida maxima con el equipamiento ya aplicado.</li>
 *   <li>{@code GET /api/v1/productos/{productoId}} — el prototipo del catalogo
 *       del que sale el heroe. No es de inventario sino de productos, y es
 *       lectura publica; hace falta porque el motor de combate busca al
 *       atacante por prototipo y no por el nombre que le puso su dueno.</li>
 * </ol>
 *
 * <p><b>La identidad va en {@code X-User-Name} y es el identificador estable
 * del jugador</b> (el {@code uid} de ADR-002), no su apodo. Inventario 1.1.1
 * (#575) cambio la clave de propiedad del apodo al identificador estable,
 * precisamente porque el apodo es mutable y reasignable; desde entonces un
 * servicio con credencial «pone en X-User-Name el identificador estable del
 * jugador afectado».
 *
 * <p>Hasta el 22-sep-2026 aqui viajaba el apodo, que era lo que inventario
 * 1.1.0 pedia. Con el contrato nuevo desplegado, inventario buscaba la vitrina
 * de un propietario llamado «anfitriona_e2e» y no encontraba nada: la puerta
 * de heroe respondia «no tienes un heroe equipado» a jugadores que si lo
 * tenian, y con eso caia el camino completo de crear sala y jugar. Es el
 * Riesgo #3 del Charter —un contrato que cambia despues de ser consumido— y se
 * cierra actualizando al consumidor, no revirtiendo al proveedor: la decision
 * de #575 es la correcta.
 *
 * <p><b>Que elige cuando hay varios heroes.</b> El contrato de inventario no
 * marca ninguno como «el activo»: no existe tal campo. Asi que se toma el
 * primero que este disponible y equipado, recorriendo la vitrina en el orden en
 * que la devuelve su dueno. Si ninguno lo esta, el resultado explica cual de las
 * dos cosas falla, que es lo que el dialogo necesita para decir que hacer.
 *
 * <p><b>Si inventario no responde</b> (conexion, tiempo, 5xx, o la credencial
 * de servicio que no se pudo obtener), las tres llamadas a inventario salen por
 * el corta circuitos de HU-DIS-003 como {@link DependenciaDegradada}: 503 con
 * la seccion «Inventario» —el ejemplo literal de la HU— y {@code Retry-After},
 * y tras varios fallos seguidos se deja de llamar hasta que toque reintentar.
 * Un 4xx (inventario contesto, pero rechazo la consulta) es
 * {@link InventarioNoDisponible} y no abre nada. Las llamadas a productos y al
 * catalogo de heroes no van por aqui: ya degradan solas y son de otras
 * dependencias.
 */
@Component
class ClienteInventarioHeroes implements HeroeDelJugador {

    /** Tope de paginas que se recorren buscando un heroe utilizable. */
    static final int PAGINAS_MAXIMAS = 10;

    private static final String TIPO_HEROE = "HEROE";

    private final RestClient restClient;
    private final String urlBase;
    private final String urlProductos;
    private final String urlHeroes;
    private final CortaCircuitos corta;

    ClienteInventarioHeroes(RestClient restClientInventario,
                            @Value("${salas.inventario.url}") String urlBase,
                            @Value("${salas.productos.url}") String urlProductos,
                            @Value("${salas.heroes.url}") String urlHeroes,
                            @Qualifier("cortaInventario") CortaCircuitos corta) {
        this.restClient = restClientInventario;
        this.urlBase = urlBase.replaceAll("/+$", "");
        this.urlProductos = urlProductos.replaceAll("/+$", "");
        this.urlHeroes = urlHeroes.replaceAll("/+$", "");
        this.corta = corta;
    }

    @Override
    public EstadoDelHeroe consultar(JugadorAutenticado jugador) {
        List<ElementoInventario> heroes = heroesDe(jugador);

        if (heroes.isEmpty()) {
            return EstadoDelHeroe.sinHeroeEquipado();
        }

        // Primera pasada: alguno disponible y equipado cierra la pregunta.
        for (ElementoInventario heroe : heroes) {
            if (heroe.disponible() && estaEquipado(jugador, heroe.id())) {
                return EstadoDelHeroe.disponible(conVida(jugador, heroe));
            }
        }

        // Ninguno sirve. El motivo importa: equipar y esperar a que se libere
        // son acciones distintas, y el dialogo tiene una variante para cada una.
        for (ElementoInventario heroe : heroes) {
            if (!heroe.disponible() && estaEquipado(jugador, heroe.id())) {
                return EstadoDelHeroe.ocupado(conVida(jugador, heroe), motivoDeBloqueo(heroe));
            }
        }
        return EstadoDelHeroe.sinHeroeEquipado();
    }

    /** Recorre la vitrina y se queda con los elementos de tipo HEROE. */
    private List<ElementoInventario> heroesDe(JugadorAutenticado jugador) {
        List<ElementoInventario> heroes = new java.util.ArrayList<>();
        for (int pagina = 0; pagina < PAGINAS_MAXIMAS; pagina++) {
            PaginaInventario respuesta = pedir(
                    urlBase + "/api/v1/inventario/elementos?pagina=" + pagina,
                    jugador, PaginaInventario.class);
            if (respuesta == null || respuesta.elementos() == null) {
                throw new InventarioNoDisponible("respuesta vacia de la vitrina");
            }
            respuesta.elementos().stream()
                    .filter(e -> TIPO_HEROE.equalsIgnoreCase(e.tipo()))
                    .forEach(heroes::add);
            if (respuesta.ultima() || respuesta.elementos().isEmpty()) {
                break;
            }
        }
        return heroes;
    }

    /**
     * Equipado es llevar algo puesto: un arma, una armadura o un item.
     *
     * <p>La regla no es de este servicio, es la lectura literal de RF-JUE-003
     * —«heroe equipado»— contra lo unico que inventario publica al respecto. Si
     * contenido decidiera que hace falta un minimo por ranura, cambia alli y
     * aqui no se toca nada.
     */
    private boolean estaEquipado(JugadorAutenticado jugador, String idHeroe) {
        Equipamiento equipamiento = pedir(
                urlBase + "/api/v1/inventario/heroes/" + idHeroe + "/equipamiento",
                jugador, Equipamiento.class);
        if (equipamiento == null) {
            throw new InventarioNoDisponible("respuesta vacia del equipamiento");
        }
        return !equipamiento.vacio();
    }

    /**
     * La vida maxima sale de las estadisticas con el equipamiento aplicado.
     *
     * <p>Si inventario no la da, el heroe se muestra igual: la verificacion
     * previa sirve para decidir si se puede entrar, y quedarse sin dialogo por
     * una cifra que solo adorna seria cambiar un problema pequeno por uno
     * grande. Un punto de vida es el minimo que admite el contrato.
     */
    private HeroeDeCombate conVida(JugadorAutenticado jugador, ElementoInventario heroe) {
        Estadisticas estadisticas = pedir(
                urlBase + "/api/v1/inventario/heroes/" + heroe.id() + "/estadisticas",
                jugador, Estadisticas.class);
        int vida = estadisticas == null || estadisticas.vida() == null || estadisticas.vida() < 1
                ? 1
                : estadisticas.vida();
        // Un solo viaje a productos para las dos cosas que salen de ahi: el
        // prototipo y el retrato. Antes se pedia la misma ficha y se tiraba la
        // imagen, y el resultado era que todos los heroes se veian iguales.
        Producto producto = productoDe(heroe);
        String prototipo = producto == null ? null : producto.prototipo();
        return HeroeDeCombate.aPleno(heroe.id(), heroe.nombrePropio(),
                prototipo, vida, defensaDe(prototipo), retratoDe(producto));
    }

    /**
     * El retrato del heroe: {@code imagen} del producto del catalogo (R8).
     *
     * <p>Es la misma referencia que la vitrina del inventario ya le pone a un
     * {@code <img src>} en el navegador, asi que aqui se pasa tal cual y no se
     * intenta normalizar a URL absoluta: reescribirla obligaria a este servicio
     * a saber donde vive el almacenamiento de imagenes, que es una decision de
     * la plataforma y no suya ({@code productos.yaml}: «su mecanismo de
     * almacenamiento sera definido por la plataforma»).
     *
     * <p>En blanco cuenta como ausente: el contrato declara {@code retratoUrl}
     * anulable, y una cadena vacia en un {@code src} pide la pagina actual como
     * si fuera una imagen.
     */
    private static String retratoDe(Producto producto) {
        if (producto == null || producto.imagen() == null || producto.imagen().isBlank()) {
            return null;
        }
        return producto.imagen();
    }

    /**
     * Defensa del prototipo, del catalogo de heroes.
     *
     * <p><b>Por que hace falta.</b> El motor acierta si la tirada de ataque
     * supera la defensa del objetivo. Sin este dato se le mandaba la vida
     * actual, y las dos cifras no estan en la misma escala: «Guerrero Tanque»
     * ataca con {@code 10+1d6} —de 11 a 16— y tiene 44 de vida. Ningun golpe
     * podia acertar nunca; el combate entero era imposible de ganar.
     *
     * <p>Se lee de {@code GET /api/v1/heroes/{prototipo}}, la misma ruta que ya
     * consume inventario para calcular las estadisticas. Lectura publica, sin
     * cambiar ningun contrato.
     *
     * <p>Como el prototipo: si falla, se devuelve {@code null} y el combate
     * degrada, pero entrar a la sala sigue funcionando.
     */
    private Integer defensaDe(String prototipo) {
        if (prototipo == null || prototipo.isBlank()) {
            return null;
        }
        try {
            FichaDeHeroe ficha = restClient.get()
                    // Sin URLEncoder: los nombres de prototipo llevan espacios
                    // y el encoder los convierte en '+', que el catalogo no
                    // reconoce. Mismo cuidado que tiene inventario.
                    .uri(urlHeroes + "/api/v1/heroes/{prototipo}", prototipo)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(FichaDeHeroe.class);
            return ficha == null || ficha.estadisticasNivel1() == null
                    ? null
                    : ficha.estadisticasNivel1().defensa();
        } catch (RestClientException catalogoNoDisponible) {
            return null;
        }
    }

    /**
     * La ficha del producto: de ahi salen el prototipo y el retrato.
     *
     * <p>Devuelve el producto entero y no solo el prototipo desde R8, porque la
     * {@code imagen} que hace de retrato viene en la misma respuesta. Pedirla
     * aparte serian dos viajes para una sola ficha.
     *
     * <p><b>Por que hace falta el prototipo.</b> El motor de combate busca al atacante en el
     * catalogo de heroes, que indexa por prototipo («Guerrero Tanque»). Hasta
     * ahora se le mandaba el {@code nombrePropio} —el que le puso su dueno,
     * «Aquiles»— y el catalogo respondia 404: <b>ningun ataque se resolvia</b>,
     * y el error se iba a la cola privada del jugador, que la vista no escucha.
     * Lo destapo el E2E del corte vertical.
     *
     * <p><b>Por que por aqui.</b> Inventario ya resuelve el prototipo por dentro
     * para calcular las estadisticas, pero no lo publica en su respuesta.
     * Pedirselo seria cambiar un contrato que no es nuestro. El producto, en
     * cambio, si lo expone en {@code GET /api/v1/productos/{id}}, que es publico
     * y de solo lectura: se consume un campo que ya existe, sin tocar nada
     * ajeno.
     *
     * <p><b>Por que no revienta si falla.</b> Un prototipo desconocido degrada
     * el combate, no la entrada a la sala: la verificacion de HU-SAL-003 sigue
     * pudiendo decir «si» y la barra de vida sigue teniendo sus dos cifras.
     * Fallar aqui dejaria sin jugar a quien solo queria entrar.
     */
    private Producto productoDe(ElementoInventario heroe) {
        if (heroe.productoId() == null || heroe.productoId().isBlank()) {
            return null;
        }
        try {
            return restClient.get()
                    .uri(urlProductos + "/api/v1/productos/" + heroe.productoId())
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Producto.class);
        } catch (RestClientException productoNoDisponible) {
            return null;
        }
    }

    /** Lo que retiene al heroe, con el detalle que inventario da: la subasta. */
    private static String motivoDeBloqueo(ElementoInventario heroe) {
        return heroe.subastaId() == null ? null : "una subasta en curso";
    }

    /**
     * Una consulta a inventario, detras del corta circuitos.
     *
     * @throws DependenciaDegradada  si inventario no responde o el circuito
     *                               esta abierto (HU-DIS-003)
     * @throws InventarioNoDisponible si inventario contesto con un 4xx: no es
     *                               una caida, pero tampoco hay veredicto
     */
    private <T> T pedir(String url, JugadorAutenticado jugador, Class<T> tipo) {
        Contestacion<T> contestacion = Contestacion.protegida(corta, () -> restClient.get()
                .uri(url)
                // Identificador estable, no apodo: inventario 1.1.1 (#575).
                .header("X-User-Name", jugador.id().toString())
                .header("Accept", "application/json")
                .retrieve()
                .body(tipo));
        if (contestacion.rechazada()) {
            throw new InventarioNoDisponible("inventario rechazo la consulta con " + contestacion.estado());
        }
        return contestacion.cuerpo();
    }

    // -- Formas exactas de las respuestas de inventario -----------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaInventario(List<ElementoInventario> elementos, boolean ultima) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ElementoInventario(String id, String tipo, String nombrePropio,
                              String productoId, boolean disponible, String subastaId) { }

    /**
     * Del producto hacen falta dos campos, no uno.
     *
     * <p>Hasta R8 este record declaraba solo {@code prototipo} y la {@code
     * imagen} se descartaba al deserializar. El efecto se veia en la vista:
     * {@code retratoUrl} llegaba nulo siempre y todos los heroes se pintaban
     * con el mismo circulo con una inicial.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Producto(String prototipo, String imagen) { }

    /** Del catalogo de heroes solo interesa la defensa del nivel 1. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FichaDeHeroe(EstadisticasDelPrototipo estadisticasNivel1) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record EstadisticasDelPrototipo(Integer defensa) { }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Equipamiento(List<String> armas, Map<String, String> armaduras, List<String> items) {

        boolean vacio() {
            return (armas == null || armas.isEmpty())
                    && (armaduras == null || armaduras.isEmpty())
                    && (items == null || items.isEmpty());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Estadisticas(Integer vida) { }
}
