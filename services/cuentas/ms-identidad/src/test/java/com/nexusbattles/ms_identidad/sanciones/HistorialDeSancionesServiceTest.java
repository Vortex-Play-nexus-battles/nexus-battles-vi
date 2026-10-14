package com.nexusbattles.ms_identidad.sanciones;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El resumen del historial de sanciones — ms-identidad-admin.yaml 1.7.0. */
class HistorialDeSancionesServiceTest {

    private static final UUID UID = UUID.fromString("f4f4f4f4-1111-4222-8333-444444444444");
    private static final Clock BOGOTA = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneId.of("America/Bogota"));

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final HistorialDeSancionesService servicio = new HistorialDeSancionesService(usuarios, BOGOTA);

    private Usuario cuenta() {
        Usuario usuario = new Usuario();
        usuario.setPublicId(UID);
        when(usuarios.findByPublicId(UID)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    @Test
    @DisplayName("guarda el total y la ultima en la hora del servidor, tal cual llegan (idempotente)")
    void fija() {
        Usuario usuario = cuenta();

        HistorialDeSancionesService.HistorialDeSancionesResponse respuesta = servicio.fijar(UID,
                new HistorialDeSancionesRequest(3, OffsetDateTime.parse("2026-10-10T15:00:00Z")));
        servicio.fijar(UID, new HistorialDeSancionesRequest(3, OffsetDateTime.parse("2026-10-10T15:00:00Z")));

        assertThat(usuario.getSancionesRegistradas()).isEqualTo(3);
        assertThat(usuario.getUltimaSancionEn()).isEqualTo(LocalDateTime.parse("2026-10-10T10:00:00"));
        assertThat(respuesta.total()).isEqualTo(3);
        assertThat(respuesta.uid()).isEqualTo(UID);
    }

    @Test
    @DisplayName("con cero sanciones no queda fecha de la ultima")
    void ceroSinFecha() {
        Usuario usuario = cuenta();
        usuario.setUltimaSancionEn(LocalDateTime.parse("2026-09-01T10:00:00"));

        HistorialDeSancionesService.HistorialDeSancionesResponse respuesta =
                servicio.fijar(UID, new HistorialDeSancionesRequest(0, OffsetDateTime.parse("2026-10-10T15:00:00Z")));

        assertThat(usuario.getSancionesRegistradas()).isZero();
        assertThat(usuario.getUltimaSancionEn()).isNull();
        assertThat(respuesta.ultimaEn()).isNull();
    }

    @Test
    @DisplayName("sin total, negativo, o con sanciones y sin fecha: invalido, y no se toca la cuenta")
    void invalido() {
        assertThatThrownBy(() -> servicio.fijar(UID, null)).isInstanceOf(ProyeccionInvalidaException.class);
        assertThatThrownBy(() -> servicio.fijar(UID, new HistorialDeSancionesRequest(null, null)))
                .isInstanceOf(ProyeccionInvalidaException.class);
        assertThatThrownBy(() -> servicio.fijar(UID, new HistorialDeSancionesRequest(-1, null)))
                .isInstanceOf(ProyeccionInvalidaException.class);
        assertThatThrownBy(() -> servicio.fijar(UID, new HistorialDeSancionesRequest(2, null)))
                .isInstanceOf(ProyeccionInvalidaException.class);
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("una cuenta que no existe es cuenta-no-encontrada")
    void cuentaInexistente() {
        when(usuarios.findByPublicId(UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.fijar(UID, new HistorialDeSancionesRequest(0, null)))
                .isInstanceOf(CuentaNoEncontradaException.class);
    }
}
