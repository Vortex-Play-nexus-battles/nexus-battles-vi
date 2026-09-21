package com.nexusbattles.plataforma.comentarios.publicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor;

/**
 * Estado disciplinario del autor contra moderacion-sanciones, segun
 * {@code contracts/openapi/moderacion-sanciones-consulta.yaml} (Grupo 6).
 *
 * <p>El contrato responde un solo hecho, {@code sancionActiva}, sin tipo de
 * sancion. Para publicar comentarios eso basta y es lo unico que se puede
 * hacer sin inventar: <b>una sancion activa silencia</b> (D-14 del registro
 * de decisiones; cuando el contrato distinga tipos, cambia solo esta clase).
 *
 * <p>Si el servicio no responde no se asume habilitado: se lanza
 * {@link SancionesNoDisponibles} (503) y el autor reintenta. Reemplaza a
 * {@code SancionesPendientesDeContrato}, que devolvia HABILITADO a todo el
 * mundo con el contrato ya publicado (#441).
 */
@Component
class ClienteSanciones implements ConsultaDeSanciones {

    private static final Logger log = LoggerFactory.getLogger(ClienteSanciones.class);

    /** Ruta del contrato; visible para la prueba que la coteja con el YAML. */
    static final String RUTA = "/sanciones/usuarios/{uid}/activa";

    private final RestClient restClient;
    private final String base;

    ClienteSanciones(RestClient restClientComentarios, @Value("${comentarios.sanciones.url}") String base) {
        this.restClient = restClientComentarios;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public EstadoDeAutor estadoDe(String autorId) {
        try {
            SancionActiva respuesta = restClient.get()
                    .uri(base + RUTA, autorId)
                    .retrieve()
                    .body(SancionActiva.class);
            if (respuesta == null) {
                throw noDisponible("respuesta vacia de sanciones");
            }
            if (respuesta.sancionActiva()) {
                log.info("Autor {} silenciado para comentar por sancion activa: {}", autorId, respuesta.motivo());
                return EstadoDeAutor.SILENCIADO;
            }
            return EstadoDeAutor.HABILITADO;
        } catch (RestClientException ex) {
            throw noDisponible(ex.getMessage());
        }
    }

    private static SancionesNoDisponibles noDisponible(String motivo) {
        log.warn("Sanciones no disponible, el comentario no se publica sin comprobar. Motivo: {}", motivo);
        return new SancionesNoDisponibles(motivo);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SancionActiva(boolean sancionActiva, String motivo, String vigenteHasta) { }
}
