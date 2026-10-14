package com.nexusbattles.ms_identidad.admin.inventario;

import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient.Entrega;
import com.nexusbattles.ms_identidad.onboarding.ServidorFalso;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cliente de la entrega administrativa al inventario (SA-10): token de quien actua, clave y referencia")
class InventarioDeLaCuentaClientTest {

    private static final UUID UID = UUID.fromString("b6b6b6b6-1111-4222-8333-444444444444");
    private static final String ADMIN = "Bearer token-del-administrador";
    private static final String CLAVE = "c0ffee00-1111-4222-8333-444444444444";
    private static final String RUTA = "/api/v1/inventario/entregas";

    private ServidorFalso inventario;
    private InventarioDeLaCuentaClient cliente;

    @BeforeEach
    void preparar() {
        inventario = new ServidorFalso();
        cliente = new InventarioDeLaCuentaClient(inventario.url() + "/", new InterceptorDeTraza());
    }

    @AfterEach
    void apagar() {
        inventario.close();
        Traza.cerrar();
    }

    private static String entrega(int unidades) {
        StringBuilder elementos = new StringBuilder();
        for (int i = 0; i < unidades; i++) {
            if (i > 0) {
                elementos.append(',');
            }
            elementos.append("{\"id\":\"el-").append(i).append("\",\"productoId\":\"p-hacha\",\"tipo\":\"ARMA\",")
                    .append("\"nombrePropio\":\"Hacha de Obsidiana\",\"disponible\":true,\"subastaId\":null}");
        }
        return "{\"id\":\"e-1\",\"uid\":\"" + UID + "\",\"origen\":\"ADMINISTRACION\",\"referencia\":\"r\","
                + "\"elementos\":[" + elementos + "],\"entregadaEn\":\"2026-10-14T12:00:00Z\"}";
    }

    @Test
    @DisplayName("entrega nueva: el token de quien actua, la clave con su prefijo, origen ADMINISTRACION y la traza")
    void entregaNueva() {
        inventario.responder("POST", RUTA, 201, entrega(2));
        String traceId = Traza.abrir("4bf92f3577b34da6a3ce929d0e0e4736");

        Entrega hecha = cliente.entregar(ADMIN, CLAVE, UID, "p-hacha", 2);

        assertThat(hecha.nueva()).isTrue();
        assertThat(hecha.id()).isEqualTo("e-1");
        assertThat(hecha.elementos()).hasSize(2);
        assertThat(hecha.elementos().get(0).nombrePropio()).isEqualTo("Hacha de Obsidiana");
        ServidorFalso.Peticion recibida = inventario.recibidas("POST", RUTA).get(0);
        assertThat(recibida.cabecera("Authorization")).isEqualTo(ADMIN);
        assertThat(recibida.cabecera("Idempotency-Key")).isEqualTo("admin-entrega:" + CLAVE);
        assertThat(recibida.cabecera("traceparent")).contains(traceId);
        assertThat(recibida.cuerpo())
                .contains("\"uid\":\"" + UID + "\"")
                .contains("\"origen\":\"ADMINISTRACION\"")
                .contains("\"referencia\":\"admin-entrega:" + CLAVE + "\"")
                .contains("\"productoId\":\"p-hacha\"")
                .contains("\"cantidad\":2");
    }

    @Test
    @DisplayName("la misma clave otra vez: 200 con la entrega original, sin nada nuevo")
    void repetida() {
        inventario.responder("POST", RUTA, 200, entrega(1));

        Entrega original = cliente.entregar(ADMIN, CLAVE, UID, "p-hacha", 1);

        assertThat(original.nueva()).isFalse();
        assertThat(original.id()).isEqualTo("e-1");
    }

    @Test
    @DisplayName("un 409 o un 422 del inventario es un rechazo con su estado y su detalle")
    void rechazos() {
        inventario.responder("POST", RUTA, 409,
                "{\"type\":\"about:blank\",\"title\":\"Producto suspendido\",\"status\":409,"
                        + "\"detail\":\"El producto esta suspendido y no se puede entregar.\"}");
        assertThatThrownBy(() -> cliente.entregar(ADMIN, CLAVE, UID, "p-x", 1))
                .isInstanceOf(EntregaRechazadaException.class)
                .hasMessage("El producto esta suspendido y no se puede entregar.")
                .satisfies(e -> assertThat(((EntregaRechazadaException) e).getEstado()).isEqualTo(409));

        inventario.responder("POST", RUTA, 422, "{\"title\":\"Producto inexistente\",\"status\":422}");
        assertThatThrownBy(() -> cliente.entregar(ADMIN, CLAVE, UID, "p-y", 1))
                .isInstanceOf(EntregaRechazadaException.class)
                .hasMessage("Producto inexistente")
                .satisfies(e -> assertThat(((EntregaRechazadaException) e).getEstado()).isEqualTo(422));
    }

    @Test
    @DisplayName("un 5xx, una respuesta sin entrega o un inventario apagado: no disponible, nunca «hecho»")
    void noDisponible() {
        inventario.responder("POST", RUTA, 503, "{\"title\":\"Inventario no disponible\",\"status\":503}");
        assertThatThrownBy(() -> cliente.entregar(ADMIN, CLAVE, UID, "p-hacha", 1))
                .isInstanceOf(InventarioNoDisponibleException.class);

        inventario.responder("POST", RUTA, 201, "{}");
        assertThatThrownBy(() -> cliente.entregar(ADMIN, CLAVE, UID, "p-hacha", 1))
                .isInstanceOf(InventarioNoDisponibleException.class);

        String direccion = inventario.url();
        inventario.close();
        InventarioDeLaCuentaClient sinServidor = new InventarioDeLaCuentaClient(direccion, null);
        assertThatThrownBy(() -> sinServidor.entregar(ADMIN, CLAVE, UID, "p-hacha", 1))
                .isInstanceOf(InventarioNoDisponibleException.class);
    }

    @Test
    @DisplayName("sin la direccion del inventario configurada no se llama a nadie")
    void sinDireccion() {
        InventarioDeLaCuentaClient sinDireccion = new InventarioDeLaCuentaClient("  ", null);

        assertThatThrownBy(() -> sinDireccion.entregar(ADMIN, CLAVE, UID, "p-hacha", 1))
                .isInstanceOf(InventarioNoDisponibleException.class)
                .hasMessageContaining("no está configurada");
        assertThat(inventario.recibidas()).isEmpty();
    }
}
