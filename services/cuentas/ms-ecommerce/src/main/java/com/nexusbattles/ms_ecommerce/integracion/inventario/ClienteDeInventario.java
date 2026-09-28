package com.nexusbattles.ms_ecommerce.integracion.inventario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * La tienda frente al inventario (inventario.yaml 1.4.0/1.5.0), siempre con
 * la credencial de servicio de la tienda.
 *
 * <ul>
 *   <li><b>Entregar lo comprado</b> ({@code POST /api/v1/inventario/entregas},
 *       origen COMPRA): la unica via por la que un jugador recibe la propiedad
 *       de un producto. Idempotente por {@code Idempotency-Key}: la misma clave
 *       con el mismo cuerpo devuelve la entrega original y nunca duplica
 *       elementos.</li>
 *   <li><b>Saber que tiene un jugador</b> ({@code GET /api/v1/inventario/elementos},
 *       paginas de 16) para marcar en la vitrina lo que ya es suyo (7.5). Con
 *       credencial de servicio el jugador va en {@code X-User-Name}: el
 *       contrato lo exige asi (1.1.1), y el identificador es su {@code uid}.</li>
 * </ul>
 */
@Component
public class ClienteDeInventario {

    static final String RUTA_DE_ENTREGAS = "/api/v1/inventario/entregas";
    static final String RUTA_DE_ELEMENTOS = "/api/v1/inventario/elementos?pagina={pagina}";
    static final String CABECERA_DE_IDEMPOTENCIA = "Idempotency-Key";
    static final String CABECERA_DEL_JUGADOR = "X-User-Name";

    /**
     * Tope de paginas por consulta (1024 elementos). No es una regla de
     * negocio: es que un inventario que declarase paginas sin fin no puede
     * dejar la vitrina esperando.
     */
    static final int MAXIMO_DE_PAGINAS = 64;

    /** Lo que el inventario respondio a una entrega. */
    public enum ResultadoDeEntrega {
        /** Entregada por esta peticion, o ya entregada con esa misma clave. */
        ENTREGADA,
        /**
         * El inventario se nego y repetirla no lo va a cambiar: un producto
         * suspendido o inexistente, o la peticion no le parecio valida.
         */
        RECHAZADA
    }

    private final RestClient cliente;

    public ClienteDeInventario(@Qualifier(ConfiguracionDeIntegraciones.INVENTARIO) RestClient cliente) {
        this.cliente = cliente;
    }

    /**
     * @param uid        el jugador que recibe (su {@code uid})
     * @param referencia el hecho que causa la entrega: el id de la orden
     * @param productos  cada producto y cuantas unidades
     * @param clave      {@code orden-{id}}, la misma en cada reintento
     * @throws ServicioNoDisponibleException si el inventario no respondio o no
     *         acepto la credencial de la tienda
     */
    public ResultadoDeEntrega entregar(String uid, String referencia, List<Unidades> productos, String clave) {
        SolicitudDeEntrega solicitud = new SolicitudDeEntrega(uid, "COMPRA", referencia, List.copyOf(productos));
        try {
            return cliente.post()
                    .uri(RUTA_DE_ENTREGAS)
                    .header(CABECERA_DE_IDEMPOTENCIA, clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(solicitud)
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (estado == 200 || estado == 201) {
                            return ResultadoDeEntrega.ENTREGADA;
                        }
                        if (estado == 400 || estado == 409 || estado == 422) {
                            return ResultadoDeEntrega.RECHAZADA;
                        }
                        throw new ServicioNoDisponibleException("inventario",
                                "El inventario respondio " + estado + " a la entrega " + referencia);
                    });
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException("inventario",
                    "No se pudo entregar " + referencia + " al inventario: " + fallo.getMessage(), fallo);
        }
    }

    /**
     * Los productos del catalogo de los que el jugador tiene al menos un
     * elemento en su inventario.
     *
     * @throws ServicioNoDisponibleException si el inventario no respondio
     */
    public Set<String> productosDe(String uid) {
        Set<String> productos = new HashSet<>();
        for (int pagina = 0; pagina < MAXIMO_DE_PAGINAS; pagina++) {
            PaginaDeInventario leida = leerPagina(uid, pagina);
            List<Elemento> elementos = leida.elementos() == null ? List.of() : leida.elementos();
            elementos.stream()
                    .filter(Objects::nonNull)
                    .map(Elemento::productoId)
                    .filter(Objects::nonNull)
                    .forEach(productos::add);
            boolean ultima = Boolean.TRUE.equals(leida.ultima()) || elementos.isEmpty()
                    || (leida.totalPaginas() != null && pagina + 1 >= leida.totalPaginas());
            if (ultima) {
                break;
            }
        }
        return Set.copyOf(productos);
    }

    private PaginaDeInventario leerPagina(String uid, int pagina) {
        PaginaDeInventario leida;
        try {
            leida = cliente.get()
                    .uri(RUTA_DE_ELEMENTOS, pagina)
                    .header(CABECERA_DEL_JUGADOR, uid)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PaginaDeInventario.class);
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException("inventario",
                    "No se pudo leer la pagina " + pagina + " del inventario: " + fallo.getMessage(), fallo);
        }
        if (leida == null) {
            throw new ServicioNoDisponibleException("inventario", "El inventario respondio sin cuerpo");
        }
        return leida;
    }

    /** Una linea de {@code SolicitudDeEntrega.productos}. */
    public record Unidades(String productoId, int cantidad) {
    }

    /** {@code SolicitudDeEntrega} del contrato de inventario. */
    record SolicitudDeEntrega(String uid, String origen, String referencia, List<Unidades> productos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaDeInventario(List<Elemento> elementos, Integer totalPaginas, Boolean ultima) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Elemento(String productoId) {
    }
}
