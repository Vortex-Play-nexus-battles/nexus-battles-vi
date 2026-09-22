package com.nexusbattles.ms_identidad.auth.correo;

import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoAvisoAccesoRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoBienvenidaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoCambioClaveRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoConfirmacionCuentaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoRecuperacionClaveRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CorreoClient {

    private static final Logger log = LoggerFactory.getLogger(CorreoClient.class);

    private final RestClient restClient;

    @Value("${app.correo.url-bienvenida}")
    private String urlBienvenida;

    @Value("${app.correo.url-aviso-acceso}")
    private String urlAvisoAcceso;

    // HU-COR-003 / HU-COR-002 (Santiago). Contrato ya publicado, hallazgo
    // suyo: TokenCredencialService generaba el token pero nunca lo enviaba
    // por correo, solo quedaba en un log.
    @Value("${app.correo.url-recuperacion-clave}")
    private String urlRecuperacionClave;

    @Value("${app.correo.url-confirmacion-cuenta}")
    private String urlConfirmacionCuenta;

    // HU-AUT-006 CA-01: aviso de que la contraseña cambió.
    @Value("${app.correo.url-cambio-clave}")
    private String urlCambioClave;

    public CorreoClient(RestClient correoRestClient) {
        this.restClient = correoRestClient;
    }

    /**
     * Aviso de cambio de contraseña (HU-AUT-006). Informativo, como el aviso
     * de acceso: si el correo no sale, el cambio ya está hecho y no se
     * deshace; se deja constancia y se sigue.
     */
    @Retry(name = "correo", fallbackMethod = "enviarCambioClaveConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarCambioClave(CorreoCambioClaveRequest datos) {
        restClient.post()
            .uri(urlCambioClave)
            .body(datos)
            .retrieve()
            .toBodilessEntity();
    }

    private void enviarCambioClaveConFallback(CorreoCambioClaveRequest datos, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el aviso de cambio de contraseña a '{}'. Motivo: {}",
            datos.getEmail(), ex.getMessage());
    }

    // Orden por defecto de Resilience4j: Retry envuelve a CircuitBreaker.
    // El respaldo va en @Retry, no en @CircuitBreaker: así se ejecuta solo
    // cuando ya se agotaron los reintentos (mismo patrón que ListaNegraClient).
    @Retry(name = "correo", fallbackMethod = "enviarBienvenidaConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarBienvenida(CorreoBienvenidaRequest datos) {
        restClient.post()
            .uri(urlBienvenida)
            .body(datos)
            .retrieve()
            .toBodilessEntity();
    }

    private void enviarBienvenidaConFallback(CorreoBienvenidaRequest datos, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el correo de bienvenida a '{}'. Motivo: {}",
            datos.getEmail(), ex.getMessage());
    }

    @Retry(name = "correo", fallbackMethod = "enviarAvisoAccesoConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarAvisoAcceso(CorreoAvisoAccesoRequest datos) {
        restClient.post()
            .uri(urlAvisoAcceso)
            .body(datos)
            .retrieve()
            .toBodilessEntity();
    }

    private void enviarAvisoAccesoConFallback(CorreoAvisoAccesoRequest datos, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el aviso de acceso a '{}'. Motivo: {}",
            datos.getEmail(), ex.getMessage());
    }

    // IMPORTANTE, distinto de bienvenida/aviso-acceso: si este correo no
    // llega, el usuario se queda sin forma de recuperar su cuenta o
    // activarla -- a diferencia de un aviso informativo, este es un paso
    // obligatorio del flujo. El fallback deja constancia igual (no lanza
    // una excepción que rompa la transacción de TokenCredencialService),
    // pero queda como un log de advertencia real, para que se note si
    // pasa -- este caso sí debería revisarse manualmente si ocurre.
    @Retry(name = "correo", fallbackMethod = "enviarRecuperacionClaveConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarRecuperacionClave(CorreoRecuperacionClaveRequest datos) {
        restClient.post()
            .uri(urlRecuperacionClave)
            .body(datos)
            .retrieve()
            .toBodilessEntity();
    }

    private void enviarRecuperacionClaveConFallback(CorreoRecuperacionClaveRequest datos, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el correo de recuperacion de clave a '{}'. Motivo: {}",
            datos.getEmail(), ex.getMessage());
    }

    @Retry(name = "correo", fallbackMethod = "enviarConfirmacionCuentaConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarConfirmacionCuenta(CorreoConfirmacionCuentaRequest datos) {
        restClient.post()
            .uri(urlConfirmacionCuenta)
            .body(datos)
            .retrieve()
            .toBodilessEntity();
    }

    private void enviarConfirmacionCuentaConFallback(CorreoConfirmacionCuentaRequest datos, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el correo de confirmacion de cuenta a '{}'. Motivo: {}",
            datos.getEmail(), ex.getMessage());
    }
}
