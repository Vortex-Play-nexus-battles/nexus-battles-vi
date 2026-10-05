package nexus.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Descuento porcentual de un producto durante una ventana de tiempo — B4.
 *
 * <p>El documento pide que "los productos en promocion" lleven "un marcador que
 * identifique el porcentaje de descuento" y que la vitrina pueda filtrarlos
 * (seccion 7.5), y que los eventos y promociones tengan "fecha de publicacion
 * y vigencia" (7.2.4). De ahi los tres datos: porcentaje, desde y hasta.
 *
 * <p>La vigencia la evalua el servidor con su reloj ({@link #vigenteEn}), nunca
 * el cliente: una promocion que ya vencio no se anuncia al publico aunque siga
 * guardada. El limite de 1 a 90 % lo valida la solicitud (SolicitudPromocion):
 * es un valor provisional de la plataforma, no del documento.
 *
 * @param porcentaje descuento entero, en porcentaje
 * @param desde      primer instante en que aplica (incluido)
 * @param hasta      instante en que deja de aplicar (excluido)
 */
public record Promocion(int porcentaje, Instant desde, Instant hasta) {

    public Promocion {
        Objects.requireNonNull(desde, "desde es obligatorio");
        Objects.requireNonNull(hasta, "hasta es obligatorio");
    }

    /** Si la promocion aplica en ese instante: desde incluido, hasta excluido. */
    public boolean vigenteEn(Instant ahora) {
        return !ahora.isBefore(desde) && ahora.isBefore(hasta);
    }
}
