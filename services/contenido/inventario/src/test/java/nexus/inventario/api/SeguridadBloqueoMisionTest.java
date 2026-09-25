package nexus.inventario.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import nexus.inventario.aplicacion.GestionarBloqueoMision;
import nexus.inventario.aplicacion.ProgresionNoDisponibleException;
import nexus.inventario.configuracion.SeguridadConfig;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.HeroeEnMisionException;
import nexus.inventario.dominio.NoEsUnHeroeException;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 1.6.0 (B9): bloquear y liberar a un heroe en mision es exclusivo del servicio
 * de misiones (credencial de servicio con su azp), y los errores del dominio
 * salen como el contrato los promete.
 */
@WebMvcTest(controllers = BloqueoMisionController.class)
@Import(SeguridadConfig.class)
class SeguridadBloqueoMisionTest {

    private static final UUID PROPIETARIO = UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
    private static final UUID EJECUCION = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GestionarBloqueoMision gestion;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor servicio(String azp) {
        return jwt().jwt(token -> token.claim("azp", azp)).authorities(new SimpleGrantedAuthority("ROLE_SERVICIO"));
    }

    private static ElementoInventario heroe(String ejecucion, Integer nivel, Double experiencia) {
        return new ElementoInventario("heroe-1", UUID.randomUUID().toString(), TipoElementoInventario.HEROE, "Vorn",
                null, null, nivel, experiencia, ejecucion);
    }

    @Test
    void soloMisionesBloquea() throws Exception {
        mvc.perform(bloqueo()).andExpect(status().isUnauthorized());
        mvc.perform(bloqueo().with(servicio("ms-subastas"))).andExpect(status().isForbidden());
        // El azp solo no basta: tiene que ser una credencial de servicio.
        mvc.perform(bloqueo().with(jwt().jwt(token -> token.claim("azp", "misiones"))
                .authorities(new SimpleGrantedAuthority("ROLE_JUGADOR")))).andExpect(status().isForbidden());

        when(gestion.bloquear(eq(PROPIETARIO), eq("heroe-1"), eq(EJECUCION), any()))
                .thenReturn(heroe(EJECUCION.toString(), null, null));
        mvc.perform(bloqueo().with(servicio("misiones")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.ejecucionMisionId").value(EJECUCION.toString()))
                .andExpect(jsonPath("$.nivel").value(1))
                .andExpect(jsonPath("$.experiencia").value(0.0));
    }

    @Test
    void soloMisionesLibera() throws Exception {
        mvc.perform(liberacion("{\"propietarioUid\":\"" + PROPIETARIO + "\",\"experiencia\":50}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(liberacion("{\"propietarioUid\":\"" + PROPIETARIO + "\",\"experiencia\":50}")
                        .with(servicio("salas-partidas")))
                .andExpect(status().isForbidden());

        when(gestion.liberar(eq(PROPIETARIO), eq("heroe-1"), eq(EJECUCION), eq(50.0), any()))
                .thenReturn(heroe(null, 1, 50.0));
        mvc.perform(liberacion("{\"propietarioUid\":\"" + PROPIETARIO + "\",\"experiencia\":50}")
                        .with(servicio("misiones")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.experiencia").value(50.0))
                .andExpect(jsonPath("$.ejecucionMisionId").doesNotExist());

        when(gestion.liberar(eq(PROPIETARIO), eq("heroe-1"), eq(EJECUCION), eq(0.0), any()))
                .thenReturn(heroe(null, 1, 0.0));
        mvc.perform(liberacion("{\"propietarioUid\":\"" + PROPIETARIO + "\"}").with(servicio("misiones")))
                .andExpect(status().isOk());
    }

    @Test
    void erroresDelContrato() throws Exception {
        when(gestion.bloquear(any(), eq("heroe-1"), any(), any()))
                .thenThrow(new HeroeEnMisionException("El heroe ya esta en otra mision."));
        mvc.perform(bloqueo().with(servicio("misiones")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Heroe en mision"));

        when(gestion.bloquear(any(), eq("heroe-1"), any(), any())).thenThrow(new NoEsUnHeroeException());
        mvc.perform(bloqueo().with(servicio("misiones")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("No es un heroe"));

        when(gestion.liberar(any(), eq("heroe-1"), any(), anyDouble(), any()))
                .thenThrow(new ProgresionNoDisponibleException("caido", null));
        mvc.perform(liberacion("{\"propietarioUid\":\"" + PROPIETARIO + "\",\"experiencia\":5}")
                        .with(servicio("misiones")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Progresion no disponible"));

        mvc.perform(liberacion("{\"propietarioUid\":\"" + PROPIETARIO + "\",\"experiencia\":-5}")
                        .with(servicio("misiones")))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bloqueo() {
        return put("/api/v1/inventario/elementos/heroe-1/bloqueo-mision")
                .header("Idempotency-Key", "mision-x-bloqueo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"propietarioUid\":\"" + PROPIETARIO + "\",\"ejecucionId\":\"" + EJECUCION + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder liberacion(String cuerpo) {
        return post("/api/v1/inventario/elementos/heroe-1/bloqueo-mision/{ejecucion}/liberacion", EJECUCION)
                .header("Idempotency-Key", "mision-x-liberacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }
}
