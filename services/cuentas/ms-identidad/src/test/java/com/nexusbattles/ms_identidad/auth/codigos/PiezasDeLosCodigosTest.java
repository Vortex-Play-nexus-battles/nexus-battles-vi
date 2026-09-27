package com.nexusbattles.ms_identidad.auth.codigos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Piezas de los codigos (B1): generador, politica, igualador de tiempo, problemas")
class PiezasDeLosCodigosTest {

    @Test
    @DisplayName("8 caracteres del alfabeto sin 0/O/1/I/L, de SecureRandom, distintos entre si")
    void generador() {
        GeneradorDeCodigos generador = new GeneradorDeCodigos(new SecureRandom());
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String codigo = generador.nuevo();
            assertThat(codigo).hasSize(8).matches("[A-HJKMNP-Z2-9]{8}").doesNotContain("0", "O", "1", "I", "L");
            vistos.add(codigo);
        }
        assertThat(vistos).as("200 codigos, ninguno repetido").hasSize(200);
        assertThat(GeneradorDeCodigos.ALFABETO).hasSize(31);
    }

    @Test
    @DisplayName("normalizar: mayusculas, sin espacios ni guiones, y nunca mas largo de lo comparable")
    void normalizar() {
        assertThat(GeneradorDeCodigos.normalizar(" k7qx-2m9p ")).isEqualTo("K7QX2M9P");
        assertThat(GeneradorDeCodigos.normalizar("K7QX 2M9P")).isEqualTo("K7QX2M9P");
        assertThat(GeneradorDeCodigos.normalizar(null)).isEmpty();
        assertThat(GeneradorDeCodigos.normalizar("A".repeat(5000))).hasSize(32);
    }

    @Test
    @DisplayName("la politica por omision: 24 h, 30 min, 24 h, 5 intentos, 60 s / 5 por hora y 60 s / 3 por hora")
    void politica() {
        PoliticaDeCodigos politica = PoliticaDeCodigos.porOmision();

        assertThat(politica.minutosVigencia(TipoCodigo.VERIFICACION)).isEqualTo(1440);
        assertThat(politica.minutosVigencia(TipoCodigo.RESTABLECIMIENTO)).isEqualTo(30);
        assertThat(politica.minutosVigencia(TipoCodigo.ACTIVACION)).isEqualTo(24 * 60);
        assertThat(politica.intentosMaximos()).isEqualTo(5);
        assertThat(politica.pausaEntreEnvios(TipoCodigo.VERIFICACION)).isEqualTo(Duration.ofSeconds(60));
        assertThat(politica.maximoPorHora(TipoCodigo.VERIFICACION)).isEqualTo(5);
        assertThat(politica.pausaEntreEnvios(TipoCodigo.RESTABLECIMIENTO)).isEqualTo(Duration.ofSeconds(60));
        assertThat(politica.maximoPorHora(TipoCodigo.RESTABLECIMIENTO)).isEqualTo(3);
        assertThat(politica.pausaEntreEnvios(TipoCodigo.ACTIVACION)).isZero();
        assertThat(politica.maximoPorHora(TipoCodigo.ACTIVACION)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("una configuracion absurda detiene el arranque en vez de dejar codigos eternos o sin intentos")
    void politicaInvalida() {
        assertThatThrownBy(() -> new PoliticaDeCodigos(0, 30, 24, 5, 60, 5, 60, 3))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minutos-vigencia");
        assertThatThrownBy(() -> new PoliticaDeCodigos(1440, 30, 24, 0, 60, 5, 60, 3))
                .hasMessageContaining("intentos-maximos");
        assertThatThrownBy(() -> new PoliticaDeCodigos(1440, 30, 24, 5, -1, 5, 60, 3))
                .hasMessageContaining("segundos-entre-reenvios");
        assertThatThrownBy(() -> new PoliticaDeCodigos(1440, 30, 24, 5, 60, 5, 60, 0))
                .hasMessageContaining("solicitudes-por-hora");
    }

    @Test
    @DisplayName("familias: la verificacion va sola; activacion y restablecimiento se anulan entre si")
    void familias() {
        assertThat(TipoCodigo.VERIFICACION.familiaComoTexto()).containsExactly("VERIFICACION");
        assertThat(TipoCodigo.ACTIVACION.familia()).isEqualTo(TipoCodigo.RESTABLECIMIENTO.familia())
                .containsExactlyInAnyOrder(TipoCodigo.ACTIVACION, TipoCodigo.RESTABLECIMIENTO);
    }

    @Test
    @DisplayName("el igualador paga un BCrypt contra un resumen de relleno, y otro al resumir")
    void igualador() {
        PasswordEncoder cifrador = mock(PasswordEncoder.class);
        when(cifrador.encode(anyString())).thenReturn("$2a$04$relleno");
        IgualadorDeTiempo igualador = new IgualadorDeTiempo(cifrador);

        igualador.comparar(" abc ");
        igualador.resumir();

        verify(cifrador).matches("ABC", "$2a$04$relleno");
        verify(cifrador, times(2)).encode(anyString());
        new IgualadorDeTiempo().comparar(null);
    }

    @Test
    @DisplayName("el evento del correo no imprime el codigo y su clave de idempotencia es estable")
    void eventoDelCorreo() {
        CodigoParaEnviar evento = new CodigoParaEnviar(12L, TipoCodigo.VERIFICACION, "a@b.co", "a", "K7QX2M9P", 5);

        assertThat(evento.toString()).doesNotContain("K7QX2M9P").contains("12");
        assertThat(evento.claveDeIdempotencia()).isEqualTo("verificacion-12");
        assertThat(new CodigoParaEnviar(3L, TipoCodigo.RESTABLECIMIENTO, "a@b.co", "a", "X", 5)
                .claveDeIdempotencia()).isEqualTo("restablecimiento-3");
    }

    @Test
    @DisplayName("la auditoria nombra la cuenta por su uid, o por su clave interna si no tiene")
    void afectado() {
        UUID uid = UUID.randomUUID();
        assertThat(new ContrasenaRestablecida(1L, TipoCodigo.RESTABLECIMIENTO, uid, 9L, "a@b.co", "a", null)
                .afectado()).isEqualTo(uid.toString());
        assertThat(new ContrasenaRestablecida(1L, TipoCodigo.RESTABLECIMIENTO, null, 9L, "a@b.co", "a", null)
                .afectado()).isEqualTo("usuario-9");
    }

    @Test
    @DisplayName("problem details con el type del servicio, el titulo, la ruta y application/problem+json")
    void problemas() {
        ResponseEntity<ProblemDetail> invalido = Problemas.codigoInvalido("/api/v1/auth/verificacion/confirmacion");
        assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalido.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(invalido.getBody().getType().toString())
                .isEqualTo("https://nexusbattles.upb.edu.co/errors/codigo-invalido");
        assertThat(invalido.getBody().getInstance().toString()).isEqualTo("/api/v1/auth/verificacion/confirmacion");

        assertThat(Problemas.demasiadosIntentos(null).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Problemas.demasiadosIntentos(null).getBody().getInstance()).isNull();
        assertThat(Problemas.datosInvalidos("/x").getBody().getType().toString()).endsWith("/datos-invalidos");
    }

    @Test
    @DisplayName("la IP del cliente: el primer X-Forwarded-For, o la direccion remota")
    void ip() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr("10.0.0.9");
        assertThat(IpDelCliente.de(peticion)).isEqualTo("10.0.0.9");
        peticion.addHeader("X-Forwarded-For", " 203.0.113.5 , 10.0.0.1");
        assertThat(IpDelCliente.de(peticion)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("marcar el igualador en el camino sin cuenta usa la entrada normalizada")
    void normalizaAntesDeComparar() {
        PasswordEncoder cifrador = mock(PasswordEncoder.class);
        when(cifrador.encode(anyString())).thenReturn("h");
        new IgualadorDeTiempo(cifrador).comparar("a-b c");
        verify(cifrador).matches(eq("ABC"), eq("h"));
    }
}
