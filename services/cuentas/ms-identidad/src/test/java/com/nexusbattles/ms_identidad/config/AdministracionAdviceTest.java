package com.nexusbattles.ms_identidad.config;

import com.nexusbattles.ms_identidad.admin.service.JerarquiaInsuficienteException;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaNoDisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los dos rechazos del panel de administracion salen como problem details
 * (regla 4), con el tipo estable que la consola discrimina.
 */
@DisplayName("AdministracionAdvice: 403 jerarquia-insuficiente y 503 auditoria-no-disponible")
class AdministracionAdviceTest {

    private final AdministracionAdvice advice = new AdministracionAdvice();

    private static MockHttpServletRequest peticion(String ruta) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("PUT", ruta);
        peticion.setRequestURI(ruta);
        return peticion;
    }

    @Test
    @DisplayName("7.3.1: actuar sobre un rol igual o superior es 403 con su tipo, su detalle y la ruta")
    void jerarquia() {
        ResponseEntity<ProblemDetail> respuesta = advice.jerarquia(
                new JerarquiaInsuficienteException("No puedes suspender a una cuenta con un rol igual o superior al tuyo."),
                peticion("/api/v1/admin/usuarios/9/suspender"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
        ProblemDetail problema = respuesta.getBody();
        assertThat(problema).isNotNull();
        assertThat(problema.getType()).isEqualTo(AdministracionAdvice.TIPO_JERARQUIA);
        assertThat(problema.getTitle()).isEqualTo("Rol insuficiente sobre esta cuenta");
        assertThat(problema.getDetail()).contains("igual o superior");
        assertThat(problema.getInstance()).isEqualTo(URI.create("/api/v1/admin/usuarios/9/suspender"));
    }

    @Test
    @DisplayName("7.3.5: sin auditoria no se aplica nada: 503 con Retry-After de 30 segundos")
    void auditoria() {
        ResponseEntity<ProblemDetail> respuesta = advice.auditoria(
                new AuditoriaNoDisponibleException("No se aplicó el cambio: el registro de auditoría no respondió."),
                peticion("/api/v1/admin/usuarios/9/banear"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .isEqualTo(String.valueOf(AuditoriaNoDisponibleException.REINTENTAR_EN_SEGUNDOS));
        ProblemDetail problema = respuesta.getBody();
        assertThat(problema).isNotNull();
        assertThat(problema.getType()).isEqualTo(AdministracionAdvice.TIPO_AUDITORIA);
        assertThat(problema.getTitle()).isEqualTo("Auditoría no disponible");
        assertThat(problema.getDetail()).contains("no respondió");
    }

    @Test
    @DisplayName("sin ruta conocida el problema sale igual, sin instance")
    void sinRuta() {
        ResponseEntity<ProblemDetail> respuesta = AdministracionAdvice.problema(HttpStatus.FORBIDDEN,
                AdministracionAdvice.TIPO_JERARQUIA, "Rol insuficiente sobre esta cuenta", "detalle", null, null);

        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getInstance()).isNull();
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
    }
}
