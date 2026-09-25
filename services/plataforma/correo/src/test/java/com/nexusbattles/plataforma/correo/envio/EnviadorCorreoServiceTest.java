package com.nexusbattles.plataforma.correo.envio;

import com.nexusbattles.plataforma.correo.template.PlantillaCorreoService;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Por donde sale cada correo (R18.4) y que resultado devuelve cada intento.
 *
 * <p>Con el proveedor real configurado, las cuentas de prueba de los canarios
 * ({@code @nexus.test}) se convertian en correos que Gmail aceptaba, no podia
 * entregar y devolvia rebotados. Aqui se fija la regla: una direccion
 * reservada va al buzon de pruebas o no sale; nunca al proveedor.
 *
 * <p>Desde B1 el enviador no anota ni lanza: devuelve un
 * {@link ResultadoDeEntrega} y la cola decide.
 */
class EnviadorCorreoServiceTest {

    private JavaMailSender proveedor;
    private JavaMailSender buzon;
    private BuzonDePruebas buzonDePruebas;
    private PlantillaCorreoService plantillas;
    private EnviadorCorreoService enviador;

    @BeforeEach
    void preparar() {
        proveedor = servidorFalso();
        buzon = servidorFalso();
        buzonDePruebas = mock(BuzonDePruebas.class);
        plantillas = mock(PlantillaCorreoService.class);
        when(plantillas.renderizar(anyString(), anyMap())).thenReturn("<p>Hola, jugador</p>");
        ConfiguracionDeCorreo configuracion = new ConfiguracionDeCorreo(
                "The Nexus Battles VI <no-reply@nexusbattles.test>", "soporte@nexusbattles.test", "http://x");
        enviador = new EnviadorCorreoService(proveedor, plantillas, configuracion, buzonDePruebas);
    }

