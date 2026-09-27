package com.nexusbattles.ms_identidad.sanciones;

import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Proyeccion de la sancion sobre la cuenta (B2): idempotente y sin atajos")
class ProyeccionDeSancionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final LocalDateTime HOY = LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final UUID UID = UUID.fromString("c1c1c1c1-1111-4222-8333-444444444444");
    private static final UUID SANCION = UUID.fromString("d2d2d2d2-1111-4222-8333-444444444444");
    private static final UUID OTRA = UUID.fromString("e3e3e3e3-1111-4222-8333-444444444444");
    private static final OffsetDateTime MANANA = OffsetDateTime.parse("2026-09-26T12:00:00Z");

    private UsuarioRepository usuarios;
    private CodigosDeCorreo codigos;
    private ProyeccionDeSancionService servicio;
    private Usuario cuenta;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        codigos = mock(CodigosDeCorreo.class);
        servicio = new ProyeccionDeSancionService(usuarios, codigos, Clock.fixed(AHORA, ZoneOffset.UTC));
        cuenta = new Usuario();
        cuenta.setId(12L);
        cuenta.setPublicId(UID);
        cuenta.setEstado(EstadoCuenta.ACTIVO);
        cuenta.setVersionToken(4);
        when(usuarios.bloquearPorIdentificadorPublico(UID)).thenReturn(Optional.of(cuenta));
    }

    private EstadoDeCuentaResponse proyectar(String estado, OffsetDateTime hasta, UUID sancion) {
        return servicio.proyectar(UID, new ProyeccionDeSancionRequest(estado, hasta, sancion, "motivo"));
    }

    @Test
    @DisplayName("SUSPENDIDO: estado, fin y sancion; revoca sesiones una vez; repetir no revoca otra vez")
    void suspension() {
        EstadoDeCuentaResponse respuesta = proyectar("SUSPENDIDO", MANANA, SANCION);

        assertThat(cuenta.getEstado()).isEqualTo("SUSPENDIDO");
        assertThat(cuenta.getSuspendidoHasta()).isEqualTo(HOY.plusDays(1));
        assertThat(cuenta.getSancionId()).isEqualTo(SANCION);
        assertThat(respuesta).isEqualTo(new EstadoDeCuentaResponse(UID, "SUSPENDIDO", MANANA, 5));

        proyectar("SUSPENDIDO", MANANA, SANCION);
        assertThat(cuenta.getVersionToken()).as("idempotente por sancionId + estado").isEqualTo(5);

        // Apelacion REDUCIDA: la misma sancion con otro fin.
        proyectar("SUSPENDIDO", MANANA.minusHours(12), SANCION);
        assertThat(cuenta.getSuspendidoHasta()).isEqualTo(HOY.plusHours(12));
        assertThat(cuenta.getVersionToken()).isEqualTo(5);
    }

    @Test
    @DisplayName("BANEADO: revoca sesiones; repetir no; y una suspension de OTRA sancion no lo rebaja")
    void baneo() {
        proyectar("BANEADO", null, SANCION);
        assertThat(cuenta.getEstado()).isEqualTo("BANEADO");
        assertThat(cuenta.getVersionToken()).isEqualTo(5);

        proyectar("BANEADO", null, SANCION);
        assertThat(cuenta.getVersionToken()).isEqualTo(5);

        proyectar("SUSPENDIDO", MANANA, OTRA);
        assertThat(cuenta.getEstado()).isEqualTo("BANEADO");
        assertThat(cuenta.getSancionId()).isEqualTo(SANCION);
    }

    @Test
    @DisplayName("ACTIVO levanta solo si lo produjo esa misma sancion")
    void levantamiento() {
        proyectar("SUSPENDIDO", MANANA, SANCION);

        proyectar("ACTIVO", null, OTRA);
        assertThat(cuenta.getEstado()).as("el levantamiento de otra sancion no borra esta").isEqualTo("SUSPENDIDO");

        proyectar("ACTIVO", null, SANCION);
        assertThat(cuenta.getEstado()).isEqualTo("ACTIVO");
        assertThat(cuenta.getSuspendidoHasta()).isNull();
        assertThat(cuenta.getVersionToken()).as("levantar no revoca").isEqualTo(5);
    }

    @Test
    @DisplayName("levantar una sancion no salta la verificacion del correo: vuelve a PENDIENTE si nunca la confirmo")
    void sinAtajoALaVerificacion() {
        cuenta.setEstado(EstadoCuenta.PENDIENTE_VERIFICACION);
        when(codigos.nuncaVerificada(12L)).thenReturn(true);

        proyectar("BANEADO", null, SANCION);
        assertThat(cuenta.getEstado()).isEqualTo("BANEADO");
        proyectar("ACTIVO", null, SANCION);
        assertThat(cuenta.getEstado()).isEqualTo("PENDIENTE_VERIFICACION");
    }

    @Test
    @DisplayName("una suspension que ya termino se guarda pero no revoca sesiones")
    void suspensionPasada() {
        proyectar("SUSPENDIDO", OffsetDateTime.parse("2026-09-25T11:00:00Z"), SANCION);

        assertThat(cuenta.getEstado()).isEqualTo("SUSPENDIDO");
        assertThat(cuenta.getVersionToken()).isEqualTo(4);
    }

    @Test
    @DisplayName("400: sin sancionId, estado desconocido o suspension sin fin; 404: uid que no existe")
    void invalidas() {
        assertThatThrownBy(() -> proyectar("SUSPENDIDO", MANANA, null)).isInstanceOf(ProyeccionInvalidaException.class);
        assertThatThrownBy(() -> proyectar("EXPULSADO", null, SANCION)).isInstanceOf(ProyeccionInvalidaException.class);
        assertThatThrownBy(() -> proyectar(null, null, SANCION)).isInstanceOf(ProyeccionInvalidaException.class);
        assertThatThrownBy(() -> proyectar("SUSPENDIDO", null, SANCION)).isInstanceOf(ProyeccionInvalidaException.class);
        assertThatThrownBy(() -> servicio.proyectar(UID, null)).isInstanceOf(ProyeccionInvalidaException.class);
        assertThatThrownBy(() -> servicio.aplicar(cuenta, "SUSPENDIDO", null, SANCION))
                .isInstanceOf(ProyeccionInvalidaException.class);
        assertThatThrownBy(() -> servicio.aplicar(cuenta, "OTRO", null, SANCION))
                .isInstanceOf(ProyeccionInvalidaException.class);

        UUID nadie = UUID.randomUUID();
        when(usuarios.bloquearPorIdentificadorPublico(nadie)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.proyectar(nadie, new ProyeccionDeSancionRequest("BANEADO", null, SANCION, null)))
                .isInstanceOf(CuentaNoEncontradaException.class);
        assertThat(cuenta.getEstado()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("levantar a la fuerza (panel) y al entrar con la suspension vencida")
    void levantarYVencida() {
        cuenta.setEstado("SUSPENDIDA");
        cuenta.setSuspendidoHasta(HOY.minusMinutes(1));
        assertThat(servicio.levantarSiVencida(cuenta)).isEqualTo("ACTIVO");
        assertThat(cuenta.getSuspendidoHasta()).isNull();

        cuenta.setEstado(EstadoCuenta.SUSPENDIDO);
        cuenta.setSuspendidoHasta(HOY.plusMinutes(1));
        assertThat(servicio.levantarSiVencida(cuenta)).isEqualTo("SUSPENDIDO");

        cuenta.setSuspendidoHasta(null);
        assertThat(servicio.levantarSiVencida(cuenta)).as("suspension sin fin conocido").isEqualTo("ACTIVO");

        cuenta.setEstado(EstadoCuenta.ACTIVO);
        assertThat(servicio.levantarSiVencida(cuenta)).isEqualTo("ACTIVO");

        cuenta.setEstado("BANEADA");
        servicio.levantar(cuenta);
        assertThat(cuenta.getEstado()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("el estado publicado usa los nombres del contrato aunque la fila sea anterior a B2")
    void estadoPublicado() {
        cuenta.setEstado("SUSPENDIDA");
        cuenta.setSuspendidoHasta(HOY.plusHours(1));
        assertThat(servicio.estadoDe(cuenta)).isEqualTo(new EstadoDeCuentaResponse(UID, "SUSPENDIDO",
                OffsetDateTime.parse("2026-09-25T13:00:00Z"), 4));
    }
}
