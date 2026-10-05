package com.nexusbattles.ms_subastas.notificaciones;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de {@code GET /api/v1/internal/usuarios/{uid}/contacto}
 * ({@code ms-identidad-admin.yaml}). Mismo estilo que los demas adaptadores del
 * servicio: HttpClient del JDK, credencial por {@link PortadorDeServicio} y
 * traza por {@link Traza}.
 */
public class ContactoClientHttp implements ContactoClient {

    private final String base;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private final PortadorDeServicio portador;

    /** @param base la de ms-identidad sin {@code /api/v1}: la ruta ya lo lleva */
    public ContactoClientHttp(String base, HttpClient http, ObjectMapper mapper, Duration timeout,
                              PortadorDeServicio portador) {
        this.base = Objects.requireNonNull(base, "base").replaceAll("/+$", "");
        this.http = Objects.requireNonNull(http);
        this.mapper = Objects.requireNonNull(mapper);
        this.timeout = Objects.requireNonNull(timeout);
        this.portador = Objects.requireNonNull(portador);
    }

    @Override
    public Optional<Contacto> contactoDe(UUID uid) {
        Objects.requireNonNull(uid, "uid");
        HttpRequest peticion;
        try {
            peticion = Traza.propagar(portador.firmar(HttpRequest.newBuilder(
                            URI.create(base + "/api/v1/internal/usuarios/" + uid + "/contacto"))))
                    .header("Accept", "application/json")
                    .timeout(timeout)
                    .GET()
                    .build();
        } catch (CredencialDeServicioNoDisponible sinCredencial) {
            throw new CorreoNoDisponibleException("Sin credencial de servicio no se puede pedir el contacto",
                    sinCredencial);
        }
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new CorreoNoDisponibleException("ms-identidad no respondio al pedir un contacto", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CorreoNoDisponibleException("Consulta de contacto interrumpida", e);
        }
        if (respuesta.statusCode() == 404) {
            // No se descarta el correo por un 404: el destinatario siempre sale
            // de una subasta (pujo o publico con su token), asi que que la
            // cuenta no exista es rarisimo, y que la RUTA no exista no lo es:
            // GET /internal/usuarios/{uid}/contacto figura como
            // `x-implementacion: pendiente` en ms-identidad-admin.yaml (B2).
            // Tratarlo como «no hay a quien escribirle» tiraria en silencio
            // todos los correos hasta que se despliegue. Se reintenta y, agotados
            // los intentos, queda FALLIDO y en la bitacora.
            throw new CorreoNoDisponibleException("ms-identidad respondio 404 al pedir un contacto: la cuenta no "
                    + "existe o GET /internal/usuarios/{uid}/contacto no esta desplegado");
        }
        if (respuesta.statusCode() != 200) {
            // 401/403 es la credencial (configuracion) y 5xx una averia: las dos
            // se arreglan sin tocar el aviso, asi que se reintenta.
            throw new CorreoNoDisponibleException("ms-identidad respondio " + respuesta.statusCode()
                    + " al pedir un contacto");
        }
        try {
            ContactoJson json = mapper.readValue(respuesta.body(), ContactoJson.class);
            return Optional.of(new Contacto(json.uid(), json.email(), json.apodo(), json.estado()));
        } catch (IOException | IllegalArgumentException ilegible) {
            throw new CorreoNoDisponibleException("Contacto ilegible de ms-identidad", ilegible);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContactoJson(UUID uid, String email, String apodo, String estado) {
    }
}
