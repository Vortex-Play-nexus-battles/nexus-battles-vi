package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.admin.dto.CuentasBloqueadasResponse;
import com.nexusbattles.ms_identidad.admin.dto.CuentasBloqueadasResponse.CuentaBloqueada;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SA-20 — §7.3.4 «Alertas de comportamiento sospechoso», señal 1 de D-45: las
 * cuentas bloqueadas por intentos fallidos en un periodo, consultadas de
 * verdad contra la base (H2 en modo PostgreSQL), con la especificacion y el
 * orden que arma el servicio.
 *
 * <p>La base la comparten otras pruebas: los bloqueos de estas cuentas van en
 * 2031 y el reloj del servicio esta fijo ahi, asi que ningun bloqueo de otra
 * prueba (de hoy) cae en el periodo.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Cuentas bloqueadas por intentos fallidos, contra la base (SA-20, D-45 señal 1)")
class CuentasBloqueadasPorIntentosEnBaseTest {

    private static final ZoneId ZONA = ZoneId.systemDefault();
    /** «Ahora» para el servicio: 10 de marzo de 2031, 12:00. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2031, 3, 10, 12, 0);

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DirectorioDeCuentas directorio;
    @Autowired private CuentasDePrueba cuentasDePrueba;

    private CuentasBloqueadasPorIntentos servicio;
    private String marca;

    @BeforeEach
    void sembrar() {
        servicio = new CuentasBloqueadasPorIntentos(directorio, cuentasDePrueba,
                Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA), 7, 366);
        marca = UUID.randomUUID().toString().substring(0, 8);
        RolEntity jugador = rol("JUGADOR");
        RolEntity moderador = rol("MODERADOR");

        // Bloqueada ahora mismo: empezo hace 5 minutos y dura 15.
        guardar(jugador, "ahora", "ahora@ejemplo.org", AHORA.minusMinutes(5), AHORA.plusMinutes(10));
        // Bloqueada anoche; ya volvio a entrar (el login borro bloqueadoHasta).
        guardar(moderador, "anoche", "anoche@ejemplo.org", AHORA.minusHours(14), null);
        // Bloqueada el primer dia del periodo por omision (4 de marzo, 00:00).
        guardar(jugador, "borde", "borde@ejemplo.org", LocalDate.of(2031, 3, 4).atStartOfDay(), null);
        // Bloqueada justo antes del periodo por omision: no entra.
        guardar(jugador, "vieja", "vieja@ejemplo.org", LocalDate.of(2031, 3, 3).atTime(23, 59), null);
        // De pruebas automaticas (dominio reservado), bloqueada ayer.
        guardar(jugador, "qa", "qa." + marca + "@nexus.test", AHORA.minusDays(1), null);
        // Nunca bloqueada.
        guardar(jugador, "nunca", "nunca@ejemplo.org", null, null);
    }

    private RolEntity rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> rolRepository.save(new RolEntity(nombre, "pruebas")));
    }

    private void guardar(RolEntity rol, String apodo, String email, LocalDateTime bloqueoEn, LocalDateTime hasta) {
        Usuario usuario = new Usuario();
        usuario.setApodo(apodo + marca);
        usuario.setEmail(email.contains(marca) ? email : marca + "." + email);
        usuario.setPassword("hash-de-prueba");
        usuario.setEstado("ACTIVO");
        usuario.setRol(rol);
        usuario.setPublicId(UUID.randomUUID());
        usuario.setUltimoBloqueoEn(bloqueoEn);
        usuario.setBloqueadoHasta(hasta);
        usuarioRepository.save(usuario);
    }

    private static java.util.List<String> apodos(CuentasBloqueadasResponse respuesta, String marca) {
        return respuesta.cuentas().stream().map(CuentaBloqueada::apodo).map(a -> a.replace(marca, "")).toList();
    }

    @Test
    @DisplayName("por omision, la ultima semana: la de bloqueo mas reciente primero, la del borde dentro, la anterior fuera")
    void ultimaSemana() {
        CuentasBloqueadasResponse respuesta = servicio.consultar(null, false, null);

        assertThat(respuesta.desde()).isEqualTo(LocalDate.of(2031, 3, 4));
        assertThat(apodos(respuesta, marca)).containsExactly("ahora", "anoche", "qa", "borde");
        assertThat(respuesta.total()).isEqualTo(4);
        assertThat(respuesta.ocultarPruebas()).isFalse();
    }

    @Test
    @DisplayName("vigente y hasta cuando: solo para la que sigue bloqueada")
    void vigencia() {
        CuentasBloqueadasResponse respuesta = servicio.consultar(null, false, null);

        CuentaBloqueada ahora = respuesta.cuentas().get(0);
        assertThat(ahora.vigente()).isTrue();
        assertThat(ahora.bloqueadaHasta()).isEqualTo(AHORA.plusMinutes(10).atZone(ZONA).toOffsetDateTime());
        assertThat(ahora.bloqueadaEn()).isEqualTo(AHORA.minusMinutes(5).atZone(ZONA).toOffsetDateTime());
        assertThat(ahora.rol()).isEqualTo("JUGADOR");
        assertThat(ahora.estado()).isEqualTo("ACTIVO");
        assertThat(ahora.uid()).isNotNull();

        CuentaBloqueada anoche = respuesta.cuentas().get(1);
        assertThat(anoche.vigente()).isFalse();
        assertThat(anoche.bloqueadaHasta()).isNull();
        assertThat(anoche.rol()).isEqualTo("MODERADOR");
    }

    @Test
    @DisplayName("sin las cuentas de pruebas automaticas, con el criterio del directorio (dominio reservado)")
    void sinPruebas() {
        CuentasBloqueadasResponse respuesta = servicio.consultar(null, true, null);

        assertThat(apodos(respuesta, marca)).containsExactly("ahora", "anoche", "borde");
        assertThat(respuesta.total()).isEqualTo(3);
        assertThat(respuesta.ocultarPruebas()).isTrue();
    }

    @Test
    @DisplayName("el limite corta la lista, no el total")
    void limite() {
        CuentasBloqueadasResponse respuesta = servicio.consultar(null, false, 2);

        assertThat(apodos(respuesta, marca)).containsExactly("ahora", "anoche");
        assertThat(respuesta.total()).isEqualTo(4);
    }

    @Test
    @DisplayName("con desde: el periodo empieza ese dia a las 00:00")
    void desdeUnDia() {
        CuentasBloqueadasResponse respuesta = servicio.consultar("2031-03-09", false, null);

        assertThat(respuesta.desde()).isEqualTo(LocalDate.of(2031, 3, 9));
        assertThat(apodos(respuesta, marca)).containsExactly("ahora", "anoche", "qa");
    }

    @Test
    @DisplayName("400 si desde es futuro o no es una fecha, si el periodo pasa del tope o si el limite sale de 1..100")
    void consultasInvalidas() {
        assertThatThrownBy(() -> servicio.consultar("2031-03-11", false, null))
                .isInstanceOf(ConsultaInvalidaException.class).hasMessageContaining("posterior a hoy");
        assertThatThrownBy(() -> servicio.consultar("ayer", false, null))
                .isInstanceOf(ConsultaInvalidaException.class).hasMessageContaining("aaaa-mm-dd");
        assertThatThrownBy(() -> servicio.consultar("2030-03-09", false, null))
                .isInstanceOf(ConsultaInvalidaException.class).hasMessageContaining("como máximo 366");
        assertThatThrownBy(() -> servicio.consultar(null, false, 0))
                .isInstanceOf(ConsultaInvalidaException.class).hasMessageContaining("«limite»");
        assertThatThrownBy(() -> servicio.consultar(null, false, 101))
                .isInstanceOf(ConsultaInvalidaException.class).hasMessageContaining("«limite»");
    }
}
