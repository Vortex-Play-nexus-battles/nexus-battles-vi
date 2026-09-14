package com.nexusbattles.ms_subastas.subastas.api;

import com.nexusbattles.ms_subastas.seguridad.*;
import com.nexusbattles.ms_subastas.subastas.service.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class PublicacionSubastaControllerTest {
    private static final String SECRET = "clave-secreta-de-prueba-con-mas-de-32-caracteres";
    private final PublicarSubastaApplicationService servicio = mock(PublicarSubastaApplicationService.class);
    private final ObjectProvider<PublicarSubastaApplicationService> provider = mock(ObjectProvider.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        when(provider.getIfAvailable()).thenReturn(servicio);
        var identidad = new IdentidadDesdeToken(new ValidadorDeToken(SECRET, Clock.systemUTC()), request);
        mvc = MockMvcBuilders.standaloneSetup(new PublicacionSubastaController(provider, identidad))
                .setControllerAdvice(new ManejadorDeErroresPublicacion()).build();
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
        request.addHeader("Authorization", token(vendedor.toString(), false));
        String esperado = """
                {"id":"%s","productoId":"%s","elementoInventarioId":"unidad","vendedorId":"%s",
                 "precioInicial":10,"ofertaVigente":10,"precioCompraInmediata":20,"comisionCobrado":%s,
                 "estado":"ACTIVA","fechaPublicacion":"%s","fechaFin":"%s","nombreProducto":"Espada",
                 "tipoProducto":"ARMA","rareza":"RARA","miniaturaUrl":"https://catalogo/espada.png",
                 "descripcionCorta":"Espada de hielo","habilidades":"Congelar"}
                """.formatted(id, producto, vendedor, comision, inicio, fin);
        mvc.perform(post("/api/v1/subastas").header("Idempotency-Key", "k").contentType("application/json")
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
        request.addHeader("Authorization", token(UUID.randomUUID().toString(), false));
        when(servicio.publicar(any(), any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("otra causa"));
        mvc.perform(post("/api/v1/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body("24H"))).andExpect(status().isInternalServerError());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "invalido", "sin-uid", "uid-invalido", "uid-numerico", "expirado"})
    void jwtFailClosed(String caso) throws Exception {
        String token = switch (caso) {
            case "ausente" -> null;
            case "invalido" -> "Bearer invalido";
            case "sin-uid" -> token(null, false);
            case "uid-invalido" -> token("no-es-uuid", false);
            case "uid-numerico" -> "Bearer " + Jwts.builder().subject("apodo").claim("rol", "JUGADOR")
                    .claim("ver", 1).claim("uid", 123).expiration(Date.from(Instant.now().plusSeconds(300)))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
            default -> token(UUID.randomUUID().toString(), true);
        };
        if (token != null) request.addHeader("Authorization", token);
        mvc.perform(post("/api/v1/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body("24H"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void sinIntegracionesDevuelve503() throws Exception {
        request.addHeader("Authorization", token(UUID.randomUUID().toString(), false));
        when(provider.getIfAvailable()).thenReturn(null);
        mvc.perform(post("/api/v1/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body("24H"))).andExpect(status().isServiceUnavailable());
    }

    @Test
    void cabeceraObligatoria() throws Exception {
        mvc.perform(post("/api/v1/subastas").contentType("application/json").content(body("24H")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void propiedadAdicionalNoPuedeSuplantarVendedor() throws Exception {
        var json = body("24H").replace("\"precioInicial\":10", "\"precioInicial\":10,\"vendedorId\":\"otro\"");
        mvc.perform(post("/api/v1/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(json)).andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "1234567890"})
    void validaLongitudDeClave(String clave) throws Exception {
        request.addHeader("Authorization", token(UUID.randomUUID().toString(), false));
        if (!clave.isBlank()) clave = clave.repeat(11);
        mvc.perform(post("/api/v1/subastas").header("Idempotency-Key", clave).contentType("application/json")
                .content(body("24H"))).andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(PublicacionSubastaException.Motivo.class)
    void traduceErroresDeNegocio(PublicacionSubastaException.Motivo motivo) throws Exception {
        request.addHeader("Authorization", token(UUID.randomUUID().toString(), false));
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
        mvc.perform(post("/api/v1/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body("24H"))).andExpect(status().is(esperado));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "H24", "72H"})
    void duracionInvalida(String duracion) throws Exception {
        mvc.perform(post("/api/v1/subastas").header("Idempotency-Key", "k").contentType("application/json")
                .content(body(duracion))).andExpect(status().isBadRequest());
    }

    private String body(String duracion) {
        return "{\"elementoInventarioId\":\"unidad\",\"productoId\":\"" + UUID.randomUUID()
                + "\",\"duracion\":\"" + duracion + "\",\"precioInicial\":10}";
    }

    private String token(String uid, boolean expirado) {
        var jwt = Jwts.builder().subject("apodo").claim("rol", "JUGADOR").claim("ver", 1)
                .expiration(Date.from(Instant.now().plusSeconds(expirado ? -60 : 300)));
        if (uid != null) jwt.claim("uid", uid);
        return "Bearer " + jwt.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
