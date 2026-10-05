package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.directorio.CuentasDePrueba;
import com.nexusbattles.ms_identidad.admin.directorio.DirectorioDeCuentas;
import com.nexusbattles.ms_identidad.admin.directorio.ExportacionDelDirectorio;
import com.nexusbattles.ms_identidad.admin.directorio.ExportacionDemasiadoGrandeException;
import com.nexusbattles.ms_identidad.admin.directorio.FiltroDelDirectorio;
import com.nexusbattles.ms_identidad.admin.directorio.IndicadoresDeCuentas;
import com.nexusbattles.ms_identidad.admin.directorio.ConsultaInvalidaException;
import com.nexusbattles.ms_identidad.admin.dto.IndicadoresDeCuentasResponse;
import com.nexusbattles.ms_identidad.admin.dto.IndicadoresDeCuentasResponse.RegistrosDelDia;
import com.nexusbattles.ms_identidad.admin.dto.IndicadoresDeCuentasResponse.RegistrosPorDia;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El directorio, sus filtros, su exportacion y los indicadores por HTTP —
 * HU-USR-008 (#561), ms-identidad-admin.yaml 1.3.0.
 *
 * <p>Con el {@code SecurityInterceptor} de verdad (el que aplica
 * {@code @RequirePermission}) y tokens firmados por el mismo emisor: asi se
 * ve que las dos rutas nuevas tienen el mismo permiso que el directorio
 * (Administrador y Super Administrador, Tabla 24), que los rechazos son
 * problem details con el {@code type} del servicio y que el CSV sale como
 * archivo. La logica de cada pieza la prueban sus propias clases.
 */
@DisplayName("Directorio, exportacion e indicadores por HTTP (HU-USR-008)")
class AdminDirectorioHttpTest {

    private static final String DIRECTORIO = "/api/v1/admin/jugadores";
    private static final String EXPORTACION = DIRECTORIO + "/exportacion";
    private static final String INDICADORES = DIRECTORIO + "/indicadores";
    private static final String ERRORES = "https://nexusbattles.upb.edu.co/errors/";

    private JwtService jwtService;
    private UsuarioRepository usuarioRepository;
    private DirectorioDeCuentas directorio;
    private IndicadoresDeCuentas indicadores;
    private ExportacionDelDirectorio exportacion;
    private MockMvc mvc;

    @BeforeEach
    void montar() {
        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");
        usuarioRepository = mock(UsuarioRepository.class);
        directorio = mock(DirectorioDeCuentas.class);
        indicadores = mock(IndicadoresDeCuentas.class);
        exportacion = mock(ExportacionDelDirectorio.class);

        // Respaldo por cabecera APAGADO, como en cualquier entorno desplegado.
        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()), null, jwtService, null, false);
        AdminDirectorioController controlador = new AdminDirectorioController(usuarioRepository, directorio,
                new CuentasDePrueba("nexus.test", "qa_"), indicadores, exportacion);
        mvc = MockMvcBuilders.standaloneSetup(controlador).addInterceptors(interceptor).build();
    }

    private String token(String rol) {
        return "Bearer " + jwtService.generarToken("operadora", rol, 0, UUID.randomUUID());
    }

    private static IndicadoresDeCuentasResponse indicadoresDePrueba() {
        Map<String, Long> porEstado = new LinkedHashMap<>();
        porEstado.put("ACTIVO", 10L);
        porEstado.put("PENDIENTE_VERIFICACION", 2L);
        porEstado.put("INACTIVO", 0L);
        porEstado.put("SUSPENDIDO", 1L);
        porEstado.put("BANEADO", 3L);
        List<RegistrosDelDia> serie = List.of(
                new RegistrosDelDia(LocalDate.of(2026, 10, 1), 4),
                new RegistrosDelDia(LocalDate.of(2026, 10, 2), 0));
        return new IndicadoresDeCuentasResponse(16, porEstado,
                new RegistrosPorDia(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), 4, serie),
                true, OffsetDateTime.parse("2026-10-05T15:00:00-05:00"));
    }

    // ------------------------------------------------------------- indicadores

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"ADMINISTRADOR", "SUPER_ADMINISTRADOR"})
    @DisplayName("indicadores: administracion los ve, con la forma del contrato y sin cache")
    void indicadoresParaAdministracion(String rol) throws Exception {
        when(indicadores.calcular("2026-10-01", "2026-10-02", true)).thenReturn(indicadoresDePrueba());

        mvc.perform(get(INDICADORES).param("desde", "2026-10-01").param("hasta", "2026-10-02")
                        .param("ocultarPruebas", "true").header("Authorization", token(rol)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.total").value(16))
                .andExpect(jsonPath("$.porEstado.ACTIVO").value(10))
                .andExpect(jsonPath("$.porEstado.BANEADO").value(3))
                .andExpect(jsonPath("$.registros.desde").value("2026-10-01"))
                .andExpect(jsonPath("$.registros.hasta").value("2026-10-02"))
                .andExpect(jsonPath("$.registros.total").value(4))
                .andExpect(jsonPath("$.registros.porDia[0].fecha").value("2026-10-01"))
                .andExpect(jsonPath("$.registros.porDia[0].cuentas").value(4))
                .andExpect(jsonPath("$.registros.porDia[1].cuentas").value(0))
                .andExpect(jsonPath("$.ocultarPruebas").value(true))
                .andExpect(jsonPath("$.calculadoEn").exists());
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"JUGADOR", "MODERADOR"})
    @DisplayName("indicadores: 403 para quien no gestiona cuentas, y no se cuenta nada")
    void indicadoresProhibidos(String rol) throws Exception {
        mvc.perform(get(INDICADORES).header("Authorization", token(rol)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(ERRORES + "forbidden"));
        verifyNoInteractions(indicadores);
    }

    @Test
    @DisplayName("indicadores sin token: 403 del interceptor")
    void indicadoresSinToken() throws Exception {
        mvc.perform(get(INDICADORES)).andExpect(status().isForbidden());
        verifyNoInteractions(indicadores);
    }

    @Test
    @DisplayName("indicadores con un rango invalido: 400 datos-invalidos con el motivo")
    void indicadoresRangoInvalido() throws Exception {
        when(indicadores.calcular(anyString(), any(), anyBoolean()))
                .thenThrow(new ConsultaInvalidaException("La fecha «desde» no es válida: escríbela como aaaa-mm-dd."));

        mvc.perform(get(INDICADORES).param("desde", "ayer").header("Authorization", token("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ERRORES + "datos-invalidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("La fecha «desde» no es válida: escríbela como aaaa-mm-dd."))
                .andExpect(jsonPath("$.instance").value(INDICADORES));
    }

    // ------------------------------------------------------------- exportacion

    @Test
    @DisplayName("exportacion: un archivo CSV con su nombre, sin cache, con los filtros pedidos")
    void exportacionComoArchivo() throws Exception {
        byte[] csv = "\uFEFFApodo,Correo,Rol,Estado,Registro,Última entrada\r\n".getBytes(StandardCharsets.UTF_8);
        when(exportacion.exportar(any(FiltroDelDirectorio.class)))
                .thenReturn(new ExportacionDelDirectorio.Exportacion("directorio-de-cuentas-20261005-1500.csv", csv));

        mvc.perform(get(EXPORTACION).param("buscar", "ana").param("ocultarPruebas", "true")
                        .param("rol", "JUGADOR").param("estado", "SUSPENDIDO")
                        .param("registradoDesde", "2026-09-01").param("registradoHasta", "2026-09-30")
                        .header("Authorization", token("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment; filename=\"directorio-de-cuentas-20261005-1500.csv\"")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().bytes(csv));

        ArgumentCaptor<FiltroDelDirectorio> filtro = ArgumentCaptor.forClass(FiltroDelDirectorio.class);
        verify(exportacion).exportar(filtro.capture());
        assertThat(filtro.getValue()).isEqualTo(new FiltroDelDirectorio("ana", true, "JUGADOR", "SUSPENDIDO",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    @DisplayName("exportacion por encima del tope: 422 exportacion-demasiado-grande y ningun archivo")
    void exportacionDemasiadoGrande() throws Exception {
        when(exportacion.exportar(any(FiltroDelDirectorio.class)))
                .thenThrow(new ExportacionDemasiadoGrandeException(12000, 10000));

        mvc.perform(get(EXPORTACION).header("Authorization", token("SUPER_ADMINISTRADOR")))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ERRORES + "exportacion-demasiado-grande"))
                .andExpect(jsonPath("$.detail").value(containsString("12000")))
                .andExpect(jsonPath("$.detail").value(containsString("10000")));
    }

    @Test
    @DisplayName("exportacion con un estado que no existe: 400 y no se exporta nada")
    void exportacionConEstadoInvalido() throws Exception {
        mvc.perform(get(EXPORTACION).param("estado", "DORMIDO").header("Authorization", token("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ERRORES + "datos-invalidos"))
                .andExpect(jsonPath("$.detail").value(containsString("ACTIVO")));
        verifyNoInteractions(exportacion);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"JUGADOR", "MODERADOR"})
    @DisplayName("exportacion: 403 para quien no gestiona cuentas; los correos no salen")
    void exportacionProhibida(String rol) throws Exception {
        mvc.perform(get(EXPORTACION).header("Authorization", token(rol)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(exportacion);
    }

    // ---------------------------------------------------------------- listado

    @Test
    @DisplayName("listado con filtros: van a la consulta y la pagina sale con la forma de siempre")
    @SuppressWarnings("unchecked")
    void listadoConFiltros() throws Exception {
        when(directorio.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get(DIRECTORIO).param("rol", "moderador").param("estado", "BANEADO")
                        .param("registradoDesde", "2026-09-01").header("Authorization", token("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("listado con una fecha mal escrita: 400 que nombra el campo, no una lista vacia")
    void listadoConFechaInvalida() throws Exception {
        mvc.perform(get(DIRECTORIO).param("registradoHasta", "30/09/2026")
                        .header("Authorization", token("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ERRORES + "datos-invalidos"))
                .andExpect(jsonPath("$.detail").value(containsString("registradoHasta")));
        verifyNoInteractions(usuarioRepository, directorio);
    }
}
