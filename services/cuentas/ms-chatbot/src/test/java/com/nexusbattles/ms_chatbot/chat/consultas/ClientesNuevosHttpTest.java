package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiPublicacionDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MisionActivaDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaDeSubastasDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ParticipacionDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaMovimientosDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoDetalleDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// B11: torneos se consulta como dato publico (sin credencial); los
// movimientos y (1.3.0) las misiones, con el token del propio jugador (nunca
// uno de servicio).
class ClientesNuevosHttpTest {

    @Test
    void torneosSinNingunaCredencial() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://torneos/api/v1");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        UUID id = UUID.randomUUID();
        servidor.expect(requestTo("http://torneos/api/v1/torneos"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess("[{\"id\":\"" + id + "\",\"nombre\":\"Copa\",\"estado\":\"EN_CURSO\","
                + "\"costoInscripcion\":10,\"equiposInscritos\":8,\"cupos\":8,\"extra\":1}]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://torneos/api/v1/torneos/" + id))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess("{\"id\":\"" + id + "\",\"nombre\":\"Copa\",\"estado\":\"EN_CURSO\",\"equipos\":[],"
                + "\"encuentros\":[{\"numero\":1,\"estado\":\"LISTO\"}]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://torneos/api/v1/torneos"))
            .andRespond(withSuccess());

