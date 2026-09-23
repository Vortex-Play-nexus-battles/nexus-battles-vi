package com.nexusbattles.ms_finanzas.contratos;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException;
import com.nexusbattles.ms_finanzas.common.exception.SaldoInsuficienteException;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ConsumirRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ConsumirResponse;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ReservaResponse;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ReservarRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.SaldoResponse;
import com.nexusbattles.ms_finanzas.creditos.service.CreditoService;

import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 * <h2>Por que el servicio arrancado y no MockMvc</h2>
 *
 * El primer intento uso {@code MockMvcTestTarget} de
 * {@code au.com.dius.pact.provider:junit5spring}, que evita levantar nada. No
 * sirve aqui: ese modulo de pact-jvm 4.6.x esta compilado contra Spring 5.3.39
 * y {@code javax.servlet} 3.1.0, y lee las cookies de toda peticion como
 * {@code javax.servlet.http.Cookie}, clase que en Spring Boot 4.1 no existe
 * —todo es {@code jakarta}—. Compila contra el jar y revienta al ejecutar.
 *
 * <p>El modulo {@code junit5} a secas no tiene ni una linea de Spring: habla
 * HTTP con Apache HttpClient 5 contra un puerto real. Sale mas caro —un
 * PostgreSQL de Testcontainers y el contexto entero— y a cambio la
 * verificacion recorre el camino de produccion completo: el borde de
 * seguridad, el {@code context-path} {@code /api/v1}, los convertidores de
 * mensajes reales y el {@code GlobalExceptionHandler} que fija los {@code
 * type} URI que el consumidor compara con valor exacto.
 *
 * <p>La capa de servicio si se simula ({@link CreditoService} es un
 * {@code @MockitoBean}): un pacto describe la <b>puerta</b> —ruta, metodo,
 * codigos, forma del cuerpo—, y sembrar filas para cada estado probaria
 * ademas la persistencia, que ya tiene sus propias pruebas en
 * {@code CreditoServiceTest} y {@code TransaccionRepositoryIT}.
 *
 * <h2>El pacto no registra la autorizacion, y eso es un hallazgo</h2>
 *
 * Ninguna de las cinco interacciones lleva cabecera {@code Authorization},
 * pero {@code /creditos/**} exige {@code ROLE_SERVICIO} desde #455/ADR-005 y
 * ms-subastas <b>si</b> manda su credencial en produccion (#631). El pacto
 * quedo desactualizado respecto al consumidor. No se arregla aqui: los pactos
 * los genera {@code CreditosPactoTest} del lado de ms-subastas y el README de
 * {@code contracts/pactos/} dice que no se editan a mano. Mientras tanto la
 * peticion se firma en {@link #verificarCadaInteraccion} con un token de
 * servicio real —el mismo emisor y el mismo JWKS que usa produccion—, de modo
 * que lo que se verifica sigue siendo la respuesta del servicio y no un 401.
 *
 * <h2>Por que ms-finanzas y no tambien ms-inventario</h2>
 *
 * El otro pacto, {@code ms-subastas-ms-inventario.json}, describe dos
 * interacciones sobre {@code POST /elementos/{id}/transferencias}, un endpoint
 * que <b>todavia no existe</b> (el propio README del directorio lo marca con
 * un aviso). Montar su verificacion hoy seria montar una compuerta roja a
 * proposito, y las compuertas rojas por diseño se acaban desactivando.
 */
@Provider("ms-finanzas")
@PactFolder("../../../contracts/pactos")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Pacto: ms-finanzas cumple lo que ms-subastas espera")
class VerificacionDelPactoDeSubastasTest {

    /** Los mismos identificadores fijos que usa el consumidor. */
    private static final String JUGADOR = "77777777-0000-0000-0000-0000000000cc";
    private static final String VENDEDOR = "88888888-0000-0000-0000-0000000000dd";
    private static final UUID RESERVA = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void identidad(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    /** Ver el javadoc de la clase: el pacto describe la puerta, no el almacen. */
    @MockitoBean
    private CreditoService creditos;

    @LocalServerPort
    private int puerto;

    @BeforeEach
    void apuntarAlServicioArrancado(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", puerto));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificarCadaInteraccion(PactVerificationContext contexto, HttpRequest peticion) {
        // Ver el javadoc: el pacto no registra la credencial que el consumidor
        // si manda. Sin esto, las cinco interacciones responderian 401 y la
        // prueba mediria la cadena de seguridad en vez del contrato.
        peticion.addHeader("Authorization",
                "Bearer " + EmisorDeTokensDePrueba.emisor().tokenDeServicio("ms-subastas"));
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
