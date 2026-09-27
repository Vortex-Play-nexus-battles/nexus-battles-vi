package com.nexusbattles.plataforma.salaspartidas.sanciones;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Sanciones de RF-USR-004 contra moderacion-sanciones, segun
 * {@code contracts/openapi/moderacion-sanciones-consulta.yaml} (Grupo 6).
 *
 * <p>El contrato responde un solo hecho, {@code sancionActiva}, sin decir de
 * que tipo es la sancion. Es lo unico que se puede usar sin inventar
 * (decision D-14 del registro); cuando el contrato distinga tipos
 * —silencio, suspension, expulsion— esta es la unica clase que cambia.
 *
 * <p>Desde R10.2 no es solo del chat: el mismo hecho lo consultan tambien
 * las puertas de sala (HU-USR-005/006). Por eso la clase vive aqui y no en
 * {@code chat.integracion}, y por eso la propiedad se llama
 * {@code salas.sanciones.url} y no {@code chat.sanciones.url}. La variable de
 * entorno que la alimenta, {@code SANCIONES_URL}, no cambia: ningun
 * despliegue se entera de esta mudanza.
 *
 * <p>Si el servicio no responde, no se asume «sin sancion»: se lanza
 * {@link SancionesNoDisponibles} y quien llamo bloquea la accion. Reemplaza a
 * {@code SancionesSinIntegrar}, que devolvia {@code false} para todo el mundo
 * con el contrato ya publicado (#441).
 *
 * <p><b>B6 — con credencial de servicio.</b> Desde la 1.4.0 del contrato
 * {@code /activa} exige token: un jugador solo consulta la suya y un servicio
 * la de cualquiera. Este cliente usaba el {@code RestClient} del chat, sin
 * credencial ni tiempos de espera; con el contrato nuevo, cada consulta daria
 * 401 y, con el fallo cerrado de arriba, nadie podria chatear, escribir un
 * mensaje privado ni entrar a una sala. Ahora va por
 * {@code restClientServicios}.
 */
@Component
public class ClienteSanciones implements SancionesDelJugador {

    private static final Logger log = LoggerFactory.getLogger(ClienteSanciones.class);

    /** Ruta del contrato; visible para la prueba que la coteja con el YAML. */
    static final String RUTA = "/sanciones/usuarios/{uid}/activa";

    private final RestClient restClient;
    private final String base;

    ClienteSanciones(@Qualifier("restClientServicios") RestClient restClient,
                     @Value("${salas.sanciones.url}") String base) {
        this.restClient = restClient;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public boolean tieneSancionActiva(UUID idJugador) {
        try {
            SancionActiva respuesta = restClient.get()
                    .uri(base + RUTA, idJugador)
                    .retrieve()
                    .body(SancionActiva.class);
            if (respuesta == null) {
                throw noDisponible("respuesta vacia de sanciones");
            }
            if (respuesta.sancionActiva()) {
                log.info("Jugador {} con sancion activa: {}", idJugador, respuesta.motivo());
            }
            return respuesta.sancionActiva();
        } catch (RestClientException ex) {
            throw noDisponible(ex.getMessage());
        }
    }

    private static SancionesNoDisponibles noDisponible(String motivo) {
        log.warn("Sanciones no disponible, la accion se bloquea sin comprobar. Motivo: {}", motivo);
        return new SancionesNoDisponibles();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SancionActiva(boolean sancionActiva, String motivo, String vigenteHasta) { }
}
