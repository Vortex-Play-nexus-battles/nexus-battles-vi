package com.nexusbattles.plataforma.correo.envio;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que fallos del SMTP merecen otro intento y cuales no.
 *
 * <p>Los casos imitan la forma exacta en que los entrega
 * {@code JavaMailSenderImpl}: un rechazo del servidor llega como
 * {@link MailSendException} SIN causa y con la excepcion de cada mensaje
 * aparte; un fallo de conexion, como causa.
 */
class ClasificadorDeFallosTest {

    private static MailSendException rechazoDelServidor(Exception porMensaje) {
        Map<Object, Exception> fallidos = new LinkedHashMap<>();
        fallidos.put(new Object(), porMensaje);
        return new MailSendException(fallidos);
    }

    private static SendFailedException rechazoDeDestinatario(String direccion) throws AddressException {
        return new SendFailedException(
                "Invalid Addresses",
                new MessagingException("550 5.1.1 <" + direccion + ">: Recipient address rejected: User unknown"),
                new Address[0],
                new Address[0],
                new Address[] {new InternetAddress(direccion)});
    }

    @Test
    void unDestinatarioRechazadoConUn5xxEsPermanente() throws Exception {
        MailSendException error = rechazoDelServidor(rechazoDeDestinatario("noexiste@gmail.com"));

        assertThat(ClasificadorDeFallos.esPermanente(error)).isTrue();
    }

    @Test
    void unDestinatarioAplazadoConUn4xxSeReintenta() throws Exception {
        // Jakarta Mail deja los 4xx en validUnsentAddresses, no en invalid.
        SendFailedException aplazado = new SendFailedException(
                "Unsent", new MessagingException("451 4.7.1 Greylisted, try again"),
                new Address[0], new Address[] {new InternetAddress("jugador@gmail.com")}, new Address[0]);

        assertThat(ClasificadorDeFallos.esPermanente(rechazoDelServidor(aplazado))).isFalse();
    }

    @Test
    void unaDireccionSinFormaDeDireccionEsPermanente() {
        assertThat(ClasificadorDeFallos.esPermanente(new AddressException("Illegal address", "no es@correo")))
                .isTrue();
    }

    @Test
    void unCorreoImposibleDeComponerEsPermanente() {
        assertThat(ClasificadorDeFallos.esPermanente(new MailParseException("mal formado"))).isTrue();
        assertThat(ClasificadorDeFallos.esPermanente(new MailPreparationException("sin preparar"))).isTrue();
    }

    @Test
    void laConexionRechazadaYLosTiemposDeEsperaSeReintentan() {
        MailSendException conexion = new MailSendException(
                "Mail server connection failed",
                new MessagingException("Couldn't connect to host", new ConnectException("Connection refused")));

        assertThat(ClasificadorDeFallos.esPermanente(conexion)).isFalse();
        assertThat(ClasificadorDeFallos.esPermanente(new MailSendException("x", new SocketTimeoutException("Read timed out"))))
                .isFalse();
    }

    @Test
    void unaClaveSmtpEquivocadaSeReintentaPorqueSeArreglaConfigurando() {
        // 535 es un 5xx, pero no del destinatario: cuando alguien corrija
        // SMTP_PASSWORD, el correo tiene que seguir en la cola.
        MailAuthenticationException autenticacion = new MailAuthenticationException(
                "Authentication failed", new MessagingException("535 5.7.8 Username and Password not accepted"));

        assertThat(ClasificadorDeFallos.esPermanente(autenticacion)).isFalse();
    }

    @Test
    void sinErrorNoHayNadaPermanente() {
        assertThat(ClasificadorDeFallos.esPermanente(null)).isFalse();
        assertThat(ClasificadorDeFallos.resumen(null)).isEmpty();
    }

    @Test
    void elResumenDiceLaCausaDeVerdadConSuClase() {
        MailSendException conexion = new MailSendException(
                "Mail server connection failed",
                new MessagingException("Couldn't connect to host", new ConnectException("Connection refused")));

        assertThat(ClasificadorDeFallos.resumen(conexion)).isEqualTo("ConnectException: Connection refused");
    }

    @Test
    void elResumenDeUnRechazoPorMensajeLlegaHastaElSmtpYEnmascaraLaDireccion() throws Exception {
        MailSendException error = rechazoDelServidor(rechazoDeDestinatario("noexiste@gmail.com"));

        assertThat(ClasificadorDeFallos.resumen(error))
                .startsWith("MessagingException: 550 5.1.1")
                .contains("n***e@gmail.com")
                .doesNotContain("noexiste");
    }

    @Test
    void elResumenNoTieneSaltosDeLineaYEstaAcotado() {
        String largo = "linea uno\r\nlinea dos\t" + "x".repeat(1000);

        String resumen = ClasificadorDeFallos.resumen(new IllegalStateException(largo));

        assertThat(resumen).doesNotContain("\n").doesNotContain("\r").doesNotContain("\t");
        assertThat(resumen).hasSize(ClasificadorDeFallos.LARGO_MAXIMO).startsWith("IllegalStateException: linea uno linea dos");
    }

    @Test
    void unaExcepcionSinMensajeDejaAlMenosSuClase() {
        assertThat(ClasificadorDeFallos.resumen(new IllegalStateException())).isEqualTo("IllegalStateException");
        assertThat(ClasificadorDeFallos.sanear(null)).isEmpty();
    }

    @Test
    void unaCadenaDeCausasQueSeMuerdeLaColaNoCuelga() {
        IllegalStateException a = new IllegalStateException("a");
        IllegalStateException b = new IllegalStateException("b", a);
        a.initCause(b);

        assertThat(ClasificadorDeFallos.esPermanente(a)).isFalse();
        assertThat(ClasificadorDeFallos.resumen(a)).isNotBlank();
    }
}
