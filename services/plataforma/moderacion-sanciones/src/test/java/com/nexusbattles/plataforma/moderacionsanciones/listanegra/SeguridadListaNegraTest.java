package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import com.nexusbattles.plataforma.moderacionsanciones.seguridad.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CA-03: acceso restringido a ADMINISTRADOR/MODERADOR en el panel de lista negra.
 */
@WebMvcTest(controllers = {ListaNegraAdminController.class, ListaNegraVerificacionController.class})
@Import({SecurityConfig.class, SeguridadListaNegraTest.CacheDePruebaConfig.class})
class SeguridadListaNegraTest {

    @TestConfiguration
    static class CacheDePruebaConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListaNegraAdminService listaNegraAdminService;

    @MockitoBean
    private VerificacionListaNegraService verificacionListaNegraService;

    @Test
    void rechazaLaRutaDeAdministracionSinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/lista-negra/terminos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaLaRutaDeAdministracionParaUnJugador() throws Exception {
        mockMvc.perform(get("/api/v1/lista-negra/terminos").with(rolDeKeycloak("JUGADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void permiteLaRutaDeAdministracionAUnModerador() throws Exception {
        when(listaNegraAdminService.listarTerminos()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/lista-negra/terminos").with(rolDeKeycloak("MODERADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void permiteLaRutaDeAdministracionAUnAdministrador() throws Exception {
        when(listaNegraAdminService.listarTerminos()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/lista-negra/terminos").with(rolDeKeycloak("ADMINISTRADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void laVerificacionQuedaAbiertaParaLlamadasEntreMicroservicios() throws Exception {
        when(verificacionListaNegraService.verificar("hola"))
                .thenReturn(new VerificacionListaNegraService.ResultadoVerificacion(true, null));

        mockMvc.perform(post("/api/v1/lista-negra/verificar")
                        .contentType("application/json")
                        .content("{\"texto\":\"hola\"}"))
                .andExpect(status().isOk());
    }

    /**
     * jwt() de spring-security-test simula un usuario ya autenticado y no ejecuta
     * el ConversorRolesJwt real -- por eso las autoridades se declaran explicitas
     * aqui. La logica del conversor en si se prueba aparte en ConversorRolesJwtTest.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor rolDeKeycloak(String rol) {
        return jwt()
                .jwt(j -> j.claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }
}
