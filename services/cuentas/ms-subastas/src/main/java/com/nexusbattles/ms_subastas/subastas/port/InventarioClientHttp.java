package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador HTTP hacia el servicio de inventario.
 *
 * <p><b>Solo una de las cuatro operaciones existe al otro lado.</b> La version
 * anterior llamaba a {@code /elementos/{id}/reservas} y
 * {@code /elementos/{id}/transferencias}, que no estan en
 * {@code contracts/openapi/inventario.yaml} ni en el codigo de inventario:
 * nadie los ha construido ni los tiene planeados. Con
 * {@code app.inventario.modo=fake} eso no se notaba; en cuanto se activara el
 * modo http, cada llamada habria muerto con un 404 en mitad de una subasta.
 *
 * <p>Lo unico publicado hoy es
 * {@code PUT /api/v1/inventario/elementos/{elementoId}/bloqueo-subasta}
 * (HU-INV-010). Las otras tres fallan aqui con un mensaje que nombra el
 * endpoint que falta, en vez de inventarse una URL: un fallo claro y temprano
 * es mejor que uno silencioso que ademas parece implementado.
 *
 * <p><b>Falta acordar que identificador cruza esta frontera.</b> Inventario
 * autentica con la cabecera {@code X-User-Name} y compara ese texto contra su
 * {@code propietarioId} con {@code equalsIgnoreCase}, asi que espera un apodo.
 * ms-subastas solo conoce el UUID estable del claim {@code uid} — precisamente
 * porque el apodo es mutable y no sirve como clave. Es el mismo problema de
 * {@code sub} contra {@code uid} que ya se resolvio dentro del token,
 * reapareciendo entre servicios. Hasta que se acuerde, esta llamada se
 * respondera con 403 y el mensaje de abajo lo explica.
 */
public class InventarioClientHttp implements InventarioClient {

    private static final Logger log = LoggerFactory.getLogger(InventarioClientHttp.class);


    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public InventarioClientHttp(String baseUrl, long timeoutMs, ObjectMapper objectMapper) {
        this(URI.create(baseUrl),
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                objectMapper,
                Duration.ofMillis(timeoutMs));
    }

    public InventarioClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
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

        HttpRequest peticion = HttpRequest.newBuilder(
                        uri("/api/v1/inventario/elementos/" + elementoInventarioId + "/bloqueo-subasta"))
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

        HttpRequest peticion = HttpRequest.newBuilder(
                        uri("/api/v1/inventario/elementos/" + elementoInventarioId))
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

        HttpRequest.Builder constructor = HttpRequest.newBuilder(
                        uri("/api/v1/inventario/elementos/" + elementoInventarioId
                                + "/bloqueo-subasta/" + subastaId))
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
     * @throws InventarioClientException siempre. La transferencia de propiedad
     *         no existe en ningun sitio del monorepo. Es lo que impide que
     *         HU-SUB-004 entregue de verdad lo que cobra.
     */
    @Override
    public void transferirProducto(String elementoInventarioId, UUID nuevoPropietarioId,
                                   UUID subastaId, String idempotencyKey) {
        throw new InventarioClientException(
                "Inventario no expone la transferencia de propiedad. Hace falta un endpoint que "
                        + "cambie el propietario de un elemento al cerrarse una subasta "
                        + "(pedido a Nicolay). Sin eso se cobra al ganador y no se le entrega nada.");
    }

    private String cuerpoDeBloqueo(UUID propietarioId, UUID subastaId) {
        try {
            return objectMapper.writeValueAsString(
                    new BloquearEnSubasta(propietarioId.toString(), subastaId.toString()));
        } catch (IOException e) {
            throw new InventarioClientException("Error al serializar el cuerpo para inventario", e);
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
     * Cuerpo exacto que declara hoy {@code BloquearEnSubastaRequest} de
     * inventario (HU-INV-010): el propietario viaja como {@code propietarioUid}
     * en el cuerpo, no en una cabecera de identidad.
     *
     * <p>Esto es lo que se acordo con Edwin el 14/09/2026 y Nicolay publico el
     * 15/09: el apodo no servia porque en tres de las cinco llamadas a
     * inventario no existe ninguno que propagar —el cierre por vencimiento y la
     * liberacion los dispara un {@code @Scheduled} sin peticion ni token, y la
     * compensacion transfiere al vendedor, que no es quien pidio nada.
     */
    private record BloquearEnSubasta(String propietarioUid, String subastaId) { }

    /** Forma exacta de {@code DetalleElementoInventarioResponse} de inventario. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DetalleElemento(String elementoId, UUID productoId, UUID propietarioUid,
                                   boolean enUso, boolean disponible, UUID subastaId) { }
}
