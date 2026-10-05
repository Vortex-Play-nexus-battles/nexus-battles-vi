package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso.Proposito;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El desafio del login en dos pasos: cuando se emite, que forma tiene, como se
 * guarda (resumido) y cuando deja de valer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Desafios del login en dos pasos")
class DesafiosDeAccesoTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    @Mock
    private DesafioDeAccesoRepository repositorio;
    @Mock
    private SegundoFactorRepository segundos;

    private DesafiosDeAcceso desafios;

    @BeforeEach
    void preparar() {
        desafios = con("", claveAlAzar());
    }

    private DesafiosDeAcceso con(String rolesObligatorios, String clave) {
        return new DesafiosDeAcceso(repositorio, segundos,
                new PoliticaDeSegundoFactor(rolesObligatorios, 5, 10, "Nexus Battles VI"),
                new CifradoDeSecretos(clave), RELOJ);
    }

    private static String claveAlAzar() {
        byte[] clave = new byte[32];
        new SecureRandom().nextBytes(clave);
        return Base64.getEncoder().encodeToString(clave);
    }

    private static Usuario cuenta(String rol) {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setApodo("ana");
        usuario.setVersionToken(3);
        RolEntity entidad = new RolEntity();
        entidad.setNombre(rol);
        usuario.setRol(entidad);
        return usuario;
    }

    private static Motivo motivoDe(ThrowingCallable llamada) {
        Throwable error = catchThrowable(llamada);
        assertThat(error).isInstanceOf(SegundoFactorRechazadoException.class);
        return ((SegundoFactorRechazadoException) error).getMotivo();
    }

    @Test
    @DisplayName("con segundo factor activo: desafio VERIFICAR de 256 bits, guardado solo resumido y con 5 min de vida")
    void conSegundoFactor() {
        when(segundos.existsByUsuarioIdAndActivoTrue(7L)).thenReturn(true);
        ArgumentCaptor<DesafioDeAcceso> guardado = ArgumentCaptor.forClass(DesafioDeAcceso.class);

        Optional<DesafioEmitido> emitido = desafios.exigirSegundoPaso(cuenta("JUGADOR"));

        assertThat(emitido).isPresent();
        verify(repositorio).borrarGastados(7L, LocalDateTime.now(RELOJ));
        verify(repositorio).save(guardado.capture());
        DesafioDeAcceso fila = guardado.getValue();
        assertThat(emitido.get().proposito()).isEqualTo(Proposito.VERIFICAR);
        assertThat(emitido.get().valor()).matches("[A-Za-z0-9_-]{43}");
        assertThat(emitido.get().expiraEn()).isEqualTo(AHORA.plusSeconds(300));
        assertThat(fila.getUsuarioId()).isEqualTo(7L);
        assertThat(fila.getProposito()).isEqualTo(Proposito.VERIFICAR);
        assertThat(fila.getVersionToken()).isEqualTo(3);
        assertThat(fila.getExpiraEn()).isEqualTo(LocalDateTime.now(RELOJ).plusMinutes(5));
        assertThat(fila.getTokenHash()).hasSize(64).isEqualTo(DesafiosDeAcceso.resumen(emitido.get().valor()))
                .doesNotContain(emitido.get().valor());
    }

    @Test
    @DisplayName("dos desafios nunca se repiten")
    void alAzar() {
        when(segundos.existsByUsuarioIdAndActivoTrue(7L)).thenReturn(true);
        String uno = desafios.exigirSegundoPaso(cuenta("JUGADOR")).orElseThrow().valor();
        String otro = desafios.exigirSegundoPaso(cuenta("JUGADOR")).orElseThrow().valor();
        assertThat(uno).isNotEqualTo(otro);
    }

    @Test
    @DisplayName("sin segundo factor y sin obligatoriedad: el login sigue como siempre (ningun desafio)")
    void sinSegundoFactor() {
        when(segundos.existsByUsuarioIdAndActivoTrue(7L)).thenReturn(false);

        assertThat(desafios.exigirSegundoPaso(cuenta("SUPER_ADMINISTRADOR"))).isEmpty();
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("rol obligatorio sin segundo factor: desafio ENROLAR")
    void obligatorio() {
        when(segundos.existsByUsuarioIdAndActivoTrue(7L)).thenReturn(false);
        DesafiosDeAcceso conObligatoriedad = con("ADMINISTRADOR", claveAlAzar());

        assertThat(conObligatoriedad.exigirSegundoPaso(cuenta("ADMINISTRADOR")).orElseThrow().proposito())
                .isEqualTo(Proposito.ENROLAR);
        assertThat(conObligatoriedad.exigirSegundoPaso(cuenta("JUGADOR"))).isEmpty();
    }

    @Test
    @DisplayName("rol obligatorio y el servicio sin clave: 503, nunca una sesion sin segundo factor")
    void obligatorioSinClave() {
        when(segundos.existsByUsuarioIdAndActivoTrue(7L)).thenReturn(false);
        DesafiosDeAcceso sinClave = con("ADMINISTRADOR", "");

        assertThat(motivoDe(() -> sinClave.exigirSegundoPaso(cuenta("ADMINISTRADOR"))))
                .isEqualTo(Motivo.NO_DISPONIBLE);
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("un desafio vigente se encuentra por el resumen de su valor y se puede gastar")
    void vigente() {
        DesafioDeAcceso fila = new DesafioDeAcceso(7L, DesafiosDeAcceso.resumen("valor-del-desafio"),
                Proposito.VERIFICAR, 3, LocalDateTime.now(RELOJ).minusMinutes(1),
                LocalDateTime.now(RELOJ).plusMinutes(4));
        when(repositorio.bloquearPorResumen(DesafiosDeAcceso.resumen("valor-del-desafio")))
                .thenReturn(Optional.of(fila));

        DesafioDeAcceso encontrado = desafios.vigente("valor-del-desafio", Proposito.VERIFICAR);
        desafios.consumir(encontrado);

        assertThat(encontrado).isSameAs(fila);
        assertThat(fila.getUsadoEn()).isEqualTo(LocalDateTime.now(RELOJ));
        verify(repositorio).save(fila);
    }

    @Test
    @DisplayName("caducado, ya usado, de otro proposito o desconocido: 401 desafio-invalido, sin distinguir")
    void invalidos() {
        DesafioDeAcceso caducado = new DesafioDeAcceso(7L, "a", Proposito.VERIFICAR, 3,
                LocalDateTime.now(RELOJ).minusMinutes(9), LocalDateTime.now(RELOJ).minusMinutes(4));
        DesafioDeAcceso usado = new DesafioDeAcceso(7L, "b", Proposito.VERIFICAR, 3,
                LocalDateTime.now(RELOJ).minusMinutes(1), LocalDateTime.now(RELOJ).plusMinutes(4));
        usado.usar(LocalDateTime.now(RELOJ));
        DesafioDeAcceso deEnrolamiento = new DesafioDeAcceso(7L, "c", Proposito.ENROLAR, 3,
                LocalDateTime.now(RELOJ).minusMinutes(1), LocalDateTime.now(RELOJ).plusMinutes(4));
        when(repositorio.bloquearPorResumen(anyString())).thenReturn(Optional.of(caducado), Optional.of(usado),
                Optional.of(deEnrolamiento), Optional.empty());

        for (int i = 0; i < 4; i++) {
            assertThat(motivoDe(() -> desafios.vigente("algun-valor", Proposito.VERIFICAR)))
                    .isEqualTo(Motivo.DESAFIO_INVALIDO);
        }
        assertThat(Motivo.DESAFIO_INVALIDO.estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("vacio o desmesurado: ni se busca")
    void sinForma() {
        assertThat(motivoDe(() -> desafios.vigente(" ", Proposito.VERIFICAR))).isEqualTo(Motivo.DESAFIO_INVALIDO);
        assertThat(motivoDe(() -> desafios.vigente(null, Proposito.VERIFICAR))).isEqualTo(Motivo.DESAFIO_INVALIDO);
        assertThat(motivoDe(() -> desafios.vigente("x".repeat(129), Proposito.ENROLAR)))
                .isEqualTo(Motivo.DESAFIO_INVALIDO);
        verifyNoInteractions(repositorio);
    }
}
