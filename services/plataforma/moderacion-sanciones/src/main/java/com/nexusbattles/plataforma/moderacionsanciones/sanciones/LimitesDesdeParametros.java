package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lee cada limite de {@code GET /parametros/{clave}/valor} de admin-parametros
 * (contrato admin-parametros.yaml), con cache corta y el valor de las
 * variables de entorno como respaldo: si admin-parametros no responde o el
 * PO no ha fijado el parametro (valor null), se usa el respaldo y se anota.
 */
public class LimitesDesdeParametros implements LimitesDeSancion {

    private static final Logger BITACORA = LoggerFactory.getLogger(LimitesDesdeParametros.class);

    static final String MINIMA = "sanciones.suspension.minima-horas";
    static final String MAXIMA = "sanciones.suspension.maxima-dias";
    static final String PLAZO = "sanciones.apelacion.plazo-dias";

    private final RestClient http;
    private final String base;
    private final LimitesDeSancion respaldo;
    private final Clock reloj;
    private final Duration vigenciaDeCache;
    private final Map<String, Cacheado> cache = new ConcurrentHashMap<>();

    public LimitesDesdeParametros(RestClient http, String base, LimitesDeSancion respaldo, Clock reloj,
                                  Duration vigenciaDeCache) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
        this.respaldo = respaldo;
        this.reloj = reloj;
        this.vigenciaDeCache = vigenciaDeCache;
    }

    @Override
    public Duration suspensionMinima() {
        return leer(MINIMA).map(Duration::ofHours).orElse(respaldo.suspensionMinima());
    }

    @Override
    public Duration suspensionMaxima() {
        return leer(MAXIMA).map(Duration::ofDays).orElse(respaldo.suspensionMaxima());
    }

    @Override
    public Duration plazoDeApelacion() {
        return leer(PLAZO).map(Duration::ofDays).orElse(respaldo.plazoDeApelacion());
    }

    private java.util.Optional<Long> leer(String clave) {
        Instant ahora = reloj.instant();
        Cacheado c = cache.get(clave);
        if (c != null && c.vence().isAfter(ahora)) {
            return c.valor();
        }
        java.util.Optional<Long> valor;
        try {
            Valor respuesta = http.get().uri(base + "/parametros/{clave}/valor", clave).retrieve().body(Valor.class);
            if (respuesta == null || respuesta.valor() == null || respuesta.valor().isBlank()) {
                BITACORA.info("El parametro {} no tiene valor en admin-parametros; se usa el respaldo", clave);
                valor = java.util.Optional.empty();
            } else {
                valor = java.util.Optional.of(Long.parseLong(respuesta.valor().strip()));
            }
        } catch (RestClientException | NumberFormatException fallo) {
            BITACORA.warn("No se pudo leer {} de admin-parametros ({}); se usa el respaldo", clave, fallo.getMessage());
            valor = java.util.Optional.empty();
        }
        cache.put(clave, new Cacheado(valor, ahora.plus(vigenciaDeCache)));
        return valor;
    }

    private record Cacheado(java.util.Optional<Long> valor, Instant vence) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Valor(String clave, String valor, String tipo, int version) { }
}
