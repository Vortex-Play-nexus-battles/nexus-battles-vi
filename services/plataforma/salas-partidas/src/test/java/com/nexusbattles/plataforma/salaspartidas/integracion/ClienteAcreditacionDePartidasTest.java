package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.resiliencia.EstadoDelCorta;
import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas.Acreditacion;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas.InformeDePartida;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas.TipoDePartida;
import com.nexusbattles.plataforma.salaspartidas.configuracion.ConfiguracionDeResiliencia;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ClienteAcreditacionDePartidas · POST /partidas/resultado contra ms-finanzas (HU-JUE-012)")
class ClienteAcreditacionDePartidasTest {

    private static final String BASE = "http://srv-ms-finanzas:8093/api/v1";
    private static final UUID PARTIDA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID COFRE = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private MockRestServiceServer libro;
    private ClienteAcreditacionDePartidas cliente;
    private CortaCircuitos corta;

    @BeforeEach
    void prepararElLibro() {
        RestClient.Builder constructor = RestClient.builder();
        libro = MockRestServiceServer.bindTo(constructor).build();
        corta = new CortaCircuitos(ConfiguracionDeResiliencia.MS_FINANZAS,
                ConfiguracionDeResiliencia.SECCION_APUESTAS, 3, Duration.ofSeconds(30),
                new ClienteCreditosTest.RelojManual(), new RegistroDeDegradacion());
        cliente = new ClienteAcreditacionDePartidas(constructor.build(), BASE + "/", corta);
    }

    private static InformeDePartida unoContraUnoGanadoPorAna() {
        return new InformeDePartida(PARTIDA, TipoDePartida.UNO_A_UNO, List.of(ANA),
                List.of(new InformeDePartida.Jugador(ANA, false), new InformeDePartida.Jugador(BRUNO, true)));
    }

    private static final String RESPUESTA = """
            {"partidaId":"%s",
             "acreditaciones":[
               {"uid":"%s","monto":2,"esGanador":true,"cofreId":"%s"}],
             "sancionadosExcluidos":["%s"]}
            """.formatted(PARTIDA, ANA, COFRE, BRUNO);

