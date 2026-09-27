package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.auth.validation.ModeracionNoDisponibleException;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.Sancion;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.SancionActiva;
import com.nexusbattles.ms_identidad.sanciones.ProyeccionDeSancionService;
import com.nexusbattles.ms_identidad.sanciones.SancionRechazadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Panel de usuarios: sanciones delegadas en moderacion-sanciones (B2)")
class AdminGestionUsuarioServiceTest {

    private static final Long USUARIO_ID = 1L;
    private static final String ADMINISTRADOR_ID = "admin";
    private static final String IP_ORIGEN = "127.0.0.1";
    private static final String ADMIN = "Bearer token-de-quien-actua";
    private static final UUID UID = UUID.fromString("dededede-1111-4222-8333-444444444444");
    private static final UUID SANCION = UUID.fromString("efefefef-1111-4222-8333-444444444444");
    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final LocalDateTime HOY = LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC);

    private AuthAdminService authAdminService;
    private PerfilUsuarioService perfilUsuarioService;
    private AuditoriaClient auditoriaClient;
    private NotificacionClient notificacionClient;
    private UsuarioRepository usuarios;
    private ModeracionSancionesClient moderacion;
    private ProyeccionDeSancionService proyecciones;
    private AdminGestionUsuarioService service;
    private Usuario cuenta;

    @BeforeEach
    void preparar() {
        authAdminService = mock(AuthAdminService.class);
        perfilUsuarioService = mock(PerfilUsuarioService.class);
        auditoriaClient = mock(AuditoriaClient.class);
        notificacionClient = mock(NotificacionClient.class);
        usuarios = mock(UsuarioRepository.class);
        moderacion = mock(ModeracionSancionesClient.class);
        proyecciones = mock(ProyeccionDeSancionService.class);
        service = new AdminGestionUsuarioService(authAdminService, perfilUsuarioService, auditoriaClient,
                notificacionClient, usuarios, moderacion, proyecciones, Clock.fixed(AHORA, ZoneOffset.UTC));
        cuenta = new Usuario();
        cuenta.setId(USUARIO_ID);
        cuenta.setPublicId(UID);
        cuenta.setEstado(EstadoCuenta.ACTIVO);
        when(usuarios.findById(USUARIO_ID)).thenReturn(Optional.of(cuenta));
    }

    private void cuentaAnterior() {
        cuenta.setPublicId(null);
    }

    // ---------------------------------------------------------------- perfil

    @Test
    void debeEditarPerfilDeUsuario() {
        PerfilUsuario perfil = new PerfilUsuario();
        when(perfilUsuarioService.actualizarPerfilPropio(USUARIO_ID, "Santiago", "Sanabria", null, "preferencias", "Santi"))
            .thenReturn(perfil);

        PerfilUsuario resultado = service.editarPerfilDeUsuario(USUARIO_ID, "Santiago", "Sanabria", null,
            "preferencias", "Santi", ADMINISTRADOR_ID, IP_ORIGEN);

        assertEquals(perfil, resultado);
        verify(auditoriaClient).registrar("ACTUALIZACION", ADMINISTRADOR_ID, "1", null,
            "nombres=Santiago, apellidos=Sanabria", "Edición administrativa de perfil", IP_ORIGEN);
    }

    // ------------------------------------------------------- via delegada (uid)

    @Test
    @DisplayName("suspender: POST /sanciones con el token de quien actua, horas hacia arriba y la proyeccion de su respuesta")
    void suspenderDelega() {
        OffsetDateTime fin = OffsetDateTime.parse("2026-09-26T13:00:00Z");
        when(moderacion.emitir(eq(ADMIN), eq(UID), eq("SUSPENSION"), anyString(), eq(25L), isNull()))
                .thenReturn(new Sancion(SANCION, "SUSPENSION", fin, true));

        service.suspenderCuenta(USUARIO_ID, HOY.plusHours(24).plusMinutes(30), null, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN);

        verify(moderacion).emitir(ADMIN, UID, "SUSPENSION", AdminGestionUsuarioService.MOTIVO_SUSPENSION, 25L, null);
        verify(proyecciones).aplicar(cuenta, EstadoCuenta.SUSPENDIDO, fin, SANCION);
        verify(auditoriaClient).registrar(eq("SUSPENSION"), eq(ADMINISTRADOR_ID), eq("1"), eq("ACTIVO"), anyString(),
                eq("Suspensión de cuenta (moderacion-sanciones)"), eq(IP_ORIGEN));
        verifyNoInteractions(notificacionClient, authAdminService);
    }

    @Test
    @DisplayName("suspender: el motivo del panel viaja tal cual; sin fin en la respuesta se usa el pedido")
    void suspenderConMotivo() {
        when(moderacion.emitir(any(), any(), any(), any(), any(), any()))
                .thenReturn(new Sancion(SANCION, "SUSPENSION", null, true));

        service.suspenderCuenta(USUARIO_ID, HOY.plusMinutes(10), "  Spam en el chat  ", ADMIN, ADMINISTRADOR_ID, IP_ORIGEN);

        verify(moderacion).emitir(ADMIN, UID, "SUSPENSION", "Spam en el chat", 1L, null);
        verify(proyecciones).aplicar(cuenta, EstadoCuenta.SUSPENDIDO,
                OffsetDateTime.parse("2026-09-25T12:10:00Z"), SANCION);
    }

    @Test
    @DisplayName("suspender: fecha pasada, 400; sin token de sesion, 403 sin llamar a nadie")
    void suspenderRechazos() {
        assertThatThrownBy(() -> service.suspenderCuenta(USUARIO_ID, HOY.minusMinutes(1), null, ADMIN,
                ADMINISTRADOR_ID, IP_ORIGEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.suspenderCuenta(USUARIO_ID, null, null, ADMIN,
                ADMINISTRADOR_ID, IP_ORIGEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.suspenderCuenta(USUARIO_ID, HOY.plusDays(1), null, null,
                ADMINISTRADOR_ID, IP_ORIGEN))
                .isInstanceOf(SancionRechazadaException.class)
                .extracting(e -> ((SancionRechazadaException) e).getEstado()).isEqualTo(403);
        verifyNoInteractions(moderacion, proyecciones);
    }

    @Test
    @DisplayName("moderacion caida: la excepcion sube (503) y aqui no se aplica nada")
    void moderacionCaida() {
        when(moderacion.emitir(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ModeracionNoDisponibleException("sin respuesta"));

        assertThatThrownBy(() -> service.banearCuenta(USUARIO_ID, null, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN))
                .isInstanceOf(ModeracionNoDisponibleException.class);
        verifyNoInteractions(proyecciones, auditoriaClient);
    }

    @Test
    @DisplayName("banear: tipo BANEO con confirmacion; si la auditoria falla despues, la sancion no se deshace")
    void banearDelega() {
        when(moderacion.emitir(ADMIN, UID, "BANEO", AdminGestionUsuarioService.MOTIVO_BANEO, null, Boolean.TRUE))
                .thenReturn(new Sancion(SANCION, "BANEO", null, true));
        doThrow(new IllegalStateException("auditoria caida")).when(auditoriaClient)
                .registrar(any(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> service.banearCuenta(USUARIO_ID, " ", ADMIN, ADMINISTRADOR_ID, IP_ORIGEN))
                .doesNotThrowAnyException();
        verify(proyecciones).aplicar(cuenta, EstadoCuenta.BANEADO, null, SANCION);
    }

    @Test
    @DisplayName("reactivar: levanta en moderacion la suspension activa y limpia la proyeccion")
    void reactivarDelega() {
        cuenta.setEstado(EstadoCuenta.SUSPENDIDO);
        when(moderacion.activa(UID)).thenReturn(new SancionActiva(true, "SUSPENSION", SANCION, null));

        service.reactivarCuenta(USUARIO_ID, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN);

        verify(moderacion).levantar(ADMIN, SANCION, AdminGestionUsuarioService.MOTIVO_REACTIVACION);
        verify(proyecciones).levantar(cuenta);
        verifyNoInteractions(notificacionClient);
    }

    @Test
    @DisplayName("reactivar: sin sancion activa alli, o ya levantada (409), solo se limpia aqui")
    void reactivarSinSancionActiva() {
        cuenta.setEstado(EstadoCuenta.SUSPENDIDO);
        when(moderacion.activa(UID)).thenReturn(new SancionActiva(false, null, null, null));
        service.reactivarCuenta(USUARIO_ID, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN);
        verify(moderacion, never()).levantar(any(), any(), any());

        when(moderacion.activa(UID)).thenReturn(new SancionActiva(true, "SUSPENSION", SANCION, null));
        when(moderacion.levantar(any(), any(), any())).thenThrow(new SancionRechazadaException(409, "ya no vigente"));
        service.reactivarCuenta(USUARIO_ID, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN);

        verify(proyecciones, org.mockito.Mockito.times(2)).levantar(cuenta);
    }

    @Test
    @DisplayName("reactivar: un baneo (aqui o en moderacion) no se levanta desde el panel; un 403 de moderacion sube")
    void reactivarRechazos() {
        cuenta.setEstado("BANEADA");
        assertThatThrownBy(() -> service.reactivarCuenta(USUARIO_ID, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AdminGestionUsuarioService.BANEO_IRREVERSIBLE);

        cuenta.setEstado(EstadoCuenta.ACTIVO);
        when(moderacion.activa(UID)).thenReturn(new SancionActiva(true, "BANEO", SANCION, null));
        assertThatThrownBy(() -> service.reactivarCuenta(USUARIO_ID, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN))
                .isInstanceOf(IllegalArgumentException.class);

        when(moderacion.activa(UID)).thenReturn(new SancionActiva(true, "SUSPENSION", SANCION, null));
        when(moderacion.levantar(any(), any(), any())).thenThrow(new SancionRechazadaException(403, "Rol insuficiente"));
        assertThatThrownBy(() -> service.reactivarCuenta(USUARIO_ID, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN))
                .isInstanceOf(SancionRechazadaException.class);
        verifyNoInteractions(proyecciones);
    }

    @Test
    @DisplayName("una cuenta que no existe: 404 (IllegalStateException), antes de llamar a nadie")
    void noExiste() {
        when(usuarios.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.banearCuenta(9L, null, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(moderacion);
    }

    // --------------------------------------------- via local (cuenta sin uid)

    @Test
    @DisplayName("cuenta sin uid: suspension local, auditoria fail-closed y aviso, como antes de B2")
    void suspenderLocal() {
        cuentaAnterior();
        LocalDateTime fecha = LocalDateTime.of(2026, 9, 30, 12, 0);

        service.suspenderCuenta(USUARIO_ID, fecha, null, ADMIN, ADMINISTRADOR_ID, IP_ORIGEN);

        verify(authAdminService).actualizarEstadoCuenta(USUARIO_ID, "SUSPENDIDO", fecha);
        verify(auditoriaClient).registrar("SUSPENSION", ADMINISTRADOR_ID, "1", "ACTIVO",
            "SUSPENDIDO hasta " + fecha, "Suspensión de cuenta", IP_ORIGEN);
        verify(notificacionClient).emitir(eq("1"), eq("SANCION"), anyString(), anyString());
        verifyNoInteractions(moderacion);
    }

    @Test
    @DisplayName("cuenta sin uid: baneo y reactivacion locales")
    void banearYReactivarLocal() {
        cuentaAnterior();
        service.banearCuenta(USUARIO_ID, null, null, ADMINISTRADOR_ID, IP_ORIGEN);
        verify(authAdminService).actualizarEstadoCuenta(USUARIO_ID, "BANEADO", null);
        verify(auditoriaClient).registrar("SANCION", ADMINISTRADOR_ID, "1", "ACTIVO", "BANEADO",
            "Baneo definitivo de cuenta", IP_ORIGEN);

        cuenta.setEstado("SUSPENDIDA");
        service.reactivarCuenta(USUARIO_ID, null, ADMINISTRADOR_ID, IP_ORIGEN);
        verify(authAdminService).actualizarEstadoCuenta(USUARIO_ID, "ACTIVO", null);
        verify(auditoriaClient).registrar("ACTUALIZACION", ADMINISTRADOR_ID, "1", "SUSPENDIDO", "ACTIVO",
            "Reactivación de cuenta", IP_ORIGEN);
        verifyNoInteractions(moderacion);
    }

    @Test
    void debeRestablecerPassword() {
        service.restablecerPassword(USUARIO_ID, ADMINISTRADOR_ID, IP_ORIGEN);

        verify(authAdminService).restablecerContrasena(USUARIO_ID);
        verify(auditoriaClient).registrar("OTRO", ADMINISTRADOR_ID, "1", null, null,
            "Restablecimiento de contraseña (código de un solo uso enviado)", IP_ORIGEN);
    }

    @Test
    @DisplayName("las horas se redondean hacia arriba y nunca bajan de una")
    void horas() {
        assertThat(service.horasHasta(HOY.plusMinutes(1))).isEqualTo(1);
        assertThat(service.horasHasta(HOY.plusMinutes(60))).isEqualTo(1);
        assertThat(service.horasHasta(HOY.plusMinutes(61))).isEqualTo(2);
        assertThat(service.horasHasta(HOY.plusDays(30))).isEqualTo(720);
    }
}
