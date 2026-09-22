package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.nexusbattles.comun.seguridad.servicio.CredencialDeServicioNoDisponible;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;

/**
 * Adaptador HTTP hacia el servicio de inventario.
 *
 * <p>Integra las operaciones acordadas con ms-inventario (HU-INV-010 y HU-SUB-004):
 * <ul>
 *   <li>Bloqueo en subasta: {@code PUT /api/v1/inventario/elementos/{elementoId}/bloqueo-subasta} con
 *       {@code SolicitudReserva} ({@code propietarioUid} y {@code subastaId}) e {@code Idempotency-Key}.</li>
 *   <li>Liberacion de bloqueo: {@code DELETE /api/v1/inventario/elementos/{elementoId}/bloqueo-subasta/{subastaId}}.</li>
 *   <li>Consulta de elemento: {@code GET /api/v1/inventario/elementos/{elementoId}}.</li>
 *   <li>Transferencia de propiedad: {@code POST /api/v1/inventario/elementos/{elementoId}/transferencias} con
 *       {@code SolicitudTransferencia} ({@code nuevoPropietarioUid} y {@code subastaId}) e {@code Idempotency-Key}.</li>
 * </ul>
 *
 * <p>Todas las operaciones internas entre servicios usan identidad por UUID estable y tokens
 * S2S (bearerAuth), sin depender de la cabecera {@code X-User-Name}.
 */
public class InventarioClientHttp implements InventarioClient, InventarioPublicacionClient {

    private static final Logger log = LoggerFactory.getLogger(InventarioClientHttp.class);


    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final PortadorDeServicio credencial;

    public InventarioClientHttp(String baseUrl, long timeoutMs, ObjectMapper objectMapper) {
        this(baseUrl, timeoutMs, objectMapper, PortadorDeServicio.ninguno());
    }

    /**
     * @param credencial la de ms-subastas ante inventario (ADR-005): inventario
     *        solo acepta operar sobre el inventario de otro jugador a un
     *        servicio con {@code ROLE_SERVICIO} (contrato 1.1.0, #451).
     */
    public InventarioClientHttp(String baseUrl, long timeoutMs, ObjectMapper objectMapper,
                                PortadorDeServicio credencial) {
        this(URI.create(baseUrl),
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                objectMapper,
                Duration.ofMillis(timeoutMs),
                credencial);
    }

