package com.nexusbattles.ms_identidad.privacidad;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.recuperacion.PreguntaSeguridadRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La ejecucion del derecho al olvido al vencer el plazo (HU-PRV-005 CA-01 y
 * CA-04): que se borra, que se sustituye, que se conserva, y que repetirla no
 * cambia nada.
 */
@DisplayName("Anonimizacion de una cuenta al vencer el plazo (HU-PRV-005)")
class AnonimizadorDeCuentasTest {

    private static final UUID UID = UUID.fromString("cccccccc-1111-4222-8333-444444444444");
    private static final UUID SANCION = UUID.fromString("dddddddd-1111-4222-8333-444444444444");
    private static final ZoneId ZONA = ZoneId.of("UTC");
    private static final LocalDateTime SOLICITADA = LocalDateTime.of(2026, 9, 1, 10, 0);
    /** Un dia despues del vencimiento. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);
    private static final String AVATAR = "/avatares-subidos/0f0f0f0f-aaaa-4bbb-8ccc-123456789abc.png";
    private static final String CLAVE_ANTERIOR = "Clave.Anterior-9";

    private final PasswordEncoder cifrador = new BCryptPasswordEncoder(4);

    private SolicitudDeCierreRepository solicitudes;
    private UsuarioRepository usuarios;
    private PerfilUsuarioRepository perfiles;
    private PreguntaSeguridadRepository preguntas;
    private AvatarStorageService avatares;
    private AuditoriaDeCuenta auditoria;
    private AnonimizadorDeCuentas anonimizador;
    private SolicitudDeCierre solicitud;
    private Usuario ada;
    private RolEntity jugador;

    @BeforeEach
    void preparar() {
        solicitudes = mock(SolicitudDeCierreRepository.class);
        usuarios = mock(UsuarioRepository.class);
        perfiles = mock(PerfilUsuarioRepository.class);
        preguntas = mock(PreguntaSeguridadRepository.class);
        avatares = mock(AvatarStorageService.class);
        auditoria = mock(AuditoriaDeCuenta.class);
        anonimizador = new AnonimizadorDeCuentas(solicitudes, usuarios, perfiles, preguntas, avatares, auditoria,
                mock(PlatformTransactionManager.class), cifrador, RELOJ);

        solicitud = SolicitudDeCierre.programar(UID, SOLICITADA);
        when(solicitudes.bloquear(solicitud.getId())).thenReturn(Optional.of(solicitud));

        jugador = new RolEntity();
        ada = new Usuario();
        ada.setId(8L);
        ada.setPublicId(UID);
        ada.setApodo("ada_lovelace");
        ada.setEmail("ada@upb.edu.co");
        ada.setPassword(cifrador.encode(CLAVE_ANTERIOR));
        ada.setEstado(EstadoCuenta.ACTIVO);
        ada.setRol(jugador);
        ada.setIntentosFallidos(2);
        ada.setBloqueadoHasta(AHORA.plusMinutes(5));
        ada.setSuspendidoHasta(AHORA.plusDays(1));
        ada.setSancionId(SANCION);
        ada.setVersionToken(3);
        ada.setCreadoEn(LocalDateTime.of(2026, 8, 20, 9, 0));
        ada.setUltimoAcceso(LocalDateTime.of(2026, 9, 30, 21, 0));
        when(usuarios.bloquearPorIdentificadorPublico(UID)).thenReturn(Optional.of(ada));

        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setUsuario(ada);
        perfil.setNombres("Ada");
        perfil.setApellidos("Lovelace");
        perfil.setAvatar(AVATAR);
        when(perfiles.findByIdentificadorPublicoConUsuario(UID)).thenReturn(Optional.of(perfil));
    }

    @Test
    @DisplayName("vencida: borra el perfil, las preguntas, los dispositivos y los codigos")
    void borraLosDatosPersonales() {
        assertThat(anonimizador.ejecutar(solicitud.getId())).isTrue();

        verify(solicitudes).borrarPerfilDe(8L);
        verify(preguntas).borrarDe(8L);
        verify(solicitudes).borrarDispositivosDe(8L);
        verify(solicitudes).borrarCodigosDe(8L);
    }

    @Test
    @DisplayName("vencida: alias aleatorio, correo no entregable, clave inutilizable, ELIMINADO y sesiones revocadas")
    void sustituyeLaIdentidad() {
        anonimizador.ejecutar(solicitud.getId());

        assertThat(ada.getApodo()).matches("eliminado-[0-9a-f]{12}");
        assertThat(ada.getApodo()).doesNotContain("ada").doesNotContain("lovelace");
        assertThat(ada.getEmail()).isEqualTo(ada.getApodo() + "@cuenta-eliminada.invalid");
        assertThat(cifrador.matches(CLAVE_ANTERIOR, ada.getPassword())).isFalse();
        assertThat(ada.getPassword()).startsWith("$2");
        assertThat(ada.getEstado()).isEqualTo(EstadoCuenta.ELIMINADO);
        assertThat(ada.getVersionToken()).isEqualTo(4);
        assertThat(ada.getIntentosFallidos()).isZero();
        assertThat(ada.getBloqueadoHasta()).isNull();
        assertThat(ada.getSuspendidoHasta()).isNull();
        assertThat(ada.getUltimoAcceso()).isNull();
        verify(usuarios).save(ada);
    }

    @Test
    @DisplayName("conserva lo que no identifica: uid, id, rol, alta y la sancion que la referencia")
    void conservaLoQueExigeLaLey() {
        anonimizador.ejecutar(solicitud.getId());

        assertThat(ada.getPublicId()).isEqualTo(UID);
        assertThat(ada.getId()).isEqualTo(8L);
        assertThat(ada.getRol()).isSameAs(jugador);
        assertThat(ada.getCreadoEn()).isEqualTo(LocalDateTime.of(2026, 8, 20, 9, 0));
        assertThat(ada.getSancionId()).isEqualTo(SANCION);
    }

    @Test
    @DisplayName("marca la solicitud EJECUTADA y, ya confirmado, borra el archivo del avatar y audita")
    void marcaEjecutadaYAvisa() {
        anonimizador.ejecutar(solicitud.getId());

        assertThat(solicitud.getEstado()).isEqualTo(SolicitudDeCierre.EJECUTADA);
        assertThat(solicitud.getEjecutadaEn()).isEqualTo(AHORA);
        verify(solicitudes).save(solicitud);
        verify(avatares).borrarAvatar(AVATAR);
        verify(auditoria).cuentaAnonimizada(UID);

        InOrder orden = inOrder(usuarios, avatares, auditoria);
        orden.verify(usuarios).save(ada);
        orden.verify(avatares).borrarAvatar(AVATAR);
        orden.verify(auditoria).cuentaAnonimizada(UID);
    }

    @Test
    @DisplayName("dos ejecuciones dan dos alias distintos: el alias no deriva del apodo")
    void aliasAleatorio() {
        anonimizador.ejecutar(solicitud.getId());
        String primero = ada.getApodo();

        SolicitudDeCierre otra = SolicitudDeCierre.programar(UID, SOLICITADA);
        when(solicitudes.bloquear(otra.getId())).thenReturn(Optional.of(otra));
        ada.setApodo("ada_lovelace");
        anonimizador.ejecutar(otra.getId());

        assertThat(ada.getApodo()).isNotEqualTo(primero);
    }

    @Test
    @DisplayName("todavia no vencida: no toca nada")
    void noVencida() {
        SolicitudDeCierre reciente = SolicitudDeCierre.programar(UID, AHORA.minusDays(3));
        when(solicitudes.bloquear(reciente.getId())).thenReturn(Optional.of(reciente));

        assertThat(anonimizador.ejecutar(reciente.getId())).isFalse();

        assertThat(reciente.getEstado()).isEqualTo(SolicitudDeCierre.PROGRAMADA);
        verifyNoInteractions(usuarios, perfiles, preguntas, avatares, auditoria);
        verify(solicitudes, never()).borrarPerfilDe(anyLong());
    }

    @Test
    @DisplayName("idempotente: una ya ejecutada o cancelada no se vuelve a ejecutar")
    void idempotente() {
        anonimizador.ejecutar(solicitud.getId());
        String alias = ada.getApodo();

        assertThat(anonimizador.ejecutar(solicitud.getId())).isFalse();
        assertThat(ada.getApodo()).isEqualTo(alias);
        assertThat(ada.getVersionToken()).isEqualTo(4);

        SolicitudDeCierre cancelada = SolicitudDeCierre.programar(UID, SOLICITADA);
        cancelada.cancelar(SOLICITADA.plusDays(1));
        when(solicitudes.bloquear(cancelada.getId())).thenReturn(Optional.of(cancelada));
        assertThat(anonimizador.ejecutar(cancelada.getId())).isFalse();
    }

    @Test
    @DisplayName("una solicitud que ya no existe: no hace nada")
    void solicitudInexistente() {
        UUID desconocida = UUID.randomUUID();
        when(solicitudes.bloquear(desconocida)).thenReturn(Optional.empty());

        assertThat(anonimizador.ejecutar(desconocida)).isFalse();
        verifyNoInteractions(usuarios, avatares, auditoria);
    }

    @Test
    @DisplayName("la cuenta ya no existe: la solicitud se cierra sin anonimizar a nadie")
    void cuentaInexistente() {
        when(usuarios.bloquearPorIdentificadorPublico(UID)).thenReturn(Optional.empty());

        assertThat(anonimizador.ejecutar(solicitud.getId())).isFalse();

        assertThat(solicitud.getEstado()).isEqualTo(SolicitudDeCierre.EJECUTADA);
        verify(solicitudes, never()).borrarPerfilDe(anyLong());
        verifyNoInteractions(avatares, auditoria);
    }

    @Test
    @DisplayName("sin perfil (cuenta administrativa) ni avatar: se anonimiza igual")
    void sinPerfil() {
        when(perfiles.findByIdentificadorPublicoConUsuario(UID)).thenReturn(Optional.empty());

        assertThat(anonimizador.ejecutar(solicitud.getId())).isTrue();
        assertThat(ada.getEstado()).isEqualTo(EstadoCuenta.ELIMINADO);
        verify(avatares, never()).borrarAvatar(any());
    }

    @Test
    @DisplayName("si borrar el archivo o auditar falla, la anonimizacion ya hecha se mantiene")
    void avisosFailOpen() {
        doThrow(new IllegalStateException("disco")).when(avatares).borrarAvatar(AVATAR);
        doThrow(new IllegalStateException("auditoria")).when(auditoria).cuentaAnonimizada(UID);

        assertThat(anonimizador.ejecutar(solicitud.getId())).isTrue();
        assertThat(ada.getEstado()).isEqualTo(EstadoCuenta.ELIMINADO);
        assertThat(solicitud.getEstado()).isEqualTo(SolicitudDeCierre.EJECUTADA);
    }
}
