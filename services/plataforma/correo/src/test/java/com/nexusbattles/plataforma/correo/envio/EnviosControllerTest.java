package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * La evidencia de entrega (RF-COR-001): que dice y que no dice.
 *
 * <p>Las reglas de acceso de la ruta estan en {@code SecurityConfig}; aqui se
 * prueba el contenido, que es lo que se ensena en la demo.
 */
class EnviosControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T18:00:00Z");

    private final ConfiguracionDeCorreo configuracion = new ConfiguracionDeCorreo(
            "The Nexus Battles VI <no-reply@nexusbattles.local>", "", "http://x", 50);

    @Test
    void separaLoQueEntregoElProveedorDeLoQueSeDesvioYDeLoQueNoSalio() {
        RegistroDeEnvios registro = new RegistroDeEnvios(configuracion);
        registro.anotar(EnvioRegistrado.aceptado(
                AHORA, "jugador.real@gmail.com", "email/bienvenida", "<1@x>", EnvioRegistrado.PROVEEDOR));
        registro.anotar(EnvioRegistrado.aceptado(
                AHORA, "canario@nexus.test", "email/bienvenida", "<2@x>", EnvioRegistrado.BUZON_DE_PRUEBAS));
        registro.anotar(EnvioRegistrado.omitido(AHORA, "otro@example.com", "email/bienvenida"));
        registro.anotar(EnvioRegistrado.rechazado(
                AHORA, "jugador@gmail.com", "email/bienvenida", "535 auth", EnvioRegistrado.PROVEEDOR));
        BuzonDePruebas buzon = new BuzonDePruebas("mailpit", "1025", "smtp.gmail.com", "587");

        EnviosController.EstadoDeEntrega estado =
                new EnviosController(registro, configuracion, buzon, "smtp.gmail.com", "587", "true", "true")
                        .estado(25);

        assertThat(estado.servidor()).isEqualTo("smtp.gmail.com");
        assertThat(estado.puerto()).isEqualTo(587);
        assertThat(estado.autentica()).isTrue();
        assertThat(estado.tls()).isTrue();
        assertThat(estado.buzonDePruebas()).isFalse();
        assertThat(estado.desvioDePruebas()).isEqualTo("mailpit:1025");
        assertThat(estado.aceptados()).isEqualTo(1);
        assertThat(estado.desviados()).isEqualTo(1);
        assertThat(estado.omitidos()).isEqualTo(1);
        assertThat(estado.rechazados()).isEqualTo(1);
        assertThat(estado.recientes()).hasSize(4);
        assertThat(estado.recientes())
                .extracting(EnvioRegistrado::destinatario)
                .as("nunca direcciones completas")
                .noneMatch(d -> d.startsWith("jugador.real") || d.startsWith("canario") || d.startsWith("jugador"));
    }

    @Test
    void contraMailpitLoDiceSinRodeos() {
        EnviosController.EstadoDeEntrega estado = new EnviosController(
                        new RegistroDeEnvios(configuracion),
                        configuracion,
                        new BuzonDePruebas("", "", "mailpit", "1025"),
                        "mailpit",
                        "1025",
                        "false",
                        "false")
                .estado(25);

        assertThat(estado.buzonDePruebas()).isTrue();
        assertThat(estado.desvioDePruebas()).isEqualTo("mailpit:1025");
        assertThat(estado.autentica()).isFalse();
    }

    @Test
    void conLasVariablesVaciasDelEnvExampleArrancaYNoInventaValores() {
        // SMTP_PORT=, SMTP_AUTENTICA=, SMTP_TLS= llegan como cadenas vacias.
        EnviosController.EstadoDeEntrega estado = new EnviosController(
                        new RegistroDeEnvios(configuracion),
                        configuracion,
                        new BuzonDePruebas("", "", "", ""),
                        null,
                        "",
                        "",
                        null)
                .estado(25);

        assertThat(estado.servidor()).isEmpty();
        assertThat(estado.puerto()).isZero();
        assertThat(estado.autentica()).isFalse();
        assertThat(estado.tls()).isFalse();
        assertThat(estado.desvioDePruebas()).isEmpty();
        assertThat(estado.recientes()).isEmpty();
    }

    @Test
    void nuncaDevuelveMasDeCienEnvios() {
        RegistroDeEnvios registro = new RegistroDeEnvios(new ConfiguracionDeCorreo("", "", "http://x", 500));
        for (int i = 0; i < 150; i++) {
            registro.anotar(EnvioRegistrado.aceptado(AHORA, "x@gmail.com", "email/x", "<" + i + ">"));
        }
        JavaMailSender principal = mock(JavaMailSender.class);
        BuzonDePruebas buzon = new BuzonDePruebas("", "", "smtp.gmail.com", "587");
        assertThat(buzon.para(principal)).isEmpty();

        EnviosController.EstadoDeEntrega estado =
                new EnviosController(registro, configuracion, buzon, "smtp.gmail.com", "587", "true", "true")
                        .estado(1000);

        assertThat(estado.recientes()).hasSize(100);
    }
}