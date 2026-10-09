package nexus.misiones.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import nexus.misiones.aplicacion.HeroeNoEncontrado;
import nexus.misiones.aplicacion.HeroeOcupado;
import nexus.misiones.aplicacion.InventarioDeHeroes;
import nexus.misiones.dominio.simulacion.Formula;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * {@link InventarioDeHeroes} contra el servicio de inventario
 * (contracts/openapi/inventario.yaml 1.6.0), con la credencial de servicio de
 * misiones (azp = misiones).
 *
 * <p>Dos corta circuitos: uno para lo que necesita la matricula (consultas,
 * bloqueo y liberacion) y otro para las entregas de botin. Asi, si
 * {@code /entregas} todavia no esta desplegado en un entorno (lo implementa la
 * fase B4), los reintentos de la liquidacion no abren el circuito de las
 * matriculas.
 */
public class ClienteInventario implements InventarioDeHeroes {

    static final String DEPENDENCIA = "inventario";

    /** Cabecera con la que un servicio dice a inventario de que jugador habla. */
    static final String CABECERA_JUGADOR = "X-User-Name";

    /** Tope de paginas de la vitrina (16 elementos cada una) al buscar un heroe historico. */
    static final int PAGINAS_DE_VITRINA = 50;

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;
    private final CortaCircuitos cortaEntregas;

    public ClienteInventario(RestClient http, String base, CortaCircuitos corta, CortaCircuitos cortaEntregas) {
        this.http = http;
        this.base = sinBarraFinal(base);
        this.corta = corta;
        this.cortaEntregas = cortaEntregas;
    }

