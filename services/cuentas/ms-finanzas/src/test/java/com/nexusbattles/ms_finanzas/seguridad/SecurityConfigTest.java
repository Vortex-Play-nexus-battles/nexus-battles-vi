package com.nexusbattles.ms_finanzas.seguridad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.ms_finanzas.creditos.controller.CreditoController;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.AcreditarRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.AcreditarResponse;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ConsumirResponse;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.MovimientoResponse;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ReservaResponse;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ReservarRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.SaldoResponse;
import com.nexusbattles.ms_finanzas.creditos.service.CreditoService;
import com.nexusbattles.ms_finanzas.partidas.AcreditacionPartidaService;
import com.nexusbattles.ms_finanzas.partidas.MisCofresConsultaService;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaResponse;
import com.nexusbattles.ms_finanzas.partidas.api.AcreditacionPartidaController;
import com.nexusbattles.ms_finanzas.partidas.api.MisCofresController;
import com.nexusbattles.ms_finanzas.transacciones.HistorialTransaccionesController;
import com.nexusbattles.ms_finanzas.transacciones.ResultadoTransaccion;
import com.nexusbattles.ms_finanzas.transacciones.ResumenTransaccion;
import com.nexusbattles.ms_finanzas.transacciones.TransaccionConsultaService;

/**
 * Reglas de acceso del libro de créditos (#455, ADR-001/ADR-005), probadas con
 * <b>tokens reales</b>: firmados RS256 por {@link EmisorDeTokensDePrueba} y
 * verificados contra su JWKS por el mismo decodificador que usa producción.
 * Nada de {@code .with(jwt())}: un principal inventado no prueba que el
 * conversor de roles y la cadena hagan lo que se afirma.
 *
 * <p>Las pruebas negativas de suplantación son el centro: un navegador sin
 * token, un jugador que intenta acreditarse o reservar a nombre de otro, un
 * jugador que mira el saldo ajeno, un servicio que intenta leer un historial
 * que no tiene, un token caducado y uno firmado por otra clave.
 */
@WebMvcTest(controllers = {
        CreditoController.class,
        AcreditacionPartidaController.class,
        HistorialTransaccionesController.class,
        MisCofresController.class})
@Import({SecurityConfig.class, DecodificadorDePrueba.class})
class SecurityConfigTest {

