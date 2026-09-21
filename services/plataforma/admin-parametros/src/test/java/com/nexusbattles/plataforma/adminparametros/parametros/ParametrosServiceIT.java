package com.nexusbattles.plataforma.adminparametros.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/** El catalogo de V1 contra PostgreSQL real y los casos de uso sobre el. */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@DisplayName("Parametros · casos de uso (HU-ADM-001)")
class ParametrosServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final Actor ADMIN = new Actor(UUID.randomUUID(), "ADMINISTRADOR");
    private static final Actor JUGADORA = new Actor(UUID.randomUUID(), "JUGADOR");
    private static final Actor SERVICIO = new Actor(null, "SERVICIO");

    @Autowired
    private ParametrosService servicio;

    @MockitoBean
    private Auditoria auditoria;

    @Test
    @DisplayName("el catalogo trae los editables de las decisiones y los inalterables del Charter, en orden")
    void catalogo() {
        List<ParametrosService.Vigente> todos = servicio.listar();
        assertThat(todos).hasSizeGreaterThanOrEqualTo(20);
        assertThat(todos.get(0).parametro().clave()).isEqualTo("sanciones.suspension.minima-horas");
        ParametrosService.Vigente dias = servicio.obtener("torneos.dias-entre-torneos");
        assertThat(dias.parametro().inalterable()).isTrue();
        assertThat(dias.valor()).isEqualTo("91");
        assertThat(servicio.obtener("metricas.umbral-sanciones-por-dia").valor()).as("sin decision del PO").isNull();
        assertThat(servicio.obtener("salas.apuestas.si-gana-la-maquina").parametro().opcionesComoLista())
                .containsExactly("LIBERAR", "CONSUMIR");
        assertThatThrownBy(() -> servicio.obtener("no.existe"))
                .isInstanceOfSatisfying(ParametroRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(ParametroRechazado.Motivo.NO_ENCONTRADO));
    }

    @Test
    @DisplayName("solo administracion cambia; con motivo; validado; versionado; auditado (CA-01, CA-02)")
    void cambiar() {
        String clave = "sanciones.suspension.maxima-dias";
        assertThatThrownBy(() -> servicio.cambiar(JUGADORA, clave, new ParametrosService.Cambio("10", "porque si", null)))
                .isInstanceOfSatisfying(ParametroRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(ParametroRechazado.Motivo.PERMISO_INSUFICIENTE));
        assertThatThrownBy(() -> servicio.cambiar(SERVICIO, clave, new ParametrosService.Cambio("10", "porque si", null)))
                .isInstanceOfSatisfying(ParametroRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(ParametroRechazado.Motivo.PERMISO_INSUFICIENTE));
        assertThatThrownBy(() -> servicio.cambiar(ADMIN, clave, new ParametrosService.Cambio("10", "", null)))
                .isInstanceOfSatisfying(ParametroRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(ParametroRechazado.Motivo.SOLICITUD_INVALIDA));
        assertThatThrownBy(() -> servicio.cambiar(ADMIN, clave, new ParametrosService.Cambio("400", "muy largo", null)))
                .isInstanceOfSatisfying(ParametroRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(ParametroRechazado.Motivo.VALOR_INVALIDO));
        assertThatThrownBy(() -> servicio.cambiar(ADMIN, "torneos.cupos", new ParametrosService.Cambio("16", "mas equipos", null)))
                .isInstanceOfSatisfying(ParametroRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(ParametroRechazado.Motivo.INALTERABLE));

        ParametrosService.Vigente cambiado = servicio.cambiar(ADMIN, clave, new ParametrosService.Cambio("15", "Acuerdo de la Sprint Review", null));
        assertThat(cambiado.valor()).isEqualTo("15");
        assertThat(cambiado.parametro().version()).isEqualTo(2);
        assertThat(cambiado.parametro().actualizadoPor()).isEqualTo(ADMIN.id());
        verify(auditoria).registrar(any());

        List<Version> historial = servicio.historial(ADMIN, clave);
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).valorAnterior()).isEqualTo("30");
        assertThat(historial.get(0).valorNuevo()).isEqualTo("15");
        assertThat(historial.get(0).motivo()).isEqualTo("Acuerdo de la Sprint Review");
        assertThatThrownBy(() -> servicio.historial(JUGADORA, clave))
                .isInstanceOfSatisfying(ParametroRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(ParametroRechazado.Motivo.PERMISO_INSUFICIENTE));
    }

    @Test
    @DisplayName("un cambio con vigencia futura queda programado y el valor vigente no cambia todavia (CA-03)")
    void programado() {
        String clave = "chat.historial.tamano";
        OffsetDateTime futuro = OffsetDateTime.now().plusDays(3);
        ParametrosService.Vigente v = servicio.cambiar(ADMIN, clave, new ParametrosService.Cambio("100", "Mas historial desde el lunes", futuro));
        assertThat(v.valor()).isEqualTo("50");
        assertThat(v.parametro().valorProgramado()).isEqualTo("100");
        assertThat(v.parametro().vigenteDesde()).isNotNull();
        assertThat(servicio.obtener(clave).valor()).isEqualTo("50");
        assertThat(servicio.historial(ADMIN, clave).get(0).vigenteDesde().toInstant().getEpochSecond())
                .isEqualTo(futuro.toInstant().getEpochSecond());

        // Vaciar una decision: valor null en un parametro que lo admite.
        ParametrosService.Vigente vacio = servicio.cambiar(ADMIN, "chat.mensajes-por-minuto", new ParametrosService.Cambio("20", "limite", null));
        assertThat(vacio.valor()).isEqualTo("20");
        assertThat(servicio.cambiar(ADMIN, "chat.mensajes-por-minuto", new ParametrosService.Cambio(null, "sin limite otra vez", null)).valor()).isNull();
    }
}
