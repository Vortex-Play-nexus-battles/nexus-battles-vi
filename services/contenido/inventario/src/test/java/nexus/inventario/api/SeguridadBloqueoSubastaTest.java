package nexus.inventario.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import nexus.inventario.aplicacion.GestionarBloqueoSubasta;
import nexus.inventario.configuracion.SeguridadConfig;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BloqueoSubastaController.class)
@Import(SeguridadConfig.class)
class SeguridadBloqueoSubastaTest {

    private static final UUID PROPIETARIO_UID =
            UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
    private static final UUID SUBASTA_ID =
            UUID.fromString("89d9040d-52e0-44ae-8d8c-8ec033978afb");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GestionarBloqueoSubasta gestion;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void bloqueoExigeTokenDelServicioSubastas() throws Exception {
        mvc.perform(put("/api/v1/inventario/elementos/elemento-1/bloqueo-subasta")
                        .header("Idempotency-Key", "publicar-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo()))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/v1/inventario/elementos/elemento-1/bloqueo-subasta")
                        .with(jwt().jwt(token -> token.claim("azp", "otro-servicio")))
                        .header("Idempotency-Key", "publicar-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo()))
                .andExpect(status().isForbidden());
    }

    @Test
    void bloqueoAceptaTokenClientCredentialsDeSubastas() throws Exception {
        when(gestion.bloquear(eq(PROPIETARIO_UID), eq("elemento-1"), eq(SUBASTA_ID), any()))
                .thenReturn(new ElementoInventario(
                        "elemento-1", UUID.randomUUID().toString(),
                        TipoElementoInventario.ITEM, "Reliquia", null, SUBASTA_ID.toString()));

        mvc.perform(put("/api/v1/inventario/elementos/elemento-1/bloqueo-subasta")
                        .with(jwt().jwt(token -> token.claim("azp", "ms-subastas")))
                        .header("Idempotency-Key", "publicar-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo()))
                .andExpect(status().isOk());
    }

    @Test
    void liberacionTambienExigeTokenDelServicioSubastas() throws Exception {
        mvc.perform(delete("/api/v1/inventario/elementos/elemento-1/bloqueo-subasta/{subastaId}",
                        SUBASTA_ID)
                        .header("Idempotency-Key", "cerrar-1"))
                .andExpect(status().isUnauthorized());
    }

    private String cuerpo() {
        return """
                {"propietarioUid":"%s","subastaId":"%s"}
                """.formatted(PROPIETARIO_UID, SUBASTA_ID);
    }
}
