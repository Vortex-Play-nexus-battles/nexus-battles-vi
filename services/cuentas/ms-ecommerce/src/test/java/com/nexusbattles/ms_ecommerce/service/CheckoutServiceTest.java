package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.client.FinanzasClient;
import com.nexusbattles.ms_ecommerce.dto.CheckoutRequestDTO;
import com.nexusbattles.ms_ecommerce.dto.TarjetaDTO;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import com.nexusbattles.ms_ecommerce.service.CheckoutService.CarritoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.service.CheckoutService.PasarelaNoDisponibleException;
import com.nexusbattles.ms_ecommerce.service.CheckoutService.ResultadoCompra;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Checkout: se cobra el carrito del usuario y solo se vacía si el pago se aprueba")
class CheckoutServiceTest {

    private static final String UID = "44444444-4444-4444-4444-444444444444";
    private static final BigDecimal TOTAL = new BigDecimal("250");

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private FinanzasClient finanzasClient;

    @Mock
    private CheckoutRequestDTO request;

    @Mock
    private TarjetaDTO tarjeta;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payload;

    private CheckoutService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CheckoutService(carritoRepository, finanzasClient);
    }

    /** Un carrito del usuario con los ítems dados. */
    private Carrito carritoCon(List<Object> items) {
        Carrito carrito = mock(Carrito.class);
        lenient().doReturn(items).when(carrito).getItems();
        lenient().doReturn(TOTAL).when(carrito).getTotal();
        lenient().doReturn(tarjeta).when(request).tarjeta();
        when(carritoRepository.findByIdAndUsuarioId(any(), eq(UID))).thenReturn(Optional.of(carrito));
        return carrito;
    }

    private static List<Object> unItem() {
        return new ArrayList<>(List.of(new Object()));
    }

    /**
     * Respuesta de error de ms-finanzas con el estado HTTP dado (-1 = sin red).
     *
     * <p>Hay que llamarla ANTES de {@code when(...).thenThrow(...)} y guardar el
     * resultado en una variable: por dentro hace su propio {@code when}, y
     * anidar un stub dentro de otro hace que Mockito falle con
     * {@code UnfinishedStubbingException}.
     */
    private static FeignException respuestaDeFinanzas(int estado) {
        FeignException error = mock(FeignException.class);
        when(error.status()).thenReturn(estado);
        return error;
    }

    @Test
    @DisplayName("un carrito que no es del usuario no se encuentra y no se cobra")
    void carritoAjenoNoSeCobra() {
        when(carritoRepository.findByIdAndUsuarioId(any(), eq(UID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.ejecutarCompra(UID, request))
            .isInstanceOf(CarritoNoDisponibleException.class);
        verifyNoInteractions(finanzasClient);
    }

    @Test
    @DisplayName("un carrito vacío no llega a la pasarela")
    void carritoVacioNoSeCobra() {
        carritoCon(new ArrayList<>());

        assertThatThrownBy(() -> servicio.ejecutarCompra(UID, request))
            .isInstanceOf(CarritoNoDisponibleException.class)
            .hasMessageContaining("vacío");
        verifyNoInteractions(finanzasClient);
    }

    @Test
    @DisplayName("aprobado: se cobra el total del carrito y el carrito se vacía")
    void aprobadoVaciaElCarrito() {
        List<Object> items = unItem();
        Carrito carrito = carritoCon(items);

        ResultadoCompra resultado = servicio.ejecutarCompra(UID, request);

        assertThat(resultado.aprobado()).isTrue();
        verify(finanzasClient).procesarPago(payload.capture());
        assertThat(payload.getValue())
            .containsEntry("monto", TOTAL)
            .containsEntry("tarjeta", tarjeta);
        assertThat(items).isEmpty();
        verify(carrito).setTotal(BigDecimal.ZERO);
        verify(carritoRepository).save(carrito);
    }

    @ParameterizedTest(name = "HTTP {0} de la pasarela es un rechazo de la tarjeta")
    @ValueSource(ints = {400, 402, 409, 422})
    void rechazoNoTocaElCarrito(int estado) {
        List<Object> items = unItem();
        Carrito carrito = carritoCon(items);
        FeignException rechazo = respuestaDeFinanzas(estado);
        when(finanzasClient.procesarPago(any())).thenThrow(rechazo);

        ResultadoCompra resultado = servicio.ejecutarCompra(UID, request);

        assertThat(resultado.aprobado()).isFalse();
        assertThat(resultado.mensaje()).contains("rechazó");
        assertThat(items).hasSize(1);
        verify(carrito, never()).setTotal(any());
        verify(carritoRepository, never()).save(any());
    }

    @ParameterizedTest(name = "HTTP {0} de la pasarela NO es culpa de la tarjeta: servicio no disponible")
    @ValueSource(ints = {-1, 401, 403, 500, 503})
    void pasarelaNoDisponible(int estado) {
        List<Object> items = unItem();
        carritoCon(items);
        FeignException caida = respuestaDeFinanzas(estado);
        when(finanzasClient.procesarPago(any())).thenThrow(caida);

        assertThatThrownBy(() -> servicio.ejecutarCompra(UID, request))
            .isInstanceOf(PasarelaNoDisponibleException.class)
            .hasMessageContaining("No se hizo ningún cobro");
        assertThat(items).hasSize(1);
        verify(carritoRepository, never()).save(any());
    }
}
