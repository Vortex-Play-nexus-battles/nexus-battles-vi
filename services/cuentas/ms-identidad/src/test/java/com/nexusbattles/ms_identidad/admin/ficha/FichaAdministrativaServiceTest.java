package com.nexusbattles.ms_identidad.admin.ficha;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.sanciones.CuentaNoEncontradaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-USR-010 — la ficha administrativa de una cuenta y la auditoria de su
 * consulta (ms-identidad-admin.yaml 1.4.0).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FichaAdministrativaServiceTest {

    private static final UUID UID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 12, 0);
    private static final Clock RELOJ = Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private PerfilUsuarioRepository perfiles;

    @Mock
    private AuditoriaClient auditoria;

    private FichaAdministrativaService servicio;
    private Usuario cuenta;

    @BeforeEach
    void preparar() {
        servicio = new FichaAdministrativaService(usuarios, perfiles, auditoria, RELOJ);
        cuenta = new Usuario();
        cuenta.setId(15L);
        cuenta.setPublicId(UID);
        cuenta.setApodo("Ana");
        cuenta.setEmail("ana@nexus.test");
        cuenta.setPassword("$2a$10$hash-que-no-debe-salir");
        cuenta.setEstado("ACTIVO");
        cuenta.setRol(new RolEntity("JUGADOR", "Jugador"));
        cuenta.setCreadoEn(LocalDateTime.of(2026, 9, 1, 10, 30));
        cuenta.setUltimoAcceso(LocalDateTime.of(2026, 10, 4, 21, 15));
        when(usuarios.findById(15L)).thenReturn(Optional.of(cuenta));
        when(usuarios.findByPublicId(UID)).thenReturn(Optional.of(cuenta));
        when(perfiles.findByIdConUsuario(15L)).thenReturn(Optional.of(perfilDe(cuenta)));
    }

    private static PerfilUsuario perfilDe(Usuario usuario) {
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setId(usuario.getId());
        perfil.setUsuario(usuario);
        perfil.setNombres("Ana María");
        perfil.setApellidos("Rueda");
        perfil.setAvatar("/avatares-subidos/ana.png");
        return perfil;
    }

    @Test
    void porLaClaveInternaDevuelveLaFichaYDejaLaConsultaEnLaAuditoria() {
        FichaAdministrativa ficha = servicio.consultar("15", "simon_superadmin", "203.0.113.5");

        assertEquals(15L, ficha.id());
        assertEquals(UID, ficha.uid());
        assertEquals("Ana", ficha.apodo());
        assertEquals("ana@nexus.test", ficha.email());
        assertEquals("Ana María", ficha.nombres());
        assertEquals("Rueda", ficha.apellidos());
        assertEquals("/avatares-subidos/ana.png", ficha.avatar());
        assertEquals("JUGADOR", ficha.rol());
        assertEquals("ACTIVO", ficha.estado());
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 30), ficha.creadoEn());
        assertEquals(LocalDateTime.of(2026, 10, 4, 21, 15), ficha.ultimoAcceso());
        assertFalse(ficha.bloqueada());
        assertTrue(ficha.accesoAuditado());
        // Mismo afectado que el resto de acciones del panel (la clave interna),
        // quien consulta, sin valores (no cambia nada) y la IP de origen.
        verify(auditoria).registrar("OTRO", "simon_superadmin", "15", null, null,
                FichaAdministrativaService.MOTIVO, "203.0.113.5");
    }

    @Test
    void porElUidBuscaPorElIdentificadorPublico() {
        FichaAdministrativa ficha = servicio.consultar(UID.toString(), "admin", "10.0.0.1");

        assertEquals(15L, ficha.id());
        verify(usuarios).findByPublicId(UID);
        verify(usuarios, never()).findById(anyLong());
        verify(auditoria).registrar("OTRO", "admin", "15", null, null, FichaAdministrativaService.MOTIVO, "10.0.0.1");
    }

    @Test
    void unaCuentaSinPerfilSaleConLosNombresVacios() {
        when(perfiles.findByIdConUsuario(15L)).thenReturn(Optional.empty());

        FichaAdministrativa ficha = servicio.consultar("15", "admin", "10.0.0.1");

        assertNull(ficha.nombres());
        assertNull(ficha.apellidos());
        assertNull(ficha.avatar());
        assertEquals("Ana", ficha.apodo());
    }

    @Test
    void unaCuentaQueNoExisteEsCuentaNoEncontradaYNoSeAudita() {
        when(usuarios.findById(99L)).thenReturn(Optional.empty());
        UUID otro = UUID.fromString("99999999-2222-3333-4444-555555555555");
        when(usuarios.findByPublicId(otro)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class, () -> servicio.consultar("99", "admin", "10.0.0.1"));
        assertThrows(CuentaNoEncontradaException.class,
                () -> servicio.consultar(otro.toString(), "admin", "10.0.0.1"));
        verifyNoInteractions(auditoria);
    }

    @Test
    void loQueNoEsNiClaveNiUidNoNombraANadieNiSeConsulta() {
        for (String raro : new String[] {"abc", "", "  ", "-15", "1.5", "1234567890123456789",
            "11111111-2222-3333-4444-55555555555", "1-1-1-1-1", "15; drop table usuarios"}) {
            assertThrows(CuentaNoEncontradaException.class, () -> servicio.consultar(raro, "admin", "10.0.0.1"),
                    () -> "«" + raro + "» no deberia nombrar a ninguna cuenta");
        }
        assertThrows(CuentaNoEncontradaException.class, () -> servicio.consultar(null, "admin", "10.0.0.1"));
        verify(usuarios, never()).findById(anyLong());
        verify(usuarios, never()).findByPublicId(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void siLaBitacoraNoRespondeLaFichaSaleIgualDiciendoQueNoQuedoAuditada() {
        doThrow(new IllegalStateException("No se pudo completar la operación: el servicio de auditoría no respondió."))
                .when(auditoria).registrar(any(), any(), any(), any(), any(), any(), any());

        FichaAdministrativa ficha = servicio.consultar("15", "admin", "10.0.0.1");

        assertEquals("Ana", ficha.apodo());
        assertFalse(ficha.accesoAuditado());
    }

    @Test
    void sinAdministradorNiIpLaAuditoriaRecibeValoresExplicitos() {
        // ms-cumplimiento exige administrador e IP: un nulo se convertiria en
        // un 503 de la bitacora y la consulta quedaria sin registrar.
        servicio.consultar("15", null, " ");

        verify(auditoria).registrar("OTRO", FichaAdministrativaService.DESCONOCIDO, "15", null, null,
                FichaAdministrativaService.MOTIVO, FichaAdministrativaService.DESCONOCIDA);
    }

    @Test
    void elEstadoSaleConElNombreDelContratoYLaSuspensionConSuFin() {
        cuenta.setEstado("SUSPENDIDA");
        cuenta.setSuspendidoHasta(LocalDateTime.of(2026, 10, 9, 8, 0));

        FichaAdministrativa ficha = servicio.consultar("15", "admin", "10.0.0.1");

        assertEquals("SUSPENDIDO", ficha.estado());
        assertEquals(LocalDateTime.of(2026, 10, 9, 8, 0), ficha.suspendidoHasta());
    }

    @Test
    void elBloqueoPorIntentosSoloCuentaMientrasSigueVigente() {
        cuenta.setBloqueadoHasta(AHORA.plusMinutes(10));
        assertTrue(servicio.consultar("15", "admin", "10.0.0.1").bloqueada());

        cuenta.setBloqueadoHasta(AHORA.minusMinutes(1));
        assertFalse(servicio.consultar("15", "admin", "10.0.0.1").bloqueada());
    }

    @Test
    void unaCuentaSinRolNiUidSeDescribeSinInventar() {
        cuenta.setRol(null);
        cuenta.setPublicId(null);

        FichaAdministrativa ficha = servicio.consultar("15", "admin", "10.0.0.1");

        assertNull(ficha.rol());
        assertNull(ficha.uid());
    }

    @Test
    void laFichaNoPublicaNiLaContrasenaNiLaVersionDeToken() {
        Set<String> campos = Arrays.stream(FichaAdministrativa.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("id", "uid", "apodo", "email", "nombres", "apellidos", "avatar", "rol", "estado",
                "suspendidoHasta", "bloqueada", "creadoEn", "ultimoAcceso", "accesoAuditado"), campos);
    }

    @Test
    void elRelojPorOmisionEsElDelSistema() {
        FichaAdministrativaService porOmision = new FichaAdministrativaService(usuarios, perfiles, auditoria);
        cuenta.setBloqueadoHasta(LocalDateTime.now().plusHours(1));

        assertTrue(porOmision.consultar("15", "admin", "10.0.0.1").bloqueada());
    }
}
