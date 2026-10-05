package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.segundofactor.CambioDeSegundoFactor.Cambio;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.DesactivarSegundoFactorRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EnrolamientoResponse;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EstadoSegundoFactorResponse;
import com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El segundo factor de la propia cuenta (HU-AUT-007): estado, enrolamiento,
 * activacion con los codigos de recuperacion, desactivacion y la comprobacion
 * del codigo en el segundo paso del login.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Segundo factor de la propia cuenta (servicio)")
class SegundoFactorServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T15:00:10Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final long PASO = Totp.pasoDe(AHORA);
    private static final byte[] SECRETO = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final UUID UID = UUID.fromString("6f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b");
    private static final String CLAVE = "Clave-Actual-2026!";
    private static final PasswordEncoder BCRYPT = new BCryptPasswordEncoder(4);

    @Mock
    private SegundoFactorRepository segundos;
    @Mock
    private CodigoDeRecuperacionRepository codigos;
    @Mock
    private DesafioDeAccesoRepository desafios;
    @Mock
    private IntentosFallidosService intentosFallidos;
    @Mock
    private ApplicationEventPublisher eventos;

    private final CifradoDeSecretos cifrado = new CifradoDeSecretos(claveAlAzar());
    private SegundoFactorService servicio;

    private static String claveAlAzar() {
        byte[] clave = new byte[32];
        new SecureRandom().nextBytes(clave);
        return Base64.getEncoder().encodeToString(clave);
    }

    @BeforeEach
    void preparar() {
        servicio = servicioCon(cifrado, new PoliticaDeSegundoFactor("", 5, 10, "Nexus Battles VI"));
    }

    private SegundoFactorService servicioCon(CifradoDeSecretos cifrador, PoliticaDeSegundoFactor politica) {
        return new SegundoFactorService(segundos, codigos, desafios, cifrador, new CodigosDeRecuperacion(),
                politica, intentosFallidos, eventos, BCRYPT, RELOJ);
    }

    private static Usuario ana() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setPublicId(UID);
        usuario.setApodo("ana");
        usuario.setEmail("ana@nexus.test");
        usuario.setPassword(BCRYPT.encode(CLAVE));
        RolEntity rol = new RolEntity();
        rol.setNombre("ADMINISTRADOR");
        usuario.setRol(rol);
        return usuario;
    }

    private SegundoFactor pendiente() {
        return new SegundoFactor(7L, cifrado.cifrar(SECRETO, SegundoFactorService.contextoDe(7L)),
                LocalDateTime.now(RELOJ).minusMinutes(2));
    }

    /** Activo desde ayer, con el ultimo paso usado hace un rato. */
    private SegundoFactor activo() {
        SegundoFactor sf = pendiente();
        sf.activar(LocalDateTime.now(RELOJ).minusDays(1), PASO - 100);
        return sf;
    }

    private static String codigoDeAhora() {
        return Totp.codigo(SECRETO, PASO);
    }

    private static String otroCodigo() {
        String bueno = codigoDeAhora();
        return bueno.equals("000000") ? "111111" : "000000";
    }

    private static Motivo motivoDe(ThrowingCallable llamada) {
        Throwable error = org.assertj.core.api.Assertions.catchThrowable(llamada);
        assertThat(error).isInstanceOf(SegundoFactorRechazadoException.class);
        return ((SegundoFactorRechazadoException) error).getMotivo();
    }

    // ------------------------------------------------------------------ estado

    @Nested
    @DisplayName("estado")
    class Estado {

        @Test
        @DisplayName("sin segundo factor: inactivo, sin pendiente, sin codigos; dice si el servicio puede enrolar")
        void sinSegundoFactor() {
            when(segundos.findById(7L)).thenReturn(Optional.empty());

            EstadoSegundoFactorResponse estado = servicio.estado(ana());

            assertThat(estado.activo()).isFalse();
            assertThat(estado.enrolamientoPendiente()).isFalse();
            assertThat(estado.obligatorio()).isFalse();
            assertThat(estado.disponible()).isTrue();
            assertThat(estado.activadoEn()).isNull();
            assertThat(estado.codigosRecuperacionRestantes()).isNull();
        }

        @Test
        @DisplayName("activo: cuando se activo (UTC) y cuantos codigos de recuperacion quedan")
        void activo() {
            when(segundos.findById(7L)).thenReturn(Optional.of(SegundoFactorServiceTest.this.activo()));
            when(codigos.countByUsuarioIdAndUsadoEnIsNull(7L)).thenReturn(9L);

            EstadoSegundoFactorResponse estado = servicio.estado(ana());

            assertThat(estado.activo()).isTrue();
            assertThat(estado.enrolamientoPendiente()).isFalse();
            assertThat(estado.activadoEn()).isEqualTo("2026-10-04T15:00:10Z");
            assertThat(estado.codigosRecuperacionRestantes()).isEqualTo(9L);
        }

        @Test
        @DisplayName("enrolamiento a medias, rol obligatorio y servicio sin clave")
        void pendienteObligatorioYSinClave() {
            when(segundos.findById(7L)).thenReturn(Optional.of(pendiente()));
            SegundoFactorService sinClave = servicioCon(new CifradoDeSecretos(""),
                    new PoliticaDeSegundoFactor("ADMINISTRADOR", 5, 10, "Nexus Battles VI"));

            EstadoSegundoFactorResponse estado = sinClave.estado(ana());

            assertThat(estado.activo()).isFalse();
            assertThat(estado.enrolamientoPendiente()).isTrue();
            assertThat(estado.obligatorio()).isTrue();
            assertThat(estado.disponible()).isFalse();
        }
    }

    // ------------------------------------------------------------ enrolamiento

    @Nested
    @DisplayName("iniciar el enrolamiento")
    class Enrolamiento {

        @Test
        @DisplayName("genera un secreto de 160 bits, lo guarda cifrado y lo entrega en base32 y en la URI otpauth")
        void generaYGuardaCifrado() {
            when(segundos.existsByUsuarioIdAndActivoTrue(7L)).thenReturn(false);
            when(segundos.bloquear(7L)).thenReturn(Optional.empty());
            ArgumentCaptor<SegundoFactor> guardado = ArgumentCaptor.forClass(SegundoFactor.class);

            EnrolamientoResponse enrolamiento = servicio.iniciarEnrolamiento(ana());

            verify(segundos).save(guardado.capture());
            byte[] secreto = Base32.decodificar(enrolamiento.secreto());
            assertThat(secreto).hasSize(20);
            assertThat(guardado.getValue().isActivo()).isFalse();
            assertThat(guardado.getValue().getSecretoCifrado())
                    .startsWith("v1:").doesNotContain(enrolamiento.secreto());
            assertThat(cifrado.descifrar(guardado.getValue().getSecretoCifrado(), SegundoFactorService.contextoDe(7L)))
                    .isEqualTo(secreto);
            assertThat(enrolamiento.uriOtpauth()).isEqualTo("otpauth://totp/Nexus%20Battles%20VI:ana%40nexus.test"
                    + "?secret=" + enrolamiento.secreto()
                    + "&issuer=Nexus%20Battles%20VI&algorithm=SHA1&digits=6&period=30");
            assertThat(enrolamiento.emisor()).isEqualTo("Nexus Battles VI");
            assertThat(enrolamiento.cuenta()).isEqualTo("ana@nexus.test");
            assertThat(enrolamiento.algoritmo()).isEqualTo("SHA1");
            assertThat(enrolamiento.digitos()).isEqualTo(6);
            assertThat(enrolamiento.periodoSegundos()).isEqualTo(30);
            assertThat(enrolamiento.toString()).doesNotContain(enrolamiento.secreto());
        }

        @Test
        @DisplayName("pedirlo otra vez antes de confirmar reemplaza el secreto pendiente")
        void reemplazaElPendiente() {
            SegundoFactor anterior = pendiente();
            String cifradoAnterior = anterior.getSecretoCifrado();
            when(segundos.existsByUsuarioIdAndActivoTrue(7L)).thenReturn(false);
            when(segundos.bloquear(7L)).thenReturn(Optional.of(anterior));

            servicio.iniciarEnrolamiento(ana());

            verify(segundos).save(anterior);
            assertThat(anterior.getSecretoCifrado()).isNotEqualTo(cifradoAnterior);
            assertThat(anterior.isActivo()).isFalse();
        }

        @Test
        @DisplayName("ya activo: 409 segundo-factor-ya-activo y no se toca nada")
        void yaActivo() {
            when(segundos.existsByUsuarioIdAndActivoTrue(7L)).thenReturn(true);

            assertThat(motivoDe(() -> servicio.iniciarEnrolamiento(ana()))).isEqualTo(Motivo.YA_ACTIVO);
            verify(segundos, never()).save(any());
        }

        @Test
        @DisplayName("sin clave de cifrado: 503 segundo-factor-no-disponible, sin guardar nada")
        void sinClave() {
            when(segundos.existsByUsuarioIdAndActivoTrue(7L)).thenReturn(false);
            SegundoFactorService sinClave = servicioCon(new CifradoDeSecretos(""),
                    new PoliticaDeSegundoFactor("", 5, 10, "x"));

            assertThat(motivoDe(() -> sinClave.iniciarEnrolamiento(ana()))).isEqualTo(Motivo.NO_DISPONIBLE);
            verify(segundos, never()).save(any());
        }
    }

    // --------------------------------------------------------------- activacion

    @Nested
    @DisplayName("activar con un codigo")
    class Activacion {

        @Test
        @DisplayName("el codigo de ahora lo activa, anota su paso y entrega N codigos de recuperacion resumidos con BCrypt")
        void activa() {
            SegundoFactor sf = pendiente();
            when(segundos.bloquear(7L)).thenReturn(Optional.of(sf));
            List<CodigoDeRecuperacion> guardados = new ArrayList<>();
            when(codigos.save(any(CodigoDeRecuperacion.class))).thenAnswer(i -> {
                guardados.add(i.getArgument(0));
                return i.getArgument(0);
            });

            List<String> entregados = servicio.activar(ana(), codigoDeAhora(), "10.0.0.1");

            assertThat(sf.isActivo()).isTrue();
            assertThat(sf.getUltimoPasoUsado()).isEqualTo(PASO);
            assertThat(sf.getActivadoEn()).isEqualTo(LocalDateTime.now(RELOJ));
            verify(segundos).save(sf);
            verify(codigos).borrarDe(7L);
            assertThat(entregados).hasSize(10).doesNotHaveDuplicates();
            assertThat(guardados).hasSize(10);
            for (int i = 0; i < entregados.size(); i++) {
                String hash = guardados.get(i).getCodigoHash();
                assertThat(hash).doesNotContain(entregados.get(i));
                assertThat(BCRYPT.matches(CodigosDeRecuperacion.normalizar(entregados.get(i)), hash)).isTrue();
            }
            verify(eventos).publishEvent(new CambioDeSegundoFactor(UID.toString(), Cambio.ACTIVADO, null, "10.0.0.1"));
            verifyNoInteractions(intentosFallidos);
        }

        @Test
        @DisplayName("un codigo de la ventana vecina tambien vale (reloj del telefono desfasado)")
        void ventanaVecina() {
            SegundoFactor sf = pendiente();
            when(segundos.bloquear(7L)).thenReturn(Optional.of(sf));
            when(codigos.save(any(CodigoDeRecuperacion.class))).thenAnswer(i -> i.getArgument(0));

            servicio.activar(ana(), Totp.codigo(SECRETO, PASO - 1), null);

            assertThat(sf.getUltimoPasoUsado()).isEqualTo(PASO - 1);
        }

        @Test
        @DisplayName("codigo incorrecto: 422 y NO cuenta como intento fallido de la cuenta; nada cambia")
        void codigoIncorrecto() {
            SegundoFactor sf = pendiente();
            when(segundos.bloquear(7L)).thenReturn(Optional.of(sf));

            assertThat(motivoDe(() -> servicio.activar(ana(), otroCodigo(), null)))
                    .isEqualTo(Motivo.CODIGO_INVALIDO);
            assertThat(Motivo.CODIGO_INVALIDO.estado()).isEqualTo(422);
            assertThat(sf.isActivo()).isFalse();
            verifyNoInteractions(intentosFallidos, codigos, eventos);
        }

        @Test
        @DisplayName("sin enrolamiento pedido: 409 sin-enrolamiento-pendiente; ya activo: 409 segundo-factor-ya-activo")
        void sinPendienteOYaActivo() {
            when(segundos.bloquear(7L)).thenReturn(Optional.empty());
            assertThat(motivoDe(() -> servicio.activar(ana(), codigoDeAhora(), null)))
                    .isEqualTo(Motivo.SIN_ENROLAMIENTO);

            when(segundos.bloquear(7L)).thenReturn(Optional.of(activo()));
            assertThat(motivoDe(() -> servicio.activar(ana(), codigoDeAhora(), null))).isEqualTo(Motivo.YA_ACTIVO);
        }
    }

    // ------------------------------------------------------------ desactivacion

    @Nested
    @DisplayName("desactivar con contrasena y codigo")
    class Desactivacion {

        private DesactivarSegundoFactorRequest conCodigo(String clave, String codigo) {
            return new DesactivarSegundoFactorRequest(clave, codigo, null);
        }

        @Test
        @DisplayName("contrasena y codigo de la aplicacion correctos: borra secreto, codigos y desafios, y lo audita")
        void desactiva() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));
            when(segundos.registrarPaso(7L, PASO)).thenReturn(1);

            servicio.desactivar(ana(), conCodigo(CLAVE, codigoDeAhora()), "10.0.0.2");

            verify(segundos).borrarDe(7L);
            verify(codigos).borrarDe(7L);
            verify(desafios).borrarDe(7L);
            verify(eventos).publishEvent(
                    new CambioDeSegundoFactor(UID.toString(), Cambio.DESACTIVADO, null, "10.0.0.2"));
            verifyNoInteractions(intentosFallidos);
        }

        @Test
        @DisplayName("tambien con un codigo de recuperacion")
        void conRecuperacion() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));
            CodigoDeRecuperacion guardado = new CodigoDeRecuperacion(7L, BCRYPT.encode("K7QX2M9PRT"),
                    LocalDateTime.now(RELOJ));
            when(codigos.findByUsuarioIdAndUsadoEnIsNull(7L)).thenReturn(List.of(guardado));
            when(codigos.marcarUsado(eq(guardado.getId()), any())).thenReturn(1);

            servicio.desactivar(ana(), new DesactivarSegundoFactorRequest(CLAVE, null, "k7qx2-m9prt"), null);

            verify(segundos).borrarDe(7L);
        }

        @Test
        @DisplayName("contrasena incorrecta: 422 contrasena-actual-incorrecta y cuenta como intento fallido")
        void contrasenaIncorrecta() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));

            assertThat(motivoDe(() -> servicio.desactivar(ana(), conCodigo("otra", codigoDeAhora()), null)))
                    .isEqualTo(Motivo.CONTRASENA_INCORRECTA);
            verify(intentosFallidos).registrarIntentoFallido(7L);
            verify(segundos, never()).borrarDe(anyLong());
        }

        @Test
        @DisplayName("codigo incorrecto: 422 codigo-segundo-factor-invalido y cuenta como intento fallido")
        void codigoIncorrecto() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));

            assertThat(motivoDe(() -> servicio.desactivar(ana(), conCodigo(CLAVE, otroCodigo()), null)))
                    .isEqualTo(Motivo.CODIGO_INVALIDO);
            verify(intentosFallidos).registrarIntentoFallido(7L);
            verify(segundos, never()).borrarDe(anyLong());
        }

        @Test
        @DisplayName("el mismo codigo dos veces: la segunda vez no vale (otra peticion ya anoto su paso)")
        void codigoRepetido() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));
            when(segundos.registrarPaso(7L, PASO)).thenReturn(0);

            assertThat(motivoDe(() -> servicio.desactivar(ana(), conCodigo(CLAVE, codigoDeAhora()), null)))
                    .isEqualTo(Motivo.CODIGO_INVALIDO);
            verify(intentosFallidos).registrarIntentoFallido(7L);
        }

        @Test
        @DisplayName("cuenta bloqueada: 423 sin comparar nada")
        void cuentaBloqueada() {
            Usuario bloqueada = ana();
            bloqueada.setBloqueadoHasta(LocalDateTime.now(RELOJ).plusMinutes(10));
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));

            assertThat(motivoDe(() -> servicio.desactivar(bloqueada, conCodigo(CLAVE, codigoDeAhora()), null)))
                    .isEqualTo(Motivo.CUENTA_BLOQUEADA);
            verifyNoInteractions(intentosFallidos);
            verify(segundos, never()).registrarPaso(anyLong(), anyLong());
        }

        @Test
        @DisplayName("sin segundo factor activo: 409 segundo-factor-no-activo")
        void noActivo() {
            when(segundos.findById(7L)).thenReturn(Optional.of(pendiente()));
            assertThat(motivoDe(() -> servicio.desactivar(ana(), conCodigo(CLAVE, codigoDeAhora()), null)))
                    .isEqualTo(Motivo.NO_ACTIVO);
        }

        @Test
        @DisplayName("ni codigo ni codigo de recuperacion, o los dos: 400 datos-invalidos")
        void exactamenteUno() {
            assertThat(motivoDe(() -> servicio.desactivar(ana(), new DesactivarSegundoFactorRequest(CLAVE, null, " "),
                    null))).isEqualTo(Motivo.DATOS_INVALIDOS);
            assertThat(motivoDe(() -> servicio.desactivar(ana(),
                    new DesactivarSegundoFactorRequest(CLAVE, "123456", "K7QX2M9PRT"), null)))
                    .isEqualTo(Motivo.DATOS_INVALIDOS);
            verifyNoInteractions(segundos, intentosFallidos);
        }
    }

    // ------------------------------------------------------ comprobar en el acceso

    @Nested
    @DisplayName("comprobar el codigo en el segundo paso del login")
    class EnElAcceso {

        @Test
        @DisplayName("codigo de la aplicacion correcto: anota su paso y no gasta codigos de recuperacion")
        void codigoCorrecto() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));
            when(segundos.registrarPaso(7L, PASO)).thenReturn(1);

            ComprobacionDelSegundoFactor comprobacion = servicio.comprobarEnElAcceso(ana(), codigoDeAhora(), null, null);

            assertThat(comprobacion.conRecuperacion()).isFalse();
            assertThat(comprobacion.restantes()).isNull();
            verify(segundos).registrarPaso(7L, PASO);
            verifyNoInteractions(codigos, intentosFallidos, eventos);
        }

        @Test
        @DisplayName("codigo incorrecto: 401 codigo-segundo-factor-invalido y cuenta como intento fallido")
        void codigoIncorrecto() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));

            assertThat(motivoDe(() -> servicio.comprobarEnElAcceso(ana(), otroCodigo(), null, null)))
                    .isEqualTo(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO);
            assertThat(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO.estado()).isEqualTo(401);
            verify(intentosFallidos).registrarIntentoFallido(7L);
        }

        @Test
        @DisplayName("el codigo de un paso ya usado no vuelve a valer aunque siga en su ventana")
        void pasoYaUsado() {
            SegundoFactor sf = pendiente();
            sf.activar(LocalDateTime.now(RELOJ).minusDays(1), PASO);
            when(segundos.findById(7L)).thenReturn(Optional.of(sf));

            assertThat(motivoDe(() -> servicio.comprobarEnElAcceso(ana(), codigoDeAhora(), null, null)))
                    .isEqualTo(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO);
            verify(intentosFallidos).registrarIntentoFallido(7L);
            verify(segundos, never()).registrarPaso(anyLong(), anyLong());
        }

        @Test
        @DisplayName("codigo de recuperacion vigente: se gasta y se dice cuantos quedan; queda en la auditoria")
        void recuperacion() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));
            CodigoDeRecuperacion otro = new CodigoDeRecuperacion(7L, BCRYPT.encode("ABCDEFGHJK"), LocalDateTime.now(RELOJ));
            CodigoDeRecuperacion bueno = new CodigoDeRecuperacion(7L, BCRYPT.encode("K7QX2M9PRT"), LocalDateTime.now(RELOJ));
            when(codigos.findByUsuarioIdAndUsadoEnIsNull(7L)).thenReturn(List.of(otro, bueno));
            when(codigos.marcarUsado(bueno.getId(), LocalDateTime.now(RELOJ))).thenReturn(1);
            when(codigos.countByUsuarioIdAndUsadoEnIsNull(7L)).thenReturn(8L);

            ComprobacionDelSegundoFactor comprobacion =
                    servicio.comprobarEnElAcceso(ana(), null, "k7qx2 m9prt", "10.0.0.3");

            assertThat(comprobacion.conRecuperacion()).isTrue();
            assertThat(comprobacion.restantes()).isEqualTo(8L);
            verify(codigos, never()).marcarUsado(eq(otro.getId()), any());
            verify(eventos).publishEvent(
                    new CambioDeSegundoFactor(UID.toString(), Cambio.RECUPERACION_USADA, 8L, "10.0.0.3"));
            verifyNoInteractions(intentosFallidos);
        }

        @Test
        @DisplayName("codigo de recuperacion que no es de la cuenta, o gastado a la vez por otra peticion: 401 e intento")
        void recuperacionInvalida() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));
            CodigoDeRecuperacion bueno = new CodigoDeRecuperacion(7L, BCRYPT.encode("K7QX2M9PRT"), LocalDateTime.now(RELOJ));
            when(codigos.findByUsuarioIdAndUsadoEnIsNull(7L)).thenReturn(List.of(bueno));

            assertThat(motivoDe(() -> servicio.comprobarEnElAcceso(ana(), null, "ZZZZZ-ZZZZZ", null)))
                    .isEqualTo(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO);

            when(codigos.marcarUsado(eq(bueno.getId()), any())).thenReturn(0);
            assertThat(motivoDe(() -> servicio.comprobarEnElAcceso(ana(), null, "K7QX2-M9PRT", null)))
                    .isEqualTo(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO);

            verify(intentosFallidos, times(2)).registrarIntentoFallido(7L);
        }

        @Test
        @DisplayName("lo que no puede ser un codigo de recuperacion no llega a BCrypt, pero cuenta como intento")
        void recuperacionConOtraForma() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));

            assertThat(motivoDe(() -> servicio.comprobarEnElAcceso(ana(), null, "x".repeat(200), null)))
                    .isEqualTo(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO);
            verify(codigos, never()).findByUsuarioIdAndUsadoEnIsNull(anyLong());
            verify(intentosFallidos).registrarIntentoFallido(7L);
        }

        @Test
        @DisplayName("cuenta bloqueada: 423 sin comparar ni contar")
        void bloqueada() {
            Usuario bloqueada = ana();
            bloqueada.setBloqueadoHasta(LocalDateTime.now(RELOJ).plusMinutes(3));

            assertThat(motivoDe(() -> servicio.comprobarEnElAcceso(bloqueada, codigoDeAhora(), null, null)))
                    .isEqualTo(Motivo.CUENTA_BLOQUEADA);
            verifyNoInteractions(segundos, codigos, intentosFallidos);
        }

        @Test
        @DisplayName("si el segundo factor ya no esta activo, el desafio no tiene sentido: 401 desafio-invalido")
        void yaNoActivo() {
            when(segundos.findById(7L)).thenReturn(Optional.empty());
            assertThat(motivoDe(() -> servicio.comprobarEnElAcceso(ana(), codigoDeAhora(), null, null)))
                    .isEqualTo(Motivo.DESAFIO_INVALIDO);
            verifyNoInteractions(intentosFallidos);
        }

        @Test
        @DisplayName("sin la clave no se puede comprobar el codigo de la aplicacion: 503, sin contar intento")
        void sinClave() {
            when(segundos.findById(7L)).thenReturn(Optional.of(activo()));
            SegundoFactorService sinClave = servicioCon(new CifradoDeSecretos(""),
                    new PoliticaDeSegundoFactor("", 5, 10, "x"));

            assertThat(motivoDe(() -> sinClave.comprobarEnElAcceso(ana(), codigoDeAhora(), null, null)))
                    .isEqualTo(Motivo.NO_DISPONIBLE);
            verifyNoInteractions(intentosFallidos);
        }

        @Test
        @DisplayName("ni codigo ni codigo de recuperacion, o los dos: 400 datos-invalidos")
        void exactamenteUno() {
            assertThat(motivoDe(() -> servicio.comprobarEnElAcceso(ana(), null, null, null)))
                    .isEqualTo(Motivo.DATOS_INVALIDOS);
            assertThat(motivoDe(() -> servicio.comprobarEnElAcceso(ana(), "123456", "K7QX2M9PRT", null)))
                    .isEqualTo(Motivo.DATOS_INVALIDOS);
            assertThatThrownBy(() -> servicio.comprobarEnElAcceso(ana(), " ", "", null))
                    .isInstanceOf(SegundoFactorRechazadoException.class);
        }
    }

    @Test
    @DisplayName("una cuenta sin uid queda en la auditoria por su clave interna")
    void afectadoSinUid() {
        Usuario sinUid = ana();
        sinUid.setPublicId(null);
        assertThat(SegundoFactorService.afectado(sinUid)).isEqualTo("usuario-7");
        assertThat(SegundoFactorService.afectado(ana())).isEqualTo(UID.toString());
    }
}
