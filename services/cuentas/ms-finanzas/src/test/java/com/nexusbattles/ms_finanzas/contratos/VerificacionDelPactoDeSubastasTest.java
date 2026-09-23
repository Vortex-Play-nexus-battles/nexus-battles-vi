package com.nexusbattles.ms_finanzas.contratos;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;

import com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException;
import com.nexusbattles.ms_finanzas.common.exception.SaldoInsuficienteException;
import com.nexusbattles.ms_finanzas.comun.GlobalExceptionHandler;
import com.nexusbattles.ms_finanzas.creditos.controller.CreditoController;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ConsumirRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ConsumirResponse;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ReservaResponse;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ReservarRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.SaldoResponse;
import com.nexusbattles.ms_finanzas.creditos.service.CreditoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ms-finanzas cumple lo que ms-subastas cree que promete — R11.4.
 *
 * <h2>El hueco que cierra</h2>
 *
 * Los pactos de {@code contracts/pactos/} existian desde agosto y <b>nadie
 * los verificaba</b>: un {@code grep} de «pact» en {@code .github/} devolvia
 * cero. Eran dos archivos JSON que solo se regeneraban si alguien ejecutaba a
 * mano el comando del README. Un pacto que no se verifica no es un contrato:
 * es la opinion del consumidor sobre el proveedor, escrita una vez.
 *
 * <p>Esto lo convierte en una compuerta: si ms-finanzas cambia una ruta, un
 * codigo de estado o el nombre de un campo que ms-subastas lee, esta prueba
 * se pone roja <b>en el repositorio de quien hizo el cambio</b>, no en la
 * integracion de la semana siguiente.
 *
 * <h2>Por que MockMvc y no el servicio arrancado con su base de datos</h2>
 *
 * Un pacto describe el <b>contrato HTTP</b>: ruta, metodo, codigos, forma del
 * cuerpo. Arrancar PostgreSQL y sembrar filas para cada estado añadiria unos
 * dos minutos por corrida y probaria ademas la persistencia, que ya tiene sus
 * propias pruebas. Aqui se monta el controlador real con su
 * {@link GlobalExceptionHandler} real —que es quien fija los {@code type} URI
 * que el consumidor compara literalmente— y se simula la capa de servicio.
 *
 * <p>Dicho de otro modo: esto verifica que <b>la puerta</b> es la que el
 * consumidor espera. Que detras haya saldo de verdad lo comprueban las
 * pruebas de {@code CreditoService} y el banco E2E.
 *
 * <h2>Por que ms-finanzas y no tambien ms-inventario</h2>
 *
 * El otro pacto, {@code ms-subastas-ms-inventario.json}, describe dos
 * interacciones sobre {@code POST /elementos/{id}/transferencias}, un endpoint
 * que <b>todavia no existe</b> (el propio README del directorio lo marca con
 * ⚠️). Montar su verificacion hoy seria montar una compuerta roja a
 * proposito, y las compuertas rojas por diseño se acaban desactivando.
 */
@Provider("ms-finanzas")
@PactFolder("../../../contracts/pactos")
@DisplayName("Pacto: ms-finanzas cumple lo que ms-subastas espera")
class VerificacionDelPactoDeSubastasTest {

    /** Los mismos identificadores fijos que usa el consumidor. */
    private static final String JUGADOR = "77777777-0000-0000-0000-0000000000cc";
    private static final String VENDEDOR = "88888888-0000-0000-0000-0000000000dd";
    private static final UUID RESERVA = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private final CreditoService creditos = Mockito.mock(CreditoService.class);

    @BeforeEach
    void apuntarAlControladorReal(PactVerificationContext contexto) {
        MockMvcTestTarget objetivo = new MockMvcTestTarget();
        // El manejador de errores entra a proposito: es quien pone los `type`
        // URI que el consumidor compara con valor exacto. Sin el, los dos
        // casos de error pasarian por casualidad o fallarian por el motivo
        // equivocado.
        objetivo.setControllers(new CreditoController(creditos));
        objetivo.setControllerAdvice(new GlobalExceptionHandler());
        // El contexto de servlet arranca en /api/v1 en produccion
        // (server.servlet.context-path), y el pacto pide rutas con ese
        // prefijo: sin esto, MockMvc no encontraria ninguna.
        objetivo.setServletPath("/api/v1");
        contexto.setTarget(objetivo);
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificarCadaInteraccion(PactVerificationContext contexto) {
        contexto.verifyInteraction();
    }

    // ------------------------------------------------------------- estados

    @State("el jugador tiene saldo disponible suficiente")
    void conSaldo() {
        Mockito.when(creditos.reservar(ArgumentMatchers.any(ReservarRequest.class), ArgumentMatchers.anyString()))
                .thenReturn(new ReservaResponse(
                        RESERVA, JUGADOR, new BigDecimal("110.00"), "ACTIVA",
                        OffsetDateTime.parse("2026-09-26T10:00:00Z")));
    }

    @State("el jugador no tiene saldo disponible suficiente")
    void sinSaldo() {
        // 422 con `type` estable: el consumidor lo distingue de una averia
        // justamente por ahi, no por el codigo.
        Mockito.when(creditos.reservar(ArgumentMatchers.any(ReservarRequest.class), ArgumentMatchers.anyString()))
                .thenThrow(new SaldoInsuficienteException(
                        "El jugador no tiene saldo disponible suficiente para esta reserva"));
    }

    @State("existe una reserva activa del comprador")
    void conReservaActiva() {
        Mockito.when(creditos.consumir(ArgumentMatchers.any(UUID.class), ArgumentMatchers.any(ConsumirRequest.class)))
                .thenReturn(new ConsumirResponse(
                        RESERVA, "CONSUMIDA", new BigDecimal("110.00"), VENDEDOR,
                        "11111111-0000-0000-0000-0000000000aa"));
    }

    @State("no existe ninguna reserva con ese identificador")
    void sinReserva() {
        Mockito.when(creditos.liberar(ArgumentMatchers.any(UUID.class)))
                .thenThrow(new ReservaNoEncontradaException(
                        "No existe ninguna reserva con el identificador indicado"));
    }

    @State("el jugador tiene una cuenta de creditos")
    void conCuenta() {
        Mockito.when(creditos.obtenerSaldo(ArgumentMatchers.anyString()))
                .thenReturn(new SaldoResponse(
                        JUGADOR, new BigDecimal("500.0"), new BigDecimal("200.0"),
                        new BigDecimal("300.0")));
    }
}
