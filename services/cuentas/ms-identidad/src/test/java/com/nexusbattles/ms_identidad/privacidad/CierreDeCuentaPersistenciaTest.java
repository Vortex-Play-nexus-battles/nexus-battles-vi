package com.nexusbattles.ms_identidad.privacidad;

import com.nexusbattles.ms_identidad.auth.model.DispositivoConocido;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.TokenCredencial;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.recuperacion.PreguntaSeguridad;
import com.nexusbattles.ms_identidad.auth.recuperacion.PreguntaSeguridadRepository;
import com.nexusbattles.ms_identidad.auth.repository.DispositivoConocidoRepository;
import com.nexusbattles.ms_identidad.auth.repository.TokenCredencialRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * El derecho al olvido contra una base de verdad (HU-PRV-005): las consultas
 * del repositorio y los borrados JPQL se ejecutan, no se simulan. Un doble no
 * puede detectar un JPQL que no casa con las entidades; esto si.
 *
 * <p>En las pruebas la base es H2 con el esquema de Hibernate; las restricciones
 * de V6 (indice unico parcial y plazo de 30 dias) se prueban contra PostgreSQL
 * en {@code MigracionesIT}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Derecho al olvido contra la base (HU-PRV-005)")
class CierreDeCuentaPersistenciaTest {

    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PerfilUsuarioRepository perfiles;
    @Autowired private PreguntaSeguridadRepository preguntas;
    @Autowired private DispositivoConocidoRepository dispositivos;
    @Autowired private TokenCredencialRepository codigos;
    @Autowired private SolicitudDeCierreRepository solicitudes;
    @Autowired private PlatformTransactionManager transacciones;
    @Autowired private EntityManager entidades;

    private final LocalDateTime ahora = LocalDateTime.now().withNano(0);
    private String marca;
    private Usuario ada;
    private Usuario testigo;

    @BeforeEach
    void sembrar() {
        marca = UUID.randomUUID().toString().substring(0, 8);
        RolEntity jugador = roles.findByNombre("JUGADOR")
                .orElseGet(() -> roles.save(new RolEntity("JUGADOR", "pruebas")));
        ada = cuentaCompleta(jugador, "ada" + marca);
        testigo = cuentaCompleta(jugador, "testigo" + marca);
        entidades.flush();
    }

    private Usuario cuentaCompleta(RolEntity rol, String apodo) {
        Usuario usuario = new Usuario();
        usuario.setApodo(apodo);
        usuario.setEmail(apodo + "@nexus.test");
        usuario.setPassword("hash-de-prueba");
        usuario.setEstado(EstadoCuenta.ACTIVO);
        usuario.setRol(rol);
        usuario = usuarios.save(usuario);

        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setUsuario(usuario);
        perfil.setNombres("Nombre de " + apodo);
        perfil.setApellidos("Apellido");
        perfil.setAvatar("/avatares-subidos/" + apodo + ".png");
        perfiles.save(perfil);

        preguntas.save(new PreguntaSeguridad(usuario.getId(), "¿Ciudad natal?", "resumen", 1, ahora));
        preguntas.save(new PreguntaSeguridad(usuario.getId(), "¿Primera mascota?", "resumen", 2, ahora));
        dispositivos.save(new DispositivoConocido(usuario, "huella-" + apodo));
        codigos.save(new TokenCredencial(usuario, "VERIFICACION", "resumen", ahora, ahora.plusHours(1)));
        return usuario;
    }

    private AnonimizadorDeCuentas anonimizadorA(LocalDateTime cuando) {
        Clock reloj = Clock.fixed(cuando.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return new AnonimizadorDeCuentas(solicitudes, usuarios, perfiles, preguntas,
                mock(AvatarStorageService.class), mock(AuditoriaDeCuenta.class), transacciones,
                new BCryptPasswordEncoder(4), reloj);
    }

    private long contar(String jpql, Object valor) {
        return entidades.createQuery(jpql, Long.class).setParameter("v", valor).getSingleResult();
    }

    @Test
    @DisplayName("al vencer: borra perfil, preguntas, dispositivos y codigos de esa cuenta, y solo de esa")
    void anonimizaSoloLaCuentaQueLoPidio() {
        SolicitudDeCierre solicitud = solicitudes.save(SolicitudDeCierre.programar(ada.getPublicId(),
                ahora.minusDays(31)));
        entidades.flush();

        boolean hecho = anonimizadorA(ahora).ejecutar(solicitud.getId());
        entidades.flush();
        entidades.clear();

        assertThat(hecho).isTrue();
        Usuario anonima = usuarios.findByPublicId(ada.getPublicId()).orElseThrow();
        assertThat(anonima.getApodo()).startsWith("eliminado-").doesNotContain(marca);
        assertThat(anonima.getEmail()).endsWith("@cuenta-eliminada.invalid");
        assertThat(anonima.getEstado()).isEqualTo(EstadoCuenta.ELIMINADO);
        assertThat(anonima.getVersionToken()).isEqualTo(1);
        assertThat(perfiles.findByIdentificadorPublicoConUsuario(ada.getPublicId())).isEmpty();
        assertThat(preguntas.countByUsuarioId(ada.getId())).isZero();
        assertThat(contar("select count(d) from DispositivoConocido d where d.usuario.id = :v", ada.getId())).isZero();
        assertThat(contar("select count(t) from TokenCredencial t where t.usuario.id = :v", ada.getId())).isZero();
        assertThat(solicitudes.findById(solicitud.getId()).orElseThrow().getEstado())
                .isEqualTo(SolicitudDeCierre.EJECUTADA);

        // La otra cuenta no se toca.
        Usuario intacta = usuarios.findByPublicId(testigo.getPublicId()).orElseThrow();
        assertThat(intacta.getApodo()).isEqualTo("testigo" + marca);
        assertThat(perfiles.findByIdentificadorPublicoConUsuario(testigo.getPublicId())).isPresent();
        assertThat(preguntas.countByUsuarioId(testigo.getId())).isEqualTo(2);
        assertThat(contar("select count(d) from DispositivoConocido d where d.usuario.id = :v", testigo.getId()))
                .isEqualTo(1);
        assertThat(contar("select count(t) from TokenCredencial t where t.usuario.id = :v", testigo.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("vencidas: solo las PROGRAMADA cuyo plazo ya se cumplio; programadaDe: la vigente")
    void consultasDelRepositorio() {
        SolicitudDeCierre vencida = solicitudes.save(SolicitudDeCierre.programar(ada.getPublicId(),
                ahora.minusDays(31)));
        SolicitudDeCierre vigente = solicitudes.save(SolicitudDeCierre.programar(testigo.getPublicId(),
                ahora.minusDays(2)));
        SolicitudDeCierre cancelada = SolicitudDeCierre.programar(testigo.getPublicId(), ahora.minusDays(40));
        cancelada.cancelar(ahora.minusDays(39));
        solicitudes.save(cancelada);
        entidades.flush();

        assertThat(solicitudes.vencidas(SolicitudDeCierre.PROGRAMADA, ahora, PageRequest.of(0, 50)))
                .contains(vencida.getId())
                .doesNotContain(vigente.getId(), cancelada.getId());
        assertThat(solicitudes.programadaDe(testigo.getPublicId())).map(SolicitudDeCierre::getId)
                .contains(vigente.getId());
        assertThat(solicitudes.programadaDe(UUID.randomUUID())).isEmpty();
        assertThat(solicitudes.bloquear(vencida.getId())).isPresent();
    }
}