    private static final UUID UID_ANA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final UUID UID_OTRO = UUID.fromString("3c9d5a10-6b7e-4d2f-9e1a-2b3c4d5e6f70");
    private static final UUID RESERVA = UUID.fromString("9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreditoService creditoService;

    @MockitoBean
    private AcreditacionPartidaService acreditacionPartidaService;

    @MockitoBean
    private TransaccionConsultaService consultaService;

    @MockitoBean
    private MisCofresConsultaService cofresService;

    // --- tokens ------------------------------------------------------------

    private String comoAna() {
        return "Bearer " + emisor.tokenDeJugador("Ana", UID_ANA);
    }

    private String comoSalasPartidas() {
        return "Bearer " + emisor.tokenDeServicio("salas-partidas");
    }

    private String comoSubastas() {
        return "Bearer " + emisor.tokenDeServicio("ms-subastas");
    }

    private static String reservaDe(UUID uid) {
        return """
                {"jugadorUid":"%s","monto":120,"concepto":"Apuesta en la sala","referenciaId":"sala-1"}
                """.formatted(uid);
    }

    private static String acreditacionA(UUID uid) {
        return """
                {"uid":"%s","monto":1000,"refId":"regalo-1","concepto":"me lo merezco"}
                """.formatted(uid);
    }

    private static String resultadoConGanador(UUID uid) {
        return """
                {"partidaId":"partida-1","tipoPartida":"UNO_A_UNO","ganadorUid":"%s",
                 "participantes":[{"uid":"%s","sancionado":false},{"uid":"%s","sancionado":false}]}
                """.formatted(uid, uid, UID_OTRO);
    }

    private ResumenTransaccion resumen() {
        return new ResumenTransaccion(
                UUID.randomUUID(), "ref-1", new BigDecimal("100.00"),
                "COP", "compra", ResultadoTransaccion.APROBADO, null,
                Instant.parse("2026-09-18T10:00:00Z"));
    }

    // --- casos ---------------------------------------------------------------

    @Nested
    @DisplayName("sin token: 401 en todo lo que no sea la sonda de salud")
    class SinToken {

        @Test
        void reservarAcreditarYResultadoDePartida() throws Exception {
            mvc.perform(post("/creditos/reservar").contentType(JSON)
                            .header("Idempotency-Key", "k-1").content(reservaDe(UID_ANA)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/creditos/acreditar").contentType(JSON).content(acreditacionA(UID_ANA)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/creditos/reservas/" + RESERVA + "/liberar"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/partidas/resultado").contentType(JSON).content(resultadoConGanador(UID_ANA)))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(creditoService, acreditacionPartidaService);
        }

        @Test
        void saldoHistorialYCofres() throws Exception {
            mvc.perform(get("/creditos/" + UID_ANA + "/saldo")).andExpect(status().isUnauthorized());
            mvc.perform(get("/transacciones/mi-historial")).andExpect(status().isUnauthorized());
            mvc.perform(get("/cofres/mios")).andExpect(status().isUnauthorized());
            verifyNoInteractions(creditoService, consultaService, cofresService);
        }

        @Test
        void tokenCaducadoOFirmadoPorOtroTampocoEntra() throws Exception {
            mvc.perform(get("/creditos/" + UID_ANA + "/saldo")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenCaducado("Ana", UID_ANA)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/creditos/acreditar").contentType(JSON).content(acreditacionA(UID_ANA))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenFirmadoPorOtro("Ana", UID_ANA)))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(creditoService);
        }
    }

    @Nested
    @DisplayName("suplantación: un jugador no mueve saldo, ni el suyo ni el de otro")
    class Suplantacion {

        @Test
        void unJugadorNoSeAcreditaASiMismoNiAOtro() throws Exception {
            mvc.perform(post("/creditos/acreditar").contentType(JSON)
                            .header(HttpHeaders.AUTHORIZATION, comoAna()).content(acreditacionA(UID_ANA)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/creditos/acreditar").contentType(JSON)
                            .header(HttpHeaders.AUTHORIZATION, comoAna()).content(acreditacionA(UID_OTRO)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(creditoService);
        }

        @Test
        void unJugadorNoReservaNiLiberaNiConsumeReservas() throws Exception {
            mvc.perform(post("/creditos/reservar").contentType(JSON).header("Idempotency-Key", "k-1")
                            .header(HttpHeaders.AUTHORIZATION, comoAna()).content(reservaDe(UID_OTRO)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/creditos/reservas/" + RESERVA + "/liberar")
                            .header(HttpHeaders.AUTHORIZATION, comoAna()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/creditos/reservas/" + RESERVA + "/consumir").contentType(JSON)
                            .header(HttpHeaders.AUTHORIZATION, comoAna())
                            .content("{\"vendedorUid\":\"" + UID_ANA + "\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/creditos/debitar").contentType(JSON)
                            .header(HttpHeaders.AUTHORIZATION, comoAna())
                            .content("{\"uid\":\"" + UID_OTRO + "\",\"monto\":5,\"refId\":\"r\",\"concepto\":\"c\"}"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(creditoService);
        }

        @Test
        void unJugadorNoInformaElResultadoDeUnaPartidaAunqueSeDeclareGanador() throws Exception {
            mvc.perform(post("/partidas/resultado").contentType(JSON)
                            .header(HttpHeaders.AUTHORIZATION, comoAna()).content(resultadoConGanador(UID_ANA)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(acreditacionPartidaService);
        }

        @Test
        void unJugadorNoMiraElSaldoDeOtro() throws Exception {
            mvc.perform(get("/creditos/" + UID_OTRO + "/saldo").header(HttpHeaders.AUTHORIZATION, comoAna()))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(creditoService);
        }

        @Test
        void unJugadorNoMiraElHistorialDeCreditosDeOtro() throws Exception {
            // #569: el historial dice cuanto aposto y cuanto gano alguien. Es
            // lectura, pero no es publica entre jugadores.
            mvc.perform(get("/creditos/" + UID_OTRO + "/movimientos").header(HttpHeaders.AUTHORIZATION, comoAna()))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(creditoService);
        }

        @Test
        void unServicioNoTieneHistorialNiCofresPropios() throws Exception {
            // El principal de un token de servicio es su client_id, no un uid:
            // dejarlo pasar consultaria el historial de un usuario inexistente
            // (o, peor, de uno cuyo uid coincidiera con ese texto).
            mvc.perform(get("/transacciones/mi-historial").header(HttpHeaders.AUTHORIZATION, comoSalasPartidas()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/cofres/mios").header(HttpHeaders.AUTHORIZATION, comoSalasPartidas()))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(consultaService, cofresService);
        }
    }

    @Nested
    @DisplayName("lo que sí pasa")
    class Autorizado {

        @Test
        void unJugadorConsultaSuPropioSaldo() throws Exception {
            when(creditoService.obtenerSaldo(UID_ANA.toString())).thenReturn(new SaldoResponse(
                    UID_ANA.toString(), new BigDecimal("500"), new BigDecimal("120"), new BigDecimal("380")));

            mvc.perform(get("/creditos/" + UID_ANA + "/saldo").header(HttpHeaders.AUTHORIZATION, comoAna()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saldoDisponible").value(380));
        }

        @Test
        void unJugadorConsultaSuPropioHistorialDeCreditos() throws Exception {
            // #569: antes de este endpoint, una partida con apuesta no aparecia
            // en ninguna vista aunque el saldo hubiera cambiado.
            when(creditoService.movimientos(eq(UID_ANA.toString()), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(new MovimientoResponse(
                            RESERVA, new BigDecimal("60"), "apuesta-sala", "sala-1",
                            "RESERVA", "LIBERADA", "NEUTRO",
                            OffsetDateTime.parse("2026-09-22T02:28:00Z")))));

            mvc.perform(get("/creditos/" + UID_ANA + "/movimientos")
                            .header(HttpHeaders.AUTHORIZATION, comoAna()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].concepto").value("apuesta-sala"))
                    .andExpect(jsonPath("$.content[0].signo").value("NEUTRO"));
        }

        @Test
        void elTamanoDePaginaDelHistorialSeAcotaEnElServidor() throws Exception {
            when(creditoService.movimientos(eq(UID_ANA.toString()), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mvc.perform(get("/creditos/" + UID_ANA + "/movimientos?size=100000")
                            .header(HttpHeaders.AUTHORIZATION, comoAna()))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> pagina = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(creditoService).movimientos(eq(UID_ANA.toString()), pagina.capture());
            org.assertj.core.api.Assertions.assertThat(pagina.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        void unServicioReservaLiberaConsumeYConsultaElSaldoDeCualquiera() throws Exception {
            when(creditoService.reservar(any(ReservarRequest.class), eq("k-1"))).thenReturn(new ReservaResponse(
                    RESERVA, UID_ANA.toString(), new BigDecimal("120"), "ACTIVA",
                    OffsetDateTime.parse("2026-09-24T10:00:00Z")));
            when(creditoService.liberar(RESERVA)).thenReturn(new ReservaResponse(
                    RESERVA, UID_ANA.toString(), new BigDecimal("120"), "LIBERADA", null));
            when(creditoService.consumir(eq(RESERVA), any())).thenReturn(new ConsumirResponse(
                    RESERVA, "CONSUMIDA", new BigDecimal("120"), UID_OTRO.toString(), "TX-1"));
            when(creditoService.obtenerSaldo(UID_OTRO.toString())).thenReturn(new SaldoResponse(
                    UID_OTRO.toString(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

            mvc.perform(post("/creditos/reservar").contentType(JSON).header("Idempotency-Key", "k-1")
                            .header(HttpHeaders.AUTHORIZATION, comoSalasPartidas()).content(reservaDe(UID_ANA)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reservaId").value(RESERVA.toString()));
            mvc.perform(post("/creditos/reservas/" + RESERVA + "/liberar")
                            .header(HttpHeaders.AUTHORIZATION, comoSalasPartidas()))
                    .andExpect(status().isOk());
            mvc.perform(post("/creditos/reservas/" + RESERVA + "/consumir").contentType(JSON)
                            .header(HttpHeaders.AUTHORIZATION, comoSubastas())
                            .content("{\"vendedorUid\":\"" + UID_OTRO + "\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/creditos/" + UID_OTRO + "/saldo").header(HttpHeaders.AUTHORIZATION, comoSubastas()))
                    .andExpect(status().isOk());
        }

        @Test
        void unServicioAcreditaYElUidDelNegocioNoEsElDelActor() throws Exception {
            when(creditoService.acreditar(any(AcreditarRequest.class))).thenReturn(new AcreditarResponse(
                    "TX-ACR-1", "regalo-1", "APLICADO", new BigDecimal("1000"), new BigDecimal("1000")));

            mvc.perform(post("/creditos/acreditar").contentType(JSON)
                            .header(HttpHeaders.AUTHORIZATION, comoSalasPartidas()).content(acreditacionA(UID_ANA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("APLICADO"));

            // El beneficiario es el del cuerpo (Ana); el token solo dice que habla
            // salas-partidas. Ni el client_id ni nada del token llega al servicio.
            org.mockito.ArgumentCaptor<AcreditarRequest> pedido =
                    org.mockito.ArgumentCaptor.forClass(AcreditarRequest.class);
            verify(creditoService).acreditar(pedido.capture());
            org.assertj.core.api.Assertions.assertThat(pedido.getValue().uid()).isEqualTo(UID_ANA.toString());
        }

        @Test
        void unServicioInformaElResultadoDeLaPartida() throws Exception {
            when(acreditacionPartidaService.procesarResultadoPartida(any()))
                    .thenReturn(new ResultadoPartidaResponse("partida-1", List.of(), List.of()));

            mvc.perform(post("/partidas/resultado").contentType(JSON)
                            .header(HttpHeaders.AUTHORIZATION, comoSalasPartidas())
                            .content(resultadoConGanador(UID_ANA)))
                    .andExpect(status().isOk());
        }

        @Test
        void unUsuarioConsultaSuHistorialConElUidDelToken() throws Exception {
            Pageable esperado = PageRequest.of(0, 20);
            Page<ResumenTransaccion> pagina = new PageImpl<>(List.of(resumen()), esperado, 1);
            when(consultaService.listarPorUsuario(eq(UID_ANA.toString()), eq(esperado))).thenReturn(pagina);

            mvc.perform(get("/transacciones/mi-historial").header(HttpHeaders.AUTHORIZATION, comoAna()))
                    .andExpect(status().isOk());
            // El uid sale del claim `uid` del token (ADR-002), no del sujeto (el apodo).
            verify(consultaService).listarPorUsuario(eq(UID_ANA.toString()), any());
        }

        @Test
        void laSondaDeSaludNoNecesitaToken() throws Exception {
            // No hay actuator en este @WebMvcTest: lo que importa es que la
            // cadena no responde 401 (llega al 404 del despacho).
            mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        }
    }
}
