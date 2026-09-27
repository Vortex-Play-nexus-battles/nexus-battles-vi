package com.nexusbattles.ms_cumplimiento.auditoria.controller;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.exception.ExportacionExcedeMaximoException;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.security.RequireSuperAdmin2FAAspect;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import com.nexusbattles.ms_cumplimiento.seguridad.SeguridadConfig;
import com.nexusbattles.ms_cumplimiento.seguridad.TokensDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditLogController.class)
@Import({SeguridadConfig.class, TokensDePrueba.Decodificador.class, ManejadorDeErrores.class,
    AuditLogControllerTest.AspectoReal.class})
class AuditLogControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class AspectoReal {
        @Bean
        RequireSuperAdmin2FAAspect requireSuperAdmin2FAAspect() {
            return new RequireSuperAdmin2FAAspect(false);
        }
    }

    private static final String BITACORA = "/api/v1/admin/auditoria";
    private static final String EXPORTAR = "/api/v1/admin/auditoria/exportar";
    private static final String EVENTOS = "/api/v1/admin/auditoria/eventos";
    private static final String EVENTO = """
            {"tipoAccion":"SUSPENSION","administradorId":"admin-2","afectado":"usuario-2",
             "valorAnterior":"ACTIVO","valorNuevo":"SUSPENDIDO","motivo":"Motivo de prueba","ipOrigen":"10.0.0.1"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuditLogService auditLogService;

    private static AuditLog registro(AuditActionType tipo) {
        return AuditLog.builder()
            .tipoAccion(tipo)
            .administradorId("admin-2")
            .afectado("usuario-2")
            .motivo("Motivo de prueba")
            .ipOrigen("10.0.0.1")
            .build();
    }

    @Nested
    @DisplayName("consultar la bitacora")
    class Consultar {

        @Test
        @DisplayName("sin token, 401")
        void sinToken() throws Exception {
            mvc.perform(get(BITACORA)).andExpect(status().isUnauthorized());
            verifyNoInteractions(auditLogService);
        }

        @Test
        @DisplayName("un SUPER_ADMINISTRADOR con su token real la consulta: el rol se llama asi, no SUPER_ADMIN")
        void superAdministrador() throws Exception {
            when(auditLogService.consultar(eq("admin-1"), eq(AuditActionType.CAMBIO_ROL), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(registro(AuditActionType.CAMBIO_ROL))));

            mvc.perform(get(BITACORA)
                    .param("administradorId", "admin-1").param("tipoAccion", "CAMBIO_ROL")
                    .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TokensDePrueba.deUsuario("root", UUID.randomUUID(), "SUPER_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipoAccion").value("CAMBIO_ROL"))
                .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("un ADMINISTRADOR, un JUGADOR o un servicio no la consultan: 403")
        void otrosRoles() throws Exception {
            for (String rol : List.of("ADMINISTRADOR", "MODERADOR", "JUGADOR")) {
                mvc.perform(get(BITACORA).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TokensDePrueba.deUsuario("alguien", UUID.randomUUID(), rol)))
                    .andExpect(status().isForbidden());
            }
            mvc.perform(get(BITACORA).header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + TokensDePrueba.deServicio("ms-identidad")))
                .andExpect(status().isForbidden());

            verifyNoInteractions(auditLogService);
        }

        @Test
        @DisplayName("un token caducado o firmado por otro es 401")
        void tokenInvalido() throws Exception {
            UUID uid = UUID.randomUUID();
            mvc.perform(get(BITACORA).header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + TokensDePrueba.caducado("root", uid)))
                .andExpect(status().isUnauthorized());
            mvc.perform(get(BITACORA).header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + TokensDePrueba.firmadoPorOtro("root", uid)))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("exportar la bitacora")
    class Exportar {

        @Test
        @DisplayName("sin token, 401")
        void sinToken() throws Exception {
            mvc.perform(get(EXPORTAR)).andExpect(status().isUnauthorized());
            verifyNoInteractions(auditLogService);
        }

        @Test
        @DisplayName("un SUPER_ADMINISTRADOR exporta: 200, PDF")
        void superAdministradorExporta() throws Exception {
            byte[] pdfSimulado = "contenido-pdf-simulado".getBytes();
            when(auditLogService.exportarPdf(any(), any(), any(), any(), any(), any()))
                .thenReturn(pdfSimulado);

            mvc.perform(get(EXPORTAR).header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + TokensDePrueba.deUsuario("root", UUID.randomUUID(), "SUPER_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdfSimulado));
        }

        @Test
        @DisplayName("un ADMINISTRADOR, un MODERADOR, un JUGADOR o un servicio no exportan: 403, sin llegar a contar")
        void otrosRolesNoExportanYNoLlegaAContar() throws Exception {
            for (String rol : List.of("ADMINISTRADOR", "MODERADOR", "JUGADOR")) {
                mvc.perform(get(EXPORTAR).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TokensDePrueba.deUsuario("alguien", UUID.randomUUID(), rol)))
                    .andExpect(status().isForbidden());
            }
            mvc.perform(get(EXPORTAR).header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + TokensDePrueba.deServicio("ms-identidad")))
                .andExpect(status().isForbidden());

            verifyNoInteractions(auditLogService);
        }

        @Test
        @DisplayName("si el filtro excede el maximo, 422 con las dos cifras en la raiz del JSON")
        void excedeMaximo() throws Exception {
            when(auditLogService.exportarPdf(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ExportacionExcedeMaximoException(15000, 10000));

            mvc.perform(get(EXPORTAR).header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + TokensDePrueba.deUsuario("root", UUID.randomUUID(), "SUPER_ADMINISTRADOR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("La exportación excede el máximo por operación"))
                .andExpect(jsonPath("$.totalEncontrado").value(15000))
                .andExpect(jsonPath("$.maximoPermitido").value(10000));
        }
    }

    @Nested
    @DisplayName("registrar eventos")
    class Registrar {

        @Test
        @DisplayName("un servicio con credencial registra el evento: 201")
        void servicioRegistra() throws Exception {
            when(auditLogService.registrarDesdeSolicitud(any(RegistrarAuditoriaRequest.class)))
                .thenReturn(registro(AuditActionType.SUSPENSION));

            mvc.perform(post(EVENTOS)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.deServicio("ms-identidad"))
                    .contentType(MediaType.APPLICATION_JSON).content(EVENTO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoAccion").value("SUSPENSION"))
                .andExpect(jsonPath("$.administrador").value("admin-2"));

            verify(auditLogService).registrarDesdeSolicitud(any(RegistrarAuditoriaRequest.class));
        }

        @Test
        @DisplayName("sin credencial 401; con token de usuario, aunque sea superadministrador, 403")
        void soloServicios() throws Exception {
            mvc.perform(post(EVENTOS).contentType(MediaType.APPLICATION_JSON).content(EVENTO))
                .andExpect(status().isUnauthorized());
            mvc.perform(post(EVENTOS)
                    .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TokensDePrueba.deUsuario("root", UUID.randomUUID(), "SUPER_ADMINISTRADOR"))
                    .contentType(MediaType.APPLICATION_JSON).content(EVENTO))
                .andExpect(status().isForbidden());

            verifyNoInteractions(auditLogService);
        }

        @Test
        @DisplayName("un tipoAccion desconocido es 400 con problem details, no 500")
        void tipoDesconocidoEs400() throws Exception {
            when(auditLogService.registrarDesdeSolicitud(any(RegistrarAuditoriaRequest.class)))
                .thenThrow(new IllegalArgumentException("tipoAccion inválido: TIPO_QUE_NO_EXISTE"));

            mvc.perform(post(EVENTOS)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.deServicio("ms-identidad"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(EVENTO.replace("SUSPENSION", "TIPO_QUE_NO_EXISTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud invalida"))
                .andExpect(jsonPath("$.detail").value("tipoAccion inválido: TIPO_QUE_NO_EXISTE"));
        }
    }
}
