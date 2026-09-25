package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Aceptar un correo es guardarlo, con su clave de idempotencia y su traza. */
class ColaDeCorreosTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final CorreoPedido RECUPERACION = CorreoPedido.paraEnviar(
            Plantilla.RECUPERACION_CLAVE,
            "jugador@ejemplo.com",
            "Recupera tu contraseña de The Nexus Battles VI",
            Map.of("apodo", "ElGuerrero", "codigo", "482915", "minutosVigencia", 15));

    private RepositorioDeEnvios repositorio;
    private MetricasDeCorreo metricas;
    private ColaDeCorreos cola;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioDeEnvios.class);
        metricas = mock(MetricasDeCorreo.class);
        when(repositorio.insertar(any())).thenReturn(true);
        cola = new ColaDeCorreos(repositorio, metricas, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @AfterEach
    void limpiarTraza() {
        MDC.remove(FiltroDeTraza.CLAVE_MDC);
    }

    private NuevoEnvio guardado() {
        ArgumentCaptor<NuevoEnvio> envio = ArgumentCaptor.forClass(NuevoEnvio.class);
        verify(repositorio).insertar(envio.capture());
        return envio.getValue();
    }

    @Test
    void unCorreoAceptadoQuedaPendienteConTodoLoQueHaceFaltaParaEntregarlo() {
        assertThat(cola.encolar(RECUPERACION, "clave-1", null)).isTrue();

        NuevoEnvio envio = guardado();
        assertThat(envio.id()).isNotNull();
        assertThat(envio.plantilla()).isEqualTo("recuperacion-clave");
        assertThat(envio.destinatario()).isEqualTo("jugador@ejemplo.com");
        assertThat(envio.asunto()).isEqualTo("Recupera tu contraseña de The Nexus Battles VI");
        assertThat(envio.datos()).containsEntry("codigo", "482915");
        assertThat(envio.estado()).isEqualTo(EstadoDeEnvio.PENDIENTE);
        assertThat(envio.motivo()).isNull();
        assertThat(envio.claveDeIdempotencia()).isEqualTo("clave-1");
        assertThat(envio.creadoEn()).isEqualTo(AHORA);
        verify(metricas).registrar(EstadoDeEnvio.PENDIENTE, "recuperacion-clave");
    }

    @Test
    void laMismaClaveDosVecesNoEncolaOtroNiSuma() {
        when(repositorio.insertar(any())).thenReturn(false);

        assertThat(cola.encolar(RECUPERACION, "clave-1", null)).isFalse();

        verify(metricas, never()).registrar(any(), anyString());
    }

    @Test
    void unaClaveEnBlancoCuentaComoSinClave() {
        cola.encolar(RECUPERACION, "   ", null);

        assertThat(guardado().claveDeIdempotencia()).isNull();
    }

    @Test
    void laClaveSeGuardaSinEspaciosAlrededor() {
        cola.encolar(RECUPERACION, "  clave-2  ", null);

        assertThat(guardado().claveDeIdempotencia()).isEqualTo("clave-2");
    }

    @Test
    void unaClaveDeMasDe120CaracteresNoSeGuarda() {
        // El controlador ya responde 400; esto es la segunda linea, por si
        // alguien llama a la cola sin pasar por el.
        assertThatThrownBy(() -> cola.encolar(RECUPERACION, "k".repeat(121), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("120");
        verifyNoInteractions(repositorio);
    }

    @Test
    void unCorreoQueNoHayQueEnviarSeGuardaComoOmitidoYSinDatos() {
        CorreoPedido suprimido = CorreoPedido.suprimido(
                Plantilla.MISION, "jugador@ejemplo.com", "Nueva misión", "el jugador no quiere correos");

        cola.encolar(suprimido, null, null);

        NuevoEnvio envio = guardado();
        assertThat(envio.estado()).isEqualTo(EstadoDeEnvio.OMITIDO);
        assertThat(envio.motivo()).isEqualTo("el jugador no quiere correos");
        assertThat(envio.datos()).isEmpty();
        verify(metricas).registrar(EstadoDeEnvio.OMITIDO, "mision");
    }

    @Test
    void elXTraceIdRecibidoMandaSobreLaTrazaDeLaPeticion() {
        MDC.put(FiltroDeTraza.CLAVE_MDC, "traza-del-traceparent");

        cola.encolar(RECUPERACION, null, "traza-del-productor");

        assertThat(guardado().trazaId()).isEqualTo("traza-del-productor");
    }

    @Test
    void sinXTraceIdSeGuardaLaTrazaDeLaPeticion() {
        MDC.put(FiltroDeTraza.CLAVE_MDC, "4bf92f3577b34da6a3ce929d0e0e4736");

        cola.encolar(RECUPERACION, null, null);

        assertThat(guardado().trazaId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void unXTraceIdConCaracteresRarosSeIgnora() {
        // Un identificador de traza no puede ser una via para meter texto
        // arbitrario (saltos de linea, JSON) en la bitacora.
        MDC.put(FiltroDeTraza.CLAVE_MDC, "traza-buena");

        cola.encolar(RECUPERACION, null, "linea\n{\"inyectada\":true}");

        assertThat(guardado().trazaId()).isEqualTo("traza-buena");
    }

    @Test
    void sinNingunaTrazaSeGuardaNula() {
        cola.encolar(RECUPERACION, null, " ");

        assertThat(guardado().trazaId()).isNull();
    }
}
