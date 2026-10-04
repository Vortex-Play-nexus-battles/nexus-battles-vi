package com.nexusbattles.plataforma.comentarios.imagenes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Borra las imagenes que se subieron y nadie uso — contrato 1.4.0: «las no
 * usadas en 24 h se borran».
 *
 * <p>Sin esto, cada imagen que un jugador sube y luego no publica (cierra la
 * pestana, se arrepiente, falla la publicacion) se quedaria para siempre en la
 * base: 2 MB que no ve nadie. Corre cada hora por omision
 * ({@code comentarios.imagenes.limpieza-cada}); la antiguedad la fija
 * {@code comentarios.imagenes.retencion-pendientes}. Que corra en dos
 * instancias a la vez no hace dano: es un DELETE por antiguedad, idempotente.
 *
 * <p>Si falla (la base no responde), lo dice y lo intenta en la siguiente
 * vuelta: una limpieza que no pudo hacerse no es motivo para tumbar nada.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "comentarios.imagenes.limpieza-activa", havingValue = "true", matchIfMissing = true)
public class LimpiezaDeImagenesPendientes {

    private static final Logger BITACORA = LoggerFactory.getLogger(LimpiezaDeImagenesPendientes.class);

    private final ServicioDeImagenes servicio;

    public LimpiezaDeImagenesPendientes(ServicioDeImagenes servicio) {
        this.servicio = servicio;
    }

    // ISO-8601 (PT1H) y no la forma corta (1h): es la que @Scheduled entiende
    // en cualquier version de Spring.
    @Scheduled(
            initialDelayString = "${comentarios.imagenes.limpieza-retraso-inicial:PT10M}",
            fixedDelayString = "${comentarios.imagenes.limpieza-cada:PT1H}")
    public void limpiar() {
        try {
            servicio.limpiarPendientes();
        } catch (RuntimeException fallo) {
            BITACORA.warn("La limpieza de imagenes pendientes no pudo completarse; se reintenta en la"
                    + " siguiente vuelta: {}", fallo.getMessage());
        }
    }
}
