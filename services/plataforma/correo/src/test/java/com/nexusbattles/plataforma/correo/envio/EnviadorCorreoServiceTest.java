package com.nexusbattles.plataforma.correo.envio;

import com.nexusbattles.plataforma.correo.template.PlantillaCorreoService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Por donde sale cada correo (R18.4).
 *
 * <p>Con el proveedor real configurado, las cuentas de prueba de los canarios
 * ({@code @nexus.test}) se convertian en correos que Gmail aceptaba, no podia
 * entregar y devolvia rebotados. Aqui se fija la regla: una direccion
 * reservada va al buzon de pruebas o no sale; nunca al proveedor.
 */
class EnviadorCorreoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T18:00:00Z");

    private JavaMailSender proveedor;
    private JavaMailSender buzon;
    private BuzonDePruebas buzonDePruebas;
    private PlantillaCorreoService plantillas;
    private RegistroDeEnvios registro;
    private EnviadorCorreoService enviador;

    @BeforeEach
    void preparar() {
        proveedor = servidorFalso();
        buzon = servidorFalso();
        buzonDePruebas = mock(BuzonDePruebas.class);
        plantillas = mock(PlantillaCorreoService.class);
        when(plantillas.renderizar(anyString(), anyMap())).thenReturn("<p>Hola, jugador</p>");
        ConfiguracionDeCorreo configuracion = new ConfiguracionDeCorreo(
                "The Nexus Battles VI <no-reply@nexusbattles.local>", "", "http://x", 10);
        registro = new RegistroDeEnvios(configuracion);
        enviador = new EnviadorCorreoService(
                proveedor,
                plantillas,
                configuracion,
                registro,
                Clock.fixed(AHORA, ZoneOffset.UTC),
                buzonDePruebas);
    }

    private static JavaMailSender servidorFalso() {
        JavaMailSender servidor = mock(JavaMailSender.class);
        when(servidor.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        return servidor;
    }

    @Test
    void unaDireccionRealSaleSiemprePorElProveedor() {
        enviador.enviar("jugador@gmail.com", "Bienvenido", "email/bienvenida", Map.of());

        verify(proveedor).send(any(MimeMessage.class));
        verify(buzon, never()).send(any(MimeMessage.class));
        EnvioRegistrado anotado = registro.ultimos(1).get(0);
        assertThat(anotado.estado()).isEqualTo(EnvioRegistrado.ACEPTADO);
        assertThat(anotado.destino()).isEqualTo(EnvioRegistrado.PROVEEDOR);
        assertThat(registro.aceptados()).isEqualTo(1);
        assertThat(registro.desviados()).isZero();
    }

    @Test
    void unaDireccionReservadaVaAlBuzonDePruebasYNuncaAlProveedor() {
        when(buzonDePruebas.para(proveedor)).thenReturn(Optional.of(buzon));

        enviador.enviar("canario-1790@nexus.test", "Recupera tu clave", "email/recuperacion-clave", Map.of());

        verify(buzon).send(any(MimeMessage.class));
        verify(proveedor, never()).send(any(MimeMessage.class));
        EnvioRegistrado anotado = registro.ultimos(1).get(0);
        assertThat(anotado.estado()).isEqualTo(EnvioRegistrado.ACEPTADO);
        assertThat(anotado.destino()).isEqualTo(EnvioRegistrado.BUZON_DE_PRUEBAS);
        // No cuenta como entregado por el proveedor: no iba a ninguna bandeja.
        assertThat(registro.aceptados()).isZero();
        assertThat(registro.desviados()).isEqualTo(1);
    }

    @Test
    void sinBuzonDePruebasUnaDireccionReservadaNoSaleYQuedaAnotada() {
        when(buzonDePruebas.para(proveedor)).thenReturn(Optional.empty());

        enviador.enviar("canario@example.com", "Bienvenido", "email/bienvenida", Map.of());

        verify(proveedor, never()).send(any(MimeMessage.class));
        verify(proveedor, never()).createMimeMessage();
        // Ni siquiera se compone: no hay a donde mandarlo.
        verify(plantillas, never()).renderizar(anyString(), anyMap());
        EnvioRegistrado anotado = registro.ultimos(1).get(0);
        assertThat(anotado.estado()).isEqualTo(EnvioRegistrado.OMITIDO);
        assertThat(anotado.destino()).isEmpty();
        assertThat(anotado.motivo()).contains("RFC 2606");
        assertThat(anotado.destinatario()).doesNotContain("canario");
        assertThat(registro.omitidos()).isEqualTo(1);
        assertThat(registro.rechazados()).isZero();
    }

    @Test
    void unRechazoDelProveedorQuedaAnotadoConSuDestinoYSeRelanza() {
        doThrow(new MailSendException("535 5.7.8 Username and Password not accepted"))
                .when(proveedor).send(any(MimeMessage.class));

        assertThatThrownBy(() -> enviador.enviar("jugador@gmail.com", "Bienvenido", "email/bienvenida", Map.of()))
                .isInstanceOf(MailSendException.class);

        EnvioRegistrado anotado = registro.ultimos(1).get(0);
        assertThat(anotado.estado()).isEqualTo(EnvioRegistrado.RECHAZADO);
        assertThat(anotado.destino()).isEqualTo(EnvioRegistrado.PROVEEDOR);
        assertThat(anotado.motivo()).contains("535");
        assertThat(registro.rechazados()).isEqualTo(1);
    }

    @Test
    void unRechazoDelBuzonDePruebasSeDistingueDelDelProveedor() {
        when(buzonDePruebas.para(proveedor)).thenReturn(Optional.of(buzon));
        doThrow(new MailSendException("Connection refused")).when(buzon).send(any(MimeMessage.class));

        assertThatThrownBy(() -> enviador.enviar("canario@nexus.test", "Hola", "email/bienvenida", Map.of()))
                .isInstanceOf(MailSendException.class);

        EnvioRegistrado anotado = registro.ultimos(1).get(0);
        assertThat(anotado.estado()).isEqualTo(EnvioRegistrado.RECHAZADO);
        assertThat(anotado.destino()).isEqualTo(EnvioRegistrado.BUZON_DE_PRUEBAS);
    }
}