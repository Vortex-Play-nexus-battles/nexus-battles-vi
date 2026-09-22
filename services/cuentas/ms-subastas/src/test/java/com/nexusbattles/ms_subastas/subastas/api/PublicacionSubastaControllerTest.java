package com.nexusbattles.ms_subastas.subastas.api;

import com.nexusbattles.ms_subastas.seguridad.*;
import com.nexusbattles.ms_subastas.subastas.service.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class PublicacionSubastaControllerTest {
    private final PublicarSubastaApplicationService servicio = mock(PublicarSubastaApplicationService.class);
    private final ObjectProvider<PublicarSubastaApplicationService> provider = mock(ObjectProvider.class);
    /**
     * Sin /api/v1 en las rutas: standaloneSetup no aplica
     * server.servlet.context-path, asi que aqui se pide la ruta tal y como la
     * declara el controlador. Escribirla con el prefijo hacia que esta prueba
     * pasara en verde mientras el endpoint real vivia en /api/v1/api/v1/subastas.
     * Que la ruta del contrato resuelve de verdad lo comprueba RutasPublicadasIT,
     * que si levanta el servidor.
     */
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        when(provider.getIfAvailable()).thenReturn(servicio);
        // La cadena de seguridad (firma, caducidad, emisor) se prueba en
        // SeguridadWebConfigTest; aqui el token ya viene validado en el contexto
        // y lo que se prueba es lo que el controlador hace con el.
        mvc = MockMvcBuilders.standaloneSetup(new PublicacionSubastaController(provider, new IdentidadDesdeToken()))
                .setControllerAdvice(new ManejadorDeErroresPublicacion()).build();
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"24H", "48H"})
    void aceptaDuracionesDelContrato(String duracion) throws Exception {
        var vendedor = UUID.randomUUID();
        var id = UUID.randomUUID();
        var producto = UUID.randomUUID();
        var inicio = Instant.parse("2026-09-13T12:00:00Z");
        var fin = inicio.plusSeconds(duracion.equals("24H") ? 86400 : 172800);
        var comision = new java.math.BigDecimal(duracion.equals("24H") ? "1" : "3");
        var respuesta = new com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse(
                id, producto, "unidad", vendedor, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN,
                new java.math.BigDecimal("20"), "ACTIVA", inicio, fin, comision,
                "Espada", "ARMA", "RARA", "https://catalogo/espada.png", "Espada de hielo", "Congelar");
        when(servicio.publicar(any(), eq("k"))).thenReturn(respuesta);
        autenticadoCon(vendedor.toString());
        String esperado = """
                {"id":"%s","productoId":"%s","elementoInventarioId":"unidad","vendedorId":"%s",
                 "precioInicial":10,"ofertaVigente":10,"precioCompraInmediata":20,"comisionCobrado":%s,
                 "estado":"ACTIVA","fechaPublicacion":"%s","fechaFin":"%s","nombreProducto":"Espada",
                 "tipoProducto":"ARMA","rareza":"RARA","miniaturaUrl":"https://catalogo/espada.png",
                 "descripcionCorta":"Espada de hielo","habilidades":"Congelar"}
                """.formatted(id, producto, vendedor, comision, inicio, fin);
        mvc.perform(post("/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content("""
                        {"elementoInventarioId":"unidad","productoId":"%s","duracion":"%s",
                         "precioInicial":10,"precioCompraInmediata":20}
                        """.formatted(producto, duracion))).andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json(esperado, org.springframework.test.json.JsonCompareMode.STRICT));
        verify(servicio).publicar(any(), eq("k"));
    }

    @Test
    void integridadNoFuncionalEs500() throws Exception {
        autenticadoCon(UUID.randomUUID().toString());
        when(servicio.publicar(any(), any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("otra causa"));
        mvc.perform(post("/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body("24H"))).andExpect(status().isInternalServerError());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "sin-uid", "uid-invalido", "uid-numerico"})
    void jwtFailClosed(String caso) throws Exception {
        switch (caso) {
            case "ausente" -> SecurityContextHolder.clearContext();
            case "sin-uid" -> autenticadoCon(null);
            case "uid-invalido" -> autenticadoCon("no-es-uuid");
            default -> autenticadoCon(123);
        }
        mvc.perform(post("/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body("24H"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void sinIntegracionesDevuelve503() throws Exception {
        autenticadoCon(UUID.randomUUID().toString());
        when(provider.getIfAvailable()).thenReturn(null);
        mvc.perform(post("/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body("24H"))).andExpect(status().isServiceUnavailable());
    }

    @Test
    void cabeceraObligatoria() throws Exception {
        mvc.perform(post("/subastas").contentType("application/json").content(body("24H")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void propiedadAdicionalNoPuedeSuplantarVendedor() throws Exception {
        var json = body("24H").replace("\"precioInicial\":10", "\"precioInicial\":10,\"vendedorId\":\"otro\"");
        mvc.perform(post("/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(json)).andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "1234567890"})
    void validaLongitudDeClave(String clave) throws Exception {
        autenticadoCon(UUID.randomUUID().toString());
        if (!clave.isBlank()) clave = clave.repeat(11);
        mvc.perform(post("/subastas").header("Idempotency-Key", clave).contentType("application/json")
                .content(body("24H"))).andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(PublicacionSubastaException.Motivo.class)
    void traduceErroresDeNegocio(PublicacionSubastaException.Motivo motivo) throws Exception {
        autenticadoCon(UUID.randomUUID().toString());
        when(servicio.publicar(any(), any())).thenThrow(new PublicacionSubastaException(motivo, "rechazo"));
        int esperado = switch (motivo) {
            case SOLICITUD_INVALIDA -> 400;
            case NO_AUTENTICADO -> 401;
            case PROHIBIDO -> 403;
            case NO_ENCONTRADO -> 404;
            case CONFLICTO -> 409;
            case REGLA_NEGOCIO -> 422;
            case DEPENDENCIA_NO_DISPONIBLE -> 503;
        };
        mvc.perform(post("/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body("24H"))).andExpect(status().is(esperado));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "H24", "72H"})
    void duracionInvalida(String duracion) throws Exception {
        mvc.perform(post("/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body(duracion))).andExpect(status().isBadRequest());
    }

    private String body(String duracion) {
        return "{\"elementoInventarioId\":\"unidad\",\"productoId\":\"" + UUID.randomUUID()
                + "\",\"duracion\":\"" + duracion + "\",\"precioInicial\":10}";
    }

    /** Deja en el contexto un token de jugador ya validado, con el uid indicado (o sin el). */
    private static void autenticadoCon(Object uid) {
        var jwt = Jwt.withTokenValue("validado-por-la-cadena").header("alg", "RS256")
                .subject("apodo").claim("rol", "JUGADOR").claim("ver", 1)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (uid != null) jwt.claim("uid", uid);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority("ROLE_JUGADOR"))));
    }
}
