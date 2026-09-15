package com.nexusbattles.ms_finanzas.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class SecurityInterceptorTest {

    private static final String CLAVE = "clave-de-pruebas-que-debe-tener-al-menos-32-bytes-para-hmac-sha256";
    private static final String OTRA_CLAVE = "una-clave-distinta-igualmente-de-32-bytes-para-forzar-firma-mala!";

    private JwtService jwtService;
    private SecurityInterceptor conRespaldoDev;
    private SecurityInterceptor sinRespaldoDev;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(CLAVE);
        conRespaldoDev = new SecurityInterceptor(jwtService, true);
        sinRespaldoDev = new SecurityInterceptor(jwtService, false);
    }

    /** Controlador de prueba para poder recuperar HandlerMethods con y sin la anotación. */
    @RequireAutenticacion
    static class ControladorProtegido {
        public void metodoProtegido() { }
    }

    static class ControladorAbierto {
        public void metodoAbierto() { }
    }

    private HandlerMethod handlerDe(Class<?> tipo, String metodo) throws Exception {
        Method m = tipo.getDeclaredMethod(metodo);
        java.lang.reflect.Constructor<?> ctor = tipo.getDeclaredConstructor();
        ctor.setAccessible(true);
        return new HandlerMethod(ctor.newInstance(), m);
    }

    private String tokenValido(String uid, String rol) {
        SecretKey clave = Keys.hmacShaKeyFor(CLAVE.getBytes(StandardCharsets.UTF_8));
        Date ahora = new Date();
        return Jwts.builder()
                .subject("jugador-1")
                .claim("uid", uid)
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + 60_000))
                .signWith(clave)
                .compact();
    }

    private String tokenFirmadoConOtraClave() {
        SecretKey otra = Keys.hmacShaKeyFor(OTRA_CLAVE.getBytes(StandardCharsets.UTF_8));
        Date ahora = new Date();
        return Jwts.builder()
                .subject("jugador-2")
                .claim("uid", UUID.randomUUID().toString())
                .claim("rol", "JUGADOR")
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + 60_000))
                .signWith(otra)
                .compact();
    }

    @Test
    void requestOPTIONS_pasa() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/transacciones/mi-historial");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(conRespaldoDev.preHandle(req, res, handlerDe(ControladorProtegido.class, "metodoProtegido")))
                .isTrue();
    }

    @Test
    void handlerNoEsHandlerMethod_pasa() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/estatico/logo.png");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(conRespaldoDev.preHandle(req, res, "no-es-un-HandlerMethod")).isTrue();
    }

    @Test
    void handlerSinAnnotation_pasa() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/publico");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(conRespaldoDev.preHandle(req, res, handlerDe(ControladorAbierto.class, "metodoAbierto")))
                .isTrue();
    }

    @Test
    void jwtValido_pasaYExponeAtributos() throws Exception {
        String uid = UUID.randomUUID().toString();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/transacciones/mi-historial");
        req.addHeader("Authorization", "Bearer " + tokenValido(uid, "JUGADOR"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean pasa = conRespaldoDev.preHandle(req, res, handlerDe(ControladorProtegido.class, "metodoProtegido"));

        assertThat(pasa).isTrue();
        assertThat(req.getAttribute(SecurityInterceptor.ATTR_UID)).isEqualTo(uid);
        assertThat(req.getAttribute(SecurityInterceptor.ATTR_ROL)).isEqualTo("JUGADOR");
        assertThat(req.getAttribute(SecurityInterceptor.ATTR_APODO)).isEqualTo("jugador-1");
    }

    @Test
    void jwtConFirmaInvalida_devuelve403ProblemJson() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/transacciones/mi-historial");
        req.addHeader("Authorization", "Bearer " + tokenFirmadoConOtraClave());
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean pasa = conRespaldoDev.preHandle(req, res, handlerDe(ControladorProtegido.class, "metodoProtegido"));

        assertThat(pasa).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentType()).contains("application/problem+json");
        assertThat(res.getContentAsString()).contains("Acceso denegado");
    }

    @Test
    void jwtSinClaimUid_devuelve403() throws Exception {
        SecretKey clave = Keys.hmacShaKeyFor(CLAVE.getBytes(StandardCharsets.UTF_8));
        String tokenSinUid = Jwts.builder()
                .subject("jugador-1")
                .claim("rol", "JUGADOR")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(clave)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/transacciones/mi-historial");
        req.addHeader("Authorization", "Bearer " + tokenSinUid);
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean pasa = conRespaldoDev.preHandle(req, res, handlerDe(ControladorProtegido.class, "metodoProtegido"));

        assertThat(pasa).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void sinCredencial_devuelve403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/transacciones/mi-historial");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean pasa = conRespaldoDev.preHandle(req, res, handlerDe(ControladorProtegido.class, "metodoProtegido"));

        assertThat(pasa).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void headersRespaldoDevConFlagActivo_pasaYExponeAtributos() throws Exception {
        String uid = UUID.randomUUID().toString();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/transacciones/mi-historial");
        req.addHeader("X-User-Uid", uid);
        req.addHeader("X-User-Role", "JUGADOR");
        req.addHeader("X-User-Name", "jugador-dev");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean pasa = conRespaldoDev.preHandle(req, res, handlerDe(ControladorProtegido.class, "metodoProtegido"));

        assertThat(pasa).isTrue();
        assertThat(req.getAttribute(SecurityInterceptor.ATTR_UID)).isEqualTo(uid);
        assertThat(req.getAttribute(SecurityInterceptor.ATTR_ROL)).isEqualTo("JUGADOR");
        assertThat(req.getAttribute(SecurityInterceptor.ATTR_APODO)).isEqualTo("jugador-dev");
    }

    @Test
    void headersRespaldoDevConFlagInactivo_devuelve403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/transacciones/mi-historial");
        req.addHeader("X-User-Uid", UUID.randomUUID().toString());
        req.addHeader("X-User-Role", "JUGADOR");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean pasa = sinRespaldoDev.preHandle(req, res, handlerDe(ControladorProtegido.class, "metodoProtegido"));

        assertThat(pasa).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("desarrollo");
    }
}
