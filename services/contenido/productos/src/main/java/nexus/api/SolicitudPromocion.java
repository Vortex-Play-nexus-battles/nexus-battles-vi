package nexus.api;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * La promocion tal como la escribe el administrador al crear o modificar un
 * producto — B4 (contrato productos 1.4.0, esquema {@code Promocion}).
 *
 * <p>El documento pide el marcador con el porcentaje de descuento y la
 * vigencia, no un rango: el tope de 1 a 90 % es una DECISION TECNICA
 * PROVISIONAL (un descuento del 100 % regala el producto, y uno de 0 % no es
 * una promocion) mientras el PO no fije otro; esta aqui y en el contrato, en un
 * solo sitio cada uno.
 *
 * <p>Para terminar una promocion antes de tiempo se modifica su {@code hasta}:
 * una promocion vencida sigue guardada pero ya no se anuncia.
 */
public record SolicitudPromocion(

        @NotNull(message = "La promocion necesita su porcentaje")
        @Min(value = 1, message = "El porcentaje de la promocion va de 1 a 90")
        @Max(value = 90, message = "El porcentaje de la promocion va de 1 a 90")
        Integer porcentaje,

        @NotNull(message = "La promocion necesita su fecha de inicio (desde)")
        Instant desde,

        @NotNull(message = "La promocion necesita su fecha de fin (hasta)")
        Instant hasta) {

        @AssertTrue(message = "La promocion debe terminar despues de empezar (desde < hasta)")
        public boolean isVigenciaOrdenada() {
                return desde == null || hasta == null || desde.isBefore(hasta);
        }
}
