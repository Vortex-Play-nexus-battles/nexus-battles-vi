package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Los dos clientes nuevos de B2 contra la forma exacta de sus contratos:
 * ms-identidad-admin.yaml (estado-sancion y contacto) y correo.yaml 1.4.0
 * (correos/sancion con Idempotency-Key).
 */
@DisplayName("Clientes de ms-identidad y de correo")
class ClientesDeIdentidadYCorreoTest {

    private static final UUID UID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SANCION = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final OffsetDateTime HASTA = OffsetDateTime.of(2026, 9, 27, 10, 0, 0, 0, ZoneOffset.UTC);

    @Nested
    @DisplayName("ms-identidad")
    class Identidad {

        private final RestClient.Builder constructor = RestClient.builder();
        private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        private final ClienteIdentidad cliente = new ClienteIdentidad(constructor.build(), "http://srv-ms-identidad:8089/");

        @Test
        @DisplayName("proyecta con PUT en la ruta interna y la forma ProyeccionDeSancion")
        void proyecta() {
            servidor.expect(requestTo("http://srv-ms-identidad:8089/api/v1/internal/usuarios/" + UID + "/estado-sancion"))
                    .andExpect(method(HttpMethod.PUT))
                    .andExpect(jsonPath("$.estado").value("SUSPENDIDO"))
                    .andExpect(jsonPath("$.hasta").value("2026-09-27T10:00:00Z"))
                    .andExpect(jsonPath("$.sancionId").value(SANCION.toString()))
                    .andExpect(jsonPath("$.motivo").value("Reincidencia"))
                    .andRespond(withSuccess("{\"uid\":\"" + UID + "\",\"estado\":\"SUSPENDIDO\",\"versionToken\":3}",
                            MediaType.APPLICATION_JSON));

            cliente.proyectar(UID, new ClienteIdentidad.ProyeccionDeSancion("SUSPENDIDO", HASTA, SANCION, "Reincidencia"));
            servidor.verify();
        }

        @Test
        @DisplayName("lee el contacto e ignora lo que no usa")
        void contacto() {
            servidor.expect(requestTo("http://srv-ms-identidad:8089/api/v1/internal/usuarios/" + UID + "/contacto"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("{\"uid\":\"" + UID + "\",\"email\":\"lyra@nexus.test\",\"apodo\":\"Lyra\","
                            + "\"estado\":\"ACTIVO\",\"otro\":1}", MediaType.APPLICATION_JSON));

            ClienteIdentidad.Contacto contacto = cliente.contacto(UID);

            assertThat(contacto.email()).isEqualTo("lyra@nexus.test");
            assertThat(contacto.apodo()).isEqualTo("Lyra");
            assertThat(contacto.estado()).isEqualTo("ACTIVO");
        }

        @Test
        @DisplayName("un 404 sale como error del cliente, para que el destino decida")
        void noExiste() {
            servidor.expect(requestTo("http://srv-ms-identidad:8089/api/v1/internal/usuarios/" + UID + "/contacto"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatThrownBy(() -> cliente.contacto(UID)).isInstanceOf(HttpClientErrorException.NotFound.class);
        }
    }

    @Nested
    @DisplayName("correo")
    class Correo {

        private final RestClient.Builder constructor = RestClient.builder();
        private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        private final ClienteCorreo cliente = new ClienteCorreo(constructor.build(), "http://srv-correo:8082");

        @Test
        @DisplayName("POST /api/v1/correos/sancion con Idempotency-Key y sin campos nulos")
        void envia() {
            servidor.expect(requestTo("http://srv-correo:8082/api/v1/correos/sancion"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Idempotency-Key", "sancion-" + SANCION + "-emision"))
                    .andExpect(jsonPath("$.email").value("lyra@nexus.test"))
                    .andExpect(jsonPath("$.apodo").value("Lyra"))
                    .andExpect(jsonPath("$.tipo").value("BANEO"))
                    .andExpect(jsonPath("$.motivo").value("Fraude"))
                    .andExpect(jsonPath("$.apelableHasta").value("2026-09-27T10:00:00Z"))
                    .andExpect(jsonPath("$.hasta").doesNotExist())
                    .andExpect(jsonPath("$.resultadoApelacion").doesNotExist())
                    .andRespond(withStatus(HttpStatus.ACCEPTED));

            cliente.enviar("sancion-" + SANCION + "-emision",
                    new ClienteCorreo.CorreoSancion("lyra@nexus.test", "Lyra", "BANEO", "Fraude", null, HASTA, null));
            servidor.verify();
        }
    }
}
