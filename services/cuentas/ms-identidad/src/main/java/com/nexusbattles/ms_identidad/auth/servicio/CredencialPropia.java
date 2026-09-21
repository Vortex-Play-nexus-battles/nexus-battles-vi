package com.nexusbattles.ms_identidad.auth.servicio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * La credencial con la que este servicio habla con los demas (correo,
 * notificaciones, cumplimiento) — ADR-001/ADR-005.
 *
 * <p>ms-identidad es el emisor de credenciales de servicio, asi que no tiene
 * que pedirsela a nadie por HTTP: se la firma a si mismo con
 * {@link EmisorDeTokensDeServicio}, con {@code client_id} = {@value #CLIENT_ID}.
 * Para los servicios que la reciben es un token de servicio como cualquier
 * otro: mismo JWKS, mismo rol, {@code azp} = ms-identidad.
 *
 * <p>Se reutiliza hasta {@value #MARGEN_SEGUNDOS} segundos antes de caducar y
 * entonces se firma otra, igual que hace {@code TokenDeServicioOAuth2} en los
 * clientes de plataforma-seguridad. Es tambien el interceptor que pone la
 * cabecera {@code Authorization} en los {@code RestClient} salientes.
 */
@Component
public class CredencialPropia implements ClientHttpRequestInterceptor {

    /** Identidad de este servicio ante los demas. */
    public static final String CLIENT_ID = "ms-identidad";

    static final long MARGEN_SEGUNDOS = 30;

    private final EmisorDeTokensDeServicio emisor;
    private final Clock reloj;

    private volatile String vigente;
    private volatile Instant caducaEn = Instant.EPOCH;

    @Autowired
    public CredencialPropia(EmisorDeTokensDeServicio emisor) {
        this(emisor, Clock.systemUTC());
    }

    CredencialPropia(EmisorDeTokensDeServicio emisor, Clock reloj) {
        this.emisor = emisor;
        this.reloj = reloj;
    }

    /** El token vigente, renovado si esta a punto de caducar. */
    public String portador() {
        Instant ahora = reloj.instant();
        if (vigente == null || !ahora.plusSeconds(MARGEN_SEGUNDOS).isBefore(caducaEn)) {
            synchronized (this) {
                if (vigente == null || !ahora.plusSeconds(MARGEN_SEGUNDOS).isBefore(caducaEn)) {
                    EmisorDeTokensDeServicio.TokenEmitido token = emisor.emitir(CLIENT_ID);
                    vigente = token.valor();
                    caducaEn = ahora.plus(Duration.ofSeconds(token.expiraEnSegundos()));
                }
            }
        }
        return vigente;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo,
                                        ClientHttpRequestExecution ejecucion) throws IOException {
        peticion.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + portador());
        return ejecucion.execute(peticion, cuerpo);
    }
}
