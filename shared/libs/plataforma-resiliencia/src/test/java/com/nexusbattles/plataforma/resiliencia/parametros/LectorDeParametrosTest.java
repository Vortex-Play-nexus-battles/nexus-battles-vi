package com.nexusbattles.plataforma.resiliencia.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Lo que esta clase demuestra es una sola cosa, dicha de siete maneras: una
 * caida de admin-parametros NO puede tumbar al servicio que lee un parametro.
 * Responde el catalogo, no responde, responde basura o no existe siquiera: el
 * servicio sigue, con el respaldo de su variable de entorno.
 */
@DisplayName("Lector de parametros: el catalogo puede caerse, el servicio no")
class LectorDeParametrosTest {

    private static final String TAMANO = "chat.historial.tamano";
    private static final String POLITICA = "salas.apuestas.si-gana-la-maquina";
    private static final String URL_TAMANO = "http://admin/api/v1/parametros/chat.historial.tamano/valor";
    private static final String URL_POLITICA =
            "http://admin/api/v1/parametros/salas.apuestas.si-gana-la-maquina/valor";

    // El orden importa: el servidor simulado instala su fabrica de peticiones
    // en el constructor, y solo los clientes construidos DESPUES la llevan.
    private final RestClient.Builder constructor = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-09-23T10:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora.get();
        }
    };
    // Con la barra final de sobra a proposito: el lector la normaliza.
    private final LectorDeParametros lector = LectorDeParametros.sobre(constructor.build(),
            "http://admin/api/v1/", reloj, Duration.ofSeconds(30));

    private static String respuesta(String clave, String valor) {
        return "{\"clave\":\"" + clave + "\",\"valor\":" + (valor == null ? "null" : "\"" + valor + "\"")
                + ",\"tipo\":\"ENTERO\",\"version\":3}";
    }

    @Test
    @DisplayName("lee el valor vigente y no vuelve a preguntar mientras la cache valga")
    void leeYCachea() {
        servidor.expect(requestTo(URL_TAMANO))
                .andRespond(withSuccess(respuesta(TAMANO, "120"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL_TAMANO))
                .andRespond(withSuccess(respuesta(TAMANO, "200"), MediaType.APPLICATION_JSON));

        assertThat(lector.entero(TAMANO, 50)).isEqualTo(120);
        assertThat(lector.entero(TAMANO, 50)).as("cacheado: ni una peticion mas").isEqualTo(120);

        ahora.set(ahora.get().plusSeconds(31));
        assertThat(lector.entero(TAMANO, 50)).as("cache vencida: vuelve a leer").isEqualTo(200);

        servidor.verify();
    }

    @Test
    @DisplayName("el catalogo caido no se propaga: devuelve el respaldo y no lanza")
    void catalogoCaido() {
        servidor.expect(requestTo(URL_TAMANO)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatCode(() -> assertThat(lector.entero(TAMANO, 50)).isEqualTo(50))
                .as("una caida del catalogo nunca sube")
                .doesNotThrowAnyException();

        // El fallo tambien se cachea: si no, cada peticion del jugador pagaria
        // un tiempo de espera completo contra un servicio que no esta. Una sola
        // peticion esperada y verify() lo comprueba.
        assertThat(lector.entero(TAMANO, 50)).isEqualTo(50);
        servidor.verify();
    }

    @Test
    @DisplayName("el catalogo que responde cualquier cosa tampoco se propaga")
    void catalogoQueRespondeBasura() {
        servidor.expect(requestTo(URL_TAMANO))
                .andRespond(withSuccess("<html>no soy json</html>", MediaType.TEXT_HTML));

        assertThatCode(() -> assertThat(lector.entero(TAMANO, 50)).isEqualTo(50)).doesNotThrowAnyException();
        servidor.verify();
    }

    @Test
    @DisplayName("parametro sin decidir por el Product Owner (valor nulo): respaldo")
    void sinValorEnElCatalogo() {
        servidor.expect(requestTo(URL_TAMANO))
                .andRespond(withSuccess(respuesta(TAMANO, null), MediaType.APPLICATION_JSON));

        assertThat(lector.entero(TAMANO, 50)).isEqualTo(50);
        assertThat(lector.texto(TAMANO)).isEmpty();
        servidor.verify();
    }

    @Test
    @DisplayName("un valor con el tipo equivocado degrada al respaldo, no a una excepcion")
    void valorConTipoEquivocado() {
        servidor.expect(requestTo(URL_TAMANO))
                .andRespond(withSuccess(respuesta(TAMANO, "muchos"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL_POLITICA))
                .andRespond(withSuccess(respuesta(POLITICA, "REGALAR"), MediaType.APPLICATION_JSON));

        assertThat(lector.entero(TAMANO, 50)).as("no es un entero").isEqualTo(50);
        assertThat(lector.opcion(POLITICA, Politica.class, Politica.LIBERAR))
                .as("no es una opcion del enumerado")
                .isEqualTo(Politica.LIBERAR);
        servidor.verify();
    }

    @Test
    @DisplayName("la opcion se acepta venga como venga de mayusculas")
    void opcionSinDistinguirCaja() {
        servidor.expect(requestTo(URL_POLITICA))
                .andRespond(withSuccess(respuesta(POLITICA, "consumir"), MediaType.APPLICATION_JSON));

        assertThat(lector.opcion(POLITICA, Politica.class, Politica.LIBERAR)).isEqualTo(Politica.CONSUMIR);
        servidor.verify();
    }

    @Nested
    @DisplayName("sin catalogo configurado (PARAMETROS_URL vacia)")
    class SinCatalogo {

        @Test
        @DisplayName("todo sale del respaldo y no se hace ni una peticion")
        void todoDelRespaldo() {
            LectorDeParametros sinCatalogo = LectorDeParametros.soloRespaldo();

            assertThat(sinCatalogo.tieneCatalogo()).isFalse();
            assertThat(sinCatalogo.entero(TAMANO, 50)).isEqualTo(50);
            assertThat(sinCatalogo.texto(POLITICA, "LIBERAR")).isEqualTo("LIBERAR");
            assertThat(sinCatalogo.opcion(POLITICA, Politica.class, Politica.LIBERAR)).isEqualTo(Politica.LIBERAR);
            assertThat(sinCatalogo.texto(TAMANO)).isEmpty();

            // Ni una peticion: el servidor simulado no espera ninguna y
            // verify() fallaria si se hubiera hecho alguna.
            servidor.verify();
        }

        @Test
        @DisplayName("desde(...) decide por la URL, para que el if no se repita en cada servicio")
        void desdeDecidePorLaUrl() {
            assertThat(LectorDeParametros.desde(constructor.build(), "", reloj, Duration.ofSeconds(30))
                    .tieneCatalogo()).isFalse();
            assertThat(LectorDeParametros.desde(constructor.build(), null, reloj, Duration.ofSeconds(30))
                    .tieneCatalogo()).isFalse();
            assertThat(LectorDeParametros.desde(constructor.build(), "http://admin/api/v1", reloj,
                    Duration.ofSeconds(30)).tieneCatalogo()).isTrue();
        }

        @Test
        @DisplayName("sobre(...) con la URL vacia es un error de cableado y se dice al construir")
        void sobreExigeUrl() {
            RestClient http = constructor.build();
            Duration vigencia = Duration.ofSeconds(30);
            assertThatThrownBy(() -> LectorDeParametros.sobre(http, " ", reloj, vigencia))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("soloRespaldo");
        }
    }

    /** Un enumerado cualquiera, para probar {@code opcion(...)} sin depender de un servicio. */
    private enum Politica {
        LIBERAR,
        CONSUMIR
    }
}
