package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.DirectorioDeJugadores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El directorio de cuentas contra ms-identidad — B6.
 *
 * <p>{@code GET /api/v1/internal/usuarios/{uid}/contacto} de
 * {@code contracts/openapi/ms-identidad-admin.yaml}: solo con token de
 * servicio, que pone el {@link RestClient} (credencial de salas-partidas,
 * ADR-005), con los tiempos de espera acotados del servicio.
 *
 * <p><b>Solo se lee lo que hace falta.</b> La respuesta trae tambien el correo
 * de la cuenta; aqui no se deserializa, no se guarda y no se escribe en la
 * bitacora. Del contrato se usan el apodo (para pintar la conversacion) y el
 * estado (solo una cuenta {@code ACTIVO} recibe).
 *
 * <p><b>Cache corta.</b> Un jugador que escribe cinco mensajes seguidos a la
 * misma persona no tiene por que preguntar cinco veces si existe. Se recuerda
 * tambien el «no existe», para que escribir a uids inventados no se convierta
 * en una forma de martillear a identidad. Si la cuenta cambia de estado, se
 * nota como mucho a los {@code vigencia} segundos. Lo que NO se recuerda es que
 * identidad no respondiera: eso se vuelve a preguntar siempre.
 */
public class ClienteDirectorioDeIdentidad implements DirectorioDeJugadores {

    private static final Logger log = LoggerFactory.getLogger(ClienteDirectorioDeIdentidad.class);

    /** Ruta del contrato; visible para la prueba que la coteja con el YAML. */
    static final String RUTA = "/api/v1/internal/usuarios/{uid}/contacto";

    /** Tope de entradas: si se supera, se olvida todo (es una cache, no un registro). */
    private static final int MAXIMO_EN_CACHE = 10_000;

    private final RestClient http;
    private final String base;
    private final Duration vigencia;
    private final Clock reloj;
    private final ConcurrentHashMap<UUID, Recordado> cache = new ConcurrentHashMap<>();

    /**
     * @param base     URL de ms-identidad sin {@code /api/v1} (la ruta ya lo lleva)
     * @param vigencia cuanto se recuerda una respuesta; cero la desactiva
     */
    public ClienteDirectorioDeIdentidad(RestClient http, String base, Duration vigencia, Clock reloj) {
        this.http = Objects.requireNonNull(http);
        this.base = Objects.requireNonNull(base, "Hace falta la URL de ms-identidad").replaceAll("/+$", "");
        this.vigencia = vigencia == null || vigencia.isNegative() ? Duration.ZERO : vigencia;
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public Optional<CuentaDeJugador> buscar(UUID uid) {
        Objects.requireNonNull(uid, "Hace falta el uid de la cuenta.");
        Instant ahora = reloj.instant();
        Recordado recordado = cache.get(uid);
        if (recordado != null && recordado.vigenteHasta().isAfter(ahora)) {
            return recordado.cuenta();
        }

        Optional<CuentaDeJugador> cuenta = preguntar(uid);
        if (!vigencia.isZero()) {
            if (cache.size() >= MAXIMO_EN_CACHE) {
                cache.clear();
            }
            cache.put(uid, new Recordado(cuenta, ahora.plus(vigencia)));
        }
        return cuenta;
    }

    private Optional<CuentaDeJugador> preguntar(UUID uid) {
        try {
            Contacto contacto = http.get()
                    .uri(base + RUTA, uid)
                    .retrieve()
                    .body(Contacto.class);
            if (contacto == null || contacto.estado() == null) {
                throw noDisponible("respuesta sin estado de la cuenta");
            }
            return Optional.of(new CuentaDeJugador(uid, contacto.apodo(), contacto.estado()));
        } catch (HttpClientErrorException.NotFound noExiste) {
            return Optional.empty();
        } catch (RestClientException ex) {
            // Un 403 (credencial de servicio ausente o rechazada) tambien cae
            // aqui: no es «no existe», es «no se pudo preguntar».
            throw noDisponible(ex.getClass().getSimpleName());
        }
    }

    private static DirectorioNoDisponible noDisponible(String motivo) {
        log.warn("ms-identidad no respondio al comprobar un destinatario; el mensaje no se entrega. Motivo: {}",
                motivo);
        return new DirectorioNoDisponible(motivo);
    }

    /** Lo del contrato que se usa. El correo se ignora a proposito. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Contacto(UUID uid, String apodo, String estado) {
    }

    private record Recordado(Optional<CuentaDeJugador> cuenta, Instant vigenteHasta) {
    }
}
