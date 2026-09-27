package com.nexusbattles.ms_identidad.sanciones;

import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.auth.servicio.EmisorDeTokensDeServicio;
import com.nexusbattles.ms_identidad.auth.validation.ModeracionNoDisponibleException;
import com.nexusbattles.ms_identidad.onboarding.ServidorFalso;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cliente de moderacion-sanciones (B2): token de quien actua, sin reintentos, 503 si no responde")
class ModeracionSancionesClientTest {

    private static final UUID UID = UUID.fromString("b6b6b6b6-1111-4222-8333-444444444444");
    private static final UUID SANCION = UUID.fromString("c7c7c7c7-1111-4222-8333-444444444444");
    private static final String ADMIN = "Bearer token-del-administrador";

    private ServidorFalso moderacion;
    private ModeracionSancionesClient cliente;

    @BeforeEach
    void preparar() {
        moderacion = new ServidorFalso();
        CredencialPropia credencial = new CredencialPropia(
                new EmisorDeTokensDeServicio(new ClavesDeFirma(""), "ms-identidad", 15));
        cliente = new ModeracionSancionesClient(moderacion.url() + "/api/v1/", credencial, new InterceptorDeTraza());
    }

    @AfterEach
    void apagar() {
        moderacion.close();
        Traza.cerrar();
    }

    private static String sancion(String tipo, String hasta) {
        return "{\"id\":\"" + SANCION + "\",\"usuarioId\":\"" + UID + "\",\"tipo\":\"" + tipo + "\","
                + "\"motivo\":\"m\",\"emitidaPor\":\"" + UUID.randomUUID() + "\",\"rolEmisor\":\"ADMINISTRADOR\","
                + "\"emitidaEn\":\"2026-09-25T12:00:00Z\",\"vigenteHasta\":" + hasta + ",\"vigente\":true}";
    }

    @Test
    @DisplayName("emitir: POST /sanciones con el Authorization de quien actua y la traza de la peticion")
    void emitir() {
        moderacion.responder("POST", "/api/v1/sanciones", 201, sancion("SUSPENSION", "\"2026-09-26T12:00:00Z\""));
        String traceId = Traza.abrir("4bf92f3577b34da6a3ce929d0e0e4736");

        ModeracionSancionesClient.Sancion emitida = cliente.emitir(ADMIN, UID, "SUSPENSION", "Reincidencia", 24L, null);

        assertThat(emitida.id()).isEqualTo(SANCION);
        assertThat(emitida.vigenteHasta()).isEqualTo(OffsetDateTime.parse("2026-09-26T12:00:00Z"));
        ServidorFalso.Peticion recibida = moderacion.recibidas("POST", "/api/v1/sanciones").get(0);
        assertThat(recibida.cabecera("Authorization")).isEqualTo(ADMIN);
        assertThat(recibida.cabecera("traceparent")).contains(traceId);
        assertThat(recibida.cuerpo()).contains("\"usuarioId\":\"" + UID + "\"").contains("\"tipo\":\"SUSPENSION\"")
                .contains("\"motivo\":\"Reincidencia\"").contains("\"duracionHoras\":24")
                .doesNotContain("confirmacion");

        moderacion.responder("POST", "/api/v1/sanciones", 201, sancion("BANEO", "null"));
        assertThat(cliente.emitir(ADMIN, UID, "BANEO", "Fraude", null, true).vigenteHasta()).isNull();
        assertThat(moderacion.recibidas("POST", "/api/v1/sanciones").get(1).cuerpo())
                .contains("\"confirmacion\":true").doesNotContain("duracionHoras");
    }

    @Test
    @DisplayName("activa: con la credencial de SERVICIO de ms-identidad, no con la del administrador")
    void activa() {
        moderacion.responder("GET", "/api/v1/sanciones/usuarios/" + UID + "/activa", 200,
                "{\"sancionActiva\":true,\"motivo\":\"m\",\"vigenteHasta\":\"2026-09-26T12:00:00Z\","
                        + "\"tipo\":\"SUSPENSION\",\"sancionId\":\"" + SANCION + "\"}");

        ModeracionSancionesClient.SancionActiva activa = cliente.activa(UID);

        assertThat(activa.sancionActiva()).isTrue();
        assertThat(activa.tipo()).isEqualTo("SUSPENSION");
        assertThat(activa.sancionId()).isEqualTo(SANCION);
        String autorizacion = moderacion.recibidas().get(0).cabecera("Authorization");
        String carga = new String(Base64.getUrlDecoder().decode(autorizacion.substring(7).split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(carga).contains("\"rol\":\"SERVICIO\"").contains("\"azp\":\"ms-identidad\"");
    }

    @Test
    @DisplayName("levantar: POST /sanciones/{id}/levantamiento con el motivo")
    void levantar() {
        moderacion.responder("POST", "/api/v1/sanciones/" + SANCION + "/levantamiento", 200,
                sancion("SUSPENSION", "\"2026-09-26T12:00:00Z\""));

        assertThat(cliente.levantar(ADMIN, SANCION, "Reactivación").id()).isEqualTo(SANCION);
        assertThat(moderacion.recibidas().get(0).cuerpo()).contains("\"motivo\":\"Reactivación\"");
    }

    @Test
    @DisplayName("4xx: moderacion decidio que no -> SancionRechazada con su detalle y su estado")
    void rechazos() {
        moderacion.responder("POST", "/api/v1/sanciones", 403,
                "{\"type\":\"https://nexusbattles.local/errores/permiso-insuficiente\",\"title\":\"x\",\"status\":403,"
                        + "\"detail\":\"Un moderador no puede banear.\"}");
        assertThatThrownBy(() -> cliente.emitir(ADMIN, UID, "BANEO", "m", null, true))
                .isInstanceOf(SancionRechazadaException.class)
                .hasMessage("Un moderador no puede banear.")
                .extracting(e -> ((SancionRechazadaException) e).getEstado()).isEqualTo(403);

        moderacion.responder("POST", "/api/v1/sanciones/" + SANCION + "/levantamiento", 409, "no es json");
        assertThatThrownBy(() -> cliente.levantar(ADMIN, SANCION, "m"))
                .isInstanceOf(SancionRechazadaException.class)
                .hasMessageContaining("409");
    }

    @Test
    @DisplayName("5xx, cuerpo vacio o nadie escuchando: 503 moderacion-no-disponible, nunca «como si hubiera ido bien»")
    void noDisponible() {
        moderacion.responder("POST", "/api/v1/sanciones", 500, "{}");
        assertThatThrownBy(() -> cliente.emitir(ADMIN, UID, "SUSPENSION", "m", 1L, null))
                .isInstanceOf(ModeracionNoDisponibleException.class);

        moderacion.responder("GET", "/api/v1/sanciones/usuarios/.*/activa", 200, "");
        assertThatThrownBy(() -> cliente.activa(UID)).isInstanceOf(ModeracionNoDisponibleException.class);

        ServidorFalso apagado = new ServidorFalso();
        String sinNadie = apagado.url() + "/api/v1";
        apagado.close();
        ModeracionSancionesClient sinRespuesta = new ModeracionSancionesClient(sinNadie,
                new CredencialPropia(new EmisorDeTokensDeServicio(new ClavesDeFirma(""), "ms-identidad", 15)), null);
        assertThatThrownBy(() -> sinRespuesta.emitir(ADMIN, UID, "SUSPENSION", "m", 1L, null))
                .isInstanceOf(ModeracionNoDisponibleException.class);
    }
}