    public InventarioClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this(baseUri, httpClient, objectMapper, timeout, PortadorDeServicio.ninguno());
    }

    public InventarioClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout,
                                PortadorDeServicio credencial) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
        this.credencial = Objects.requireNonNull(credencial, "credencial");
    }

    /**
     * Bloquea el elemento mientras la subasta siga viva. Es lo que HU-SUB-001
     * llama "reservar" y lo que inventario llama "bloqueo-subasta": misma
     * operacion, dos nombres, porque el puerto se escribio antes que el
     * endpoint.
     */
    @Override
    public void reservar(String elementoInventarioId, UUID propietarioId, UUID subastaId, String idempotencyKey) {
        exigir(elementoInventarioId != null && !elementoInventarioId.isBlank(),
                "El identificador del elemento de inventario es obligatorio");
        exigir(propietarioId != null, "El propietario es obligatorio para el bloqueo");
        exigir(subastaId != null, "El identificador de la subasta es obligatorio para el bloqueo");
        // Inventario la exige: sin ella responde 400.
        exigir(idempotencyKey != null && !idempotencyKey.isBlank(),
                "La clave de idempotencia es obligatoria: inventario la exige para el bloqueo");

        HttpRequest peticion = firmada(HttpRequest.newBuilder(
                        uri("/api/v1/inventario/elementos/" + elementoInventarioId + "/bloqueo-subasta")))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .timeout(timeout)
                .PUT(HttpRequest.BodyPublishers.ofString(cuerpoDeBloqueo(propietarioId, subastaId)))
                .build();

        HttpResponse<String> respuesta = enviar(peticion);
        int estado = respuesta.statusCode();
        if (estado >= 200 && estado < 300) {
            return;
        }
        if (estado == 403) {
            throw new InventarioClientException(
                    "Inventario rechazo el bloqueo del elemento " + elementoInventarioId
                            + ": el propietarioUid enviado no es el dueno del elemento");
        }
        if (estado == 409) {
            throw new InventarioClientException(
                    "El elemento " + elementoInventarioId + " esta equipado o ya bloqueado por otra subasta");
        }
        if (estado == 503) {
            throw new InventarioNoDisponibleException(
                    "Inventario no esta disponible para bloquear el elemento " + elementoInventarioId);
        }
        throw new InventarioClientException(
                "Respuesta inesperada de inventario al bloquear el elemento: " + estado);
    }

    /**
     * Resuelve un elemento por su identificador contra
     * {@code GET /api/v1/inventario/elementos/{elementoId}}, publicado por
     * Nicolay el 15/09/2026 (HU-INV-010). Hasta entonces esta operacion no
     * existia y este metodo fallaba siempre.
     *
     * <p>No manda identidad: la consulta no la pide, y es justo lo que la hace
     * utilizable desde el job de cierre, donde no hay peticion de nadie.
     *
     * @return vacio si inventario responde 404. Que un elemento ya no exista es
     *         un estado normal —lo pudieron borrar entre la publicacion y el
     *         cierre—, no un fallo, asi que quien llama decide que hacer en vez
     *         de recibir una excepcion.
     */
    @Override
    public Optional<ElementoInventario> buscar(String elementoInventarioId) {
        exigir(elementoInventarioId != null && !elementoInventarioId.isBlank(),
                "El identificador del elemento de inventario es obligatorio");

        HttpRequest peticion = firmada(HttpRequest.newBuilder(
                        uri("/api/v1/inventario/elementos/" + elementoInventarioId)))
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();

        HttpResponse<String> respuesta = enviar(peticion);
        int estado = respuesta.statusCode();
        if (estado == 404) {
            return Optional.empty();
        }
        if (estado == 503) {
            throw new InventarioNoDisponibleException(
                    "Inventario no esta disponible para consultar el elemento " + elementoInventarioId);
        }
        if (estado < 200 || estado >= 300) {
            throw new InventarioClientException(
                    "Respuesta inesperada de inventario al consultar el elemento: " + estado);
        }

        DetalleElemento detalle;
        try {
            detalle = objectMapper.readValue(respuesta.body(), DetalleElemento.class);
        } catch (IOException e) {
            throw new InventarioClientException(
                    "Respuesta de inventario ilegible al consultar el elemento " + elementoInventarioId, e);
        }
        // 'enUso' de inventario es "equipado por su dueno". Un elemento
        // bloqueado por una subasta no esta en uso, y esa distincion importa:
        // confundirlas dejaria fuera de subasta a todo lo ya publicado.
        return Optional.of(new ElementoInventario(
                detalle.elementoId() == null ? elementoInventarioId : detalle.elementoId(),
                detalle.productoId(), detalle.propietarioUid(), detalle.enUso()));
    }

    /**
     * Quita el bloqueo al cerrarse o cancelarse la subasta.
     *
     * <p>A diferencia del bloqueo, inventario <b>no pide identidad</b> aqui, y
     * eso es lo que hace que funcione: esta operacion se invoca desde el job de
     * cierre, donde no hay peticion HTTP ni token de nadie porque la disparo el
     * reloj. Si exigiera {@code X-User-Name}, este camino seria imposible.
     *
     * <p>La idempotencia la garantiza inventario: repetir el aviso sobre un
     * producto ya disponible responde 200. Por eso el reintento es seguro.
     */
    @Override
    public void liberarReserva(String elementoInventarioId, UUID subastaId, String idempotencyKey) {
        exigir(elementoInventarioId != null && !elementoInventarioId.isBlank(),
                "El identificador del elemento de inventario es obligatorio");
        exigir(subastaId != null, "El identificador de la subasta es obligatorio para liberar el bloqueo");

        HttpRequest.Builder constructor = firmada(HttpRequest.newBuilder(
                        uri("/api/v1/inventario/elementos/" + elementoInventarioId
                                + "/bloqueo-subasta/" + subastaId)))
                .header("Accept", "application/json")
                .timeout(timeout)
                .DELETE();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            constructor.header("Idempotency-Key", idempotencyKey);
        }

        HttpResponse<String> respuesta = enviar(constructor.build());
        int estado = respuesta.statusCode();
        if (estado >= 200 && estado < 300) {
            return;
        }
        if (estado == 404) {
            // El elemento ya no existe en inventario, asi que no queda nada que
            // desbloquear: el efecto buscado ya se cumple. Tratarlo como error
            // seria peor que inutil — el cierre se invoca dentro de una
            // transaccion, asi que al fallar revierte el estado, la subasta se
            // queda ACTIVA y el job la reintenta cada 30 s para siempre sin que
            // ninguna de esas vueltas pueda salir bien.
            log.warn("El elemento {} ya no existe en inventario: no hay bloqueo que liberar "
                    + "y la subasta {} se cierra igualmente.", elementoInventarioId, subastaId);
            return;
        }
        if (estado == 503) {
            throw new InventarioNoDisponibleException(
                    "Inventario no esta disponible para liberar el bloqueo del elemento " + elementoInventarioId);
        }
        if (estado == 409) {
            // El elemento esta bloqueado, pero por OTRA subasta. Inventario lo
            // deja bloqueado a proposito. Liberarlo seria soltar el producto de
            // una subasta viva, asi que aqui solo se reporta.
            throw new InventarioClientException(
                    "El bloqueo del elemento " + elementoInventarioId + " no corresponde a la subasta "
                            + subastaId + ": pertenece a otra subasta y se conserva.");
        }
        throw new InventarioClientException(
                "Respuesta inesperada de inventario al liberar el bloqueo: " + estado);
    }

    /**
     * Transfiere formalmente la propiedad de un elemento de inventario al ganador de la subasta.
     * Operacion acordada con Nicolay (ms-inventario):
     * {@code POST /api/v1/inventario/elementos/{elementoId}/transferencias}.
     */
    @Override
    public void transferirProducto(String elementoInventarioId, UUID nuevoPropietarioId,
                                   UUID subastaId, String idempotencyKey) {
        exigir(elementoInventarioId != null && !elementoInventarioId.isBlank(),
                "El identificador del elemento de inventario es obligatorio");
        exigir(nuevoPropietarioId != null, "El nuevo propietario es obligatorio para la transferencia");
        exigir(subastaId != null, "El identificador de la subasta es obligatorio para la transferencia");

        HttpRequest.Builder constructor = firmada(HttpRequest.newBuilder(
                        uri("/api/v1/inventario/elementos/" + elementoInventarioId + "/transferencias")))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpoDeTransferencia(nuevoPropietarioId, subastaId)));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            constructor.header("Idempotency-Key", idempotencyKey);
        }

        HttpResponse<String> respuesta = enviar(constructor.build());
        int estado = respuesta.statusCode();
        if (estado >= 200 && estado < 300) {
            return;
        }
        if (estado == 404) {
            // Dos cosas distintas con el mismo numero, y hoy la segunda es la
            // probable: ms-inventario todavia NO publica esta ruta —solo el
            // bloqueo, su liberacion y la consulta por id—, asi que un 404 aqui
            // seguramente no dice "ese elemento no existe" sino "esa operacion
            // no existe". Culpar al elemento mandaria a quien lo depure a mirar
            // la subasta en vez del contrato. Cuando Nicolay la publique, este
            // mensaje se puede recortar a la primera mitad.
            throw new InventarioClientException(
                    "Inventario respondio 404 al transferir el elemento " + elementoInventarioId
                            + ". O el elemento no existe, o —mas probable hoy— ms-inventario aun no expone "
                            + "POST /api/v1/inventario/elementos/{elementoId}/transferencias. "
                            + "Comprobar el contrato antes que el dato.");
        }
        if (estado == 409) {
            throw new InventarioClientException(
                    "Inventario rechazo la transferencia del elemento " + elementoInventarioId
                            + ": conflicto con el estado del elemento o subasta");
        }
        if (estado == 503) {
            throw new InventarioNoDisponibleException(
                    "Inventario no esta disponible para transferir el elemento " + elementoInventarioId);
        }
        throw new InventarioClientException(
                "Respuesta inesperada de inventario al transferir el elemento: " + estado);
    }

    private String cuerpoDeBloqueo(UUID propietarioId, UUID subastaId) {
        try {
            return objectMapper.writeValueAsString(
                    new SolicitudReserva(propietarioId, subastaId));
        } catch (IOException e) {
            throw new InventarioClientException("Error al serializar el cuerpo para inventario", e);
        }
    }

    private String cuerpoDeTransferencia(UUID nuevoPropietarioId, UUID subastaId) {
        try {
            return objectMapper.writeValueAsString(
                    new SolicitudTransferencia(nuevoPropietarioId, subastaId));
        } catch (IOException e) {
            throw new InventarioClientException("Error al serializar el cuerpo para inventario", e);
        }
    }

    /**
     * Pone la credencial de ms-subastas (ADR-005). Sin ella la llamada no
     * sale y se trata como inventario no disponible (reintento y cortacircuitos).
     */
    private HttpRequest.Builder firmada(HttpRequest.Builder peticion) {
        try {
            return credencial.firmar(peticion);
        } catch (CredencialDeServicioNoDisponible sinCredencial) {
            throw new InventarioNoDisponibleException(
                    "Sin credencial de servicio no se puede llamar a inventario", sinCredencial);
        }
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new InventarioClientException(mensaje);
        }
    }

    private URI uri(String ruta) {
        String basePath = baseUri.getPath();
        if (basePath == null || basePath.isBlank() || basePath.equals("/")) {
            basePath = "";
        } else {
            basePath = basePath.replaceAll("/+$", "");
        }
        return URI.create(baseUri.getScheme() + "://" + baseUri.getAuthority()
                + basePath + (ruta.startsWith("/") ? ruta : "/" + ruta));
    }

    private HttpResponse<String> enviar(HttpRequest peticion) {
        try {
            return httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // De disponibilidad, no de negocio: esto si merece reintento y si
            // debe empujar el cortacircuitos.
            throw new InventarioNoDisponibleException("No se pudo contactar al servicio de inventario", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InventarioNoDisponibleException("Peticion a inventario interrumpida", e);
        }
    }

    /**
     * Solicitud enviada a inventario para bloquear un elemento en subasta (HU-INV-010).
     * El propietario viaja en el cuerpo como {@code propietarioUid}.
     */
    static record SolicitudReserva(
            @JsonProperty("propietarioUid") UUID propietarioUid,
            @JsonProperty("subastaId") UUID subastaId) { }

    /**
     * Solicitud enviada a inventario para transferir la propiedad al ganador (HU-SUB-004).
     * El nuevo propietario viaja en el cuerpo como {@code nuevoPropietarioUid}.
     */
    static record SolicitudTransferencia(
            @JsonProperty("nuevoPropietarioUid") UUID nuevoPropietarioUid,
            @JsonProperty("subastaId") UUID subastaId) { }

    /** Forma exacta de {@code DetalleElementoInventarioResponse} de inventario. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DetalleElemento(String elementoId, UUID productoId, UUID propietarioUid,
                                   boolean enUso, boolean disponible, UUID subastaId) { }
}
