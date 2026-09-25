package com.nexusbattles.ms_finanzas.creditos.controller;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.*;
import com.nexusbattles.ms_finanzas.creditos.service.CreditoService;
import com.nexusbattles.ms_finanzas.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura de logica del controlador (mapeo request/response, codigos de
 * estado), no de reglas de acceso -- eso ya esta cubierto a fondo por
 * {@code SecurityConfigTest} (suplantacion, tokens caducados, etc.).
 *
 * <p>Cierra el hueco anotado en build.gradle desde la resolucion de
 * conflictos del PR #379: CreditoController estaba al 0% de cobertura
 * porque nadie ejercitaba debitar, reversar ni consultarOperacion, y las
 * ramas de negocio (idempotencia, reserva ya liberada, etc.) tampoco se
 * probaban a nivel HTTP.
 *
 * <p>Usa tokens reales de servicio, igual que SecurityConfigTest: los
 * endpoints que mueven saldo exigen ROLE_SERVICIO.
 */
@WebMvcTest(controllers = CreditoController.class)
@Import({SecurityConfig.class, DecodificadorDePrueba.class})
class CreditoControllerTest {

    private static final UUID UID_ANA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final UUID UID_VENDEDOR = UUID.fromString("3c9d5a10-6b7e-4d2f-9e1a-2b3c4d5e6f70");
    private static final UUID RESERVA = UUID.fromString("9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreditoService creditoService;

    private String comoServicio() {
        return "Bearer " + emisor.tokenDeServicio("ms-subastas");
    }

    private String comoAna() {
        return "Bearer " + emisor.tokenDeJugador("Ana", UID_ANA);
    }

    @Nested
    @DisplayName("saldo")
    class Saldo {

        @Test
        @DisplayName("un servicio consulta el saldo de cualquiera: 200 con los tres montos")
        void unServicioConsultaElSaldo() throws Exception {
            when(creditoService.obtenerSaldo(UID_ANA.toString())).thenReturn(new SaldoResponse(
                UID_ANA.toString(), new BigDecimal("500"), new BigDecimal("120"), new BigDecimal("380")));

            mvc.perform(get("/creditos/" + UID_ANA + "/saldo")
                    .header(HttpHeaders.AUTHORIZATION, comoServicio()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jugadorUid").value(UID_ANA.toString()))
                .andExpect(jsonPath("$.saldoBruto").value(500))
                .andExpect(jsonPath("$.saldoReservado").value(120))
                .andExpect(jsonPath("$.saldoDisponible").value(380));
        }
    }

    @Nested
    @DisplayName("reservar")
    class Reservar {

        @Test
        @DisplayName("una reserva valida: 201 con la reserva creada")
        void reservaValida() throws Exception {
            when(creditoService.reservar(any(ReservarRequest.class), eq("k-1"))).thenReturn(new ReservaResponse(
                RESERVA, UID_ANA.toString(), new BigDecimal("120"), "ACTIVA",
                OffsetDateTime.parse("2026-09-24T10:00:00Z")));

            mvc.perform(post("/creditos/reservar").contentType(JSON)
                    .header(HttpHeaders.AUTHORIZATION, comoServicio())
                    .header("Idempotency-Key", "k-1")
                    .content("""
                                    {"jugadorUid":"%s","monto":120,"concepto":"Apuesta","referenciaId":"sala-1"}
                                    """.formatted(UID_ANA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservaId").value(RESERVA.toString()))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
        }
    }

    @Nested
    @DisplayName("liberar")
    class Liberar {

        @Test
        @DisplayName("libera una reserva activa: 200 con estado LIBERADA")
        void liberaReservaActiva() throws Exception {
            when(creditoService.liberar(RESERVA)).thenReturn(new ReservaResponse(
                RESERVA, UID_ANA.toString(), new BigDecimal("120"), "LIBERADA", null));

            mvc.perform(post("/creditos/reservas/" + RESERVA + "/liberar")
                    .header(HttpHeaders.AUTHORIZATION, comoServicio()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LIBERADA"));
        }
    }

    @Nested
    @DisplayName("consumir")
    class Consumir {

        @Test
        @DisplayName("consume una reserva a favor de un vendedor: 200 con el transaccionId")
        void consumeReserva() throws Exception {
            when(creditoService.consumir(eq(RESERVA), any(ConsumirRequest.class))).thenReturn(new ConsumirResponse(
                RESERVA, "CONSUMIDA", new BigDecimal("120"), UID_VENDEDOR.toString(), "TX-1"));

            mvc.perform(post("/creditos/reservas/" + RESERVA + "/consumir").contentType(JSON)
                    .header(HttpHeaders.AUTHORIZATION, comoServicio())
                    .content("{\"vendedorUid\":\"" + UID_VENDEDOR + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONSUMIDA"))
                .andExpect(jsonPath("$.transaccionId").value("TX-1"));
        }
    }

    @Nested
    @DisplayName("debitar")
    class Debitar {

        @Test
        @DisplayName("debita un monto directo: 200 con el nuevo saldo disponible")
        void debitaMontoDirecto() throws Exception {
            when(creditoService.debitar(any(DebitarRequest.class))).thenReturn(new DebitarResponse(
                "TX-DEB-1", "refId-1", "EXITOSO", new BigDecimal("50"), new BigDecimal("450")));

            mvc.perform(post("/creditos/debitar").contentType(JSON)
                    .header(HttpHeaders.AUTHORIZATION, comoServicio())
                    .content("""
                                    {"uid":"%s","monto":50,"refId":"refId-1","concepto":"comision"}
                                    """.formatted(UID_ANA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaccionId").value("TX-DEB-1"))
                .andExpect(jsonPath("$.refId").value("refId-1"))
                .andExpect(jsonPath("$.montoDebitado").value(50))
                .andExpect(jsonPath("$.nuevoSaldoDisponible").value(450));
        }
    }

    @Nested
    @DisplayName("reversar")
    class Reversar {

        @Test
        @DisplayName("reversa un debito por refId: 200 con estado REVERSADO")
        void reversaUnDebito() throws Exception {
            when(creditoService.reversar(any(ReversarRequest.class))).thenReturn(new ReversarResponse(
                "refId-1", "REVERSADO", new BigDecimal("50"), "compra cancelada"));

            mvc.perform(post("/creditos/reversar").contentType(JSON)
                    .header(HttpHeaders.AUTHORIZATION, comoServicio())
                    .content("""
                                    {"refId":"refId-1","motivo":"compra cancelada"}
                                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refId").value("refId-1"))
                .andExpect(jsonPath("$.estado").value("REVERSADO"))
                .andExpect(jsonPath("$.montoReversado").value(50))
                .andExpect(jsonPath("$.motivo").value("compra cancelada"));
        }

        @Test
        @DisplayName("reversar dos veces el mismo refId responde YA_REVERSADO, no error")
        void reversarDosVecesEsIdempotente() throws Exception {
            when(creditoService.reversar(any(ReversarRequest.class))).thenReturn(new ReversarResponse(
                "refId-1", "YA_REVERSADO", new BigDecimal("50"), "compra cancelada"));

            mvc.perform(post("/creditos/reversar").contentType(JSON)
                    .header(HttpHeaders.AUTHORIZATION, comoServicio())
                    .content("""
                                    {"refId":"refId-1","motivo":"compra cancelada"}
                                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("YA_REVERSADO"));
        }
    }

    @Nested
    @DisplayName("consultar operacion")
    class ConsultarOperacion {

        @Test
        @DisplayName("consulta una operacion por refId: 200 con sus datos")
        void consultaPorRefId() throws Exception {
            when(creditoService.consultarOperacionPorRefId("refId-1")).thenReturn(new OperacionResponse(
                "refId-1", UID_ANA.toString(), new BigDecimal("50"), "comision", "CONSUMIDA",
                OffsetDateTime.parse("2026-09-24T10:00:00Z")));

            mvc.perform(get("/creditos/operaciones/refId-1")
                    .header(HttpHeaders.AUTHORIZATION, comoServicio()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refId").value("refId-1"))
                .andExpect(jsonPath("$.uid").value(UID_ANA.toString()))
                .andExpect(jsonPath("$.estado").value("CONSUMIDA"));
        }
    }

    @Nested
    @DisplayName("acreditar")
    class Acreditar {

        @Test
        @DisplayName("acredita un monto: 200 con el nuevo saldo disponible")
        void acreditaMonto() throws Exception {
            when(creditoService.acreditar(any(AcreditarRequest.class))).thenReturn(new AcreditarResponse(
                "TX-ACR-1", "refId-2", "APLICADO", new BigDecimal("1000"), new BigDecimal("1000")));

            mvc.perform(post("/creditos/acreditar").contentType(JSON)
                    .header(HttpHeaders.AUTHORIZATION, comoServicio())
                    .content("""
                                    {"uid":"%s","monto":1000,"refId":"refId-2","concepto":"regalo"}
                                    """.formatted(UID_ANA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaccionId").value("TX-ACR-1"))
                .andExpect(jsonPath("$.estado").value("APLICADO"))
                .andExpect(jsonPath("$.montoAcreditado").value(1000));
        }
    }
}
