package nexus.inventario.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nexus.inventario.aplicacion.EntregarProductos;
import nexus.inventario.aplicacion.EntregarProductos.ResultadoEntrega;
import nexus.inventario.aplicacion.SolicitudDeEntrega;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import nexus.inventario.configuracion.SeguridadConfig;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.LineaDeEntrega;
import nexus.inventario.dominio.OrigenDeEntrega;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * B4 — quien puede dar la propiedad de un producto, con la cadena de seguridad
 * real y tokens firmados de verdad: un servicio o un administrador, nunca un
 * jugador (ni para si mismo ni para otro).
 */
@WebMvcTest(controllers = EntregasController.class)
@Import({SeguridadConfig.class, IdentidadDelLlamador.class, DecodificadorDePrueba.class, ManejadorDeErrores.class})
class SeguridadDeEntregasTest {

    private static final String RUTA = "/api/v1/inventario/entregas";
    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
    private static final UUID JUGADOR = UUID.fromString("0c3b8a52-7f1e-4d2a-9b6c-5e4d3c2b1a00");
    private static final String CUERPO = """
            {"uid":"%s","origen":"COMPRA","referencia":"orden-9","productos":[{"productoId":"espada","cantidad":1}]}"""
            .formatted(JUGADOR);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EntregarProductos entregas;

    private ResultActions entregarCon(String portador) throws Exception {
        var peticion = post(RUTA).header("Idempotency-Key", "clave-9")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO);
        if (portador != null) {
            peticion = peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + portador);
        }
        return mvc.perform(peticion);
    }

    private static ResultadoEntrega resultado(boolean realizadaAhora) {
        Entrega entrega = Entrega.pendiente("entrega-9", "clave-9", "huella", JUGADOR.toString(),
                        OrigenDeEntrega.COMPRA, "orden-9", List.of(new LineaDeEntrega("espada", 1)), List.of(),
                        "quien", Instant.parse("2026-09-25T15:00:00Z"))
                .completadaEn(Instant.parse("2026-09-25T15:00:01Z"));
        return new ResultadoEntrega(entrega, realizadaAhora);
    }

    @Test
    @DisplayName("sin token, 401 'Identidad requerida'")
    void sinToken() throws Exception {
        entregarCon(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Identidad requerida"));
        verifyNoInteractions(entregas);
    }

    @Test
    @DisplayName("un JUGADOR no se entrega nada, ni a si mismo: 403 con problem detail")
    void unJugadorNo() throws Exception {
        entregarCon(EMISOR.tokenDeJugador("lyra_roja", JUGADOR))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.instance").value(RUTA));
        entregarCon(EMISOR.tokenDeUsuario("mod_1", UUID.randomUUID(), "MODERADOR"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(entregas);
    }

    @Test
    @DisplayName("un servicio entrega: 201, y la auditoria guarda su azp, no lo que diga el cuerpo")
    void unServicioEntrega() throws Exception {
        when(entregas.entregar(any(SolicitudDeEntrega.class), anyString(), anyString())).thenReturn(resultado(true));

        entregarCon(EMISOR.tokenDeServicio("ms-ecommerce"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("entrega-9"))
                .andExpect(jsonPath("$.uid").value(JUGADOR.toString()));

        verify(entregas).entregar(
                eq(new SolicitudDeEntrega(JUGADOR, OrigenDeEntrega.COMPRA, "orden-9",
                        List.of(new LineaDeEntrega("espada", 1)))),
                eq("clave-9"), eq("ms-ecommerce"));
    }

    @Test
    @DisplayName("un ADMINISTRADOR o SUPER_ADMINISTRADOR entrega (alta manual auditada con su uid)")
    void unAdministradorEntrega() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID superAdmin = UUID.randomUUID();
        when(entregas.entregar(any(SolicitudDeEntrega.class), anyString(), anyString())).thenReturn(resultado(true));

        entregarCon(EMISOR.tokenDeUsuario("admin_1", admin, "ADMINISTRADOR")).andExpect(status().isCreated());
        entregarCon(EMISOR.tokenDeUsuario("super_1", superAdmin, "SUPER_ADMINISTRADOR"))
                .andExpect(status().isCreated());

        verify(entregas).entregar(any(SolicitudDeEntrega.class), eq("clave-9"), eq(admin.toString()));
        verify(entregas).entregar(any(SolicitudDeEntrega.class), eq("clave-9"), eq(superAdmin.toString()));
    }

    @Test
    @DisplayName("una clave ya completada devuelve 200 con la entrega original")
    void repeticionEs200() throws Exception {
        when(entregas.entregar(any(SolicitudDeEntrega.class), anyString(), anyString())).thenReturn(resultado(false));

        entregarCon(EMISOR.tokenDeServicio("ms-ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("entrega-9"));
    }

    @Test
    @DisplayName("sin Idempotency-Key es 400, aunque quien llame pueda entregar")
    void sinClave() throws Exception {
        mvc.perform(post(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("ms-ecommerce"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud invalida"));
        mvc.perform(post(RUTA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("ms-ecommerce"))
                        .header("Idempotency-Key", "x".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(entregas);
    }
}
