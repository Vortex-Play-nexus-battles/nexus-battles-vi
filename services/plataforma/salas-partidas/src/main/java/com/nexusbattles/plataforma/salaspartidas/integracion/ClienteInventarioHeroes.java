package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroeDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.JugadorAutenticado;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadisticasDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.PerfilDeCombate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptador hacia inventario para la verificacion de heroe — HU-SAL-003.
 *
 * <p>Habla con {@code contracts/openapi/inventario.yaml}, del equipo de
 * contenido, sin reglas propias:
 *
 * <ol>
 *   <li>{@code GET /api/v1/inventario/elementos} — la vitrina del jugador, de
 *       donde salen sus heroes y si estan disponibles (HU-INV-001, HU-INV-010),
 *       y desde B7 tambien sus armas, armaduras, items y epicas.</li>
 *   <li>{@code GET /api/v1/inventario/heroes/{id}/equipamiento} — que lleva
 *       puesto. Sin nada puesto, no esta equipado (RF-JUE-003).</li>
 *   <li>{@code GET /api/v1/inventario/heroes/{id}/estadisticas} — sus
 *       estadisticas con el equipamiento ya aplicado (HU-INV-006).</li>
 *   <li>{@code GET /api/v1/productos/{productoId}} — el prototipo y el retrato
 *       del heroe, y desde B7 el nombre de cada objeto equipado y de cada
 *       epica: el motor de combate aplica sus efectos por nombre (Tablas 8 a
 *       20).</li>
 *   <li>{@code GET /api/v1/heroes/{prototipo}} y, para un heroe de nivel mayor
 *       que 1, {@code GET /api/v1/heroes/{prototipo}/niveles/{nivel}} — el
 *       catalogo de heroes, para la defensa y para llevar las estadisticas con
 *       el equipo al nivel del heroe.</li>
 * </ol>
 *
 * <p><b>El perfil de combate (B7).</b> La misma consulta que abre la puerta
 * captura lo que el heroe lleva al combate: nivel, estadisticas en su nivel con
 * el equipo, nombres del equipamiento y epicas. El heroe con el que alguien
 * entra es el que combate. <b>Nivel:</b> el del elemento de inventario cuando
 * inventario lo publica, y 1 cuando no. <b>Estadisticas:</b> inventario las
 * publica en el nivel 1 con el equipo (HU-INV-006); para un heroe de nivel
 * mayor se suma lo que aporta el equipo a las del catalogo en su nivel
 * (escalado HU-HER-008). Si no se pueden componer, van nulas y el motor usa las
 * del catalogo en su nivel, sin el equipo plano.
 *
 * <p><b>La identidad va en {@code X-User-Name} y es el identificador estable
 * del jugador</b> (el {@code uid} de ADR-002), no su apodo (inventario 1.1.1,
 * #575).
 *
 * <p><b>Que elige cuando hay varios heroes.</b> El contrato de inventario no
 * marca ninguno como «el activo». Se toma el primero que este disponible y
 * equipado, en el orden de la vitrina. Si ninguno lo esta, el resultado explica
 * cual de las dos cosas falla.
 *
 * <p><b>Si inventario no responde</b>, las llamadas a inventario salen por el
 * corta circuitos de HU-DIS-003 como {@link DependenciaDegradada}; un 4xx es
 * {@link InventarioNoDisponible}. Las llamadas a productos y al catalogo de
 * heroes no van por aqui: degradan solas y el combate con ellas.
 */
@Component
class ClienteInventarioHeroes implements HeroeDelJugador {

    /** Tope de paginas que se recorren buscando un heroe utilizable. */
    static final int PAGINAS_MAXIMAS = 10;

    private static final String TIPO_HEROE = "HEROE";
    private static final String TIPO_EPICA = "EPICA";

    /** Contrato de motor-combate: hasta ocho epicas por combatiente. */
    private static final int EPICAS_MAXIMAS = 8;

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
        List<ElementoInventario> vitrina = vitrinaDe(jugador);
        List<ElementoInventario> heroes = vitrina.stream()
                .filter(e -> TIPO_HEROE.equalsIgnoreCase(e.tipo()))
                .toList();

        if (heroes.isEmpty()) {
            return EstadoDelHeroe.sinHeroeEquipado();
        }

        // Primera pasada: alguno disponible y equipado cierra la pregunta.
        for (ElementoInventario heroe : heroes) {
            if (heroe.disponible()) {
                Equipamiento equipamiento = equipamientoDe(jugador, heroe.id());
                if (!equipamiento.vacio()) {
                    return EstadoDelHeroe.disponible(conPerfil(jugador, heroe, equipamiento, vitrina));
                }
            }
        }

        // Ninguno sirve. El motivo importa: equipar y esperar a que se libere
        // son acciones distintas, y el dialogo tiene una variante para cada una.
        for (ElementoInventario heroe : heroes) {
            if (!heroe.disponible()) {
                Equipamiento equipamiento = equipamientoDe(jugador, heroe.id());
                if (!equipamiento.vacio()) {
                    return EstadoDelHeroe.ocupado(conPerfil(jugador, heroe, equipamiento, vitrina),
                            motivoDeBloqueo(heroe));
                }
            }
        }
        return EstadoDelHeroe.sinHeroeEquipado();
    }

    /** Recorre la vitrina entera: heroes, y lo que llevan encima y las epicas. */
    private List<ElementoInventario> vitrinaDe(JugadorAutenticado jugador) {
        List<ElementoInventario> elementos = new ArrayList<>();
        for (int pagina = 0; pagina < PAGINAS_MAXIMAS; pagina++) {
            PaginaInventario respuesta = pedir(
                    urlBase + "/api/v1/inventario/elementos?pagina=" + pagina,
                    jugador, PaginaInventario.class);
            if (respuesta == null || respuesta.elementos() == null) {
                throw new InventarioNoDisponible("respuesta vacia de la vitrina");
            }
            elementos.addAll(respuesta.elementos());
            if (respuesta.ultima() || respuesta.elementos().isEmpty()) {
                break;
            }
        }
        return elementos;
    }

    /**
     * Que lleva puesto. Equipado es llevar algo: un arma, una armadura o un
     * item (lectura literal de RF-JUE-003 contra lo que inventario publica).
     */
    private Equipamiento equipamientoDe(JugadorAutenticado jugador, String idHeroe) {
        Equipamiento equipamiento = pedir(
                urlBase + "/api/v1/inventario/heroes/" + idHeroe + "/equipamiento",
                jugador, Equipamiento.class);
        if (equipamiento == null) {
            throw new InventarioNoDisponible("respuesta vacia del equipamiento");
        }
        return equipamiento;
    }

    /**
     * El heroe con su vida y su perfil de combate.
     *
     * <p>Si inventario no da las estadisticas, el heroe se muestra igual: la
     * verificacion previa sirve para decidir si se puede entrar. Un punto de
     * vida es el minimo que admite el contrato.
     */
    private HeroeDeCombate conPerfil(JugadorAutenticado jugador, ElementoInventario heroe,
                                     Equipamiento equipamiento, List<ElementoInventario> vitrina) {
        Estadisticas estadisticas = pedir(
                urlBase + "/api/v1/inventario/heroes/" + heroe.id() + "/estadisticas",
                jugador, Estadisticas.class);
        Map<String, Producto> productos = new HashMap<>();
        Producto producto = productoDe(heroe.productoId(), productos);
        String prototipo = producto == null ? null : producto.prototipo();
        int nivel = nivelDe(heroe);
        FichaDeHeroe ficha = fichaDe(prototipo);

        EstadisticasDeCombate enSuNivel = enSuNivel(estadisticas, ficha, prototipo, nivel);
        int vida = enSuNivel != null ? enSuNivel.vida()
                : estadisticas == null || estadisticas.vida() == null || estadisticas.vida() < 1
                ? 1 : estadisticas.vida();
        Integer defensa = enSuNivel != null ? Integer.valueOf(enSuNivel.defensa())
                : ficha == null || ficha.estadisticasNivel1() == null ? null : ficha.estadisticasNivel1().defensa();

        PerfilDeCombate perfil = new PerfilDeCombate(nivel, enSuNivel,
                nombresDelEquipamiento(equipamiento, vitrina, productos),
                epicasDe(vitrina, productos));
        return HeroeDeCombate.aPleno(heroe.id(), heroe.nombrePropio(), prototipo, vida, defensa,
                retratoDe(producto)).conPerfil(perfil);
    }

    /** El nivel del elemento cuando inventario lo publica; 1 cuando no (§6.1.1: todo heroe empieza en el 1). */
    private static int nivelDe(ElementoInventario heroe) {
        Integer nivel = heroe.nivel();
        if (nivel == null || nivel < PerfilDeCombate.NIVEL_MINIMO) {
            return PerfilDeCombate.NIVEL_MINIMO;
        }
        return Math.min(nivel, PerfilDeCombate.NIVEL_MAXIMO);
    }

    /**
     * Las estadisticas con el equipo, en el nivel del heroe.
     *
     * <p>Inventario las publica en el nivel 1 (HU-INV-006: parte de las del
     * catalogo en nivel 1). En nivel 1 van tal cual. En uno mayor, lo que
     * aporta el equipo —la diferencia entre las de inventario y las del
     * catalogo en nivel 1— se suma a las del catalogo en su nivel. Si
     * inventario llegara a publicar su {@code nivel} y es el del heroe, se usan
     * tal cual.
     *
     * @return nulas si no se pueden componer: el motor usa entonces las del
     *         catalogo en ese nivel
     */
    private EstadisticasDeCombate enSuNivel(Estadisticas inventario, FichaDeHeroe ficha, String prototipo,
                                            int nivel) {
        if (inventario == null || inventario.poder() == null || inventario.vida() == null
                || inventario.defensa() == null) {
            return null;
        }
        EstadisticasDeCombate conEquipo = new EstadisticasDeCombate(Math.max(0, inventario.poder()),
                Math.max(1, inventario.vida()), Math.max(0, inventario.defensa()),
                FormulaInventario.aDominio(inventario.ataque()), FormulaInventario.aDominio(inventario.dano()),
                FormulaInventario.aDominio(inventario.sanar()));
        if (nivel == PerfilDeCombate.NIVEL_MINIMO || Objects.equals(inventario.nivel(), nivel)) {
            return conEquipo;
        }
        EstadisticasDelPrototipo base = ficha == null ? null : ficha.estadisticasNivel1();
        EstadisticasDelPrototipo enNivel = vistaEnNivel(prototipo, nivel);
        if (base == null || enNivel == null || !base.completas() || !enNivel.completas()) {
            return null;
        }
        return new EstadisticasDeCombate(
                Math.max(0, enNivel.poder() + conEquipo.poder() - base.poder()),
                Math.max(1, enNivel.vida() + conEquipo.vida() - base.vida()),
                Math.max(0, enNivel.defensa() + conEquipo.defensa() - base.defensa()),
                sumarEquipo(enNivel.ataqueDetalle(), conEquipo.ataque(), base.ataqueDetalle()),
                sumarEquipo(enNivel.danoDetalle(), conEquipo.dano(), base.danoDetalle()),
                sumarEquipo(enNivel.sanarDetalle(), conEquipo.sanar(), base.sanarDetalle()));
    }

    /** La formula del catalogo en su nivel mas lo que le suma el equipo (base y dados). */
    private static EstadisticasDeCombate.Formula sumarEquipo(FormulaInventario enNivel,
                                                             EstadisticasDeCombate.Formula conEquipo,
                                                             FormulaInventario base) {
        if (enNivel == null) {
            return null;
        }
        int deltaBase = conEquipo == null || base == null ? 0 : conEquipo.base() - base.base();
        int deltaDados = conEquipo == null || base == null ? 0 : conEquipo.cantidadDados() - base.cantidadDados();
        return new EstadisticasDeCombate.Formula(Math.max(0, enNivel.base() + deltaBase),
                Math.max(0, enNivel.cantidadDados() + deltaDados), Math.max(0, enNivel.caras()));
    }

    /** Nombres de lo que lleva puesto, para que el motor aplique sus efectos (Tablas 8 a 19). */
    private List<String> nombresDelEquipamiento(Equipamiento equipamiento, List<ElementoInventario> vitrina,
                                                Map<String, Producto> productos) {
        Map<String, ElementoInventario> porId = new LinkedHashMap<>();
        vitrina.forEach(e -> porId.putIfAbsent(e.id(), e));
        List<String> nombres = new ArrayList<>();
        for (String idElemento : equipamiento.todos()) {
            ElementoInventario elemento = porId.get(idElemento);
            Producto producto = elemento == null ? null : productoDe(elemento.productoId(), productos);
            if (producto != null && producto.nombre() != null && !producto.nombre().isBlank()) {
                nombres.add(producto.nombre());
            }
        }
        return nombres;
    }

    /** Las epicas del jugador que no estan retenidas por una subasta (Tabla 20). */
    private List<String> epicasDe(List<ElementoInventario> vitrina, Map<String, Producto> productos) {
        List<String> nombres = new ArrayList<>();
        for (ElementoInventario elemento : vitrina) {
            if (!TIPO_EPICA.equalsIgnoreCase(elemento.tipo()) || !elemento.disponible()) {
                continue;
            }
            Producto producto = productoDe(elemento.productoId(), productos);
            if (producto != null && producto.nombre() != null && !producto.nombre().isBlank()
                    && !nombres.contains(producto.nombre()) && nombres.size() < EPICAS_MAXIMAS) {
                nombres.add(producto.nombre());
            }
        }
        return nombres;
    }

    /**
     * El retrato del heroe: {@code imagen} del producto del catalogo (R8), tal
     * cual. En blanco cuenta como ausente: una cadena vacia en un {@code src}
     * pide la pagina actual como si fuera una imagen.
     */
    private static String retratoDe(Producto producto) {
        if (producto == null || producto.imagen() == null || producto.imagen().isBlank()) {
            return null;
        }
        return producto.imagen();
    }

    /**
     * La ficha del prototipo en el catalogo de heroes: la defensa y las
     * estadisticas del nivel 1. Nula si falla: el combate degrada, pero entrar a
     * la sala sigue funcionando.
     */
    private FichaDeHeroe fichaDe(String prototipo) {
        if (prototipo == null || prototipo.isBlank()) {
            return null;
        }
        try {
            return restClient.get()
                    // Sin URLEncoder: los nombres de prototipo llevan espacios
                    // y el encoder los convierte en '+', que el catalogo no
                    // reconoce. Mismo cuidado que tiene inventario.
                    .uri(urlHeroes + "/api/v1/heroes/{prototipo}", prototipo)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(FichaDeHeroe.class);
        } catch (RestClientException catalogoNoDisponible) {
            return null;
        }
    }

    /** Las estadisticas del prototipo en un nivel (HU-HER-008), o nulas si el catalogo no responde. */
    private EstadisticasDelPrototipo vistaEnNivel(String prototipo, int nivel) {
        try {
            VistaPorNivel vista = restClient.get()
                    .uri(urlHeroes + "/api/v1/heroes/{prototipo}/niveles/{nivel}", prototipo, nivel)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(VistaPorNivel.class);
            return vista == null ? null : vista.estadisticas();
        } catch (RestClientException catalogoNoDisponible) {
            return null;
        }
    }

    /**
     * La ficha de un producto: prototipo y retrato del heroe, o nombre de un
     * objeto o de una epica. Una sola peticion por producto en cada consulta.
     * Nula si productos no responde: degrada el combate, no la entrada.
     */
    private Producto productoDe(String productoId, Map<String, Producto> yaPedidos) {
        if (productoId == null || productoId.isBlank()) {
            return null;
        }
        if (yaPedidos.containsKey(productoId)) {
            return yaPedidos.get(productoId);
        }
        Producto producto;
        try {
            producto = restClient.get()
                    .uri(urlProductos + "/api/v1/productos/" + productoId)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Producto.class);
        } catch (RestClientException productoNoDisponible) {
            producto = null;
        }
        yaPedidos.put(productoId, producto);
        return producto;
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

    // -- Formas exactas de las respuestas -----------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaInventario(List<ElementoInventario> elementos, boolean ultima) { }

    /**
     * Elemento de la vitrina. {@code nivel} lo anade B4 al heroe; mientras no
     * lo publique, llega nulo y el heroe combate en el nivel 1.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ElementoInventario(String id, String tipo, String nombrePropio,
                              String productoId, boolean disponible, String subastaId, Integer nivel) {

        ElementoInventario(String id, String tipo, String nombrePropio, String productoId, boolean disponible,
                           String subastaId) {
            this(id, tipo, nombrePropio, productoId, disponible, subastaId, null);
        }
    }

    /**
     * Del producto hacen falta el prototipo y el retrato de un heroe, y el
     * nombre de un objeto o una epica (B7).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Producto(String prototipo, String imagen, String nombre) {

        Producto(String prototipo, String imagen) {
            this(prototipo, imagen, null);
        }
    }

    /** Del catalogo de heroes, las estadisticas del nivel 1. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FichaDeHeroe(EstadisticasDelPrototipo estadisticasNivel1) { }

    /** La vista del prototipo en un nivel. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record VistaPorNivel(EstadisticasDelPrototipo estadisticas) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EstadisticasDelPrototipo(Integer poder, Integer vida, Integer defensa,
                                    FormulaInventario ataqueDetalle, FormulaInventario danoDetalle,
                                    FormulaInventario sanarDetalle) {

        EstadisticasDelPrototipo(Integer defensa) {
            this(null, null, defensa, null, null, null);
        }

        boolean completas() {
            return poder != null && vida != null && defensa != null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FormulaInventario(int base, int cantidadDados, int caras) {

        static EstadisticasDeCombate.Formula aDominio(FormulaInventario f) {
            return f == null ? null
                    : new EstadisticasDeCombate.Formula(Math.max(0, f.base()), Math.max(0, f.cantidadDados()),
                    Math.max(0, f.caras()));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Equipamiento(List<String> armas, Map<String, String> armaduras, List<String> items) {

        boolean vacio() {
            return (armas == null || armas.isEmpty())
                    && (armaduras == null || armaduras.isEmpty())
                    && (items == null || items.isEmpty());
        }

        List<String> todos() {
            List<String> todos = new ArrayList<>();
            if (armas != null) {
                todos.addAll(armas);
            }
            if (armaduras != null) {
                todos.addAll(armaduras.values());
            }
            if (items != null) {
                todos.addAll(items);
            }
            return todos;
        }
    }

    /** Estadisticas equipadas de inventario (HU-INV-006). {@code nivel}: si algun dia lo publica. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Estadisticas(Integer poder, Integer vida, Integer defensa, FormulaInventario ataque,
                        FormulaInventario dano, FormulaInventario sanar, Integer nivel) {

        Estadisticas(Integer vida) {
            this(null, vida, null, null, null, null, null);
        }
    }
}
