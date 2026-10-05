package nexus.api;

import java.time.Instant;

import nexus.dominio.Promocion;

/**
 * La promocion tal como la publica el catalogo — B4 (contrato productos 1.4.0,
 * esquema {@code PromocionVigente}).
 *
 * <p>{@code vigente} lo calcula el servidor con su reloj en el momento de
 * responder: el cliente no tiene que comparar fechas ni fiarse de su hora. La
 * proyeccion publica solo incluye la promocion cuando esta vigente.
 */
public record PromocionVista(int porcentaje, Instant desde, Instant hasta, boolean vigente) {

        /** La vista de una promocion en un instante; nulo si no hay promocion. */
        public static PromocionVista de(Promocion promocion, Instant ahora) {
                if (promocion == null) {
                        return null;
                }
                return new PromocionVista(
                        promocion.porcentaje(),
                        promocion.desde(),
                        promocion.hasta(),
                        promocion.vigenteEn(ahora));
        }
}
