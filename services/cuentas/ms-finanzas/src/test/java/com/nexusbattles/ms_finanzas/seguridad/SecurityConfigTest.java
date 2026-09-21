package com.nexusbattles.ms_finanzas.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusbattles.ms_finanzas.transacciones.HistorialTransaccionesController;
import com.nexusbattles.ms_finanzas.transacciones.ResultadoTransaccion;
import com.nexusbattles.ms_finanzas.transacciones.ResumenTransaccion;
import com.nexusbattles.ms_finanzas.transacciones.TransaccionConsultaService;

/**
 * Verifica el {@link SecurityConfig} contra rutas reales:
 * {@code /actuator/**} público, {@code /creditos/**} abierto temporalmente
 * (ver el javadoc del SecurityConfig para el porqué),
 * {@code /transacciones/**} exige JWT.
 *
 * <p>Sigue el mismo patrón que {@code SeguridadListaNegraTest} de
 * moderacion-sanciones: {@link WebMvcTest} + {@code @Import(SecurityConfig)}
 * para cargar solo el controller y la cadena de seguridad, sin arrancar el
 * contexto completo.
 *
 * <p><b>Nota sobre {@code .with(jwt())}:</b> spring-security-test simula el
 * usuario ya autenticado y NO ejecuta el {@code ConversorRolesJwt} real —
 * por eso el {@code principal.getName()} devuelve el {@code subject} del
 * token, no el claim {@code uid}. En este test se pasa el {@code uid} como
 * subject a propósito. La lógica del conversor tiene sus tests aparte en
 * {@code ConversorRolesJwtTest} de plataforma-seguridad.
 */
@WebMvcTest(controllers = HistorialTransaccionesController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransaccionConsultaService consultaService;

    private ResumenTransaccion resumen() {
        return new ResumenTransaccion(
                UUID.randomUUID(), "ref-1", new BigDecimal("100.00"),
                "COP", "compra", ResultadoTransaccion.APROBADO, null,
                Instant.parse("2026-09-18T10:00:00Z"));
    }

    @Test
    void creditosEstaAbiertoTemporalmente() throws Exception {
        // La ruta no existe en este @WebMvcTest (solo cargó el
        // HistorialTransaccionesController), así que Spring devuelve 404
        // en vez de 401. Lo importante es que NO devuelve 401: el filter
        // chain deja pasar sin JWT — cuando cambie a authenticated() en
        // el futuro, este test empezará a devolver 401 y se ajustará.
        mockMvc.perform(get("/creditos/no-importa"))
                .andExpect(status().isNotFound());
    }

    @Test
    void transaccionesSinJwt_devuelve401() throws Exception {
        mockMvc.perform(get("/transacciones/mi-historial"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void transaccionesConJwt_devuelve200YUsaSubjectComoPrincipal() throws Exception {
        String uid = UUID.randomUUID().toString();
        Pageable esperado = PageRequest.of(0, 20);
        Page<ResumenTransaccion> pagina = new PageImpl<>(List.of(resumen()), esperado, 1);
        org.mockito.Mockito.when(consultaService.listarPorUsuario(
                        org.mockito.ArgumentMatchers.eq(uid),
                        org.mockito.ArgumentMatchers.eq(esperado)))
                .thenReturn(pagina);

        // subject = uid porque .with(jwt()) no ejecuta ConversorRolesJwt.
        mockMvc.perform(get("/transacciones/mi-historial")
                        .with(jwt().jwt(builder -> builder
                                .subject(uid)
                                .claim("rol", "JUGADOR"))))
                .andExpect(status().isOk());
    }
}
