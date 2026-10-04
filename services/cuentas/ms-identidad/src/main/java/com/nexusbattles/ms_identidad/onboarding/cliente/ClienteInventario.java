package com.nexusbattles.ms_identidad.onboarding.cliente;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido.Causa;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El heroe y el equipo iniciales, por la API del inventario (Grupo 2,
 * contrato 1.1.0).
 *
 * <p>Llama como servicio (ADR-005) y dice a que jugador afecta en
 * {@code X-User-Name}, con el uid: es la unica forma que el inventario acepta
 * de operar sobre el inventario de otro, y la clave estable con la que guarda
 * la propiedad ({@code IdentidadDelLlamador}).
 *
 * <p>El inventario no tiene idempotencia propia: crear dos veces crea dos
 * elementos. La idempotencia la pone el alta: antes de crear, mira lo que el
 * jugador ya tiene ({@link #elementosDe}) y adopta lo que un intento anterior
 * dejo hecho. Solo un trabajador procesa a un jugador a la vez (el turno de
 * {@code onboarding_jugador}), asi que mirar y luego crear no compite consigo
 * mismo.
 */
@Component
public class ClienteInventario {

    static final String CABECERA_PROPIETARIO = "X-User-Name";
    /** Un jugador recien creado tiene dos elementos; esto es solo un tope de seguridad. */
    static final int MAX_PAGINAS = 20;

    private final RestClient http;
    private final String base;

    @Autowired
    public ClienteInventario(@Value("${app.onboarding.inventario-url:}") String base,
                             CredencialPropia credencial,
                             InterceptorDeTraza traza) {
        this.base = ClientesHttp.sinBarraFinal(base);
        this.http = ClientesHttp.construir(credencial, traza);
    }

    /** Todos los elementos del jugador, recorriendo las paginas. */
    public List<Elemento> elementosDe(UUID uid) {
        ClientesHttp.exigirUrl(base, "inventario");
        List<Elemento> todos = new ArrayList<>();
        try {
            for (int pagina = 0; pagina < MAX_PAGINAS; pagina++) {
                Pagina actual = http.get()
                        .uri(base + "/api/v1/inventario/elementos?pagina={pagina}", pagina)
                        .header(CABECERA_PROPIETARIO, uid.toString())
                        .retrieve()
                        .body(Pagina.class);
                if (actual == null || actual.elementos() == null || actual.elementos().isEmpty()) {
                    break;
                }
                todos.addAll(actual.elementos());
                if (actual.ultima()) {
                    break;
                }
            }
            return todos;
        } catch (RuntimeException fallo) {
            throw ClientesHttp.traducir("inventario", "consultar elementos", fallo);
        }
    }

    /** Crea un elemento en el inventario del jugador; 201 con el elemento creado. */
    public Elemento crear(UUID uid, String productoId, String tipo, String nombrePropio, String parteArmadura) {
        ClientesHttp.exigirUrl(base, "inventario");
        try {
            Elemento creado = http.post()
                    .uri(base + "/api/v1/inventario/elementos")
                    .header(CABECERA_PROPIETARIO, uid.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new NuevoElemento(productoId, tipo, nombrePropio, parteArmadura))
                    .retrieve()
                    .body(Elemento.class);
            if (creado == null || creado.id() == null) {
                throw new PasoFallido(Causa.RECHAZADO, "inventario crear respondio sin elemento");
            }
            return creado;
        } catch (RuntimeException fallo) {
            throw ClientesHttp.traducir("inventario", "crear " + tipo, fallo);
        }
    }

    public Equipamiento equipamientoDe(UUID uid, String heroeId) {
        ClientesHttp.exigirUrl(base, "inventario");
        try {
            Equipamiento equipamiento = http.get()
                    .uri(base + "/api/v1/inventario/heroes/{heroe}/equipamiento", heroeId)
                    .header(CABECERA_PROPIETARIO, uid.toString())
                    .retrieve()
                    .body(Equipamiento.class);
            return equipamiento == null ? new Equipamiento(heroeId, List.of(), Map.of(), List.of()) : equipamiento;
        } catch (RuntimeException fallo) {
            throw ClientesHttp.traducir("inventario", "consultar equipamiento", fallo);
        }
    }

    /**
     * Equipa un elemento al heroe.
     *
     * <p>Un 409 no se da por bueno a ciegas: se vuelve a leer el equipamiento
     * y solo si el elemento ya esta puesto se considera hecho (un intento
     * anterior lo equipo y murio antes de anotarlo). Cualquier otro 409 —ranura
     * ocupada, elemento en subasta— es un rechazo de verdad.
     */
    public void equipar(UUID uid, String heroeId, String elementoId) {
        ClientesHttp.exigirUrl(base, "inventario");
        try {
            http.put()
                    .uri(base + "/api/v1/inventario/heroes/{heroe}/equipamiento/{elemento}", heroeId, elementoId)
                    .header(CABECERA_PROPIETARIO, uid.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException respuesta) {
            if (respuesta.getStatusCode().value() == 409 && equipamientoDe(uid, heroeId).contiene(elementoId)) {
                return;
            }
            throw ClientesHttp.traducir("inventario", "equipar", respuesta);
        } catch (RuntimeException fallo) {
            throw ClientesHttp.traducir("inventario", "equipar", fallo);
        }
    }

    /** Cuerpo de {@code CrearElementoRequest}. */
    public record NuevoElemento(String productoId, String tipo, String nombrePropio, String parteArmadura) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Elemento(String id, String productoId, String tipo, String nombrePropio,
                           String parteArmadura, boolean disponible, String subastaId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagina(List<Elemento> elementos, int numero, int tamanio, long totalElementos,
                         int totalPaginas, boolean ultima) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Equipamiento(String heroeId, List<String> armas, Map<String, String> armaduras,
                               List<String> items) {

        public boolean contiene(String elementoId) {
            return (armas != null && armas.contains(elementoId))
                    || (items != null && items.contains(elementoId))
                    || (armaduras != null && armaduras.containsValue(elementoId));
        }
    }
}
