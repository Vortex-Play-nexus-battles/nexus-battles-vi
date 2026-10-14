package com.nexusbattles.ms_identidad.auth.sesion;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 7.3.9 — la consola renueva el token mientras se trabaja. Renovar conserva la
 * autenticacion original y nunca resucita lo que ya no deberia valer.
 */
@DisplayName("Renovacion de una sesion en uso (7.3.9)")
class RenovacionDeSesionServiceTest {

    private static final UUID UID = UUID.fromString("5e5e5e5e-1111-4222-8333-444444444444");

    private JwtService jwt;
    private UsuarioRepository usuarios;
    private RenovacionDeSesionService servicio;
    private Usuario cuenta;

    @BeforeEach
    void preparar() {
        jwt = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwt, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwt, "emisor", "ms-identidad");
        ReflectionTestUtils.setField(jwt, "minutosSesionAdministrativa", 15);
        ReflectionTestUtils.setField(jwt, "horasMaximasSesionAdministrativa", 12);
        usuarios = mock(UsuarioRepository.class);
        servicio = new RenovacionDeSesionService(jwt, usuarios, Clock.systemUTC());

        cuenta = new Usuario();
        cuenta.setId(3L);
        cuenta.setPublicId(UID);
        cuenta.setApodo("ana_admin");
        cuenta.setEstado("ACTIVO");
        cuenta.setVersionToken(2);
        cuenta.setRol(new RolEntity("ADMINISTRADOR", "Administrador"));
        when(usuarios.findByPublicId(UID)).thenReturn(Optional.of(cuenta));
    }

    private String tokenAutenticadoHace(Duration hace) {
        return "Bearer " + jwt.generarToken("ana_admin", "ADMINISTRADOR", 2, UID, List.of("pwd", "otp"),
            Instant.now().minus(hace));
    }

    @Test
    @DisplayName("emite un token nuevo de 15 minutos con el mismo auth_time y el mismo amr")
    void renueva() {
        String original = tokenAutenticadoHace(Duration.ofMinutes(40));
        Instant autenticado = JwtService.autenticadoEn(jwt.validarYObtenerClaims(original.substring(7)));

        RenovacionDeSesionService.SesionRenovada sesion = servicio.renovar(original);

        Claims claims = jwt.validarYObtenerClaims(sesion.token());
        assertThat(JwtService.autenticadoEn(claims)).isEqualTo(autenticado);
        assertThat(JwtService.metodosDe(claims)).containsExactly("pwd", "otp");
        assertThat(claims.get("ver", Integer.class)).isEqualTo(2);
        assertThat(Duration.between(Instant.now(), sesion.expiraEn()))
            .isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("sin cabecera, o con un token que no es nuestro, no hay nada que renovar")
    void sinTokenValido() {
        assertThatThrownBy(() -> servicio.renovar(null)).isInstanceOf(SesionNoRenovableException.class);
        assertThatThrownBy(() -> servicio.renovar("Basic abc")).isInstanceOf(SesionNoRenovableException.class);
        assertThatThrownBy(() -> servicio.renovar("Bearer no-es-un-jwt"))
            .isInstanceOf(SesionNoRenovableException.class);
    }

    @Test
    @DisplayName("una version superada (rol cambiado, sancion, segundo factor restablecido) no se renueva")
    void versionSuperada() {
        String token = tokenAutenticadoHace(Duration.ofMinutes(5));
        cuenta.setVersionToken(3);

        assertThatThrownBy(() -> servicio.renovar(token)).isInstanceOf(SesionNoRenovableException.class);
    }

    @Test
    @DisplayName("una cuenta suspendida o baneada no continua su sesion")
    void cuentaNoActiva() {
        String token = tokenAutenticadoHace(Duration.ofMinutes(5));
        cuenta.setEstado("SUSPENDIDO");

        assertThatThrownBy(() -> servicio.renovar(token)).isInstanceOf(SesionNoRenovableException.class);
    }

    @Test
    @DisplayName("pasadas 12 h desde que se autentico, hay que volver a entrar con clave y segundo factor")
    void duracionMaxima() {
        // Se firma hace 12 h y 1 min con un emisor de pruebas cuyo token aun no
        // caduca: lo que se comprueba es el auth_time, no la expiracion.
        ReflectionTestUtils.setField(jwt, "horasMaximasSesionAdministrativa", 13);
        String token = tokenAutenticadoHace(Duration.ofHours(12).plusMinutes(1));
        ReflectionTestUtils.setField(jwt, "horasMaximasSesionAdministrativa", 12);

        assertThatThrownBy(() -> servicio.renovar(token)).isInstanceOf(SesionNoRenovableException.class);
    }

    @Test
    @DisplayName("la cuenta que ya no existe no se renueva")
    void sinTitular() {
        String token = tokenAutenticadoHace(Duration.ofMinutes(5));
        when(usuarios.findByPublicId(UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.renovar(token)).isInstanceOf(SesionNoRenovableException.class);
    }
}
