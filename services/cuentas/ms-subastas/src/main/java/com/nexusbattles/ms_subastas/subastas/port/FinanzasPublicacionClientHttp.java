package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nexusbattles.comun.seguridad.servicio.CredencialDeServicioNoDisponible;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;

/** Adaptador de comisiones de HU-SUB-001, independiente del cliente de creditos de pujas. */
@Component
public class FinanzasPublicacionClientHttp implements FinanzasPublicacionClient {
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final PortadorDeServicio credencial;

    /**
     * @param tokenDeServicio credencial de ms-subastas ante ms-finanzas (ADR-005),
     *        presente cuando {@code DIRECTORIO_ACTIVO_CLIENT_ID} esta configurado.
     *        Desde #455 {@code /creditos/debitar} y {@code /creditos/reversar}
     *        exigen {@code ROLE_SERVICIO}: sin credencial, la comision de
     *        publicacion no se puede cobrar y HU-SUB-001 responde 503.
     */
    @Autowired
    public FinanzasPublicacionClientHttp(
            @Value("${app.finanzas.base-url:http://localhost:8093/api/v1}") String baseUrl,
            @Value("${app.finanzas.timeout-ms:5000}") long timeoutMs,
            ObjectMapper objectMapper,
            ObjectProvider<TokenDeServicio> tokenDeServicio) {
        this(URI.create(baseUrl), HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                objectMapper, Duration.ofMillis(timeoutMs), credencialDesde(tokenDeServicio));
    }

