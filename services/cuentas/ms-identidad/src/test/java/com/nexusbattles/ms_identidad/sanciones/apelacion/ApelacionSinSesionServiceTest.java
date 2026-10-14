package com.nexusbattles.ms_identidad.sanciones.apelacion;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso.Proposito;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafiosDeAcceso;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.ApelacionDeLaCuenta;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.EstadoDeApelacion;
import com.nexusbattles.ms_identidad.sanciones.SancionRechazadaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SA-4 — el codigo del login abre la consulta y UNA apelacion, en nombre de
 * la cuenta y solo de ella; nada mas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Apelacion sin sesion · el codigo del login, la cuenta y moderacion")
class ApelacionSinSesionServiceTest {

    private static final UUID UID = UUID.fromString("6f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b");
    private static final UUID SANCION = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-10-14T15:00:00Z");

    @Mock DesafiosDeAcceso desafios;
    @Mock UsuarioRepository usuarios;
    @Mock ModeracionSancionesClient moderacion;
    @InjectMocks ApelacionSinSesionService servicio;

    private static Usuario cuenta(int version) {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setPublicId(UID);
        usuario.setVersionToken(version);
        return usuario;
    }

    private static DesafioDeAcceso codigo(int version) {
        return new DesafioDeAcceso(7L, "resumen", Proposito.APELAR, version, LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(30));
    }

    private static ApelacionDeLaCuenta apelacion() {
        return new ApelacionDeLaCuenta(UUID.randomUUID(), SANCION, "PENDIENTE", "No fui yo", AHORA,
                AHORA.plusDays(21), false, null, null, null);
    }

    @Test
    @DisplayName("consultar: la sancion de ESA cuenta, sin gastar el codigo")
    void consultar() {
        DesafioDeAcceso desafio = codigo(3);
        EstadoDeApelacion estado = new EstadoDeApelacion(null, null, false, "SIN_SANCION", null);
        when(desafios.vigente("valor", Proposito.APELAR)).thenReturn(desafio);
        when(usuarios.findById(7L)).thenReturn(Optional.of(cuenta(3)));
        when(moderacion.estadoDeApelacion(UID)).thenReturn(estado);

        assertThat(servicio.consultar("valor")).isSameAs(estado);
        verify(desafios, never()).consumir(any());
    }

    @Test
    @DisplayName("apelar: en nombre de la cuenta, la sancion que vio y el argumento limpio; el exito gasta el codigo")
    void apelar() {
        DesafioDeAcceso desafio = codigo(3);
        ApelacionDeLaCuenta creada = apelacion();
        when(desafios.vigente("valor", Proposito.APELAR)).thenReturn(desafio);
        when(usuarios.findById(7L)).thenReturn(Optional.of(cuenta(3)));
        when(moderacion.apelarSinSesion(UID, SANCION, "No fui yo")).thenReturn(creada);

        assertThat(servicio.apelar("valor", SANCION, "  No fui yo  ")).isSameAs(creada);
        verify(desafios).consumir(desafio);
    }

    @Test
    @DisplayName("si moderacion dice que no, el codigo sigue sirviendo para ver por que")
    void rechazoNoGasta() {
        DesafioDeAcceso desafio = codigo(3);
        when(desafios.vigente("valor", Proposito.APELAR)).thenReturn(desafio);
        when(usuarios.findById(7L)).thenReturn(Optional.of(cuenta(3)));
        when(moderacion.apelarSinSesion(any(), any(), any()))
                .thenThrow(new SancionRechazadaException(422, "ya hay una apelacion abierta"));

        assertThatThrownBy(() -> servicio.apelar("valor", SANCION, "otra vez"))
                .isInstanceOf(SancionRechazadaException.class);
        verify(desafios, never()).consumir(any());
    }

    @Test
    @DisplayName("un codigo caducado, gastado o de otro proposito: 401 sin preguntar a moderacion")
    void codigoInvalido() {
        when(desafios.vigente("valor", Proposito.APELAR)).thenThrow(SegundoFactorRechazadoException.desafioInvalido());

        assertThatThrownBy(() -> servicio.consultar("valor")).isInstanceOf(CodigoDeApelacionInvalidoException.class)
                .hasMessage(CodigoDeApelacionInvalidoException.MENSAJE);
        assertThatThrownBy(() -> servicio.apelar("valor", SANCION, "x"))
                .isInstanceOf(CodigoDeApelacionInvalidoException.class);
        verifyNoInteractions(moderacion);
    }

    @Test
    @DisplayName("si la cuenta cambio de contrasena o de rol desde el login, el codigo ya no vale")
    void otraVersion() {
        when(desafios.vigente("valor", Proposito.APELAR)).thenReturn(codigo(3));
        when(usuarios.findById(7L)).thenReturn(Optional.of(cuenta(4)));

        assertThatThrownBy(() -> servicio.consultar("valor")).isInstanceOf(CodigoDeApelacionInvalidoException.class);
        verifyNoInteractions(moderacion);
    }

    @Test
    @DisplayName("una cuenta que ya no existe o sin uid: 401")
    void sinCuenta() {
        when(desafios.vigente("valor", Proposito.APELAR)).thenReturn(codigo(3));
        when(usuarios.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.consultar("valor")).isInstanceOf(CodigoDeApelacionInvalidoException.class);

        Usuario sinUid = cuenta(3);
        sinUid.setPublicId(null);
        when(usuarios.findById(7L)).thenReturn(Optional.of(sinUid));
        assertThatThrownBy(() -> servicio.consultar("valor")).isInstanceOf(CodigoDeApelacionInvalidoException.class);
        verifyNoInteractions(moderacion);
    }
}
