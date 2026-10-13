package com.nexusbattles.plataforma.metricasplataforma.sondeo;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Plazos y tamano del sondeo de las peticiones INTERACTIVAS del panel
 * (RFINAL-08): {@code GET /admin/sistema/servicios} y {@code GET /tecnicas}.
 *
 * <p>Son constantes tecnicas, no valores de negocio: no cambian QUE se mide,
 * solo cuanto se espera a quien no contesta. Viven en application.yml con su
 * variable de entorno (regla 10) y aqui solo se validan: un plazo cero o
 * negativo seria esperar para siempre o no esperar nada, y eso se rechaza al
 * arrancar con un mensaje claro en vez de descubrirse en la pantalla.
 *
 * <p>El monitor de disponibilidad (HU-DIS-001) NO usa estos plazos: tiene los
 * suyos y su propio intervalo, y la cifra del 99,95 % no cambia por esto.
 *
 * @param conexionMs  plazo para abrir la conexion con un servicio
 * @param respuestaMs plazo para que conteste una vez conectada
 * @param hilos       cuantas llamadas a la vez como mucho: el ejecutor es acotado
 * @param vigenciaMs  cuanto vale el ultimo resultado; 0 apaga la reutilizacion
 */
@ConfigurationProperties(prefix = "sondeo")
public record ConfiguracionDelSondeo(long conexionMs, long respuestaMs, int hilos, long vigenciaMs) {

    /**
     * Lo que la ronda espera por encima de conectar y responder: la cola del
     * ejecutor cuando hay mas comprobaciones que hilos. Es un margen de
     * seguridad, no un plazo de negocio; lo normal es que cada llamada termine
     * por sus propios plazos mucho antes.
     */
    static final long MARGEN_DE_LA_RONDA_MS = 500;

    public ConfiguracionDelSondeo {
        if (conexionMs <= 0) {
            throw new IllegalArgumentException("sondeo.conexion-ms debe ser mayor que 0 y llego " + conexionMs);
        }
        if (respuestaMs <= 0) {
            throw new IllegalArgumentException("sondeo.respuesta-ms debe ser mayor que 0 y llego " + respuestaMs);
        }
        if (hilos <= 0) {
            throw new IllegalArgumentException("sondeo.hilos debe ser mayor que 0 y llego " + hilos);
        }
        if (vigenciaMs < 0) {
            throw new IllegalArgumentException("sondeo.vigencia-ms no puede ser negativa y llego " + vigenciaMs);
        }
    }

    public Duration plazoDeConexion() {
        return Duration.ofMillis(conexionMs);
    }

    public Duration plazoDeRespuesta() {
        return Duration.ofMillis(respuestaMs);
    }

    /** Lo mas que espera una peticion del panel: conectar, responder y el margen de la cola. */
    public Duration plazoDeLaRonda() {
        return Duration.ofMillis(conexionMs + respuestaMs + MARGEN_DE_LA_RONDA_MS);
    }

    public Duration vigencia() {
        return Duration.ofMillis(vigenciaMs);
    }
}