    @Test
    @DisplayName("manda el informe con la forma del contrato y traduce lo acreditado, cofre incluido")
    void informaYTraduce() {
        libro.expect(requestTo(BASE + "/partidas/resultado"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.partidaId").value(PARTIDA.toString()))
                .andExpect(jsonPath("$.tipoPartida").value("UNO_A_UNO"))
                .andExpect(jsonPath("$.ganadorUid").value(ANA.toString()))
                .andExpect(jsonPath("$.ganadoresUid[0]").value(ANA.toString()))
                .andExpect(jsonPath("$.participantes[0].uid").value(ANA.toString()))
                .andExpect(jsonPath("$.participantes[0].sancionado").value(false))
                .andExpect(jsonPath("$.participantes[1].uid").value(BRUNO.toString()))
                .andExpect(jsonPath("$.participantes[1].sancionado").value(true))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        Acreditacion acreditacion = cliente.acreditar(unoContraUnoGanadoPorAna());

        libro.verify();
        assertAll(
                () -> assertFalse(acreditacion.yaProcesada()),
                () -> assertEquals(List.of(new CreditoPorPartida(ANA, 2, true, COFRE)), acreditacion.creditos()),
                () -> assertEquals(List.of(BRUNO), acreditacion.sancionadosExcluidos()));
    }

    @Test
    @DisplayName("con varios ganadores (equipo) va la lista y ganadorUid queda nulo; sin ganadores, las dos vacias")
    void ganadoresEnLista() {
        libro.expect(requestTo(BASE + "/partidas/resultado"))
                .andExpect(jsonPath("$.tipoPartida").value("GRUPAL"))
                .andExpect(jsonPath("$.ganadorUid").doesNotExist())
                .andExpect(jsonPath("$.ganadoresUid.length()").value(2))
                .andRespond(withSuccess("{\"partidaId\":\"" + PARTIDA + "\",\"acreditaciones\":[]}",
                        MediaType.APPLICATION_JSON));
        libro.expect(requestTo(BASE + "/partidas/resultado"))
                .andExpect(jsonPath("$.ganadorUid").doesNotExist())
                .andExpect(jsonPath("$.ganadoresUid.length()").value(0))
                .andRespond(withSuccess("{\"partidaId\":\"" + PARTIDA + "\",\"acreditaciones\":[]}",
                        MediaType.APPLICATION_JSON));

        cliente.acreditar(new InformeDePartida(PARTIDA, TipoDePartida.GRUPAL, List.of(ANA, BRUNO),
                List.of(new InformeDePartida.Jugador(ANA, false), new InformeDePartida.Jugador(BRUNO, false))));
        cliente.acreditar(new InformeDePartida(PARTIDA, TipoDePartida.GRUPAL, List.of(),
                List.of(new InformeDePartida.Jugador(ANA, false))));

        libro.verify();
    }

    @Test
    @DisplayName("409 partida-ya-procesada es la idempotencia del libro: se devuelve «repetida», no un fallo")
    void yaProcesada() {
        libro.expect(requestTo(BASE + "/partidas/resultado"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"type\":\"https://nexusbattles.local/errores/partida-ya-procesada\"}"));

        Acreditacion acreditacion = cliente.acreditar(unoContraUnoGanadoPorAna());

        assertAll(
                () -> assertTrue(acreditacion.yaProcesada()),
                () -> assertTrue(acreditacion.creditos().isEmpty()),
                () -> assertEquals(EstadoDelCorta.CERRADO, corta.estado(), "una respuesta no es un fallo"));
    }

    @Test
    @DisplayName("cualquier otro 4xx es un desacuerdo con el libro: CreditosNoDisponibles, y el circuito sigue cerrado")
    void otroRechazo() {
        libro.expect(requestTo(BASE + "/partidas/resultado"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        CreditosNoDisponibles rechazo = assertThrows(CreditosNoDisponibles.class,
                () -> cliente.acreditar(unoContraUnoGanadoPorAna()));

        assertAll(
                () -> assertTrue(rechazo.getMessage().contains("400")),
                () -> assertEquals(EstadoDelCorta.CERRADO, corta.estado()));
    }

    @Test
    @DisplayName("una respuesta que no se entiende no se da por acreditada")
    void respuestaIncompleta() {
        libro.expect(requestTo(BASE + "/partidas/resultado"))
                .andRespond(withSuccess("{\"partidaId\":\"" + PARTIDA + "\"}", MediaType.APPLICATION_JSON));

        assertThrows(CreditosNoDisponibles.class, () -> cliente.acreditar(unoContraUnoGanadoPorAna()));
    }

    @Test
    @DisplayName("si el libro no contesta, sale como DependenciaDegradada y cuenta para abrir el circuito")
    void noContesta() {
        for (int i = 0; i < 3; i++) {
            libro.expect(requestTo(BASE + "/partidas/resultado"))
                    .andRespond(withException(new IOException("connection refused")));
        }

        for (int intento = 0; intento < 3; intento++) {
            assertThrows(DependenciaDegradada.class, () -> cliente.acreditar(unoContraUnoGanadoPorAna()));
        }

        assertEquals(EstadoDelCorta.ABIERTO, corta.estado());
    }

    @Test
    @DisplayName("la ruta y los campos que manda son los que publica el contrato creditos.yaml")
    void contrato() throws IOException {
        Path contrato = Path.of("..", "..", "..", "contracts", "openapi", "creditos.yaml");
        String yaml = Files.readString(contrato, StandardCharsets.UTF_8);
        assertAll(
                () -> assertTrue(yaml.contains("/partidas/resultado:"), "la ruta esta en el contrato"),
                () -> assertTrue(yaml.contains("ganadoresUid"), "la lista de ganadores esta en el contrato"),
                () -> assertTrue(yaml.contains("partida-ya-procesada"), "el 409 esta en el contrato"),
                () -> assertTrue(yaml.contains("sancionadosExcluidos"), "los excluidos estan en el contrato"));
    }
}
