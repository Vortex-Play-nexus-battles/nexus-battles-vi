package com.nexusbattles.ms_chatbot.chat.auditoria;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

// ms-chatbot.yaml 1.18.0 (observacion de RF-CHA-008): el asiento de cada
// consulta del asistente a los datos de una persona, en la bitacora central de
// ms-cumplimiento (ms-cumplimiento-auditoria.yaml 1.3.0).
class AuditoriaDeAccesosTest {

    private static final String URL = "http://srv-ms-cumplimiento:8091/api/v1/admin/auditoria/eventos";
    private static final String UID = "6f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-14T20:00:00Z"), ZoneOffset.UTC);

    // La credencial de servicio de ms-chatbot (ADR-005), con un emisor de mentira.
    private static final InterceptorDePortadorDeServicio CREDENCIAL =
        new InterceptorDePortadorDeServicio(() -> "token-de-servicio");

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @Test
    void elAsientoLlevaQuienDeQuienQueDatoYDesdeDondeConLaCredencialDeServicioYSinContenido() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer token-de-servicio"))
            .andExpect(jsonPath("$.tipoAccion").value("OTRO"))
            .andExpect(jsonPath("$.administradorId").value(UID))
            .andExpect(jsonPath("$.afectado").value(UID))
            .andExpect(jsonPath("$.valorAnterior").value(nullValue()))
            .andExpect(jsonPath("$.valorNuevo").value("dato=inventario;resultado=consultado"))
            .andExpect(jsonPath("$.motivo").value("CONSULTA_DEL_ASISTENTE"))
            .andExpect(jsonPath("$.ipOrigen").value("203.0.113.7"))
            .andExpect(jsonPath("$.ocurridoEn").value("2026-10-14T20:00:00Z"))
            .andExpect(jsonPath("$.referencia").value(matchesPattern("^ms-chatbot:consulta-[0-9a-f-]{36}$")))
            .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                .body("{}"));
        AuditoriaDeAccesos auditoria = new AuditoriaDeAccesos(constructor.requestInterceptor(CREDENCIAL).build(), URL,
            RELOJ, Runnable::run);

        auditoria.registrar(UID, DatoConsultado.INVENTARIO, ResultadoDeRespuesta.CONSULTA_EN_VIVO, " 203.0.113.7 ");

        servidor.verify();
    }

    @Test
    void loQueNoLlegoTambienSeAsientaYUnaDireccionQueFaltaOSobraSeAjusta() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(URL))
            .andExpect(jsonPath("$.valorNuevo").value("dato=subastas;resultado=no-disponible"))
            .andExpect(jsonPath("$.ipOrigen").value("DESCONOCIDA"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));
        servidor.expect(requestTo(URL))
            .andExpect(jsonPath("$.valorNuevo").value("dato=informe-de-actividad;resultado=consultado"))
            .andExpect(jsonPath("$.ipOrigen").value("x".repeat(45)))
            .andRespond(withStatus(org.springframework.http.HttpStatus.OK));
        AuditoriaDeAccesos auditoria = new AuditoriaDeAccesos(constructor.build(), URL, RELOJ, Runnable::run);

        auditoria.registrar(UID, DatoConsultado.SUBASTAS, ResultadoDeRespuesta.NO_DISPONIBLE, null);
        auditoria.registrar(UID, DatoConsultado.INFORME_DE_ACTIVIDAD, ResultadoDeRespuesta.CONSULTA_EN_VIVO,
            "x".repeat(60));

        servidor.verify();
    }

    @Test
    void sinUidNiDatoNoHayNadaQueAuditar() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        AuditoriaDeAccesos auditoria = new AuditoriaDeAccesos(constructor.build(), URL, RELOJ, Runnable::run);

        auditoria.registrar(null, DatoConsultado.INVENTARIO, ResultadoDeRespuesta.CONSULTA_EN_VIVO, "203.0.113.7");
        auditoria.registrar("  ", DatoConsultado.INVENTARIO, ResultadoDeRespuesta.CONSULTA_EN_VIVO, "203.0.113.7");
        auditoria.registrar(UID, null, ResultadoDeRespuesta.CONSULTA_EN_VIVO, "203.0.113.7");

        servidor.verify();
    }

    // Fail-open: que la bitacora este caida no impide consultar lo suyo.
    @Test
    void siLaBitacoraCentralFallaLaConsultaSigue() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(URL)).andRespond(withServerError());
        AuditoriaDeAccesos auditoria = new AuditoriaDeAccesos(constructor.build(), URL, RELOJ, Runnable::run);

        assertThatCode(() -> auditoria.registrar(UID, DatoConsultado.MISIONES, ResultadoDeRespuesta.CONSULTA_EN_VIVO,
            "203.0.113.7")).doesNotThrowAnyException();
        servidor.verify();
    }

    @Test
    void siNoSePuedeProgramarElEnvioLaConsultaSigue() {
        AuditoriaDeAccesos auditoria = new AuditoriaDeAccesos(RestClient.builder().build(), URL, RELOJ, tarea -> {
            throw new java.util.concurrent.RejectedExecutionException("sin hilos");
        });

        assertThatCode(() -> auditoria.registrar(UID, DatoConsultado.MISIONES, ResultadoDeRespuesta.CONSULTA_EN_VIVO,
            "203.0.113.7")).doesNotThrowAnyException();
    }

    // En segundo plano, con la traza de la peticion que la origino (regla 5).
    @Test
    void seEntregaEnSegundoPlanoConLaTrazaDeLaPregunta() {
        RestClient.Builder constructor = RestClient.builder().requestInterceptor(new InterceptorDeTraza());
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        String traza = "4bf92f3577b34da6a3ce929d0e0e4736";
        servidor.expect(requestTo(URL))
            .andExpect(header(FiltroDeTraza.CABECERA, matchesPattern("^00-" + traza + "-[0-9a-f]{16}-01$")))
            .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));
        AuditoriaDeAccesos auditoria = new AuditoriaDeAccesos(constructor.build(), URL, RELOJ,
            tarea -> Thread.ofVirtual().start(tarea));
        MDC.put(FiltroDeTraza.CLAVE_MDC, traza);

        auditoria.registrar(UID, DatoConsultado.NOTIFICACIONES, ResultadoDeRespuesta.CONSULTA_EN_VIVO, "203.0.113.7");

        servidor.verify(Duration.ofSeconds(5));
    }

    // Sin URL o sin credencial de servicio: solo la linea de la bitacora JSON.
    @Test
    @SuppressWarnings("unchecked")
    void sinUrlOSinCredencialSoloQuedaEnLaBitacora() {
        ObjectProvider<InterceptorDePortadorDeServicio> sinCredencial = mock(ObjectProvider.class);
        ObjectProvider<RestClient.Builder> constructores = mock(ObjectProvider.class);
        when(sinCredencial.getIfAvailable()).thenReturn(null);

        AuditoriaDeAccesos sinUrl = new AuditoriaDeAccesos(" ", sinCredencial, constructores, RELOJ);
        AuditoriaDeAccesos sinLlave = new AuditoriaDeAccesos(URL, sinCredencial, constructores, RELOJ);

        assertThatCode(() -> {
            sinUrl.registrar(UID, DatoConsultado.INVENTARIO, ResultadoDeRespuesta.CONSULTA_EN_VIVO, "203.0.113.7");
            sinLlave.registrar(UID, DatoConsultado.INVENTARIO, ResultadoDeRespuesta.CONSULTA_EN_VIVO, "203.0.113.7");
            AuditoriaDeAccesos.soloEnLaBitacora(RELOJ)
                .registrar(UID, DatoConsultado.INVENTARIO, ResultadoDeRespuesta.CONSULTA_EN_VIVO, null);
        }).doesNotThrowAnyException();
        org.mockito.Mockito.verifyNoInteractions(constructores);
    }

    @Test
    @SuppressWarnings("unchecked")
    void conUrlYCredencialUsaElConstructorDelServicio() {
        ObjectProvider<InterceptorDePortadorDeServicio> credencial = mock(ObjectProvider.class);
        ObjectProvider<RestClient.Builder> constructores = mock(ObjectProvider.class);
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(URL))
            .andExpect(header("Authorization", "Bearer token-de-servicio"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));
        when(credencial.getIfAvailable()).thenReturn(CREDENCIAL);
        when(constructores.getObject()).thenReturn(constructor);

        AuditoriaDeAccesos auditoria = new AuditoriaDeAccesos(URL, credencial, constructores, RELOJ);
        auditoria.registrar(UID, DatoConsultado.MOVIMIENTOS, ResultadoDeRespuesta.CONSULTA_EN_VIVO, "203.0.113.7");

        servidor.verify(Duration.ofSeconds(5));
    }
}
