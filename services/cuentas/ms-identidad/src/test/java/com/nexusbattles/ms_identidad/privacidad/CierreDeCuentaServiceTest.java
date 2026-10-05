package com.nexusbattles.ms_identidad.privacidad;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.privacidad.CierreRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.privacidad.ConsultaDeSubastas.OperacionesAbiertas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Solicitar, consultar y cancelar el cierre de la propia cuenta (HU-PRV-005).
 *
 * <p>Lo que se comprueba: la identidad se verifica con la contrasena actual y
 * un fallo cuenta para el bloqueo de RF-AUT-009; el plazo es de 30 dias; si
 * hay subastas o pujas abiertas, o no se puede saber, no se programa nada; y
 * repetir la solicitud no programa otra.
 */
@DisplayName("Cierre de la propia cuenta (HU-PRV-005)")
class CierreDeCuentaServiceTest {

    private static final UUID UID = UUID.fromString("bbbbbbbb-1111-4222-8333-444444444444");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** 14:30 en Bogota. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T19:30:00Z"), BOGOTA);
    private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);
    private static final String CLAVE = "Clave.Correcta-9";
    private static final String AUTORIZACION = "Bearer token-de-la-persona";
    private static final String IP = "203.0.113.7";

    private SolicitudDeCierreRepository solicitudes;
    private UsuarioRepository usuarios;
    private IntentosFallidosService intentosFallidos;
    private ConsultaDeSubastas subastas;
    private AuditoriaDeCuenta auditoria;
    private NotificacionClient notificaciones;
    private PasswordEncoder cifrador;
    private CierreDeCuentaService servicio;
    private Usuario ada;

    @BeforeEach
    void preparar() {
        solicitudes = mock(SolicitudDeCierreRepository.class);
        usuarios = mock(UsuarioRepository.class);
        intentosFallidos = mock(IntentosFallidosService.class);
        subastas = mock(ConsultaDeSubastas.class);
        auditoria = mock(AuditoriaDeCuenta.class);
        notificaciones = mock(NotificacionClient.class);
        cifrador = mock(PasswordEncoder.class);
        servicio = new CierreDeCuentaService(solicitudes, usuarios, intentosFallidos, subastas, auditoria,
                notificaciones, mock(PlatformTransactionManager.class), cifrador, RELOJ);

        ada = new Usuario();
        ada.setId(8L);
        ada.setApodo("ada");
        ada.setPublicId(UID);
        ada.setPassword("$2a$hash-de-ada");
        when(cifrador.matches(CLAVE, "$2a$hash-de-ada")).thenReturn(true);
        when(solicitudes.programadaDe(UID)).thenReturn(Optional.empty());
        when(usuarios.bloquearPorIdentificadorPublico(UID)).thenReturn(Optional.of(ada));
        when(solicitudes.save(any(SolicitudDeCierre.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(subastas.delJugador(AUTORIZACION)).thenReturn(new OperacionesAbiertas(0, 0));
    }

    private SolicitudDeCierre programadaHaceDosDias() {
        return SolicitudDeCierre.programar(UID, AHORA.minusDays(2));
    }

    // ------------------------------------------------------------- consultar

    @Test
    @DisplayName("consultar sin solicitud: SIN_SOLICITUD con el plazo del requisito y sin fechas")
    void consultarSinSolicitud() {
        EstadoDelCierre estado = servicio.consultar(ada);

        assertThat(estado.estado()).isEqualTo(EstadoDelCierre.SIN_SOLICITUD);
        assertThat(estado.plazoDias()).isEqualTo(30);
        assertThat(estado.solicitadoEn()).isNull();
        assertThat(estado.programadoPara()).isNull();
    }

    @Test
    @DisplayName("consultar con un cierre programado: sus fechas, con la zona del servidor")
    void consultarProgramado() {
        SolicitudDeCierre programada = programadaHaceDosDias();
        when(solicitudes.programadaDe(UID)).thenReturn(Optional.of(programada));

        EstadoDelCierre estado = servicio.consultar(ada);

        assertThat(estado.estado()).isEqualTo(EstadoDelCierre.PROGRAMADO);
        assertThat(estado.solicitadoEn()).isEqualTo(OffsetDateTime.parse("2026-10-03T14:30:00-05:00"));
        assertThat(estado.programadoPara()).isEqualTo(OffsetDateTime.parse("2026-11-02T14:30:00-05:00"));
    }

    // ------------------------------------------------------------- solicitar

    @Test
    @DisplayName("solicitar: comprueba subastas con el token de la persona y programa a 30 dias")
    void solicitarProgramaATreintaDias() {
        CierreDeCuentaService.Solicitud resultado = servicio.solicitar(ada, CLAVE, AUTORIZACION, IP);

        assertThat(resultado.nueva()).isTrue();
        assertThat(resultado.estado().estado()).isEqualTo(EstadoDelCierre.PROGRAMADO);
        assertThat(resultado.estado().solicitadoEn()).isEqualTo(OffsetDateTime.parse("2026-10-05T14:30:00-05:00"));
        assertThat(resultado.estado().programadoPara()).isEqualTo(OffsetDateTime.parse("2026-11-04T14:30:00-05:00"));

        ArgumentCaptor<SolicitudDeCierre> guardada = ArgumentCaptor.forClass(SolicitudDeCierre.class);
        verify(solicitudes).save(guardada.capture());
        assertThat(guardada.getValue().getUsuarioUid()).isEqualTo(UID);
        assertThat(guardada.getValue().getProgramadaPara()).isEqualTo(AHORA.plusDays(30));

        // Primero se comprueba en ms-subastas, despues se serializa sobre la
        // cuenta y se guarda: nunca una llamada HTTP con la fila bloqueada.
        InOrder orden = inOrder(subastas, usuarios, solicitudes);
        orden.verify(subastas).delJugador(AUTORIZACION);
        orden.verify(usuarios).bloquearPorIdentificadorPublico(UID);
        orden.verify(solicitudes).save(any());

        verify(auditoria).cierreDeCuentaSolicitado(UID, AHORA.plusDays(30), IP);
        verify(notificaciones).emitir(eq(UID.toString()), eq("CUENTA"), anyString(), anyString());
        verifyNoInteractions(intentosFallidos);
    }

    @Test
    @DisplayName("contrasena incorrecta: 422, cuenta como intento fallido y no cambia nada")
    void contrasenaIncorrecta() {
        assertThatThrownBy(() -> servicio.solicitar(ada, "otra", AUTORIZACION, IP))
                .isInstanceOfSatisfying(CierreRechazadoException.class,
                        rechazo -> assertThat(rechazo.getMotivo()).isEqualTo(Motivo.ACTUAL_INCORRECTA));

        verify(intentosFallidos).registrarIntentoFallido(8L);
        verifyNoInteractions(subastas, auditoria, notificaciones);
        verify(solicitudes, never()).save(any());
    }

    @Test
    @DisplayName("cuenta bloqueada por intentos: 423 sin comparar la contrasena")
    void cuentaBloqueada() {
        ada.setBloqueadoHasta(AHORA.plusMinutes(10));

        assertThatThrownBy(() -> servicio.solicitar(ada, CLAVE, AUTORIZACION, IP))
                .isInstanceOfSatisfying(CierreRechazadoException.class,
                        rechazo -> assertThat(rechazo.getMotivo()).isEqualTo(Motivo.CUENTA_BLOQUEADA));

        verify(cifrador, never()).matches(anyString(), anyString());
        verifyNoInteractions(intentosFallidos, subastas, auditoria, notificaciones);
        verify(solicitudes, never()).save(any());
    }

    @Test
    @DisplayName("un bloqueo ya vencido no impide solicitar")
    void bloqueoVencido() {
        ada.setBloqueadoHasta(AHORA.minusMinutes(1));

        assertThat(servicio.solicitar(ada, CLAVE, AUTORIZACION, IP).nueva()).isTrue();
    }

    @Test
    @DisplayName("ya habia un cierre programado: se devuelve tal cual, sin otro ni nueva fecha")
    void yaProgramado() {
        SolicitudDeCierre programada = programadaHaceDosDias();
        when(solicitudes.programadaDe(UID)).thenReturn(Optional.of(programada));

        CierreDeCuentaService.Solicitud resultado = servicio.solicitar(ada, CLAVE, AUTORIZACION, IP);

        assertThat(resultado.nueva()).isFalse();
        assertThat(resultado.estado().programadoPara()).isEqualTo(OffsetDateTime.parse("2026-11-02T14:30:00-05:00"));
        verify(solicitudes, never()).save(any());
        verifyNoInteractions(subastas, auditoria, notificaciones);
    }

    @Test
    @DisplayName("subastas activas como vendedora: 409 con cuantas, y no se programa")
    void subastasActivas() {
        when(subastas.delJugador(AUTORIZACION)).thenReturn(new OperacionesAbiertas(2, 0));

        assertThatThrownBy(() -> servicio.solicitar(ada, CLAVE, AUTORIZACION, IP))
                .isInstanceOfSatisfying(CierreRechazadoException.class, rechazo -> {
                    assertThat(rechazo.getMotivo()).isEqualTo(Motivo.OPERACIONES_PENDIENTES);
                    assertThat(rechazo.getSubastasActivas()).isEqualTo(2);
                    assertThat(rechazo.getPujasVigentes()).isZero();
                });
        verify(solicitudes, never()).save(any());
        verifyNoInteractions(auditoria, notificaciones);
    }

    @Test
    @DisplayName("pujas vigentes: 409 y no se programa")
    void pujasVigentes() {
        when(subastas.delJugador(AUTORIZACION)).thenReturn(new OperacionesAbiertas(0, 1));

        assertThatThrownBy(() -> servicio.solicitar(ada, CLAVE, AUTORIZACION, IP))
                .isInstanceOfSatisfying(CierreRechazadoException.class, rechazo -> {
                    assertThat(rechazo.getMotivo()).isEqualTo(Motivo.OPERACIONES_PENDIENTES);
                    assertThat(rechazo.getPujasVigentes()).isEqualTo(1);
                });
        verify(solicitudes, never()).save(any());
    }

    @Test
    @DisplayName("ms-subastas no responde: 503 y no se programa (sin comprobarlo, no)")
    void subastasNoDisponibles() {
        when(subastas.delJugador(AUTORIZACION)).thenThrow(new SubastasNoDisponiblesException("sin respuesta"));

        assertThatThrownBy(() -> servicio.solicitar(ada, CLAVE, AUTORIZACION, IP))
                .isInstanceOfSatisfying(CierreRechazadoException.class,
                        rechazo -> assertThat(rechazo.getMotivo()).isEqualTo(Motivo.SUBASTAS_NO_DISPONIBLES));
        verify(solicitudes, never()).save(any());
        verifyNoInteractions(auditoria, notificaciones);
    }

    @Test
    @DisplayName("otra solicitud gano la carrera: se devuelve la que quedo programada")
    void carrera() {
        SolicitudDeCierre ganadora = programadaHaceDosDias();
        when(solicitudes.programadaDe(UID)).thenReturn(Optional.empty(), Optional.empty(), Optional.of(ganadora));
        when(solicitudes.save(any(SolicitudDeCierre.class)))
                .thenThrow(new DataIntegrityViolationException("ux_cierre_programado_por_cuenta"));

        CierreDeCuentaService.Solicitud resultado = servicio.solicitar(ada, CLAVE, AUTORIZACION, IP);

        assertThat(resultado.nueva()).isFalse();
        assertThat(resultado.estado().programadoPara()).isEqualTo(OffsetDateTime.parse("2026-11-02T14:30:00-05:00"));
        verifyNoInteractions(auditoria, notificaciones);
    }

    @Test
    @DisplayName("si la auditoria o la bandeja fallan, el cierre queda programado igual")
    void avisosFailOpen() {
        doThrow(new IllegalStateException("auditoria caida")).when(auditoria).cierreDeCuentaSolicitado(any(), any(), any());
        doThrow(new IllegalStateException("bandeja caida")).when(notificaciones)
                .emitir(anyString(), anyString(), anyString(), anyString());

        assertThat(servicio.solicitar(ada, CLAVE, AUTORIZACION, IP).nueva()).isTrue();
        verify(solicitudes).save(any());
    }

    // ------------------------------------------------------------- cancelar

    @Test
    @DisplayName("cancelar: el cierre programado pasa a CANCELADA y queda SIN_SOLICITUD")
    void cancelar() {
        SolicitudDeCierre programada = programadaHaceDosDias();
        when(solicitudes.programadaDe(UID)).thenReturn(Optional.of(programada));

        EstadoDelCierre estado = servicio.cancelar(ada, IP);

        assertThat(estado.estado()).isEqualTo(EstadoDelCierre.SIN_SOLICITUD);
        assertThat(programada.getEstado()).isEqualTo(SolicitudDeCierre.CANCELADA);
        assertThat(programada.getCanceladaEn()).isEqualTo(AHORA);
        verify(usuarios).bloquearPorIdentificadorPublico(UID);
        verify(solicitudes).save(programada);
        verify(auditoria).cierreDeCuentaCancelado(UID, IP);
        verify(notificaciones).emitir(eq(UID.toString()), eq("CUENTA"), anyString(), anyString());
    }

    @Test
    @DisplayName("cancelar sin nada programado: SIN_SOLICITUD, sin escribir ni auditar (idempotente)")
    void cancelarSinSolicitud() {
        EstadoDelCierre estado = servicio.cancelar(ada, IP);

        assertThat(estado.estado()).isEqualTo(EstadoDelCierre.SIN_SOLICITUD);
        verify(solicitudes, never()).save(any());
        verifyNoInteractions(auditoria, notificaciones);
    }
}
