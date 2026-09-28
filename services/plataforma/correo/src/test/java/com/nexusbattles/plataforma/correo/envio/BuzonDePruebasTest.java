package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** A donde se desvian las direcciones reservadas, segun lo que haya configurado. */
class BuzonDePruebasTest {

    private final JavaMailSender principal = mock(JavaMailSender.class);

    @Test
    void conBuzonPropioLasReservadasVanAEl() {
        // Dev: el proveedor es Gmail y el buzon es el Mailpit del compose.
        BuzonDePruebas buzon = new BuzonDePruebas("mailpit", "1025", "smtp.gmail.com", "587");

        JavaMailSender elegido = buzon.para(principal).orElseThrow();

        assertThat(elegido).isNotSameAs(principal).isInstanceOf(JavaMailSenderImpl.class);
        assertThat(((JavaMailSenderImpl) elegido).getHost()).isEqualTo("mailpit");
        assertThat(((JavaMailSenderImpl) elegido).getPort()).isEqualTo(1025);
        assertThat(buzon.descripcion()).isEqualTo("mailpit:1025");
    }

    @Test
    void sinBuzonPropioYConElPrincipalEnMailpitVanAlPrincipalComoSiempre() {
        // Desarrollo local: el servidor principal ya es un recogedor.
        BuzonDePruebas buzon = new BuzonDePruebas("", "", "localhost", "1025");

        assertThat(buzon.para(principal)).containsSame(principal);
        assertThat(buzon.descripcion()).isEqualTo("localhost:1025");
    }

    @Test
    void sinBuzonPropioYConUnProveedorRealNoSalen() {
        BuzonDePruebas buzon = new BuzonDePruebas("  ", "1025", "smtp.gmail.com", "587");

        assertThat(buzon.para(principal)).isEmpty();
        assertThat(buzon.descripcion()).isEmpty();
    }

    @Test
    void elPuertoDelBuzonPropioPorOmisionEsElDeMailpit() {
        BuzonDePruebas buzon = new BuzonDePruebas("mailpit", "", "smtp.gmail.com", "587");

        assertThat(buzon.descripcion()).isEqualTo("mailpit:1025");
    }

    @Test
    void variablesVaciasNoImpidenArrancar() {
        // SMTP_PORT= tal como viene en .env.example: llega como cadena vacia.
        BuzonDePruebas buzon = new BuzonDePruebas(null, null, null, "");

        assertThat(buzon.para(principal)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {
        "587, 587",
        "' 1025 ', 1025",
        "'', 0",
        "NULL, 0",
        "no-es-un-puerto, 0",
    }, nullValues = "NULL")
    void leeElPuertoSinReventar(String texto, int esperado) {
        assertThat(BuzonDePruebas.puertoDe(texto, 0)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"1025, true", "1026, true", "8025, true", "587, false", "465, false", "25, false", "0, false"})
    void reconoceLosPuertosDeLosRecogedoresDeDesarrollo(int puerto, boolean esBuzon) {
        assertThat(BuzonDePruebas.esPuertoDeBuzon(puerto)).isEqualTo(esBuzon);
    }
}