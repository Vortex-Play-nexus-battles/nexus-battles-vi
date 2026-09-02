package nexus.api;

import java.math.BigDecimal;

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

        BigDecimal tasaDeCaida) {

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
                        || tasaDeCaida != null;
        }
}
