package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.CheckoutRequestDTO;
import com.nexusbattles.ms_ecommerce.service.CheckoutService;
import com.nexusbattles.ms_ecommerce.service.CheckoutService.CarritoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.service.CheckoutService.PasarelaNoDisponibleException;
import com.nexusbattles.ms_ecommerce.service.CheckoutService.ResultadoCompra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Checkout: el comprador sale del token y el resultado llega en un contrato fijo")
class CheckoutControllerTest {

    private static final String UID = "44444444-4444-4444-4444-444444444444";

    private final CheckoutService servicio = mock(CheckoutService.class);
    private final CheckoutRequestDTO request = mock(CheckoutRequestDTO.class);
    private final CheckoutController controlador = new CheckoutController(servicio);

    private static Jwt tokenCon(String claim, String valor) {
        return Jwt.withTokenValue("token").header("alg", "none").claim(claim, valor).build();
    }

    @Test
    @DisplayName("aprobado: 200 con aprobado=true y el uid del token va al servicio")
    void aprobado() {
        when(servicio.ejecutarCompra(UID, request)).thenReturn(ResultadoCompra.aprobado("ok"));

        ResponseEntity<Map<String, Object>> respuesta = controlador.procesarCheckout(tokenCon("uid", UID), request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody())
            .containsEntry("aprobado", true)
            .containsEntry("estado", "APROBADO")
            .containsEntry("mensaje", "ok");
        verify(servicio).ejecutarCompra(UID, request);
    }

    @Test
    @DisplayName("rechazado: 200 con aprobado=false y el motivo para el jugador")
    void rechazado() {
        when(servicio.ejecutarCompra(UID, request))
            .thenReturn(ResultadoCompra.rechazado("Fondos insuficientes"));

        ResponseEntity<Map<String, Object>> respuesta = controlador.procesarCheckout(tokenCon("uid", UID), request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody())
            .containsEntry("aprobado", false)
            .containsEntry("estado", "RECHAZADO")
            .containsEntry("mensaje", "Fondos insuficientes");
    }

    @Test
    @DisplayName("un token sin uid no paga nada: 401")
    void tokenSinUid() {
        ResponseEntity<Map<String, Object>> respuesta =
            controlador.procesarCheckout(tokenCon("sub", "alguien"), request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("sin token no paga nada: 401")
    void sinToken() {
        ResponseEntity<Map<String, Object>> respuesta = controlador.procesarCheckout(null, request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("carrito no disponible: 409 con el mensaje")
    void carritoNoDisponible() {
        ResponseEntity<Map<String, Object>> respuesta =
            controlador.carritoNoDisponible(new CarritoNoDisponibleException("Tu carrito está vacío."));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(respuesta.getBody()).containsEntry("mensaje", "Tu carrito está vacío.");
    }

    @Test
    @DisplayName("pasarela caída: 503 con el mensaje")
    void pasarelaNoDisponible() {
        ResponseEntity<Map<String, Object>> respuesta = controlador.pasarelaNoDisponible(
            new PasarelaNoDisponibleException("No se hizo ningún cobro."));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        assertThat(respuesta.getBody()).containsEntry("mensaje", "No se hizo ningún cobro.");
    }
}
