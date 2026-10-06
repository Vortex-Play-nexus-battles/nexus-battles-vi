package com.nexusbattles.plataforma.comentarios.publicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nexusbattles.plataforma.comentarios.DeteccionAutomatica;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro;

/**
 * Pruebas del cliente de la lista negra contra el contrato de HU-ADM-002
 * (moderacion-lista-negra 2.0.0 desde B3: {@code contexto} y {@code accion};
 * 2.1.0 desde HU-COM-007: {@code reglas}).
 *
 * Lo que mas importa verificar aqui es el respaldo. Para los apodos se decidio
 * dejar pasar cuando el servicio no responde, pero la postcondicion de RF-COM-007 es que el
 * contenido inapropiado no alcance la publicacion sin revision, asi que
 * publicar sin verificar rompe el requisito. Lo unico que no lo rompe es retener el comentario
 * para que lo revise un moderador, y eso es lo que se prueba en los casos de falla.
 *
 * <p>Desde HU-COM-007 CA-01 el veredicto trae ademas la deteccion: que reglas
 * coincidieron, la categoria y el motivo, para que el moderador sepa por que
 * se retuvo. Nunca los terminos coincidentes. Si la lista negra falla, el
 * motivo es un texto fijo: el detalle tecnico de la falla, que puede llevar la
 * URL interna, va solo a la bitacora.
 */
@ExtendWith(OutputCaptureExtension.class)
class ClienteListaNegraTest {

    private static final String URL = "http://localhost:8086/api/v1/lista-negra/verificar";
    private static final Instant AHORA = Instant.parse("2026-10-06T15:00:00Z");

    /** Lo que ve el moderador cuando la lista negra no responde, sea cual sea la falla. */
    private static final String SIN_SERVICIO =
            "La lista negra no respondio; el comentario queda retenido para revision";

    private MockRestServiceServer servidor;
    private ClienteListaNegra cliente;

