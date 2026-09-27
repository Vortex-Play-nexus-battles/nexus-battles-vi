package com.nexusbattles.ms_ecommerce.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * La promocion de un producto tal como la publica el catalogo
 * ({@code PromocionVigente} de productos.yaml 1.4.0): porcentaje de descuento y
 * vigencia, con {@code desde} incluido y {@code hasta} excluido.
 *
 * <p>La vigencia la vuelve a evaluar la tienda con su propio reloj en vez de
 * fiarse del {@code vigente} que calculo el catalogo al responder: la vitrina
 * guarda una copia del catalogo 30 segundos, y en ese tiempo una promocion
 * puede terminar. Una promocion sin fechas no se aplica: sin saber cuando
 * acaba no se puede saber si sigue.
 *
 * @param porcentaje descuento entero; solo se aplica si esta entre 1 y 99
 * @param desde      primer instante en que aplica (incluido)
 * @param hasta      instante en que deja de aplicar (excluido)
 * @param vigente    lo que dijo el catalogo al responder; informativo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromocionDelCatalogo(Integer porcentaje, Instant desde, Instant hasta, Boolean vigente) {

    /** El porcentaje si la promocion aplica en ese instante; null si no. */
    public Integer porcentajeVigenteEn(Instant ahora) {
        if (porcentaje == null || porcentaje < 1 || porcentaje > 99 || desde == null || hasta == null) {
            return null;
        }
        return !ahora.isBefore(desde) && ahora.isBefore(hasta) ? porcentaje : null;
    }
}