    public FinanzasPublicacionClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this(baseUri, httpClient, objectMapper, timeout, PortadorDeServicio.ninguno());
    }

    public FinanzasPublicacionClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper,
                                         Duration timeout, PortadorDeServicio credencial) {
        this.credencial = Objects.requireNonNull(credencial, "credencial");
        Objects.requireNonNull(baseUri, "La URL de finanzas es obligatoria");
        if (!("http".equalsIgnoreCase(baseUri.getScheme()) || "https".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null || baseUri.getQuery() != null || baseUri.getFragment() != null
                || baseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("La URL de finanzas debe ser una base HTTP valida");
        }
        this.baseUri = URI.create(baseUri.toString().replaceAll("/+$", "") + "/");
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("El timeout debe ser positivo");
    }

    private static final Logger log = LoggerFactory.getLogger(FinanzasPublicacionClientHttp.class);

    /** Codigo del rechazo por saldo insuficiente (motivo estable para la interfaz). */
    public static final String SALDO_INSUFICIENTE = "SALDO_INSUFICIENTE";

    private static PortadorDeServicio credencialDesde(ObjectProvider<TokenDeServicio> proveedor) {
        TokenDeServicio token = proveedor.getIfAvailable();
        if (token == null) {
            // No se falla el arranque: este adaptador es un @Component que vive
            // tambien en los entornos con el doble de creditos. Pero se avisa
            // una vez, porque contra el ms-finanzas real cada comision moriria
            // con 401 y HU-SUB-001 no se podria demostrar.
            log.warn("ms-subastas no tiene credencial de servicio (DIRECTORIO_ACTIVO_CLIENT_ID vacio): "
                    + "las comisiones de publicacion saldran sin Authorization y ms-finanzas las rechazara.");
            return PortadorDeServicio.ninguno();
        }
        return PortadorDeServicio.de(token);
    }

    @Override
    public void debitarComision(UUID jugadorUid, BigDecimal monto, UUID subastaId, String concepto) {
        validarTexto(concepto, "concepto");
        debitar(jugadorUid, monto, refId(PREFIJO_PUBLICACION, subastaId), concepto,
                "Creditos insuficientes para publicar la subasta");
    }

    @Override
    public void compensarDebito(UUID subastaId, String motivo) {
        validarTexto(motivo, "motivo");
        String refId = refId(PREFIJO_PUBLICACION, subastaId);
        post("reversar", new Reversa(refId, motivo), refId, null);
    }

    /**
     * B8 (7.7.10): la penalizacion de cancelar, con su propio {@code refId}.
     * Por el refId es idempotente: repetir la cancelacion no cobra dos veces.
     *
     * <p><b>G7: una penalizacion devuelta no se reutiliza.</b> ms-finanzas no
     * vuelve a descontar un {@code refId} ya registrado, tampoco si aquel debito
     * se reverso (creditos.yaml 1.4.1, {@code debitar}: «si ya existe una
     * operacion registrada con ese refId, no se vuelve a descontar»; responde la
     * del primero). Una cancelacion que fallaba despues de cobrar —inventario
     * caido— devolvia la penalizacion, y al repetirla con el mismo refId el
     * libro contestaba 200 sin cobrar: la subasta quedaba cancelada gratis. Por
     * eso cada penalizacion devuelta abre una generacion nueva del refId
     * ({@code sub-cancelacion-{id}}, {@code -2}, {@code -3}...), y antes de
     * cobrar se pregunta al libro en que quedo cada una
     * ({@code GET /creditos/operaciones/{refId}}): libre, se cobra con ella;
     * cobrada (una respuesta perdida), se repite con ella sin cobrar dos veces;
     * devuelta, se pasa a la siguiente.
     */
    @Override
    public void debitarPenalizacionCancelacion(UUID jugadorUid, BigDecimal monto, UUID subastaId) {
        String refId = refIdParaCobrarLaPenalizacion(subastaId);
        debitar(jugadorUid, monto, refId, CONCEPTO_CANCELACION,
                "Creditos insuficientes para pagar la penalizacion de cancelar");
    }

    /** Devuelve la ultima generacion cobrada (la unica que puede seguir cobrada: ver arriba). */
    @Override
    public void compensarPenalizacionCancelacion(UUID subastaId, String motivo) {
        validarTexto(motivo, "motivo");
        String refId = refIdParaDevolverLaPenalizacion(subastaId);
        post("reversar", new Reversa(refId, motivo), refId, null);
    }

    static final String PREFIJO_PUBLICACION = "sub-publicacion-";
    static final String PREFIJO_CANCELACION = "sub-cancelacion-";
    static final String CONCEPTO_CANCELACION = "penalizacion-cancelacion-subasta";

    /**
     * Tope de penalizaciones devueltas de una misma subasta: mas cancelaciones
     * fallidas seguidas que esto no es un reintento, es algo que concilia una
     * persona.
     */
    static final int GENERACIONES_MAXIMAS = 20;

    /** El refId de la generacion {@code n} de la penalizacion: la primera es el de siempre. */
    static String refIdDeLaPenalizacion(UUID subastaId, int generacion) {
        String base = refId(PREFIJO_CANCELACION, subastaId);
        return generacion <= 1 ? base : base + "-" + generacion;
    }

    private String refIdParaCobrarLaPenalizacion(UUID subastaId) {
        for (int generacion = 1; generacion <= GENERACIONES_MAXIMAS; generacion++) {
            String refId = refIdDeLaPenalizacion(subastaId, generacion);
            String estado = estadoEnElLibro(refId);
            if (!ESTADO_DEVUELTA.equals(estado)) {
                return refId;
            }
        }
        throw new FinanzasPublicacionClientException("La subasta " + subastaId + " acumula "
                + GENERACIONES_MAXIMAS + " penalizaciones devueltas: requiere conciliacion");
    }

    private String refIdParaDevolverLaPenalizacion(UUID subastaId) {
        String ultima = refIdDeLaPenalizacion(subastaId, 1);
        for (int generacion = 1; generacion <= GENERACIONES_MAXIMAS; generacion++) {
            String refId = refIdDeLaPenalizacion(subastaId, generacion);
            if (estadoEnElLibro(refId) == null) {
                break;
            }
            ultima = refId;
        }
        return ultima;
    }

    /** Estado de una operacion del libro que ya fue reversada (creditos.yaml, {@code Operacion.estado}). */
    static final String ESTADO_DEVUELTA = "LIBERADA";

    /**
     * El estado de la operacion con ese refId en el libro, o nulo si no existe
     * (el 404 de esta consulta si significa eso, creditos.yaml). Cualquier otra
     * respuesta es finanzas no disponible: no se cobra a ciegas.
     */
    private String estadoEnElLibro(String refId) {
        HttpRequest request = firmada(HttpRequest.newBuilder(baseUri.resolve("creditos/operaciones/" + refId)))
                .header("Accept", "application/json").timeout(timeout).GET().build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new FinanzasPublicacionClientException("No se pudo consultar " + refId + " en finanzas", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FinanzasPublicacionClientException("No se pudo consultar " + refId + " en finanzas", e);
        }
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new FinanzasPublicacionClientException("Respuesta inesperada de finanzas al consultar " + refId
                    + ": " + response.statusCode());
        }
        Resultado resultado = leerResultado(response.body(), refId);
        if (resultado.estado() == null || resultado.estado().isBlank()) {
            throw new FinanzasPublicacionClientException("Finanzas no dijo en que quedo " + refId);
        }
        return resultado.estado();
    }

    private void debitar(UUID jugadorUid, BigDecimal monto, String refId, String concepto, String sinSaldo) {
        if (jugadorUid == null) throw new FinanzasPublicacionClientException("El uid del jugador es obligatorio");
        if (monto == null || monto.signum() <= 0) throw new FinanzasPublicacionClientException("El monto debe ser positivo");
        post("debitar", new Debito(jugadorUid, monto, refId, concepto), refId, sinSaldo);
    }

    private static String refId(String prefijo, UUID subastaId) {
        if (subastaId == null) throw new FinanzasPublicacionClientException("El id de subasta es obligatorio");
        return prefijo + subastaId;
    }

    private static void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) throw new FinanzasPublicacionClientException("El " + campo + " es obligatorio");
    }

    /**
     * @param sinSaldo el mensaje del rechazo por saldo insuficiente, que es
     *                 distinto al publicar y al cancelar; nulo en las
     *                 operaciones que no cobran (reversar)
     */
    private void post(String operacion, Object payload, String refId, String sinSaldo) {
        final String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new FinanzasPublicacionClientException("No se pudo serializar la solicitud a finanzas", e);
        }
        HttpRequest request = firmada(HttpRequest.newBuilder(baseUri.resolve("creditos/" + operacion)))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            consultarTrasFallo(operacion, payload, refId, e);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FinanzasPublicacionClientException("Resultado financiero incierto: " + refId, e, true);
        }
        if (response.statusCode() != 200) {
            if (sinSaldo != null && operacion.equals("debitar") && response.statusCode() == 422
                    && tipoDelProblema(response).equals("https://nexusbattles.upb.edu.co/errors/saldo-insuficiente")) {
                throw new PublicacionSubastaException(PublicacionSubastaException.Motivo.REGLA_NEGOCIO,
                        SALDO_INSUFICIENTE, sinSaldo);
            }
            throw new FinanzasPublicacionClientException("Respuesta inesperada de finanzas: " + response.statusCode(),
                    null, response.statusCode() >= 500);
        }
        leerResultado(response.body(), refId);
    }

    private String tipoDelProblema(HttpResponse<String> response) {
        try {
            var json = objectMapper.readTree(response.body());
            return json == null ? "" : json.path("type").asText("");
        } catch (IOException | IllegalArgumentException e) { return ""; }
    }

    private Resultado leerResultado(String body, String refId) {
        try {
            Resultado resultado = objectMapper.readValue(body, Resultado.class);
            if (resultado == null || !refId.equals(resultado.refId())) {
                throw new FinanzasPublicacionClientException("Finanzas devolvio un refId distinto o ausente", null, true);
            }
            return resultado;
        } catch (IOException | IllegalArgumentException e) {
            throw new FinanzasPublicacionClientException("Respuesta de finanzas invalida", e, true);
        }
    }

    /** Una lectura no prueba que una mutacion aun en vuelo no vaya a completarse. */
    private void consultarTrasFallo(String operacion, Object payload, String refId, IOException causa) {
        try {
            var request = firmada(HttpRequest.newBuilder(baseUri.resolve("creditos/operaciones/" + refId)))
                    .header("Accept", "application/json").timeout(timeout).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Resultado resultado = leerResultado(response.body(), refId);
                if (operacion.equals("reversar") && "LIBERADA".equals(resultado.estado())) return;
                if (payload instanceof Debito debito && "CONSUMIDA".equals(resultado.estado())
                        && debito.uid().equals(resultado.uid()) && resultado.monto() != null
                        && debito.monto().compareTo(resultado.monto()) == 0
                        && debito.concepto().equals(resultado.concepto())) return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            causa.addSuppressed(e);
        } catch (IOException | RuntimeException e) {
            if (e != causa) causa.addSuppressed(e);
        }
        throw new FinanzasPublicacionClientException("Resultado financiero incierto; requiere conciliacion: " + refId, causa, true);
    }

    /** Credencial de ms-subastas (ADR-005); sin ella, la comision no se intenta cobrar. */
    private HttpRequest.Builder firmada(HttpRequest.Builder peticion) {
        try {
            return credencial.firmar(peticion);
        } catch (CredencialDeServicioNoDisponible sinCredencial) {
            throw new FinanzasPublicacionClientException(
                    "Sin credencial de servicio no se puede llamar a finanzas", sinCredencial);
        }
    }

    private record Debito(UUID uid, BigDecimal monto, String refId, String concepto) { }
    private record Reversa(String refId, String motivo) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Resultado(String refId, String estado, UUID uid, BigDecimal monto, String concepto) { }
}
