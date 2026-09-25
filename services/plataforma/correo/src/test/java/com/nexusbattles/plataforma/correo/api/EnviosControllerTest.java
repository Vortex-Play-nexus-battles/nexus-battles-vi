package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.cola.EnvioRegistrado;
import com.nexusbattles.plataforma.correo.cola.EstadoDeEnvio;
import com.nexusbattles.plataforma.correo.cola.RepositorioDeEnvios;
import com.nexusbattles.plataforma.correo.envio.BuzonDePruebas;
import com.nexusbattles.plataforma.correo.envio.ConfiguracionDeCorreo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La evidencia de entrega (RF-COR-001): que dice y que no dice.
 *
 * <p>Las reglas de acceso de la ruta estan en {@code SecurityConfig} y se
 * prueban de punta a punta en {@code CorreoDurableIT}; aqui se prueba el
 * contenido, que es lo que se ensena en la demo.
 */
class EnviosControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T18:00:00Z");

    private final ConfiguracionDeCorreo configuracion = new ConfiguracionDeCorreo(
            "The Nexus Battles VI <no-reply@nexusbattles.test>", "", "http://x");

    private RepositorioDeEnvios repositorio;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioDeEnvios.class);
        when(repositorio.contarPorEstado()).thenReturn(Map.of());
        when(repositorio.recientes(anyInt(), any())).thenReturn(List.of());
    }

    private EnviosController controlador(BuzonDePruebas buzon, String servidor, String puerto, String autentica, String tls) {
        return new EnviosController(repositorio, configuracion, buzon, servidor, puerto, autentica, tls);
    }

    @Test
    void separaLoQueEntregoElProveedorDeLoQueSeDesvioLoQueNoSalioYLoQueEspera() {
        when(repositorio.contarPorEstado()).thenReturn(Map.of(
                EstadoDeEnvio.ENVIADO, 5L,
                EstadoDeEnvio.DESVIADO, 3L,
                EstadoDeEnvio.OMITIDO, 2L,
                EstadoDeEnvio.FALLIDO, 1L,
                EstadoDeEnvio.PENDIENTE, 4L,
                EstadoDeEnvio.ENVIANDO, 1L,
                EstadoDeEnvio.ERROR_REINTENTABLE, 2L));
        List<EnvioRegistrado> recientes = List.of(new EnvioRegistrado(
                AHORA, "j***l@gmail.com", "bienvenida", "ENVIADO", "PROVEEDOR", "<1@x>", null, 1));
        when(repositorio.recientes(25, null)).thenReturn(recientes);
        BuzonDePruebas buzon = new BuzonDePruebas("mailpit", "1025", "smtp.gmail.com", "587");

        EnviosController.EstadoDeEntrega estado =
                controlador(buzon, "smtp.gmail.com", "587", "true", "true").estado(25, null);

        assertThat(estado.servidor()).isEqualTo("smtp.gmail.com");
        assertThat(estado.puerto()).isEqualTo(587);
        assertThat(estado.autentica()).isTrue();
        assertThat(estado.tls()).isTrue();
        assertThat(estado.buzonDePruebas()).isFalse();
        assertThat(estado.desvioDePruebas()).isEqualTo("mailpit:1025");
        assertThat(estado.remitente()).isEqualTo("The Nexus Battles VI <no-reply@nexusbattles.test>");
        // Los nombres anteriores a la cola se conservan por compatibilidad.
        assertThat(estado.aceptados()).as("ENVIADO").isEqualTo(5);
        assertThat(estado.desviados()).as("DESVIADO").isEqualTo(3);
        assertThat(estado.omitidos()).as("OMITIDO").isEqualTo(2);
        assertThat(estado.rechazados()).as("FALLIDO").isEqualTo(1);
        assertThat(estado.pendientes()).as("PENDIENTE + ENVIANDO + ERROR_REINTENTABLE").isEqualTo(7);
        assertThat(estado.recientes()).isEqualTo(recientes);
    }

    @Test
    void sinNingunEnvioTodosLosContadoresSonCero() {
        EnviosController.EstadoDeEntrega estado = controlador(
                        new BuzonDePruebas("", "", "mailpit", "1025"), "mailpit", "1025", "false", "false")
                .estado(25, null);

        assertThat(estado.aceptados()).isZero();
        assertThat(estado.desviados()).isZero();
        assertThat(estado.rechazados()).isZero();
        assertThat(estado.omitidos()).isZero();
        assertThat(estado.pendientes()).isZero();
        assertThat(estado.recientes()).isEmpty();
    }

    @Test
    void contraMailpitLoDiceSinRodeos() {
        EnviosController.EstadoDeEntrega estado = controlador(
                        new BuzonDePruebas("", "", "mailpit", "1025"), "mailpit", "1025", "false", "false")
                .estado(25, null);

        assertThat(estado.buzonDePruebas()).isTrue();
        assertThat(estado.desvioDePruebas()).isEqualTo("mailpit:1025");
        assertThat(estado.autentica()).isFalse();
    }

    @Test
    void conLasVariablesVaciasDelEnvExampleArrancaYNoInventaValores() {
        // SMTP_PORT=, SMTP_AUTENTICA=, SMTP_TLS= llegan como cadenas vacias.
        EnviosController.EstadoDeEntrega estado =
                controlador(new BuzonDePruebas("", "", "", ""), null, "", "", null).estado(25, null);

        assertThat(estado.servidor()).isEmpty();
        assertThat(estado.puerto()).isZero();
        assertThat(estado.autentica()).isFalse();
        assertThat(estado.tls()).isFalse();
        assertThat(estado.desvioDePruebas()).isEmpty();
    }

    @ParameterizedTest(name = "ultimos={0} -> {1}")
    @CsvSource({"1000, 100", "100, 100", "25, 25", "1, 1", "0, 1", "-7, 1"})
    void elNumeroDeRecientesSeAjustaAlRangoDelContrato(int pedidos, int esperados) {
        controlador(new BuzonDePruebas("", "", "smtp.gmail.com", "587"), "smtp.gmail.com", "587", "true", "true")
                .estado(pedidos, null);

        verify(repositorio).recientes(eq(esperados), isNull());
    }

    @Test
    void elFiltroPorEstadoLlegaALaConsulta() {
        controlador(new BuzonDePruebas("", "", "smtp.gmail.com", "587"), "smtp.gmail.com", "587", "true", "true")
                .estado(10, EstadoDeEnvio.ERROR_REINTENTABLE);

        verify(repositorio).recientes(10, EstadoDeEnvio.ERROR_REINTENTABLE);
    }
}
