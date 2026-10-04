package nexus.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import nexus.dominio.ParteArmadura;

public record SolicitudModificarProducto(

        String nombre,

        String imagen,

        String descripcion,

        Integer tiraje,

        Integer precioCreditos,

        BigDecimal precioMonedaReal,

        Boolean premium,

        String prototipo,

        String heroe,

        Integer costoPoder,

        BigDecimal multiplicadorNivel,

        Integer turnosCarga,

        Integer turnosRecarga,

        String efectoGeneral,

        String efectoPotenciado,

        Integer defensa,

        ParteArmadura parte,

        String efecto,

        Integer poderDeAtaque,

        BigDecimal tasaDeCaida,

        // B4 (contrato 1.4.0). Reemplaza la promocion entera: para terminarla
        // antes de tiempo se envia con otro `hasta`.
        @Valid
        SolicitudPromocion promocion) {

        /** El constructor canonico es el que usa Jackson, aunque haya otros. */
        @JsonCreator
        public SolicitudModificarProducto {
        }

        /** La forma anterior a B4, sin promocion (pruebas previas). */
        public SolicitudModificarProducto(
                        String nombre,
                        String imagen,
                        String descripcion,
                        Integer tiraje,
                        Integer precioCreditos,
                        BigDecimal precioMonedaReal,
                        Boolean premium,
                        String prototipo,
                        String heroe,
                        Integer costoPoder,
                        BigDecimal multiplicadorNivel,
                        Integer turnosCarga,
                        Integer turnosRecarga,
                        String efectoGeneral,
                        String efectoPotenciado,
                        Integer defensa,
                        ParteArmadura parte,
                        String efecto,
                        Integer poderDeAtaque,
                        BigDecimal tasaDeCaida) {
                this(nombre, imagen, descripcion, tiraje, precioCreditos, precioMonedaReal, premium,
                        prototipo, heroe, costoPoder, multiplicadorNivel, turnosCarga, turnosRecarga,
                        efectoGeneral, efectoPotenciado, defensa, parte, efecto, poderDeAtaque,
                        tasaDeCaida, null);
        }

        @AssertTrue(message = "Debe modificar al menos un campo")
        public boolean isAlgunCampoPresente() {
                return nombre != null
                        || imagen != null
                        || descripcion != null
                        || tiraje != null
                        || precioCreditos != null
                        || precioMonedaReal != null
                        || premium != null
                        || prototipo != null
                        || heroe != null
                        || costoPoder != null
                        || multiplicadorNivel != null
                        || turnosCarga != null
                        || turnosRecarga != null
                        || efectoGeneral != null
                        || efectoPotenciado != null
                        || defensa != null
                        || parte != null
                        || efecto != null
                        || poderDeAtaque != null
                        || tasaDeCaida != null
                        || promocion != null;
        }
}
