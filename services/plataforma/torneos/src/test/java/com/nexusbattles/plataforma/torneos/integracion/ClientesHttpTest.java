package com.nexusbattles.plataforma.torneos.integracion;

import com.nexusbattles.plataforma.torneos.torneo.AvisosAlJugador;
import com.nexusbattles.plataforma.torneos.torneo.FalloDeIntegracion;
import com.nexusbattles.plataforma.torneos.torneo.LibroDeCreditos;
import com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Los clientes contra las formas exactas de sus contratos. */
@DisplayName("Torneos · clientes HTTP")
class ClientesHttpTest {

    private final RestClient.Builder constructor = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
    private final RestClient http = constructor.build();

    @Test
    @DisplayName("reservar manda la clave idempotente y el concepto del torneo; 422 es CREDITOS_INSUFICIENTES; caido es LIBRO_NO_DISPONIBLE")
    void reservar() {
        ClienteCreditos libro = new ClienteCreditos(http, "http://finanzas/api/v1/");
        UUID jugador = UUID.randomUUID();
        UUID reserva = UUID.randomUUID();
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "torneo-1-jugador-2-inscripcion"))
                .andExpect(content().json("{\"jugadorUid\":\"" + jugador + "\",\"monto\":30,\"concepto\":\"inscripcion-torneo\",\"referenciaId\":\"torneo-1\"}"))
                .andRespond(withSuccess("{\"reservaId\":\"" + reserva + "\",\"monto\":30,\"estado\":\"ACTIVA\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservar"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"Saldo insuficiente\"}"));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservar"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservar"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservar"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(libro.reservar(jugador, 30, "torneo-1-jugador-2-inscripcion", "torneo-1"))
                .isEqualTo(new LibroDeCreditos.Reserva(reserva, "ACTIVA"));
        assertThatThrownBy(() -> libro.reservar(jugador, 30, "k", "r"))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.CREDITOS_INSUFICIENTES));
        assertThatThrownBy(() -> libro.reservar(jugador, 30, "k", "r"))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE));
        assertThatThrownBy(() -> libro.reservar(jugador, 30, "k", "r"))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE));
        assertThatThrownBy(() -> libro.reservar(jugador, 30, "k", "r"))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE));
        servidor.verify();
    }

    @Test
    @DisplayName("consumir y liberar por la reserva; 409 (ya liberada) es definitivo, 503 es pasajero; liberar dice en que quedo")
    void consumirYLiberar() {
        ClienteCreditos libro = new ClienteCreditos(http, "http://finanzas/api/v1");
        UUID reserva = UUID.randomUUID();
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservas/" + reserva + "/consumir"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"transaccionId\":\"TX-1\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservas/" + reserva + "/consumir"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservas/" + reserva + "/liberar"))
                .andRespond(withSuccess("{\"reservaId\":\"" + reserva + "\",\"estado\":\"CONSUMIDA\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservas/" + reserva + "/liberar"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/reservas/" + reserva + "/liberar"))
                .andRespond(withSuccess());

        libro.consumir(reserva);
        assertThatThrownBy(() -> libro.consumir(reserva))
                .isInstanceOfSatisfying(FalloDeIntegracion.class, f -> assertThat(f.reintentable()).isFalse());
        assertThat(libro.liberar(reserva)).isEqualTo("CONSUMIDA");
        assertThatThrownBy(() -> libro.liberar(reserva))
                .isInstanceOfSatisfying(FalloDeIntegracion.class, f -> assertThat(f.reintentable()).isTrue());
        assertThat(libro.liberar(reserva)).isEqualTo("LIBERADA");
        servidor.verify();
    }

    @Test
    @DisplayName("acreditar lleva refId y concepto (creditos.yaml 1.4.0)")
    void acreditar() {
        ClienteCreditos libro = new ClienteCreditos(http, "http://finanzas/api/v1");
        UUID jugador = UUID.randomUUID();
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/acreditar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"uid\":\"" + jugador + "\",\"monto\":100,\"refId\":\"torneo-1-jugador-2-premio\",\"concepto\":\"premio-torneo\"}"))
                .andRespond(withSuccess("{\"estado\":\"APLICADO\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://finanzas/api/v1/creditos/acreditar"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        libro.acreditar(jugador, 100, "torneo-1-jugador-2-premio", "premio-torneo");
        assertThatThrownBy(() -> libro.acreditar(jugador, 100, "torneo-1-jugador-2-premio", "premio-torneo"))
                .isInstanceOfSatisfying(FalloDeIntegracion.class, f -> assertThat(f.reintentable()).isTrue());
        servidor.verify();
    }

    @Test
    @DisplayName("la lista negra decide sobre el texto en el contexto NOMBRE_EQUIPO; sin respuesta no se aprueba nada")
    void listaNegra() {
        ClienteListaNegra filtro = new ClienteListaNegra(http, "http://moderacion/api/v1/lista-negra/verificar");
        servidor.expect(requestTo("http://moderacion/api/v1/lista-negra/verificar"))
                .andExpect(content().json("{\"texto\":\"Los Valientes\",\"contexto\":\"NOMBRE_EQUIPO\"}"))
                .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"PERMITIR\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://moderacion/api/v1/lista-negra/verificar"))
                .andRespond(withSuccess("{\"aprobado\":false,\"accion\":\"RECHAZAR\",\"motivo\":\"termino prohibido\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://moderacion/api/v1/lista-negra/verificar"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        servidor.expect(requestTo("http://moderacion/api/v1/lista-negra/verificar"))
                .andRespond(withSuccess());
        assertThat(filtro.aprobado("Los Valientes")).isTrue();
        assertThat(filtro.aprobado("Los Groseros")).isFalse();
        assertThatThrownBy(() -> filtro.aprobado("x"))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.LISTA_NEGRA_NO_DISPONIBLE));
        assertThatThrownBy(() -> filtro.aprobado("y"))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.LISTA_NEGRA_NO_DISPONIBLE));
        servidor.verify();
    }

    @Test
    @DisplayName("la consulta de sancion activa es fail-closed")
    void sanciones() {
        ClienteSanciones consulta = new ClienteSanciones(http, "http://moderacion/api/v1");
        UUID jugador = UUID.randomUUID();
        servidor.expect(requestTo("http://moderacion/api/v1/sanciones/usuarios/" + jugador + "/activa"))
                .andRespond(withSuccess("{\"sancionActiva\":true,\"tipo\":\"SUSPENSION\",\"vigenteHasta\":\"2026-10-02T00:00:00Z\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://moderacion/api/v1/sanciones/usuarios/" + jugador + "/activa"))
                .andRespond(withSuccess("{\"sancionActiva\":false}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://moderacion/api/v1/sanciones/usuarios/" + jugador + "/activa"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        servidor.expect(requestTo("http://moderacion/api/v1/sanciones/usuarios/" + jugador + "/activa"))
                .andRespond(withSuccess());
        assertThat(consulta.sancionado(jugador)).isTrue();
        assertThat(consulta.sancionado(jugador)).isFalse();
        assertThatThrownBy(() -> consulta.sancionado(jugador))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.SANCIONES_NO_DISPONIBLES));
        assertThatThrownBy(() -> consulta.sancionado(jugador))
                .isInstanceOfSatisfying(TorneoRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(TorneoRechazado.Motivo.SANCIONES_NO_DISPONIBLES));
        servidor.verify();
    }

    @Test
    @DisplayName("inventario: la epica va por /entregas con origen PREMIO_TORNEO e Idempotency-Key; 404 se reintenta, 422 no")
    void inventario() {
        ClienteInventario inventario = new ClienteInventario(http, "http://inventario/");
        UUID jugador = UUID.randomUUID();
        servidor.expect(requestTo("http://inventario/api/v1/inventario/entregas"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "torneo-1-jugador-2-epica"))
                .andExpect(content().json("{\"uid\":\"" + jugador + "\",\"origen\":\"PREMIO_TORNEO\",\"referencia\":\"torneo-1\","
                        + "\"productos\":[{\"productoId\":\"epica-1\",\"cantidad\":1}]}"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"id\":\"e1\"}"));
        servidor.expect(requestTo("http://inventario/api/v1/inventario/entregas"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo("http://inventario/api/v1/inventario/entregas"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        inventario.entregarEpica(jugador, "epica-1", "torneo-1", "torneo-1-jugador-2-epica");
        assertThatThrownBy(() -> inventario.entregarEpica(jugador, "epica-1", "torneo-1", "k"))
                .isInstanceOfSatisfying(FalloDeIntegracion.class, f -> assertThat(f.reintentable()).isTrue());
        assertThatThrownBy(() -> inventario.entregarEpica(jugador, "epica-1", "torneo-1", "k"))
                .isInstanceOfSatisfying(FalloDeIntegracion.class, f -> assertThat(f.reintentable()).isFalse());
        servidor.verify();
    }

    @Test
    @DisplayName("avisos: la bandeja recibe el id estable y el tipo TORNEO; un 409 es un aviso que ya estaba")
    void notificaciones() {
        ClienteAvisos avisos = new ClienteAvisos(http, "http://notificaciones/api/v1/", "", "");
        UUID jugador = UUID.randomUUID();
        OffsetDateTime cuando = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        servidor.expect(requestTo("http://notificaciones/api/v1/internal/notifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"usuarioId\":\"" + jugador + "\",\"id\":\"clave-1\",\"tipo\":\"TORNEO\","
                        + "\"titulo\":\"Titulo\",\"cuerpo\":\"Cuerpo\"}"))
                .andRespond(withStatus(HttpStatus.CREATED));
        servidor.expect(requestTo("http://notificaciones/api/v1/internal/notifications"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        servidor.expect(requestTo("http://notificaciones/api/v1/internal/notifications"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        avisos.notificar(jugador, "clave-1", "Titulo", "Cuerpo", cuando);
        avisos.notificar(jugador, "clave-1", "Titulo", "Cuerpo", cuando);
        assertThatThrownBy(() -> avisos.notificar(jugador, "clave-1", "Titulo", "Cuerpo", cuando))
                .isInstanceOfSatisfying(FalloDeIntegracion.class, f -> assertThat(f.reintentable()).isTrue());
        assertThat(avisos.correoConfigurado()).isFalse();
        servidor.verify();
    }

    @Test
    @DisplayName("correo: el contacto sale de ms-identidad por uid y el envio lleva Idempotency-Key; sin cuenta no se envia")
    void correo() {
        ClienteAvisos avisos = new ClienteAvisos(http, "http://notificaciones/api/v1", "http://identidad", "http://correo/api/v1/");
        UUID jugador = UUID.randomUUID();
        UUID torneo = UUID.randomUUID();
        servidor.expect(requestTo("http://identidad/api/v1/internal/usuarios/" + jugador + "/contacto"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"uid\":\"" + jugador + "\",\"email\":\"lyra@nexus.test\",\"apodo\":\"lyra\",\"estado\":\"ACTIVO\"}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://correo/api/v1/correos/torneo"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "clave-correo"))
                .andExpect(content().json("{\"email\":\"lyra@nexus.test\",\"apodo\":\"lyra\",\"asunto\":\"Asunto\","
                        + "\"mensaje\":\"Mensaje\",\"torneoId\":\"" + torneo + "\"}"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        servidor.expect(requestTo("http://identidad/api/v1/internal/usuarios/" + jugador + "/contacto"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo("http://identidad/api/v1/internal/usuarios/" + jugador + "/contacto"))
                .andRespond(withSuccess("{\"uid\":\"" + jugador + "\",\"email\":\"lyra@nexus.test\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://correo/api/v1/correos/torneo"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo("http://identidad/api/v1/internal/usuarios/" + jugador + "/contacto"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        servidor.expect(requestTo("http://identidad/api/v1/internal/usuarios/" + jugador + "/contacto"))
                .andRespond(withSuccess("{\"uid\":\"" + jugador + "\"}", MediaType.APPLICATION_JSON));

        assertThat(avisos.correoConfigurado()).isTrue();
        assertThat(avisos.enviarCorreo(jugador, torneo, "Asunto", "Mensaje", "clave-correo"))
                .isEqualTo(AvisosAlJugador.Correo.ENVIADO);
        assertThat(avisos.enviarCorreo(jugador, torneo, "Asunto", "Mensaje", "clave-correo"))
                .isEqualTo(AvisosAlJugador.Correo.SIN_CONTACTO);
        // Un 404 de correo (una version anterior a la 1.5.0 todavia desplegada, o
        // una base mal configurada) no es un rechazo del correo: se reintenta.
        assertThatThrownBy(() -> avisos.enviarCorreo(jugador, torneo, "Asunto", "Mensaje", "clave-correo"))
                .isInstanceOfSatisfying(FalloDeIntegracion.class, f -> assertThat(f.reintentable()).isTrue());
        assertThatThrownBy(() -> avisos.enviarCorreo(jugador, torneo, "Asunto", "Mensaje", "clave-correo"))
                .isInstanceOfSatisfying(FalloDeIntegracion.class, f -> assertThat(f.reintentable()).isTrue());
        assertThat(avisos.enviarCorreo(jugador, torneo, "Asunto", "Mensaje", "clave-correo"))
                .isEqualTo(AvisosAlJugador.Correo.SIN_CONTACTO);
        servidor.verify();
    }

    @Test
    @DisplayName("clasificador: 5xx, 401/403, 408, 425, 429 y sin respuesta son pasajeros; el resto de 4xx, definitivo")
    void clasificador() {
        assertThat(clasificado(HttpStatus.SERVICE_UNAVAILABLE, true).reintentable()).isTrue();
        assertThat(clasificado(HttpStatus.UNAUTHORIZED, true).reintentable()).isTrue();
        assertThat(clasificado(HttpStatus.FORBIDDEN, true).reintentable()).isTrue();
        assertThat(clasificado(HttpStatus.REQUEST_TIMEOUT, true).reintentable()).isTrue();
        assertThat(clasificado(HttpStatus.TOO_EARLY, true).reintentable()).isTrue();
        assertThat(clasificado(HttpStatus.TOO_MANY_REQUESTS, true).reintentable()).isTrue();
        assertThat(clasificado(HttpStatus.NOT_FOUND, true).reintentable()).isFalse();
        assertThat(clasificado(HttpStatus.NOT_FOUND, false).reintentable()).isTrue();
        assertThat(clasificado(HttpStatus.BAD_REQUEST, false).reintentable()).isFalse();
        assertThat(ClasificadorDeFallos.clasificar("x", new ResourceAccessException("timeout"), true).reintentable()).isTrue();
    }

    private FalloDeIntegracion clasificado(HttpStatus estado, boolean noEncontradoEsDefinitivo) {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer s = MockRestServiceServer.bindTo(b).build();
        s.expect(requestTo("http://x/y")).andRespond(withStatus(estado));
        try {
            b.build().get().uri("http://x/y").retrieve().toBodilessEntity();
            throw new AssertionError("se esperaba un fallo");
        } catch (org.springframework.web.client.RestClientException fallo) {
            return ClasificadorDeFallos.clasificar("x", fallo, noEncontradoEsDefinitivo);
        }
    }
}