    private static JavaMailSender servidorFalso() {
        JavaMailSender servidor = mock(JavaMailSender.class);
        when(servidor.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        return servidor;
    }

    @Test
    void unaDireccionRealSaleSiemprePorElProveedor() {
        ResultadoDeEntrega resultado =
                enviador.enviar("jugador@gmail.com", "Bienvenido", "email/bienvenida", Map.of());

        verify(proveedor).send(any(MimeMessage.class));
        verify(buzon, never()).send(any(MimeMessage.class));
        assertThat(resultado).isInstanceOf(ResultadoDeEntrega.Entregado.class);
        assertThat(((ResultadoDeEntrega.Entregado) resultado).destino()).isEqualTo(DestinoDeEntrega.PROVEEDOR);
    }

    @Test
    void elMensajeLlevaRemitenteResponderAYElAsunto() throws Exception {
        enviador.enviar("jugador@gmail.com", "Recupera tu contraseña", "email/recuperacion-clave", Map.of());

        ArgumentCaptor<MimeMessage> mensaje = ArgumentCaptor.forClass(MimeMessage.class);
        verify(proveedor).send(mensaje.capture());
        assertThat(mensaje.getValue().getFrom()[0].toString()).contains("no-reply@nexusbattles.test");
        assertThat(mensaje.getValue().getReplyTo()[0].toString()).isEqualTo("soporte@nexusbattles.test");
        assertThat(mensaje.getValue().getSubject()).isEqualTo("Recupera tu contraseña");
    }

    @Test
    void unaDireccionReservadaVaAlBuzonDePruebasYNuncaAlProveedor() {
        when(buzonDePruebas.para(proveedor)).thenReturn(Optional.of(buzon));

        ResultadoDeEntrega resultado =
                enviador.enviar("canario-1790@nexus.test", "Recupera tu clave", "email/recuperacion-clave", Map.of());

        verify(buzon).send(any(MimeMessage.class));
        verify(proveedor, never()).send(any(MimeMessage.class));
        assertThat(((ResultadoDeEntrega.Entregado) resultado).destino())
                .as("no cuenta como entregado por el proveedor: no iba a ninguna bandeja")
                .isEqualTo(DestinoDeEntrega.BUZON_DE_PRUEBAS);
    }

    @Test
    void sinBuzonDePruebasUnaDireccionReservadaNoSaleYSeOmite() {
        when(buzonDePruebas.para(proveedor)).thenReturn(Optional.empty());

        ResultadoDeEntrega resultado =
                enviador.enviar("canario@example.com", "Bienvenido", "email/bienvenida", Map.of());

        verify(proveedor, never()).send(any(MimeMessage.class));
        verify(proveedor, never()).createMimeMessage();
        // Ni siquiera se compone: no hay a donde mandarlo.
        verify(plantillas, never()).renderizar(anyString(), anyMap());
        assertThat(resultado).isInstanceOf(ResultadoDeEntrega.Omitido.class);
        assertThat(((ResultadoDeEntrega.Omitido) resultado).motivo()).contains("RFC 2606");
    }

    @Test
    void unFalloDeConexionSeDevuelveComoTransitorio() {
        doThrow(new MailSendException("Mail server connection failed", new MessagingException("Connection refused")))
                .when(proveedor).send(any(MimeMessage.class));

        ResultadoDeEntrega resultado =
                enviador.enviar("jugador@gmail.com", "Bienvenido", "email/bienvenida", Map.of());

        ResultadoDeEntrega.Fallido fallido = (ResultadoDeEntrega.Fallido) resultado;
        assertThat(fallido.permanente()).isFalse();
        assertThat(fallido.motivo()).contains("Connection refused");
    }

    @Test
    void unRechazoDelDestinatarioSeDevuelveComoPermanente() throws Exception {
        Map<Object, Exception> fallidos = new LinkedHashMap<>();
        fallidos.put(new Object(), new SendFailedException(
                "Invalid Addresses", new MessagingException("550 5.1.1 User unknown"),
                new Address[0], new Address[0], new Address[] {new InternetAddress("noexiste@gmail.com")}));
        doThrow(new MailSendException(fallidos)).when(proveedor).send(any(MimeMessage.class));

        ResultadoDeEntrega resultado =
                enviador.enviar("noexiste@gmail.com", "Bienvenido", "email/bienvenida", Map.of());

        ResultadoDeEntrega.Fallido fallido = (ResultadoDeEntrega.Fallido) resultado;
        assertThat(fallido.permanente()).isTrue();
        assertThat(fallido.motivo()).contains("550").doesNotContain("noexiste@gmail.com");
    }

    @Test
    void unRechazoDelBuzonDePruebasTambienSeDevuelve() {
        when(buzonDePruebas.para(proveedor)).thenReturn(Optional.of(buzon));
        doThrow(new MailSendException("Connection refused")).when(buzon).send(any(MimeMessage.class));

        ResultadoDeEntrega resultado = enviador.enviar("canario@nexus.test", "Hola", "email/bienvenida", Map.of());

        assertThat(resultado).isInstanceOf(ResultadoDeEntrega.Fallido.class);
    }

    @Test
    void unCorreoQueNoSePuedeComponerEsUnFalloPermanente() {
        when(plantillas.renderizar(anyString(), anyMap()))
                .thenThrow(new IllegalArgumentException("Plantilla no registrada: 'email/inventada'"));

        ResultadoDeEntrega resultado = enviador.enviar("jugador@gmail.com", "Hola", "email/inventada", Map.of());

        ResultadoDeEntrega.Fallido fallido = (ResultadoDeEntrega.Fallido) resultado;
        assertThat(fallido.permanente()).isTrue();
        assertThat(fallido.motivo()).startsWith("no se pudo componer el correo").contains("email/inventada");
        verify(proveedor, never()).send(any(MimeMessage.class));
    }

    @Test
    void unDestinatarioSinFormaDeDireccionNiSeIntenta() {
        ResultadoDeEntrega resultado = enviador.enviar("no es un correo@", "Hola", "email/bienvenida", Map.of());

        assertThat(resultado).isInstanceOf(ResultadoDeEntrega.Fallido.class);
        assertThat(((ResultadoDeEntrega.Fallido) resultado).permanente()).isTrue();
        verify(proveedor, never()).send(any(MimeMessage.class));
    }
}
