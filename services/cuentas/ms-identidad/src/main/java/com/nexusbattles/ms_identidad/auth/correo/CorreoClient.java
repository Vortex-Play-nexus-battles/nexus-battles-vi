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
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente del servicio de correo (contracts/openapi/correo.yaml 1.4.0).
 *
 * <p><b>B1 — {@code Idempotency-Key}.</b> Desde 1.4.0 correo guarda cada
 * peticion en su cola persistente y reconoce la clave: la misma clave dos
 * veces no encola dos correos. Es lo que hace seguro el reintento de
 * Resilience4j: si la primera llamada llego a correo pero la respuesta se
 * perdio por el camino, el reintento no le manda a la persona dos copias del
 * mismo codigo. Cada correo con clave la forma de algo que no se repite
 * (el id del codigo, el uid, la version de token).
 *
 * <p>Todos los envios son fail-open: si correo no responde, lo que ya se hizo
 * no se deshace (una cuenta registrada, una contrasena cambiada) y queda
 * constancia en la bitacora. Para los codigos, la salida es pedir otro
 * (reenvio de verificacion, nueva solicitud de restablecimiento).
 */
@Component
public class CorreoClient {

    private static final Logger log = LoggerFactory.getLogger(CorreoClient.class);

    /** Cabecera de correo.yaml 1.4.0. */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

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
     * Aviso de cambio de contraseña (HU-AUT-006 y canje de un restablecimiento,
     * B1). Informativo, como el aviso de acceso: si el correo no sale, el
     * cambio ya está hecho y no se deshace; se deja constancia y se sigue.
     */
    @Retry(name = "correo", fallbackMethod = "enviarCambioClaveConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarCambioClave(CorreoCambioClaveRequest datos, String claveIdempotencia) {
        publicar(urlCambioClave, datos, claveIdempotencia);
    }

    private void enviarCambioClaveConFallback(CorreoCambioClaveRequest datos, String claveIdempotencia, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el aviso de cambio de contraseña a '{}'. Motivo: {}",
            Enmascarado.correo(datos.getEmail()), ex.getMessage());
    }

    // Orden por defecto de Resilience4j: Retry envuelve a CircuitBreaker.
    // El respaldo va en @Retry, no en @CircuitBreaker: así se ejecuta solo
    // cuando ya se agotaron los reintentos (mismo patrón que ListaNegraClient).
    //
    // B1: la bienvenida ya no sale al registrarse sino al confirmar el correo
    // (un correo que nadie ha verificado no recibe «bienvenido»).
    @Retry(name = "correo", fallbackMethod = "enviarBienvenidaConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarBienvenida(CorreoBienvenidaRequest datos, String claveIdempotencia) {
        publicar(urlBienvenida, datos, claveIdempotencia);
    }

    private void enviarBienvenidaConFallback(CorreoBienvenidaRequest datos, String claveIdempotencia, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el correo de bienvenida a '{}'. Motivo: {}",
            Enmascarado.correo(datos.getEmail()), ex.getMessage());
    }

    @Retry(name = "correo", fallbackMethod = "enviarAvisoAccesoConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarAvisoAcceso(CorreoAvisoAccesoRequest datos) {
        publicar(urlAvisoAcceso, datos, null);
    }

    private void enviarAvisoAccesoConFallback(CorreoAvisoAccesoRequest datos, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el aviso de acceso a '{}'. Motivo: {}",
            Enmascarado.correo(datos.getEmail()), ex.getMessage());
    }

    // IMPORTANTE, distinto de bienvenida/aviso-acceso: si este correo no
    // llega, el usuario se queda sin forma de recuperar su cuenta o
    // activarla -- a diferencia de un aviso informativo, este es un paso
    // obligatorio del flujo. El fallback deja constancia (nunca el codigo) y
    // la persona puede pedir otro.
    @Retry(name = "correo", fallbackMethod = "enviarRecuperacionClaveConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarRecuperacionClave(CorreoRecuperacionClaveRequest datos, String claveIdempotencia) {
        publicar(urlRecuperacionClave, datos, claveIdempotencia);
    }

    private void enviarRecuperacionClaveConFallback(CorreoRecuperacionClaveRequest datos, String claveIdempotencia,
                                                    Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el correo de recuperacion de clave a '{}'. Motivo: {}",
            Enmascarado.correo(datos.getEmail()), ex.getMessage());
    }

    /** Verificacion del autorregistro (B1) o activacion de una cuenta administrativa: ver {@code proposito}. */
    @Retry(name = "correo", fallbackMethod = "enviarConfirmacionCuentaConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarConfirmacionCuenta(CorreoConfirmacionCuentaRequest datos, String claveIdempotencia) {
        publicar(urlConfirmacionCuenta, datos, claveIdempotencia);
    }

    private void enviarConfirmacionCuentaConFallback(CorreoConfirmacionCuentaRequest datos, String claveIdempotencia,
                                                     Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el correo de confirmacion de cuenta ({}) a '{}'. Motivo: {}",
            datos.getProposito(), Enmascarado.correo(datos.getEmail()), ex.getMessage());
    }

    private void publicar(String url, Object datos, String claveIdempotencia) {
        restClient.post()
            .uri(url)
            .headers(cabeceras -> ponerClave(cabeceras, claveIdempotencia))
            .body(datos)
            .retrieve()
            .toBodilessEntity();
    }

    private static void ponerClave(HttpHeaders cabeceras, String claveIdempotencia) {
        if (claveIdempotencia != null && !claveIdempotencia.isBlank()) {
            cabeceras.set(IDEMPOTENCY_KEY, claveIdempotencia);
        }
    }
}
