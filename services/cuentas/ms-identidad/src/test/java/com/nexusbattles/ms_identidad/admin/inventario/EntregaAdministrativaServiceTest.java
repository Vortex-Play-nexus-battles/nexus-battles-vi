package com.nexusbattles.ms_identidad.admin.inventario;

import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient.ElementoEntregado;
import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient.Entrega;
import com.nexusbattles.ms_identidad.admin.service.JerarquiaInsuficienteException;
import com.nexusbattles.ms_identidad.admin.service.UsuarioNoEncontradoException;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaNoDisponibleException;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Entrega administrativa al inventario (SA-10, §7.3.4): jerarquia, inventario, auditoria y aviso")
class EntregaAdministrativaServiceTest {

    private static final UUID UID = UUID.fromString("d1d1d1d1-1111-4222-8333-444444444444");
    private static final String TOKEN = "Bearer token-del-administrador";
    private static final String CLAVE = "c0ffee00-1111-4222-8333-444444444444";
    private static final String ADMIN = "uid-del-administrador";
    private static final String IP = "10.0.0.7";

    private UsuarioRepository usuarios;
    private InventarioDeLaCuentaClient inventario;
    private AuditoriaClient auditoria;
    private NotificacionClient notificaciones;
    private EntregaAdministrativaService servicio;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        inventario = mock(InventarioDeLaCuentaClient.class);
        auditoria = mock(AuditoriaClient.class);
        notificaciones = mock(NotificacionClient.class);
        servicio = new EntregaAdministrativaService(usuarios, inventario, auditoria, notificaciones);
    }

    private Usuario cuenta(long id, String rol, UUID uid) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setApodo("cuenta" + id);
        usuario.setRol(new RolEntity(rol, rol));
        usuario.setPublicId(uid);
        when(usuarios.findById(id)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private static Entrega entrega(int unidades, boolean nueva) {
        List<ElementoEntregado> elementos = java.util.stream.IntStream.range(0, unidades)
                .mapToObj(i -> new ElementoEntregado("el-" + i, "p-hacha", "ARMA", "Hacha de Obsidiana"))
                .toList();
        return new Entrega("e-1", UID, "ADMINISTRACION", "admin-entrega:" + CLAVE, elementos,
                OffsetDateTime.parse("2026-10-14T12:00:00Z"), nueva);
    }

    private static EntregaAdministrativaRequest pedido(int cantidad) {
        return new EntregaAdministrativaRequest(" p-hacha ", cantidad, " Compensación por la caída del 13 de octubre ");
    }

    @Test
    @DisplayName("entrega, deja la auditoria con su motivo, IP y referencia, y avisa a la cuenta")
    void entrega() {
        cuenta(7, "JUGADOR", UID);
        when(inventario.entregar(TOKEN, CLAVE, UID, "p-hacha", 2)).thenReturn(entrega(2, true));

        EntregaAdministrativaResponse respuesta =
                servicio.entregar(7L, CLAVE, pedido(2), TOKEN, ADMIN, "ADMINISTRADOR", IP);

        assertThat(respuesta.nueva()).isTrue();
        assertThat(respuesta.entregaId()).isEqualTo("e-1");
        assertThat(respuesta.cantidad()).isEqualTo(2);
        assertThat(respuesta.productoId()).isEqualTo("p-hacha");
        verify(auditoria).registrar("ACTUALIZACION", ADMIN, UID.toString(), null,
                "Inventario: 2 × Hacha de Obsidiana (entrega e-1)",
                "Compensación por la caída del 13 de octubre", IP,
                "ms-identidad:entrega-administrativa:" + CLAVE);
        verify(notificaciones).emitir(UID.toString(), "CUENTA", "Recibiste productos en tu inventario",
                "La administración agregó 2 × Hacha de Obsidiana a tu inventario.");
    }

    @Test
    @DisplayName("repetir con la misma clave: la entrega original, la auditoria con la misma referencia y sin otro aviso")
    void repeticion() {
        cuenta(7, "JUGADOR", UID);
        when(inventario.entregar(TOKEN, CLAVE, UID, "p-hacha", 1)).thenReturn(entrega(1, false));

        EntregaAdministrativaResponse respuesta =
                servicio.entregar(7L, CLAVE, pedido(1), TOKEN, ADMIN, "ADMINISTRADOR", IP);

        assertThat(respuesta.nueva()).isFalse();
        verify(auditoria).registrar(eq("ACTUALIZACION"), eq(ADMIN), eq(UID.toString()), any(), anyString(),
                anyString(), eq(IP), eq("ms-identidad:entrega-administrativa:" + CLAVE));
        verifyNoInteractions(notificaciones);
    }

    @Test
    @DisplayName("7.3.1: un Administrador no toca el inventario de otro Administrador; un Super Administrador, de otro, si")
    void jerarquia() {
        cuenta(8, "ADMINISTRADOR", UID);
        assertThatThrownBy(() -> servicio.entregar(8L, CLAVE, pedido(1), TOKEN, ADMIN, "ADMINISTRADOR", IP))
                .isInstanceOf(JerarquiaInsuficienteException.class);
        verifyNoInteractions(inventario, auditoria);

        cuenta(9, "SUPER_ADMINISTRADOR", UID);
        when(inventario.entregar(any(), any(), any(), any(), anyInt())).thenReturn(entrega(1, true));
        assertThat(servicio.entregar(9L, CLAVE, pedido(1), TOKEN, ADMIN, "SUPER_ADMINISTRADOR", IP).nueva())
                .isTrue();
    }

    @Test
    @DisplayName("una cuenta que no existe, una sin uid, una clave mala o sin token: nada se entrega")
    void rechazosPrevios() {
        when(usuarios.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.entregar(404L, CLAVE, pedido(1), TOKEN, ADMIN, "ADMINISTRADOR", IP))
                .isInstanceOf(UsuarioNoEncontradoException.class);

        cuenta(10, "JUGADOR", null);
        assertThatThrownBy(() -> servicio.entregar(10L, CLAVE, pedido(1), TOKEN, ADMIN, "ADMINISTRADOR", IP))
                .isInstanceOf(CuentaSinInventarioException.class);

        cuenta(7, "JUGADOR", UID);
        for (String mala : new String[] {null, "", "corta", "con espacios dentro", "x".repeat(65)}) {
            assertThatThrownBy(() -> servicio.entregar(7L, mala, pedido(1), TOKEN, ADMIN, "ADMINISTRADOR", IP))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> servicio.entregar(7L, CLAVE, pedido(1), null, ADMIN, "ADMINISTRADOR", IP))
                .isInstanceOf(EntregaRechazadaException.class)
                .satisfies(e -> assertThat(((EntregaRechazadaException) e).getEstado()).isEqualTo(403));
        verifyNoInteractions(inventario, auditoria, notificaciones);
    }

    @Test
    @DisplayName("si el inventario dice que no, no hay auditoria ni aviso")
    void inventarioRechaza() {
        cuenta(7, "JUGADOR", UID);
        when(inventario.entregar(any(), any(), any(), any(), anyInt()))
                .thenThrow(new EntregaRechazadaException(409, "El producto esta suspendido."));

        assertThatThrownBy(() -> servicio.entregar(7L, CLAVE, pedido(1), TOKEN, ADMIN, "ADMINISTRADOR", IP))
                .isInstanceOf(EntregaRechazadaException.class);
        verifyNoInteractions(auditoria, notificaciones);
    }

    @Test
    @DisplayName("§7.3.5 fail-closed: sin bitacora, 503 y sin aviso; repetir con la misma clave lo completa")
    void bitacoraCaida() {
        cuenta(7, "JUGADOR", UID);
        when(inventario.entregar(TOKEN, CLAVE, UID, "p-hacha", 1))
                .thenReturn(entrega(1, true))
                .thenReturn(entrega(1, false));
        doThrow(new AuditoriaNoDisponibleException("sin bitacora"))
                .doNothing()
                .when(auditoria).registrar(anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                        anyString(), anyString());

        assertThatThrownBy(() -> servicio.entregar(7L, CLAVE, pedido(1), TOKEN, ADMIN, "ADMINISTRADOR", IP))
                .isInstanceOf(AuditoriaNoDisponibleException.class);
        verify(notificaciones, never()).emitir(anyString(), anyString(), anyString(), anyString());

        EntregaAdministrativaResponse repetida =
                servicio.entregar(7L, CLAVE, pedido(1), TOKEN, ADMIN, "ADMINISTRADOR", IP);
        assertThat(repetida.nueva()).isFalse();
        verify(auditoria, times(2)).registrar(anyString(), anyString(), anyString(), any(), anyString(),
                anyString(), anyString(), eq("ms-identidad:entrega-administrativa:" + CLAVE));
    }

    @Test
    @DisplayName("el resumen usa el nombre del catalogo y, sin el, el identificador del producto")
    void resumen() {
        assertThat(EntregaAdministrativaService.resumen(entrega(3, true), "p-hacha"))
                .isEqualTo("3 × Hacha de Obsidiana");
        Entrega sinNombre = new Entrega("e-2", UID, "ADMINISTRACION", "r",
                List.of(new ElementoEntregado("el-0", "p-x", "ITEM", null)), null, true);
        assertThat(EntregaAdministrativaService.resumen(sinNombre, "p-x")).isEqualTo("1 × p-x");
    }
}
