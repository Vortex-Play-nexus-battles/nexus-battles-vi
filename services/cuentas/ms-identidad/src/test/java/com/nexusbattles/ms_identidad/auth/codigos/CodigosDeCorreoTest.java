package com.nexusbattles.ms_identidad.auth.codigos;

import com.nexusbattles.ms_identidad.auth.model.TokenCredencial;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.TokenCredencialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Codigos de un solo uso (B1): resumen BCrypt, intentos, vigencia y frecuencia")
class CodigosDeCorreoTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final LocalDateTime HOY = LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC);

    private final PasswordEncoder cifrador = new BCryptPasswordEncoder(4);
    private TokenCredencialRepository repositorio;
    private IgualadorDeTiempo igualador;
    private ApplicationEventPublisher eventos;
    private CodigosDeCorreo codigos;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        repositorio = mock(TokenCredencialRepository.class);
        igualador = mock(IgualadorDeTiempo.class);
        eventos = mock(ApplicationEventPublisher.class);
        codigos = new CodigosDeCorreo(repositorio, PoliticaDeCodigos.porOmision(), new GeneradorDeCodigos(),
                igualador, eventos, cifrador, Clock.fixed(AHORA, ZoneOffset.UTC));
        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setEmail("ada@upb.edu.co");
        usuario.setApodo("ada");
        when(repositorio.save(any(TokenCredencial.class))).thenAnswer(invocacion -> {
            TokenCredencial fila = invocacion.getArgument(0);
            if (fila.getId() == null) {
                fila.setId(99L);
            }
            return fila;
        });
    }

    private TokenCredencial vigente(String codigo, TipoCodigo tipo) {
        TokenCredencial fila = new TokenCredencial(usuario, tipo.name(), cifrador.encode(codigo), HOY.minusMinutes(1),
                HOY.plusMinutes(30));
        fila.setId(41L);
        return fila;
    }

    private void ultimo(TokenCredencial fila) {
        when(repositorio.findFirstByUsuario_IdAndTipoInOrderByIdDesc(eq(7L), any())).thenReturn(Optional.of(fila));
    }

    // ------------------------------------------------------------------ emitir

    @Test
    @DisplayName("emitir: anula los vigentes de la familia, guarda el RESUMEN y publica el codigo para el correo")
    void emitir() {
        CodigoEmitido emitido = codigos.emitir(usuario, TipoCodigo.VERIFICACION);

        verify(repositorio).anularVigentes(7L, List.of("VERIFICACION"), HOY);
        ArgumentCaptor<TokenCredencial> guardado = ArgumentCaptor.forClass(TokenCredencial.class);
        verify(repositorio).save(guardado.capture());
        ArgumentCaptor<CodigoParaEnviar> evento = ArgumentCaptor.forClass(CodigoParaEnviar.class);
        verify(eventos).publishEvent(evento.capture());

        TokenCredencial fila = guardado.getValue();
        String codigo = evento.getValue().codigo();
        assertThat(fila.getToken()).as("el codigo en claro no se guarda").isNull();
        assertThat(fila.getCodigoHash()).isNotEqualTo(codigo);
        assertThat(cifrador.matches(codigo, fila.getCodigoHash())).isTrue();
        assertThat(fila.getTipo()).isEqualTo("VERIFICACION");
        assertThat(fila.getCreadoEn()).isEqualTo(HOY);
        assertThat(fila.getFechaExpiracion()).isEqualTo(HOY.plusMinutes(1440));
        assertThat(fila.getIntentosFallidos()).isZero();
        assertThat(codigo).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}");
        assertThat(evento.getValue()).extracting(CodigoParaEnviar::email, CodigoParaEnviar::apodo,
                CodigoParaEnviar::minutosVigencia, CodigoParaEnviar::codigoId)
                .containsExactly("ada@upb.edu.co", "ada", 1440, 99L);
        assertThat(emitido).isEqualTo(new CodigoEmitido(99L, TipoCodigo.VERIFICACION, 1440));
        assertThat(emitido.toString()).doesNotContain(codigo);
    }

    @Test
    @DisplayName("restablecer anula tambien la activacion (misma familia) y vence en 30 minutos")
    void emitirRestablecimiento() {
        codigos.emitir(usuario, TipoCodigo.RESTABLECIMIENTO);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<String>> tipos = ArgumentCaptor.forClass((Class) List.class);
        verify(repositorio).anularVigentes(eq(7L), tipos.capture(), eq(HOY));
        assertThat(tipos.getValue()).containsExactlyInAnyOrder("ACTIVACION", "RESTABLECIMIENTO");
        ArgumentCaptor<TokenCredencial> guardado = ArgumentCaptor.forClass(TokenCredencial.class);
        verify(repositorio).save(guardado.capture());
        assertThat(guardado.getValue().getFechaExpiracion()).isEqualTo(HOY.plusMinutes(30));
    }

    // --------------------------------------------------------------- comprobar

    @Test
    @DisplayName("sin ningun codigo: invalido, pagando el mismo BCrypt")
    void sinCodigo() {
        when(repositorio.findFirstByUsuario_IdAndTipoInOrderByIdDesc(eq(7L), any())).thenReturn(Optional.empty());

        assertThat(codigos.comprobar(7L, TipoCodigo.VERIFICACION, "ABCDEFGH").resultado())
                .isEqualTo(Comprobacion.Resultado.INVALIDO);
        verify(igualador).comparar("ABCDEFGH");
    }

    @Test
    @DisplayName("el codigo correcto vale aunque se escriba en minusculas, con espacios o en dos bloques")
    void correctoNormalizado() {
        ultimo(vigente("K7QX2M9P", TipoCodigo.RESTABLECIMIENTO));

        Comprobacion resultado = codigos.comprobar(7L, TipoCodigo.RESTABLECIMIENTO, " k7qx-2m9p ");

        assertThat(resultado).isEqualTo(new Comprobacion(Comprobacion.Resultado.VALIDO, 41L,
                TipoCodigo.RESTABLECIMIENTO));
        assertThat(resultado.esValida()).isTrue();
        assertThat(resultado.exigirValida()).isSameAs(resultado);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("cada fallo cuenta contra el codigo; el quinto lo anula y responde demasiados intentos")
    void intentos() {
        TokenCredencial fila = vigente("K7QX2M9P", TipoCodigo.VERIFICACION);
        ultimo(fila);

        for (int i = 1; i <= 4; i++) {
            assertThat(codigos.comprobar(7L, TipoCodigo.VERIFICACION, "ZZZZZZZZ").resultado())
                    .isEqualTo(Comprobacion.Resultado.INVALIDO);
            assertThat(fila.getIntentosFallidos()).isEqualTo(i);
            assertThat(fila.getAnuladoEn()).isNull();
        }
        assertThat(codigos.comprobar(7L, TipoCodigo.VERIFICACION, "ZZZZZZZZ").resultado())
                .isEqualTo(Comprobacion.Resultado.DEMASIADOS_INTENTOS);
        assertThat(fila.getAnuladoEn()).isEqualTo(HOY);

        // Anulado: ni el codigo correcto sirve ya, y se sigue diciendo por que.
        assertThat(codigos.comprobar(7L, TipoCodigo.VERIFICACION, "K7QX2M9P").resultado())
                .isEqualTo(Comprobacion.Resultado.DEMASIADOS_INTENTOS);
        assertThat(fila.getIntentosFallidos()).isEqualTo(5);
    }

    @Test
    @DisplayName("caducado, usado, sin resumen o sustituido por otro: invalido, y no cuenta intento")
    void noVigentes() {
        TokenCredencial caducado = vigente("K7QX2M9P", TipoCodigo.VERIFICACION);
        caducado.setFechaExpiracion(HOY.minusSeconds(1));
        ultimo(caducado);
        assertThat(codigos.comprobar(7L, TipoCodigo.VERIFICACION, "K7QX2M9P").resultado())
                .isEqualTo(Comprobacion.Resultado.INVALIDO);

        TokenCredencial usado = vigente("K7QX2M9P", TipoCodigo.VERIFICACION);
        usado.setUsado(true);
        ultimo(usado);
        assertThat(codigos.comprobar(7L, TipoCodigo.VERIFICACION, "K7QX2M9P").resultado())
                .isEqualTo(Comprobacion.Resultado.INVALIDO);

        TokenCredencial heredado = vigente("K7QX2M9P", TipoCodigo.RESTABLECIMIENTO);
        heredado.setCodigoHash(null);
        ultimo(heredado);
        assertThat(codigos.comprobar(7L, TipoCodigo.RESTABLECIMIENTO, "K7QX2M9P").resultado())
                .isEqualTo(Comprobacion.Resultado.INVALIDO);

        TokenCredencial sustituido = vigente("K7QX2M9P", TipoCodigo.VERIFICACION);
        sustituido.setAnuladoEn(HOY.minusMinutes(1));
        ultimo(sustituido);
        assertThat(codigos.comprobar(7L, TipoCodigo.VERIFICACION, "K7QX2M9P").resultado())
                .isEqualTo(Comprobacion.Resultado.INVALIDO);

        verify(repositorio, never()).save(any());
        assertThat(caducado.getIntentosFallidos() + usado.getIntentosFallidos()).isZero();
    }

    @Test
    @DisplayName("exigirValida traduce a las excepciones de las rutas publicas")
    void exigir() {
        assertThatThrownBy(() -> Comprobacion.invalido().exigirValida()).isInstanceOf(CodigoInvalidoException.class)
                .hasMessage(CodigoInvalidoException.MENSAJE);
        assertThatThrownBy(() -> Comprobacion.demasiadosIntentos().exigirValida())
                .isInstanceOf(DemasiadosIntentosException.class).hasMessage(DemasiadosIntentosException.MENSAJE);
        assertThat(Comprobacion.invalido().esValida()).isFalse();
    }

    // ---------------------------------------------------------- registrarFallo

    @Test
    @DisplayName("un fallo de lo que acompana al codigo (las respuestas) cuenta igual y anula al quinto")
    void registrarFallo() {
        TokenCredencial fila = vigente("K7QX2M9P", TipoCodigo.RESTABLECIMIENTO);
        fila.setIntentosFallidos(3);
        when(repositorio.bloquear(41L)).thenReturn(Optional.of(fila));

        assertThat(codigos.registrarFallo(41L).resultado()).isEqualTo(Comprobacion.Resultado.INVALIDO);
        assertThat(codigos.registrarFallo(41L).resultado()).isEqualTo(Comprobacion.Resultado.DEMASIADOS_INTENTOS);
        assertThat(fila.getAnuladoEn()).isEqualTo(HOY);
        assertThat(codigos.registrarFallo(41L).resultado()).isEqualTo(Comprobacion.Resultado.DEMASIADOS_INTENTOS);

        TokenCredencial sustituido = vigente("K7QX2M9P", TipoCodigo.RESTABLECIMIENTO);
        sustituido.setAnuladoEn(HOY);
        when(repositorio.bloquear(42L)).thenReturn(Optional.of(sustituido));
        assertThat(codigos.registrarFallo(42L).resultado()).isEqualTo(Comprobacion.Resultado.INVALIDO);

        TokenCredencial usado = vigente("K7QX2M9P", TipoCodigo.RESTABLECIMIENTO);
        usado.setUsado(true);
        when(repositorio.bloquear(43L)).thenReturn(Optional.of(usado));
        assertThat(codigos.registrarFallo(43L).resultado()).isEqualTo(Comprobacion.Resultado.INVALIDO);

        when(repositorio.bloquear(44L)).thenReturn(Optional.empty());
        assertThat(codigos.registrarFallo(44L).resultado()).isEqualTo(Comprobacion.Resultado.INVALIDO);
    }

    // ------------------------------------------------------ canje y frecuencia

    @Test
    @DisplayName("marcar usado solo gana una vez")
    void marcarUsado() {
        when(repositorio.marcarUsado(41L, HOY)).thenReturn(1, 0);

        assertThat(codigos.marcarUsado(41L)).isTrue();
        assertThat(codigos.marcarUsado(41L)).isFalse();
    }

    @Test
    @DisplayName("frecuencia: un minuto entre dos envios y un maximo por hora, calculados en la base")
    void frecuencia() {
        when(repositorio.ultimaEmision(7L, "VERIFICACION")).thenReturn(Optional.of(HOY.minusSeconds(30)));
        assertThat(codigos.admiteOtroEnvio(7L, TipoCodigo.VERIFICACION)).as("hace 30 s").isFalse();

        when(repositorio.ultimaEmision(7L, "VERIFICACION")).thenReturn(Optional.of(HOY.minusSeconds(61)));
        when(repositorio.contarEmitidosDesde(7L, "VERIFICACION", HOY.minusHours(1))).thenReturn(4L);
        assertThat(codigos.admiteOtroEnvio(7L, TipoCodigo.VERIFICACION)).isTrue();

        when(repositorio.contarEmitidosDesde(7L, "VERIFICACION", HOY.minusHours(1))).thenReturn(5L);
        assertThat(codigos.admiteOtroEnvio(7L, TipoCodigo.VERIFICACION)).as("cinco en la hora").isFalse();

        when(repositorio.ultimaEmision(7L, "RESTABLECIMIENTO")).thenReturn(Optional.empty());
        when(repositorio.contarEmitidosDesde(7L, "RESTABLECIMIENTO", HOY.minusHours(1))).thenReturn(3L);
        assertThat(codigos.admiteOtroEnvio(7L, TipoCodigo.RESTABLECIMIENTO)).as("tres restablecimientos").isFalse();

        when(repositorio.ultimaEmision(7L, "ACTIVACION")).thenReturn(Optional.of(HOY));
        when(repositorio.contarEmitidosDesde(anyLong(), eq("ACTIVACION"), any())).thenReturn(50L);
        assertThat(codigos.admiteOtroEnvio(7L, TipoCodigo.ACTIVACION)).as("la emite un administrador").isTrue();
    }

    @Test
    @DisplayName("nunca verificada: tiene codigos de verificacion y ninguno se uso; una cuenta anterior a B1 no")
    void nuncaVerificada() {
        when(repositorio.existsByUsuario_IdAndTipo(7L, "VERIFICACION")).thenReturn(true);
        when(repositorio.existsByUsuario_IdAndTipoAndUsadoTrue(7L, "VERIFICACION")).thenReturn(false);
        assertThat(codigos.nuncaVerificada(7L)).isTrue();

        when(repositorio.existsByUsuario_IdAndTipoAndUsadoTrue(7L, "VERIFICACION")).thenReturn(true);
        assertThat(codigos.nuncaVerificada(7L)).isFalse();

        when(repositorio.existsByUsuario_IdAndTipo(8L, "VERIFICACION")).thenReturn(false);
        assertThat(codigos.nuncaVerificada(8L)).isFalse();
    }
}
