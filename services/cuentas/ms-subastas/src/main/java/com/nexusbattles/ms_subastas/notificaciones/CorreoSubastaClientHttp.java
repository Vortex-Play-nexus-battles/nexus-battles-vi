package com.nexusbattles.ms_subastas.notificaciones;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.servicio.CredencialDeServicioNoDisponible;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;
import com.nexusbattles.ms_subastas.seguridad.Traza;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Adaptador de {@code POST /api/v1/correos/subasta} ({@code correo.yaml}
 * 1.4.0), con credencial de servicio, traza e {@code Idempotency-Key}.
 */
public class CorreoSubastaClientHttp implements CorreoSubastaClient {

    static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";

    private final String base;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private final PortadorDeServicio portador;

    /** @param base la de correo sin {@code /api/v1}: la ruta ya lo lleva */
    public CorreoSubastaClientHttp(String base, HttpClient http, ObjectMapper mapper, Duration timeout,
                                   PortadorDeServicio portador) {
        this.base = Objects.requireNonNull(base, "base").replaceAll("/+$", "");
        this.http = Objects.requireNonNull(http);
        this.mapper = Objects.requireNonNull(mapper);
        this.timeout = Objects.requireNonNull(timeout);
        this.portador = Objects.requireNonNull(portador);
    }

    @Override
    public void enviar(String claveDeIdempotencia, CorreoSubasta correo) {
        Objects.requireNonNull(claveDeIdempotencia, "claveDeIdempotencia");
        String cuerpo;
        try {
            cuerpo = mapper.writeValueAsString(correo);
        } catch (JsonProcessingException e) {
            throw new CorreoRechazadoException("No se pudo serializar el correo de subasta");
        }
        HttpRequest peticion;
        try {
            peticion = Traza.propagar(portador.firmar(HttpRequest.newBuilder(
                            URI.create(base + "/api/v1/correos/subasta"))))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header(CABECERA_IDEMPOTENCIA, claveDeIdempotencia)
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
        } catch (CredencialDeServicioNoDisponible sinCredencial) {
            throw new CorreoNoDisponibleException("Sin credencial de servicio no se puede llamar a correo",
                    sinCredencial);
        }
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new CorreoNoDisponibleException("El servicio de correo no respondio", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CorreoNoDisponibleException("Envio del correo interrumpido", e);
        }
        int estado = respuesta.statusCode();
        if (estado >= 200 && estado < 300) {
            return;
        }
        if (estado == 400 || estado == 422) {
            throw new CorreoRechazadoException("El servicio de correo rechazo el aviso (" + estado + ")");
        }
        throw new CorreoNoDisponibleException("El servicio de correo respondio " + estado);
    }
}
