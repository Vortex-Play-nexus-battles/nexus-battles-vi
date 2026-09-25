package com.nexusbattles.plataforma.correo.cola;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Como trabaja la cola de correo ({@code correo.entrega.*} en application.yml).
 *
 * <p>Todo por variable de entorno (regla 10); los valores por omision son los
 * del encargo B1 y se repiten aqui para cuando una variable llega vacia desde
 * el {@code .env}.
 *
 * @param activa           false = no se programa ninguna ronda
 * @param intervaloMs      pausa entre rondas del trabajador
 * @param lote             correos reclamados por ronda
 * @param maxIntentos      intentos antes de dar un correo por FALLIDO
 * @param esperas          espera tras cada fallo transitorio
 * @param atascadoTras     tiempo en ENVIANDO a partir del cual se da por
 *                         interrumpido
 * @param retencionDias    dias que se conservan los envios terminados
 * @param purgaIntervaloMs pausa entre purgas de retencion
 */
@ConfigurationProperties(prefix = "correo.entrega")
public record ConfiguracionDeEntrega(
        Boolean activa,
        Long intervaloMs,
        Integer lote,
        Integer maxIntentos,
        List<Duration> esperas,
        Duration atascadoTras,
        Integer retencionDias,
        Long purgaIntervaloMs) {

    public static final List<Duration> ESPERAS_POR_OMISION = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofHours(1));

    public ConfiguracionDeEntrega {
        activa = activa == null || activa;
        intervaloMs = positivoO(intervaloMs, 2000L);
        lote = positivoO(lote, 10);
        maxIntentos = positivoO(maxIntentos, 8);
        esperas = esperas == null || esperas.isEmpty() ? ESPERAS_POR_OMISION : List.copyOf(esperas);
        atascadoTras = atascadoTras == null || atascadoTras.isNegative() || atascadoTras.isZero()
                ? Duration.ofMinutes(5)
                : atascadoTras;
        retencionDias = positivoO(retencionDias, 30);
        purgaIntervaloMs = positivoO(purgaIntervaloMs, 3_600_000L);
    }

    /** Los valores del encargo, para pruebas y arranques sin configuracion. */
    public static ConfiguracionDeEntrega porOmision() {
        return new ConfiguracionDeEntrega(null, null, null, null, null, null, null, null);
    }

    private static Integer positivoO(Integer valor, int siFalta) {
        return valor == null || valor < 1 ? siFalta : valor;
    }

    private static Long positivoO(Long valor, long siFalta) {
        return valor == null || valor < 1 ? siFalta : valor;
    }
}