    @BeforeEach
    void montarServidorSimulado() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteListaNegra(constructor.build(), URL, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("la accion PERMITIR publica, sin deteccion")
    void permitirPublica() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"PERMITIR\"}", MediaType.APPLICATION_JSON));

        FiltroDeContenido.VeredictoDelFiltro veredicto = cliente.verificar("Muy buena espada");

        assertEquals(ResultadoDelFiltro.LIMPIO, veredicto.resultado());
        assertNull(veredicto.deteccion(), "lo limpio no tiene nada que explicar");
        servidor.verify();
    }

    @Test
    @DisplayName("la accion REVISION (la de COMENTARIO por politica) retiene en revision")
    void revisionRetiene() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"aprobado\":false,\"accion\":\"REVISION\",\"motivo\":\"termino prohibido\","
                                + "\"categoria\":\"OFENSIVO\",\"coincidencias\":[\"x\"]}",
                        MediaType.APPLICATION_JSON));

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("texto con groseria").resultado());
        servidor.verify();
    }

    @Test
    @DisplayName("HU-COM-007 CA-01: lo senalado conserva reglas, categoria y motivo, y nunca las coincidencias")
    void senaladoConservaLaDeteccion() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"aprobado\":false,\"accion\":\"REVISION\","
                                + "\"motivo\":\"El texto contiene un termino no permitido\","
                                + "\"categoria\":\"OFENSIVO\",\"coincidencias\":[\"groseria\"],\"reglas\":[7,9]}",
                        MediaType.APPLICATION_JSON));

        FiltroDeContenido.VeredictoDelFiltro veredicto = cliente.verificar("texto con groseria");

        assertEquals(ResultadoDelFiltro.SENALADO, veredicto.resultado());
        // La deteccion no tiene donde guardar «groseria»: las reglas van por su id.
        assertEquals(new DeteccionAutomatica(AHORA, List.of(7L, 9L), "OFENSIVO",
                "El texto contiene un termino no permitido", false), veredicto.deteccion());
    }

    @Test
    @DisplayName("el veredicto no se contradice: lo limpio no trae deteccion y lo senalado siempre la trae")
    void elVeredictoNoSeContradice() {
        DeteccionAutomatica deteccion = new DeteccionAutomatica(AHORA, List.of(7L), null, null, false);

        assertThrows(IllegalArgumentException.class,
                () -> new FiltroDeContenido.VeredictoDelFiltro(ResultadoDelFiltro.LIMPIO, deteccion));
        assertThrows(IllegalArgumentException.class,
                () -> new FiltroDeContenido.VeredictoDelFiltro(ResultadoDelFiltro.SENALADO, null));
    }

    @Test
    @DisplayName("una respuesta rara no impide retener: motivo y categoria se recortan a su columna y una regla nula se descarta")
    void respuestaRaraSeAcomoda() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"aprobado\":false,\"accion\":\"REVISION\","
                                + "\"motivo\":\"" + "m".repeat(DeteccionAutomatica.MOTIVO_MAXIMO + 100) + "\","
                                + "\"categoria\":\"" + "C".repeat(DeteccionAutomatica.CATEGORIA_MAXIMA + 8) + "\","
                                + "\"reglas\":[7,null]}",
                        MediaType.APPLICATION_JSON));

        DeteccionAutomatica deteccion = cliente.verificar("texto").deteccion();

        assertEquals(DeteccionAutomatica.MOTIVO_MAXIMO, deteccion.motivo().length());
        assertEquals(DeteccionAutomatica.CATEGORIA_MAXIMA, deteccion.categoria().length());
        assertEquals(List.of(7L), deteccion.reglas());
    }

    @ParameterizedTest(name = "la accion {0} tambien retiene: nada inapropiado sale publicado")
    @ValueSource(strings = {"RECHAZAR", "BLOQUEAR", "UNA_NUEVA"})
    void otrasAccionesRetienen(String accion) {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":false,\"accion\":\"" + accion + "\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("texto").resultado());
    }

    @Test
    @DisplayName("la accion manda aunque aprobado diga otra cosa")
    void laAccionManda() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"REVISION\"}", MediaType.APPLICATION_JSON));

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("texto").resultado());
    }

    @Test
    @DisplayName("un servidor anterior a la 2.0.0, sin accion, se entiende por aprobado; sin reglas, la deteccion va sin ellas")
    void servidorSinAccion() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":true}", MediaType.APPLICATION_JSON));
        assertEquals(ResultadoDelFiltro.LIMPIO, cliente.verificar("Muy buena espada").resultado());

        servidor.reset();
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"aprobado\":false,\"motivo\":\"termino prohibido\"}",
                        MediaType.APPLICATION_JSON));
        FiltroDeContenido.VeredictoDelFiltro veredicto = cliente.verificar("texto con groseria");

        assertEquals(ResultadoDelFiltro.SENALADO, veredicto.resultado());
        assertEquals(new DeteccionAutomatica(AHORA, List.of(), null, "termino prohibido", false),
                veredicto.deteccion());
    }

    @Test
    @DisplayName("si el servicio de lista negra falla, el comentario se retiene en vez de publicarse")
    void siElServicioFallaSeRetiene() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertEquals(ResultadoDelFiltro.SENALADO, cliente.verificar("da igual el texto").resultado());
        servidor.verify();
    }

    @Test
    @DisplayName("HU-COM-007 CA-01: si la lista negra no responde, la deteccion lo dice con un motivo fijo, "
            + "sin reglas ni categoria; el detalle tecnico va solo a la bitacora")
    void fallaQuedaComoServicioNoDisponible(CapturedOutput bitacora) {
        // Sin conexion, el RestClient lanza una excepcion cuyo mensaje lleva la URL interna.
        servidor.expect(requestTo(URL)).andRespond(peticion -> {
            throw new IOException("Connection refused");
        });

        DeteccionAutomatica deteccion = cliente.verificar("da igual el texto").deteccion();

        assertEquals(new DeteccionAutomatica(AHORA, List.of(), null, SIN_SERVICIO, true), deteccion);
        assertTrue(bitacora.getAll().contains("Connection refused"), "el detalle tecnico queda en la bitacora");
    }

    @Test
    @DisplayName("una respuesta vacia tambien retiene, no se asume que estaba limpio, y queda dicho por que")
    void respuestaVaciaTambienRetiene() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        FiltroDeContenido.VeredictoDelFiltro veredicto = cliente.verificar("da igual el texto");

        assertEquals(ResultadoDelFiltro.SENALADO, veredicto.resultado());
        assertEquals(new DeteccionAutomatica(AHORA, List.of(), null, SIN_SERVICIO, true), veredicto.deteccion());
        servidor.verify();
    }

    @Test
    @DisplayName("el texto viaja con contexto COMENTARIO, con los nombres que fija el contrato 2.0.0")
    void elTextoViajaConSuContexto() {
        servidor.expect(requestTo(URL))
                .andExpect(content().json("{\"texto\":\"Muy buena espada\",\"contexto\":\"COMENTARIO\"}",
                        JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"aprobado\":true,\"accion\":\"PERMITIR\"}", MediaType.APPLICATION_JSON));

        cliente.verificar("Muy buena espada");
        servidor.verify();
    }
}
