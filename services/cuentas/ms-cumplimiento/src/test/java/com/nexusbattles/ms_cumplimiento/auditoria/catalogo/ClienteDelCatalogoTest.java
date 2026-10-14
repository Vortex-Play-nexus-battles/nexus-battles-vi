package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Bitácora: cliente del catálogo y credencial de servicio (SA-18)")
class ClienteDelCatalogoTest {

    private static final String EMISOR = "http://srv-ms-identidad:8089/api/v1/auth/token";
    private static final String BASE = "http://34.193.90.11:8103/";
    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    private MockRestServiceServer servidor;
    private RestClient http;

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        http = constructor.build();
    }

    private CredencialDeServicio credencial(Clock reloj) {
        return new CredencialDeServicio(http, EMISOR, "ms-cumplimiento", "secreto-de-prueba-123", reloj);
    }

    private void esperarToken(String token, long expira) {
        servidor.expect(requestTo(EMISOR))
            .andExpect(method(HttpMethod.POST))
            // Basic base64("ms-cumplimiento:secreto-de-prueba-123")
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic bXMtY3VtcGxpbWllbnRvOnNlY3JldG8tZGUtcHJ1ZWJhLTEyMw=="))
            .andExpect(content().string("grant_type=client_credentials"))
            .andRespond(withSuccess("{\"access_token\":\"" + token + "\",\"expires_in\":" + expira + "}",
                MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("pide el lote con desde y límite, con el token de la bitácora, y lo entiende")
    void lote() {
        esperarToken("token-1", 300);
        // La plantilla de URI codifica los dos puntos de la hora; productos los decodifica.
        servidor.expect(requestTo("http://34.193.90.11:8103/api/v1/productos/auditoria/operaciones"
                + "?desde=2026-10-14T14%3A58%3A00Z&limite=100"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
            .andRespond(withSuccess("""
                {"hasta":"2026-10-14T15:00:00.250Z","completo":false,"operaciones":[
                 {"id":"op-1","ocurridaEn":"2026-10-14T15:00:00.250Z","autor":"uid-admin",
                  "tipo":"RETIRO_BANNER","objeto":"BANNER","objetoId":"b-1","nombre":"Temporada",
                  "motivo":"Evento cancelado","descripcion":"Retiro de un banner","ipOrigen":"181.52.10.4",
                  "campoNuevo":"se ignora"}]}
                """, MediaType.APPLICATION_JSON));

        LoteDelCatalogo lote = new ClienteDelCatalogo(http, BASE, credencial(Clock.fixed(AHORA, ZoneOffset.UTC)))
            .consultar(Instant.parse("2026-10-14T14:58:00Z"), 100);

        assertThat(lote.estaCompleto()).isFalse();
        assertThat(lote.hasta()).isEqualTo(Instant.parse("2026-10-14T15:00:00.250Z"));
        assertThat(lote.operaciones()).singleElement().satisfies(operacion -> {
            assertThat(operacion.tipo()).isEqualTo("RETIRO_BANNER");
            assertThat(operacion.nombre()).isEqualTo("Temporada");
            assertThat(operacion.ipOrigen()).isEqualTo("181.52.10.4");
        });
        servidor.verify();
    }

    @Test
    @DisplayName("sin desde no lo envía; sin «completo» en la respuesta se da por completo")
    void sinDesde() {
        esperarToken("token-1", 300);
        servidor.expect(requestTo("http://34.193.90.11:8103/api/v1/productos/auditoria/operaciones?limite=50"))
            .andRespond(withSuccess("{\"hasta\":\"2026-10-14T15:00:00Z\",\"operaciones\":[]}",
                MediaType.APPLICATION_JSON));

        LoteDelCatalogo lote = new ClienteDelCatalogo(http, BASE, credencial(Clock.fixed(AHORA, ZoneOffset.UTC)))
            .consultar(null, 50);

        assertThat(lote.estaCompleto()).isTrue();
        assertThat(lote.operaciones()).isEmpty();
    }

    @Test
    @DisplayName("un 403 de productos o un lote sin «hasta» son «catálogo no disponible», no un lote vacío")
    void rechazos() {
        esperarToken("token-1", 300);
        servidor.expect(requestTo(BASE + "api/v1/productos/auditoria/operaciones?limite=100"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));
        servidor.expect(requestTo(BASE + "api/v1/productos/auditoria/operaciones?limite=100"))
            .andRespond(withSuccess("{\"operaciones\":[]}", MediaType.APPLICATION_JSON));
        ClienteDelCatalogo cliente = new ClienteDelCatalogo(http, BASE, credencial(Clock.fixed(AHORA, ZoneOffset.UTC)));

        assertThatThrownBy(() -> cliente.consultar(null, 100))
            .isInstanceOf(CatalogoNoDisponibleException.class)
            .hasMessage("productos respondió 403");
        assertThatThrownBy(() -> cliente.consultar(null, 100))
            .isInstanceOf(CatalogoNoDisponibleException.class)
            .hasMessage("productos respondió un lote que no se entiende");
    }

    @Test
    @DisplayName("el token se reutiliza mientras está vigente y se renueva antes de caducar")
    void tokenEnCache() {
        Clock[] reloj = {Clock.fixed(AHORA, ZoneOffset.UTC)};
        CredencialDeServicio credencial = new CredencialDeServicio(http, EMISOR, "ms-cumplimiento",
            "secreto-de-prueba-123", new Clock() {
                @Override
                public ZoneOffset getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zona) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return reloj[0].instant();
                }
            });
        esperarToken("token-1", 120);
        esperarToken("token-2", 120);

        assertThat(credencial.portador()).isEqualTo("token-1");
        reloj[0] = Clock.fixed(AHORA.plusSeconds(60), ZoneOffset.UTC);
        assertThat(credencial.portador()).isEqualTo("token-1");
        reloj[0] = Clock.fixed(AHORA.plusSeconds(95), ZoneOffset.UTC);
        assertThat(credencial.portador()).isEqualTo("token-2");
        servidor.verify();
    }

    @Test
    @DisplayName("si el emisor falla o responde sin token, no hay credencial (nunca un token inventado)")
    void emisorCaido() {
        servidor.expect(requestTo(EMISOR)).andRespond(withServerError());
        servidor.expect(requestTo(EMISOR)).andRespond(withSuccess("{\"token_type\":\"Bearer\"}",
            MediaType.APPLICATION_JSON));
        CredencialDeServicio credencial = credencial(Clock.fixed(AHORA, ZoneOffset.UTC));

        assertThatThrownBy(credencial::portador).isInstanceOf(CatalogoNoDisponibleException.class);
        assertThatThrownBy(credencial::portador)
            .isInstanceOf(CatalogoNoDisponibleException.class)
            .hasMessageContaining("sin token");
    }

    @Test
    @DisplayName("sin URL del emisor, client_id o secreto no se construye la credencial")
    void sinDatos() {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        assertThatThrownBy(() -> new CredencialDeServicio(http, " ", "ms-cumplimiento", "s", reloj))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DIRECTORIO_ACTIVO_URL");
        assertThatThrownBy(() -> new CredencialDeServicio(http, EMISOR, null, "s", reloj))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DIRECTORIO_ACTIVO_CLIENT_ID");
        assertThatThrownBy(() -> new CredencialDeServicio(http, EMISOR, "ms-cumplimiento", "", reloj))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DIRECTORIO_ACTIVO_CLIENT_SECRET");
    }
}
