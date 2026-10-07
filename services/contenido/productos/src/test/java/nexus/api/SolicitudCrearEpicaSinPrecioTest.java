package nexus.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import nexus.dominio.TipoProducto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RG-085 / RF-MOT-36: la unica fuente de epicas es derrotar al Master, asi que
 * una epica no se vende. La regla vive en la propia solicitud de creacion, que es
 * la que validan tanto el alta (POST) como la fusion de una modificacion
 * (PUT/PATCH), y con el mismo validador que usa la semilla.
 */
class SolicitudCrearEpicaSinPrecioTest {

        private final Validator validator =
                Validation.buildDefaultValidatorFactory().getValidator();

        @Test
        @DisplayName("una epica con 0 creditos, 0 pesos y sin premium es valida: asi la deja la semilla")
        void epicaSinPrecioEsValida() {
                assertEquals(Set.of(), mensajes(epica(0, BigDecimal.ZERO, false)));
        }

        @Test
        @DisplayName("una epica sin precio en moneda real (null) tambien es valida")
        void epicaSinPrecioEnPesosEsValida() {
                assertEquals(Set.of(), mensajes(epica(0, null, false)));
        }

        @Test
        @DisplayName("una epica con precio en creditos mayor que cero se rechaza explicando la regla")
        void rechazaPrecioEnCreditos() {
                assertRechazaPorLaRegla(epica(500, BigDecimal.ZERO, false));
        }

        @Test
        @DisplayName("una epica con precio en moneda real mayor que cero se rechaza explicando la regla")
        void rechazaPrecioEnMonedaReal() {
                assertRechazaPorLaRegla(epica(0, new BigDecimal("10000"), false));
        }

        @Test
        @DisplayName("una epica premium se rechaza explicando la regla, aunque su precio sea cero")
        void rechazaPremium() {
                assertRechazaPorLaRegla(epica(null, BigDecimal.ZERO, true));
        }

        @Test
        @DisplayName("la regla es solo de las epicas: un arma con precio sigue siendo valida")
        void otrosTiposConservanSuPrecio() {
                SolicitudCrearProducto arma = new SolicitudCrearProducto(
                        "Espada solar", "productos/espada.webp", "Arma de prueba", TipoProducto.ARMA,
                        100, 500, null, false, null, null, null, null, null, null, null, null,
                        null, null, null, 40, new BigDecimal("12.5"));

                assertEquals(Set.of(), mensajes(arma));
        }

        private void assertRechazaPorLaRegla(SolicitudCrearProducto solicitud) {
                Set<String> mensajes = mensajes(solicitud);
                assertTrue(
                        mensajes.stream().anyMatch(m -> m.contains("derrotando al Máster")),
                        "mensajes: " + mensajes);
        }

        private Set<String> mensajes(SolicitudCrearProducto solicitud) {
                return validator.validate(solicitud).stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toSet());
        }

        private static SolicitudCrearProducto epica(
                        Integer precioCreditos, BigDecimal precioMonedaReal, boolean premium) {
                return new SolicitudCrearProducto(
                        "Epica de prueba", "productos/epica-prueba.webp", "Epica de prueba", TipoProducto.EPICA,
                        -1, precioCreditos, precioMonedaReal, premium, null,
                        "550e8400-e29b-41d4-a716-446655440000", null, null, null, 2,
                        "Aumenta el poder de todo el equipo", "Duplica el poder durante dos turnos",
                        null, null, null, null, null);
        }
}