        TorneosClientHttp cliente = new TorneosClientHttp(constructor.build());
        assertThat(cliente.listar()).singleElement().satisfies(t -> assertThat(t.nombre()).isEqualTo("Copa"));
        TorneoDetalleDto detalle = cliente.obtener(id);
        assertThat(detalle.encuentros()).singleElement().satisfies(e -> assertThat(e.estado()).isEqualTo("LISTO"));
        assertThat(cliente.listar()).isEmpty();
        servidor.verify();
    }

    @Test
    void movimientosConElTokenDelJugador() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://finanzas/api/v1");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/uid-1/movimientos?page=0&size=5"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token-del-jugador"))
            .andRespond(withSuccess("{\"content\":[{\"monto\":2,\"concepto\":\"recompensa-victoria\",\"signo\":\"SUMA\"}],"
                + "\"totalElements\":1}", MediaType.APPLICATION_JSON));

        PaginaMovimientosDto pagina = new FinanzasClientHttp(constructor.build()).movimientos("token-del-jugador", "uid-1", 5);
        assertThat(pagina.totalElements()).isEqualTo(1);
        assertThat(pagina.content()).singleElement().satisfies(m -> assertThat(m.signo()).isEqualTo("SUMA"));
        servidor.verify();
    }

    @Test
    void misionesEnCursoConElTokenDelJugador() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://misiones");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo("http://misiones/api/v1/misiones/en-curso"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token-del-jugador"))
            .andRespond(withSuccess("[{\"ejecucionId\":\"" + UUID.randomUUID() + "\",\"misionId\":\"templo\","
                + "\"nombre\":\"El Templo Olvidado\",\"categoria\":\"EXPLORACION\","
                + "\"heroe\":{\"id\":\"h-1\",\"nombre\":\"Guerrero Tanque\",\"nivel\":3},"
                + "\"iniciadaEn\":\"2026-09-28T19:00:00Z\",\"terminaEn\":\"2026-09-28T20:30:00Z\","
                + "\"progreso\":0.4,\"estado\":\"EN_PROGRESO\"}]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://misiones/api/v1/misiones/en-curso"))
            .andRespond(withSuccess());

        MisionesClientHttp cliente = new MisionesClientHttp(constructor.build());
        List<MisionActivaDto> misiones = cliente.enCurso("token-del-jugador");
        assertThat(misiones).singleElement().satisfies(m -> {
            assertThat(m.nombre()).isEqualTo("El Templo Olvidado");
            assertThat(m.heroe().nivel()).isEqualTo(3);
            assertThat(m.terminaEn()).isEqualTo(Instant.parse("2026-09-28T20:30:00Z"));
            assertThat(m.progreso()).isEqualTo(0.4);
        });
        // Sin cuerpo: lista vacia, nunca null.
        assertThat(cliente.enCurso("token-del-jugador")).isEmpty();
        servidor.verify();
    }

    // 1.16.0 (7.4.4): las suyas con su token; el listado de la subasta sin
    // ninguna credencial, como lo ve cualquiera.
    @Test
    void subastasLasSuyasConSuTokenYLasAbiertasSinCredencial() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("http://subastas/api/v1");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo("http://subastas/api/v1/mis-pujas"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token-del-jugador"))
            .andRespond(withSuccess("[{\"subastaId\":\"" + UUID.randomUUID() + "\",\"nombreProducto\":\"Hacha\","
                + "\"estadoSubasta\":\"ACTIVA\",\"estado\":\"GANANDO\",\"tuMejorPuja\":\"120.00\","
                + "\"ofertaVigente\":\"120.00\",\"cantidadPujas\":2,\"fechaFin\":\"2026-10-15T23:00:00Z\","
                + "\"ultimaPujaEn\":null}]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://subastas/api/v1/mis-subastas/publicadas?estado=ACTIVA"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token-del-jugador"))
            .andRespond(withSuccess("[{\"subastaId\":\"" + UUID.randomUUID() + "\",\"nombreProducto\":\"Escudo\","
                + "\"estado\":\"ACTIVA\",\"ofertaVigente\":\"80.00\",\"cantidadPujas\":0,"
                + "\"fechaFin\":\"2026-10-16T01:00:00Z\",\"vistas\":3,\"cancelable\":true}]",
                MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://subastas/api/v1/subastas?size=5&ordenarPor=TIEMPO_RESTANTE"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess("{\"contenido\":[{\"id\":\"" + UUID.randomUUID() + "\",\"nombreProducto\":\"Orbe\","
                + "\"tipoProducto\":\"ITEM\",\"precioInicial\":50,\"ofertaVigente\":75.5,"
                + "\"precioCompraInmediata\":null,\"cantidadPujas\":1,\"fechaFin\":\"2026-10-15T20:00:00Z\","
                + "\"esMaestroDeJuego\":false,\"vendedorId\":null,\"esPropia\":false}],\"pagina\":0,"
                + "\"tamanoPagina\":5,\"totalElementos\":7,\"totalPaginas\":2}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://subastas/api/v1/subastas?size=5&ordenarPor=TIEMPO_RESTANTE&q=espada"))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess());
        servidor.expect(requestTo("http://subastas/api/v1/mis-pujas"))
            .andRespond(withSuccess());
        servidor.expect(requestTo("http://subastas/api/v1/mis-subastas/publicadas?estado=ACTIVA"))
            .andRespond(withSuccess());

        SubastasClientHttp cliente = new SubastasClientHttp(constructor.build());
        List<ParticipacionDto> pujas = cliente.misParticipaciones("token-del-jugador");
        assertThat(pujas).singleElement().satisfies(p -> {
            assertThat(p.activa()).isTrue();
            assertThat(p.tuMejorPuja()).isEqualTo("120.00");
            assertThat(p.fechaFin()).isEqualTo(Instant.parse("2026-10-15T23:00:00Z"));
        });
        List<MiPublicacionDto> publicadas = cliente.misPublicacionesActivas("token-del-jugador");
        assertThat(publicadas).singleElement().satisfies(p -> assertThat(p.cantidadPujas()).isZero());
        PaginaDeSubastasDto abiertas = cliente.activas(null, 5);
        assertThat(abiertas.totalElementos()).isEqualTo(7);
        assertThat(abiertas.subastas()).singleElement().satisfies(s -> {
            assertThat(s.ofertaVigente()).isEqualByComparingTo(new BigDecimal("75.5"));
            assertThat(s.precioCompraInmediata()).isNull();
        });
        // Sin cuerpo: nada, nunca null.
        assertThat(cliente.activas("espada", 5).subastas()).isEmpty();
        assertThat(cliente.misParticipaciones("t")).isEmpty();
        assertThat(cliente.misPublicacionesActivas("t")).isEmpty();
        servidor.verify();
    }
}