    @Override
    public HeroeDelInventario consultar(String jugadorUid, String heroeId) {
        Contestacion<Detalle> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/inventario/elementos/{id}", heroeId)
                .retrieve()
                .body(Detalle.class));
        if (c.rechazada()) {
            if (c.estado() == 409) {
                // Inventario historico (inventario.yaml: «aun no usa
                // identificadores UUID»): el heroe existe, pero la consulta
                // interna, hecha para subastas, exige que su producto tenga id
                // UUID. Darlo por inexistente dejaba sin misiones a quien tuviera
                // un heroe asi (el banco E2E lo encontro con su kit).
                return buscarEnLaVitrina(jugadorUid, heroeId);
            }
            if (c.estado() == 404 || c.estado() == 400) {
                throw new HeroeNoEncontrado();
            }
            throw c.comoRechazo(DEPENDENCIA);
        }
        Detalle d = c.cuerpo();
        return new HeroeDelInventario(d.elementoId(), d.productoId(), d.propietarioUid(), d.tipo(), d.nombrePropio(),
                d.disponible(), d.subastaId(), d.ejecucionMisionId(), d.nivel(), d.experiencia());
    }

    /**
     * El heroe en la vitrina del jugador ({@code GET /api/v1/inventario/elementos},
     * paginas de dieciseis), leida con la credencial de misiones y el jugador en
     * {@code X-User-Name}. Esta en SU vitrina, asi que es suyo: el dueno es el
     * jugador. Se recorre como mucho {@link #PAGINAS_DE_VITRINA} paginas.
     */
    private HeroeDelInventario buscarEnLaVitrina(String jugadorUid, String heroeId) {
        for (int pagina = 0; pagina < PAGINAS_DE_VITRINA; pagina++) {
            PaginaDeVitrina vitrina = paginaDeVitrina(jugadorUid, pagina);
            List<ElementoDeVitrina> elementos = vitrina.elementos() == null ? List.of() : vitrina.elementos();
            for (ElementoDeVitrina e : elementos) {
                if (heroeId.equals(e.id())) {
                    return new HeroeDelInventario(e.id(), e.productoId(), jugadorUid, e.tipo(), e.nombrePropio(),
                            Boolean.TRUE.equals(e.disponible()), e.subastaId(), e.ejecucionMisionId(), e.nivel(),
                            e.experiencia());
                }
            }
            if (esLaUltima(vitrina, pagina)) {
                break;
            }
        }
        throw new HeroeNoEncontrado();
    }

    /** Una pagina de la vitrina del jugador, leida con la credencial de misiones y el jugador en {@code X-User-Name}. */
    private PaginaDeVitrina paginaDeVitrina(String jugadorUid, int numero) {
        Contestacion<PaginaDeVitrina> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/inventario/elementos?pagina={pagina}", numero)
                .header(CABECERA_JUGADOR, jugadorUid)
                .retrieve()
                .body(PaginaDeVitrina.class));
        if (c.rechazada()) {
            throw noEncontradoOAjeno(c);
        }
        return c.cuerpo();
    }

    private static boolean esLaUltima(PaginaDeVitrina vitrina, int numero) {
        List<ElementoDeVitrina> elementos = vitrina.elementos() == null ? List.of() : vitrina.elementos();
        return Boolean.TRUE.equals(vitrina.ultima())
                || elementos.isEmpty()
                || (vitrina.totalPaginas() != null && numero + 1 >= vitrina.totalPaginas());
    }

    @Override
    public boolean equipado(String jugadorUid, String heroeId) {
        Contestacion<Equipamiento> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/inventario/heroes/{id}/equipamiento", heroeId)
                .header(CABECERA_JUGADOR, jugadorUid)
                .retrieve()
                .body(Equipamiento.class));
        if (c.rechazada()) {
            throw noEncontradoOAjeno(c);
        }
        Equipamiento e = c.cuerpo();
        return !vacia(e.armas()) || (e.armaduras() != null && !e.armaduras().isEmpty()) || !vacia(e.items());
    }

    @Override
    public EstadisticasDelHeroe estadisticas(String jugadorUid, String heroeId) {
        Contestacion<Estadisticas> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/inventario/heroes/{id}/estadisticas", heroeId)
                .header(CABECERA_JUGADOR, jugadorUid)
                .retrieve()
                .body(Estadisticas.class));
        if (c.rechazada()) {
            throw noEncontradoOAjeno(c);
        }
        Estadisticas e = c.cuerpo();
        return new EstadisticasDelHeroe(e.poder(), e.vida(), e.defensa(), e.ataque() == null ? null : e.ataque().aDominio(),
                e.dano() == null ? null : e.dano().aDominio(), e.sanar() == null ? null : e.sanar().aDominio());
    }

    /**
     * Lo que lleva puesto el heroe ({@code GET .../heroes/{id}/equipamiento}: ids
     * de elementos) y las epicas del jugador, ambos como ids de PRODUCTO: el
     * inventario guarda el elemento, el nombre que entiende el motor es el del
     * producto. Se resuelve con la vitrina del jugador, entera (hasta
     * {@link #PAGINAS_DE_VITRINA} paginas), como hace salas-partidas para sus
     * batallas. Una epica retenida por una subasta no se puede usar y no cuenta;
     * un elemento equipado que ya no esta en la vitrina no se manda.
     */
    @Override
    public EquipoDelHeroe equipo(String jugadorUid, String heroeId) {
        Contestacion<Equipamiento> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/inventario/heroes/{id}/equipamiento", heroeId)
                .header(CABECERA_JUGADOR, jugadorUid)
                .retrieve()
                .body(Equipamiento.class));
        if (c.rechazada()) {
            throw noEncontradoOAjeno(c);
        }
        Equipamiento puesto = c.cuerpo();
        List<String> idsEquipados = new ArrayList<>();
        if (puesto.armas() != null) {
            idsEquipados.addAll(puesto.armas());
        }
        if (puesto.armaduras() != null) {
            idsEquipados.addAll(puesto.armaduras().values());
        }
        if (puesto.items() != null) {
            idsEquipados.addAll(puesto.items());
        }

        Map<String, String> productoPorElemento = new java.util.HashMap<>();
        LinkedHashSet<String> epicas = new LinkedHashSet<>();
        for (int pagina = 0; pagina < PAGINAS_DE_VITRINA; pagina++) {
            PaginaDeVitrina vitrina = paginaDeVitrina(jugadorUid, pagina);
            for (ElementoDeVitrina e : vitrina.elementos() == null ? List.<ElementoDeVitrina>of() : vitrina.elementos()) {
                if (e.id() != null && e.productoId() != null) {
                    productoPorElemento.put(e.id(), e.productoId());
                }
                if ("EPICA".equals(e.tipo()) && Boolean.TRUE.equals(e.disponible()) && e.productoId() != null) {
                    epicas.add(e.productoId());
                }
            }
            if (esLaUltima(vitrina, pagina)) {
                break;
            }
        }
        List<String> productosEquipados = idsEquipados.stream()
                .map(productoPorElemento::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new EquipoDelHeroe(productosEquipados, List.copyOf(epicas));
    }

    @Override
    public void bloquear(String heroeId, String jugadorUid, UUID ejecucionId) {
        Contestacion<Object> c = Contestacion.protegida(corta, () -> http.put()
                .uri(base + "/api/v1/inventario/elementos/{id}/bloqueo-mision", heroeId)
                .header("Idempotency-Key", "mision-" + ejecucionId + "-bloqueo")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BloquearEnMision(jugadorUid, ejecucionId.toString()))
                .retrieve()
                .body(Object.class));
        if (c.rechazada()) {
            if (c.estado() == 409) {
                throw c.detalle().toLowerCase(Locale.ROOT).contains("subasta")
                        ? HeroeOcupado.enSubasta()
                        : HeroeOcupado.enMision();
            }
            if (c.estado() == 404) {
                throw new HeroeNoEncontrado();
            }
            throw c.comoRechazo(DEPENDENCIA);
        }
    }

    @Override
    public ProgresionDelHeroe liberar(String heroeId, String jugadorUid, UUID ejecucionId, double experiencia) {
        Contestacion<Elemento> c = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/api/v1/inventario/elementos/{id}/bloqueo-mision/{ejecucion}/liberacion",
                        heroeId, ejecucionId)
                .header("Idempotency-Key", "mision-" + ejecucionId + "-liberacion")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LiberarDeMision(jugadorUid, experiencia))
                .retrieve()
                .body(Elemento.class));
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
        Elemento e = c.cuerpo();
        return new ProgresionDelHeroe(e.nivel() == null ? 1 : e.nivel(),
                e.experiencia() == null ? 0 : e.experiencia());
    }

    /**
     * {@code POST /api/v1/inventario/entregas}. Una ruta que no existe (404,
     * 405: el inventario de ese entorno todavia no trae la fase B4) no es un
     * «no» del inventario sino un «todavia no»: se trata como falta de
     * respuesta y la entrega se reintenta, en vez de darla por perdida.
     */
    @Override
    public List<String> entregar(String jugadorUid, UUID ejecucionId, List<ProductoAEntregar> productos,
                                 String claveIdempotencia) {
        SolicitudDeEntrega solicitud = new SolicitudDeEntrega(jugadorUid, "MISION", "mision-" + ejecucionId,
                productos.stream().map(p -> new ProductoEntregado(p.productoId(), p.cantidad())).toList());
        Contestacion<EntregaRealizada> c = Contestacion.protegida(cortaEntregas, () -> {
            try {
                return http.post()
                        .uri(base + "/api/v1/inventario/entregas")
                        .header("Idempotency-Key", claveIdempotencia)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(solicitud)
                        .retrieve()
                        .body(EntregaRealizada.class);
            } catch (HttpClientErrorException.NotFound | HttpClientErrorException.MethodNotAllowed sinRuta) {
                throw new IllegalStateException("El inventario no expone todavia POST /api/v1/inventario/entregas",
                        sinRuta);
            }
        });
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
        EntregaRealizada entrega = c.cuerpo();
        return entrega == null || entrega.yaTenia() == null ? List.of() : List.copyOf(entrega.yaTenia());
    }

    private static RuntimeException noEncontradoOAjeno(Contestacion<?> c) {
        if (c.estado() == 403 || c.estado() == 404 || c.estado() == 400) {
            return new HeroeNoEncontrado();
        }
        return c.comoRechazo(DEPENDENCIA);
    }

    private static boolean vacia(List<?> lista) {
        return lista == null || lista.isEmpty();
    }

    static String sinBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Solo lo que misiones lee. En Jackson 3 un primitivo ausente no se
     * rellena con su valor por omision (es un error), asi que lo que el
     * contrato no garantiza va en tipos con nulo.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Detalle(String elementoId, String productoId, String propietarioUid, boolean disponible,
                   String subastaId, String tipo, String nombrePropio, Integer nivel, Double experiencia,
                   String ejecucionMisionId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Equipamiento(String heroeId, List<String> armas, Map<String, String> armaduras, List<String> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Estadisticas(String heroeId, int poder, int vida, int defensa, FormulaDetalle ataque, FormulaDetalle dano,
                        FormulaDetalle sanar) {
    }

    /** {@code FormulaDetalle} del contrato: base + cantidadDados dados de N caras (el texto {@code formula} no se lee). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FormulaDetalle(int base, int cantidadDados, int caras) {

        Formula aDominio() {
            return new Formula(base, cantidadDados, caras);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Elemento(String id, String tipo, Integer nivel, Double experiencia, String ejecucionMisionId) {
    }

    /** {@code PaginaInventario} del contrato: solo lo que se lee para buscar un heroe. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaDeVitrina(List<ElementoDeVitrina> elementos, Integer totalPaginas, Boolean ultima) {
    }

    /** {@code ElementoInventario} del contrato (1.6.0). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ElementoDeVitrina(String id, String productoId, String tipo, String nombrePropio, Boolean disponible,
                             String subastaId, Integer nivel, Double experiencia, String ejecucionMisionId) {
    }

    record BloquearEnMision(String propietarioUid, String ejecucionId) {
    }

    record LiberarDeMision(String propietarioUid, double experiencia) {
    }

    record SolicitudDeEntrega(String uid, String origen, String referencia, List<ProductoEntregado> productos) {
    }

    /** Solo lo que misiones lee de la entrega: lo que el jugador ya tenia (inventario 1.7.0; ausente antes). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EntregaRealizada(List<String> yaTenia) {
    }

    record ProductoEntregado(String productoId, int cantidad) {
    }
}
