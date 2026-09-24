package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El registro de envios: lo que se puede demostrar y lo que no se guarda.
 *
 * <p>La mitad de este archivo prueba lo que NO aparece. Es deliberado: este
 * registro existe para responder "salio o no salio", y en cuanto guarde
 * direcciones completas deja de ser un diagnostico y pasa a ser una lista de
 * correos de los jugadores.
 */
class RegistroDeEnviosTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T14:00:00Z");

    private static RegistroDeEnvios registro(int capacidad) {
        return new RegistroDeEnvios(
                new ConfiguracionDeCorreo("Nexus <no-reply@nexus.test>", "", "http://x", capacidad));
    }

    @ParameterizedTest
    @CsvSource({
        "simon.perez@gmail.com, s**********z@gmail.com",
        "ana@nexus.test, a*a@nexus.test",
        "ab@nexus.test, **@nexus.test",
        "a@nexus.test, *@nexus.test",
    })
    void enmascaraElUsuarioYConservaElDominio(String direccion, String esperado) {
        // El dominio entero se conserva porque casi todo lo que un proveedor
        // rechaza lo rechaza por dominio: sin el no se puede diagnosticar.
        assertThat(EnvioRegistrado.enmascarar(direccion)).isEqualTo(esperado);
    }

    @Test
    void unaDireccionVaciaOSinArrobaNoRevienta() {
        assertThat(EnvioRegistrado.enmascarar(null)).isEqualTo("(sin destinatario)");
        assertThat(EnvioRegistrado.enmascarar("   ")).isEqualTo("(sin destinatario)");
        assertThat(EnvioRegistrado.enmascarar("no-es-un-correo")).isEqualTo("***");
    }

    @Test
    void loQueSeAnotaNoLlevaLaDireccionCompleta() {
        RegistroDeEnvios registro = registro(10);

        registro.anotar(
                EnvioRegistrado.aceptado(AHORA, "simon.perez@gmail.com", "email/bienvenida", "<id@x>"));

        EnvioRegistrado anotado = registro.ultimos(1).get(0);
        assertThat(anotado.destinatario()).doesNotContain("simon.perez");
        assertThat(anotado.destinatario()).endsWith("@gmail.com");
        assertThat(anotado.identificador()).isEqualTo("<id@x>");
        assertThat(anotado.estado()).isEqualTo(EnvioRegistrado.ACEPTADO);
    }

    @Test
    void cuentaAceptadosYRechazadosPorSeparado() {
        RegistroDeEnvios registro = registro(10);

        registro.anotar(EnvioRegistrado.aceptado(AHORA, "a@nexus.test", "email/bienvenida", "<1>"));
        registro.anotar(EnvioRegistrado.aceptado(AHORA, "b@nexus.test", "email/bienvenida", "<2>"));
        registro.anotar(
                EnvioRegistrado.rechazado(AHORA, "c@nexus.test", "email/bienvenida", "auth fallo"));

        assertThat(registro.aceptados()).isEqualTo(2);
        assertThat(registro.rechazados()).isEqualTo(1);
    }

    @Test
    void devuelveElMasRecienteElPrimero() {
        RegistroDeEnvios registro = registro(10);

        registro.anotar(EnvioRegistrado.aceptado(AHORA, "viejo@nexus.test", "email/x", "<1>"));
        registro.anotar(EnvioRegistrado.aceptado(AHORA, "nuevo@nexus.test", "email/x", "<2>"));

        assertThat(registro.ultimos(2).get(0).identificador()).isEqualTo("<2>");
    }

    /** Memoria acotada: no puede crecer sin limite dentro del servicio. */
    @Test
    void olvidaLosMasAntiguosAlLlegarAlTope() {
        RegistroDeEnvios registro = registro(3);

        for (int i = 1; i <= 5; i++) {
            registro.anotar(EnvioRegistrado.aceptado(AHORA, "x@nexus.test", "email/x", "<" + i + ">"));
        }

        assertThat(registro.ultimos(100)).hasSize(3);
        assertThat(registro.ultimos(100).get(0).identificador()).isEqualTo("<5>");
        assertThat(registro.ultimos(100).get(2).identificador()).isEqualTo("<3>");
        // Los contadores NO se olvidan: cuentan todo lo que paso.
        assertThat(registro.aceptados()).isEqualTo(5);
    }

    @Test
    void pedirMasDeLoQueHayDevuelveLoQueHay() {
        RegistroDeEnvios registro = registro(10);
        registro.anotar(EnvioRegistrado.aceptado(AHORA, "x@nexus.test", "email/x", "<1>"));

        assertThat(registro.ultimos(50)).hasSize(1);
        assertThat(registro.ultimos(0)).isEmpty();
        assertThat(registro.ultimos(-3)).isEmpty();
    }
}