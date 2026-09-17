package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroeDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.JugadorAutenticado;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;
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
 * </ol>
 *
 * <p><b>La identidad va en {@code X-User-Name}</b> porque es lo que inventario
 * exige hoy: «las rutas del jugador conservan temporalmente X-User-Name». No es
 * una decision de este servicio ni una que apruebe — ADR-001 fija OAuth2
 * {@code client_credentials} como destino — pero mientras el proveedor no
 * acepte el token, mandar otra cosa seria mandar algo que no mira.
 *
 * <p><b>Que elige cuando hay varios heroes.</b> El contrato de inventario no
 * marca ninguno como «el activo»: no existe tal campo. Asi que se toma el
 * primero que este disponible y equipado, recorriendo la vitrina en el orden en
 * que la devuelve su dueno. Si ninguno lo esta, el resultado explica cual de las
 * dos cosas falla, que es lo que el dialogo necesita para decir que hacer.
 */
@Component
class ClienteInventarioHeroes implements HeroeDelJugador {

    /** Tope de paginas que se recorren buscando un heroe utilizable. */
    static final int PAGINAS_MAXIMAS = 10;

    private static final String TIPO_HEROE = "HEROE";

    private final RestClient restClient;
    private final String urlBase;

    ClienteInventarioHeroes(RestClient restClientInventario,
                            @Value("${salas.inventario.url}") String urlBase) {
        this.restClient = restClientInventario;
        this.urlBase = urlBase.replaceAll("/+$", "");
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
        return HeroeDeCombate.aPleno(heroe.id(), heroe.nombrePropio(), vida);
    }

    /** Lo que retiene al heroe, con el detalle que inventario da: la subasta. */
    private static String motivoDeBloqueo(ElementoInventario heroe) {
        return heroe.subastaId() == null ? null : "una subasta en curso";
    }

    private <T> T pedir(String url, JugadorAutenticado jugador, Class<T> tipo) {
        try {
            return restClient.get()
                    .uri(url)
                    .header("X-User-Name", jugador.apodo())
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(tipo);
        } catch (RestClientException ex) {
            throw new InventarioNoDisponible(ex);
        }
    }

    // -- Formas exactas de las respuestas de inventario -----------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaInventario(List<ElementoInventario> elementos, boolean ultima) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ElementoInventario(String id, String tipo, String nombrePropio,
                              boolean disponible, String subastaId) { }

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
