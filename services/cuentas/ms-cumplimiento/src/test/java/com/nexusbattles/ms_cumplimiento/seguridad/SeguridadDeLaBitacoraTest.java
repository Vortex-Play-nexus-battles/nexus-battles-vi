package com.nexusbattles.ms_cumplimiento.seguridad;

import com.nexusbattles.ms_cumplimiento.auditoria.controller.AuditLogController;
import com.nexusbattles.ms_cumplimiento.auditoria.controller.ManejadorDeErrores;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La bitacora es un espacio cerrado (RF-AUD-003): toda ruta bajo
 * {@code /api/v1/admin/auditoria} que no se haya abierto a proposito es solo
 * de {@code SUPER_ADMINISTRADOR}, y eso lo impone la cadena de seguridad, no
 * la memoria de quien escriba el siguiente endpoint.
 *
 * <p>Por que existe (HU-AUD-004): la exportacion va a vivir en una subruta de
 * la bitacora. Hasta este cambio la cadena solo cerraba la ruta exacta de la
 * consulta y cualquier subruta caia en {@code anyRequest().authenticated()}:
 * un JUGADOR llegaba al controlador y, si al metodo le faltaba
 * {@code @RequireSuperAdmin2FA}, podia provocar el 422 de "la exportacion
 * excede el maximo" y leer en {@code totalEncontrado} cuantos asientos
 * cumplen un filtro. La anotacion sigue haciendo falta (anade el segundo
 * factor); esto es la segunda puerta.
 */
@WebMvcTest(controllers = AuditLogController.class)
@Import({SeguridadConfig.class, TokensDePrueba.Decodificador.class, ManejadorDeErrores.class})
class SeguridadDeLaBitacoraTest {

    /** Inventada a proposito: no debe coincidir con ningun endpoint real, ni presente ni futuro. */
    private static final String RUTA_NUEVA = "/api/v1/admin/auditoria/ruta-que-no-existe";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuditLogService auditLogService;

    private static String deUsuario(String rol) {
        return "Bearer " + TokensDePrueba.deUsuario("alguien", UUID.randomUUID(), rol);
    }

    @Test
    @DisplayName("una ruta nueva de la bitacora nace cerrada: ADMINISTRADOR, MODERADOR, JUGADOR y un servicio reciben 403, por GET y por POST")
    void rutaNuevaNaceCerrada() throws Exception {
        List<String> credenciales = List.of(deUsuario("ADMINISTRADOR"), deUsuario("MODERADOR"), deUsuario("JUGADOR"),
                "Bearer " + TokensDePrueba.deServicio("ms-identidad"));

        for (String credencial : credenciales) {
            mvc.perform(get(RUTA_NUEVA).header(HttpHeaders.AUTHORIZATION, credencial))
                    .andExpect(status().isForbidden());
            mvc.perform(post(RUTA_NUEVA).header(HttpHeaders.AUTHORIZATION, credencial))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("un SUPER_ADMINISTRADOR si pasa la cadena: como la ruta no existe, contesta 404 y no 403")
    void superAdministradorPasaLaCadena() throws Exception {
        mvc.perform(get(RUTA_NUEVA).header(HttpHeaders.AUTHORIZATION, deUsuario("SUPER_ADMINISTRADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token sigue siendo 401")
    void sinToken() throws Exception {
        mvc.perform(get(RUTA_NUEVA)).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA_NUEVA)).andExpect(status().isUnauthorized());
    }
}
