package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.controller.RbacController;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La politica RBAC deja de ser publica — R9.5.
 *
 * <h2>Que fija esta clase</h2>
 *
 * {@code RbacControllerTest}, que sigue viva al lado, monta el controlador con
 * {@code standaloneSetup(controller)} <b>sin interceptor</b>: prueba que los
 * tres metodos devuelven lo que prometen, no quien puede pedirlos. Por eso no
 * se puso roja cuando el controlador estaba completamente abierto — no miraba
 * ahi.
 *
 * <p>Esta clase si registra el {@code SecurityInterceptor}, que es el que
 * aplica {@code @RequirePermission}, y comprueba el otro lado:
 *
 * <ul>
 *   <li>sin credencial, los tres caminos responden 403 (fail-closed de
 *       HU-RBAC-004) — antes de R9.5 respondian 200 con la matriz entera;</li>
 *   <li>un JUGADOR con JWT valido tampoco entra: tener token no es tener
 *       permiso;</li>
 *   <li>un ADMINISTRADOR con JWT valido si;</li>
 *   <li>y la cabecera {@code X-User-Role} no acredita nada con el respaldo
 *       apagado, que es como queda dev desde R9.5. Es la prueba de que la
 *       escalada por cabecera esta cerrada: quien mande
 *       {@code X-User-Role: SUPER_ADMINISTRADOR} sin token recibe 403.</li>
 * </ul>
 */
@DisplayName("R9.5: la politica RBAC exige GESTIONAR_CUENTAS")
class RbacControllerSeguridadTest {

    private static final String AUTORIZAR = """
            {"role":"JUGADOR","action":"BANEAR_DEFINITIVAMENTE"}""";

    private JwtService jwtService;
    private MockMvc conRespaldoApagado;
    private MockMvc conRespaldoEncendido;

    @BeforeEach
    void montar() {
        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");

        conRespaldoApagado = montarCon(false);
        conRespaldoEncendido = montarCon(true);
    }

    private MockMvc montarCon(boolean permitirHeaderRol) {
        RbacAuthorizationService servicio = new RbacAuthorizationService(new RbacMatrixRepository());
        SecurityInterceptor interceptor =
                new SecurityInterceptor(servicio, null, jwtService, null, permitirHeaderRol);
        return MockMvcBuilders.standaloneSetup(new RbacController(servicio))
                .addInterceptors(interceptor)
                .build();
    }

    private String token(String apodo, String rol) {
        return jwtService.generarToken(apodo, rol, 1, java.util.UUID.randomUUID());
    }

    @Nested
    @DisplayName("Sin credencial: fail-closed en los tres caminos")
    class SinCredencial {

        @Test
        @DisplayName("GET /matrix ya no reparte la tabla entera a quien la pida")
        void matrizNoEsPublica() throws Exception {
            conRespaldoApagado.perform(get("/api/v1/rbac/matrix"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /roles tampoco")
        void rolesNoEsPublico() throws Exception {
            conRespaldoApagado.perform(get("/api/v1/rbac/roles"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /authorize deja de ser un oraculo abierto de la politica")
        void authorizeNoEsPublico() throws Exception {
            conRespaldoApagado.perform(post("/api/v1/rbac/authorize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(AUTORIZAR))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Con credencial: manda el rol, no el token")
    class ConCredencial {

        @Test
        @DisplayName("un JUGADOR con JWT valido recibe 403: tener token no es tener permiso")
        void jugadorConTokenValido() throws Exception {
            conRespaldoApagado.perform(get("/api/v1/rbac/matrix")
                            .header("Authorization", "Bearer " + token("lyra", "JUGADOR")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un MODERADOR tampoco: GESTIONAR_CUENTAS le esta DENEGADA en la matriz")
        void moderadorConTokenValido() throws Exception {
            conRespaldoApagado.perform(get("/api/v1/rbac/matrix")
                            .header("Authorization", "Bearer " + token("mod", "MODERADOR")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un ADMINISTRADOR con JWT valido si obtiene la matriz completa")
        void administradorConTokenValido() throws Exception {
            conRespaldoApagado.perform(get("/api/v1/rbac/matrix")
                            .header("Authorization", "Bearer " + token("raiz", "ADMINISTRADOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matrix").isNotEmpty());
        }

        @Test
        @DisplayName("y un SUPER_ADMINISTRADOR el catalogo de roles")
        void superAdministradorVeLosRoles() throws Exception {
            conRespaldoApagado.perform(get("/api/v1/rbac/roles")
                            .header("Authorization", "Bearer " + token("raiz", "SUPER_ADMINISTRADOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4));
        }
    }

    @Nested
    @DisplayName("La escalada por cabecera, cerrada")
    class Escalada {

        @Test
        @DisplayName("X-User-Role: SUPER_ADMINISTRADOR sin token no acredita nada (403)")
        void cabeceraNoEscala() throws Exception {
            conRespaldoApagado.perform(get("/api/v1/rbac/matrix")
                            .header("X-User-Name", "atacante")
                            .header("X-User-Role", "SUPER_ADMINISTRADOR"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("la cabecera no puede tampoco mejorar el rol de un token real")
        void cabeceraNoMejoraElToken() throws Exception {
            // El JWT manda: se lee primero y la rama del header ni se visita.
            conRespaldoApagado.perform(get("/api/v1/rbac/matrix")
                            .header("Authorization", "Bearer " + token("lyra", "JUGADOR"))
                            .header("X-User-Role", "SUPER_ADMINISTRADOR"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("solo con el respaldo encendido a proposito la cabecera vuelve a servir")
        void respaldoEncendidoSigueSiendoPosible() throws Exception {
            // Esta es la unica forma de que la cabecera valga, y desde R9.5 hay
            // que pedirla: RBAC_PERMITIR_HEADER_ROL=true. Se prueba para que
            // quede claro que el respaldo no se rompio, solo dejo de venir
            // encendido de fabrica.
            conRespaldoEncendido.perform(get("/api/v1/rbac/matrix")
                            .header("X-User-Name", "demo")
                            .header("X-User-Role", "ADMINISTRADOR"))
                    .andExpect(status().isOk());
        }
    }
}
