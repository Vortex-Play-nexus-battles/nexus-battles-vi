package nexus.misiones.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import nexus.misiones.aplicacion.HeroeNoEncontrado;
import nexus.misiones.aplicacion.HeroeOcupado;
import nexus.misiones.aplicacion.InventarioDeHeroes;
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
    public HeroeDelInventario consultar(String heroeId) {
        Contestacion<Detalle> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/inventario/elementos/{id}", heroeId)
                .retrieve()
                .body(Detalle.class));
        if (c.rechazada()) {
            // 409: el inventario historico no usa UUID; para misiones es lo
            // mismo que no encontrarlo.
            if (c.estado() == 404 || c.estado() == 409 || c.estado() == 400) {
                throw new HeroeNoEncontrado();
            }
            throw c.comoRechazo(DEPENDENCIA);
        }
        Detalle d = c.cuerpo();
        return new HeroeDelInventario(d.elementoId(), d.productoId(), d.propietarioUid(), d.tipo(), d.nombrePropio(),
                d.disponible(), d.subastaId(), d.ejecucionMisionId(), d.nivel(), d.experiencia());
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
        return new EstadisticasDelHeroe(c.cuerpo().poder(), c.cuerpo().vida(), c.cuerpo().defensa());
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
    public void entregar(String jugadorUid, UUID ejecucionId, List<ProductoAEntregar> productos,
                         String claveIdempotencia) {
        SolicitudDeEntrega solicitud = new SolicitudDeEntrega(jugadorUid, "MISION", "mision-" + ejecucionId,
                productos.stream().map(p -> new ProductoEntregado(p.productoId(), p.cantidad())).toList());
        Contestacion<Object> c = Contestacion.protegida(cortaEntregas, () -> {
            try {
                return http.post()
                        .uri(base + "/api/v1/inventario/entregas")
                        .header("Idempotency-Key", claveIdempotencia)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(solicitud)
                        .retrieve()
                        .body(Object.class);
            } catch (HttpClientErrorException.NotFound | HttpClientErrorException.MethodNotAllowed sinRuta) {
                throw new IllegalStateException("El inventario no expone todavia POST /api/v1/inventario/entregas",
                        sinRuta);
            }
        });
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Detalle(String elementoId, String productoId, String propietarioUid, boolean enUso, boolean disponible,
                   String subastaId, String tipo, String nombrePropio, Integer nivel, Double experiencia,
                   String ejecucionMisionId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Equipamiento(String heroeId, List<String> armas, Map<String, String> armaduras, List<String> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Estadisticas(String heroeId, int poder, int vida, int defensa) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Elemento(String id, String tipo, Integer nivel, Double experiencia, String ejecucionMisionId) {
    }

    record BloquearEnMision(String propietarioUid, String ejecucionId) {
    }

    record LiberarDeMision(String propietarioUid, double experiencia) {
    }

    record SolicitudDeEntrega(String uid, String origen, String referencia, List<ProductoEntregado> productos) {
    }

    record ProductoEntregado(String productoId, int cantidad) {
    }
}
