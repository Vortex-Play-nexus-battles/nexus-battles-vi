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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Los creditos de bienvenida, por la API de ms-finanzas (contrato
 * {@code creditos.yaml}, {@code POST /creditos/acreditar}).
 *
 * <p>Identidad NUNCA escribe saldo: se lo pide al dueno del libro con su
 * credencial de servicio (ADR-005), y ms-finanzas deja el movimiento con su
 * concepto en el historial del jugador. La idempotencia es la de
 * ms-finanzas: el mismo {@code refId} no vuelve a sumar, devuelve el
 * resultado original. Por eso reintentar este paso es seguro.
 */
@Component
public class ClienteCreditos {

    /** Concepto con el que el movimiento aparece en el historial del jugador. */
    public static final String CONCEPTO_BONO = "bono-registro";

    private final RestClient http;
    private final String base;

    @Autowired
    public ClienteCreditos(@Value("${app.onboarding.creditos-url:}") String base,
                           CredencialPropia credencial,
                           InterceptorDeTraza traza) {
        this.base = ClientesHttp.sinBarraFinal(base);
        this.http = ClientesHttp.construir(credencial, traza);
    }

    /**
     * Acredita {@code monto} con la clave de idempotencia {@code refId}.
     *
     * @throws PasoFallido si ms-finanzas no responde o rechaza el credito
     */
    public Acreditacion acreditar(UUID uid, long monto, String refId) {
        ClientesHttp.exigirUrl(base, "ms-finanzas");
        try {
            Acreditacion respuesta = http.post()
                    .uri(base + "/creditos/acreditar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Solicitud(uid.toString(), monto, refId, CONCEPTO_BONO))
                    .retrieve()
                    .body(Acreditacion.class);
            if (respuesta == null || respuesta.transaccionId() == null) {
                throw new PasoFallido(Causa.RECHAZADO, "ms-finanzas acreditar respondio sin transaccion");
            }
            return respuesta;
        } catch (RuntimeException fallo) {
            throw ClientesHttp.traducir("ms-finanzas", "acreditar", fallo);
        }
    }

    /** Cuerpo de {@code AcreditarCreditos}. */
    public record Solicitud(String uid, long monto, String refId, String concepto) {
    }

    /** Respuesta {@code Credito}; la misma en la primera llamada y en cada repeticion. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Acreditacion(String transaccionId, String refId, String estado,
                               BigDecimal montoAcreditado, BigDecimal nuevoSaldoDisponible) {
    }
}
