package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los filtros de HU-USR-008 y los conteos de los indicadores, ejecutados de
 * verdad contra una base (ms-identidad-admin.yaml 1.3.0).
 *
 * <p>Como {@code DirectorioDeCuentasTest}: un doble no ejecuta una
 * especificacion, asi que una subconsulta mal armada, una columna mal
 * nombrada o un recuento agrupado que la base no acepta solo se ven aqui. Las
 * cuentas llevan una marca unica por ejecucion porque la base en memoria la
 * comparten otras pruebas: cada consulta se acota a LAS SUYAS con la busqueda
 * por la marca.
 *
 * <p>Corre en la integracion continua ({@code ./mvnw -B test}, H2 en modo
 * PostgreSQL).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FiltrosDelDirectorioEnBaseTest {

    private static final PageRequest PEDIDO = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "id"));

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PerfilUsuarioRepository perfilRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DirectorioDeCuentas directorio;
    @Autowired private CuentasDePrueba cuentasDePrueba;
    @Autowired private ConteosDeCuentas conteos;

    private String marca;
    private Usuario ana;
    private Usuario beto;

    @BeforeEach
    void sembrar() {
        // Solo letras (las cifras del UUID pasan a g..p): asi ningun apodo,
        // correo ni nombre de esta ejecucion contiene la clave de una cuenta, y
        // buscar por esa clave solo puede encontrarla por el identificador.
        marca = "f" + UUID.randomUUID().toString().substring(0, 8).chars()
                .map(c -> Character.isDigit(c) ? 'g' + (c - '0') : c)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append);
        RolEntity jugador = rol("JUGADOR");
        RolEntity moderador = rol("MODERADOR");

        ana = usuarioRepository.save(usuario(jugador, "Ana" + marca, "ana." + marca + "@ejemplo.org",
                "ACTIVO", LocalDateTime.of(2026, 9, 1, 0, 0)));
        perfil(ana, "Ana Lucía", "Pérez Gómez");
        beto = usuarioRepository.save(usuario(moderador, "Beto" + marca, "beto." + marca + "@ejemplo.org",
                "SUSPENDIDO", LocalDateTime.of(2026, 9, 15, 23, 59)));
        perfil(beto, "Roberto", "Díaz");
        // Fila anterior a B2, en femenino: el filtro SUSPENDIDO tambien la encuentra.
        usuarioRepository.save(usuario(jugador, "Cris" + marca, "cris." + marca + "@ejemplo.org",
                "SUSPENDIDA", LocalDateTime.of(2026, 9, 30, 12, 0)));
        // Sin perfil (como las administrativas) y sin fecha de alta (anterior al campo).
        usuarioRepository.save(usuario(jugador, "Dani" + marca, "dani." + marca + "@ejemplo.org",
                "BANEADO", null));
        // De pruebas automaticas, por el dominio reservado.
        usuarioRepository.save(usuario(jugador, "Eva" + marca, "eva." + marca + "@nexus.test",
                "ACTIVO", LocalDateTime.of(2026, 9, 15, 8, 0)));
    }

    private RolEntity rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> rolRepository.save(new RolEntity(nombre, "pruebas")));
    }

    private static Usuario usuario(RolEntity rol, String apodo, String email, String estado, LocalDateTime creadoEn) {
        Usuario usuario = new Usuario();
        usuario.setApodo(apodo);
        usuario.setEmail(email);
        usuario.setPassword("hash-de-prueba");
        usuario.setEstado(estado);
        usuario.setRol(rol);
        usuario.setCreadoEn(creadoEn);
        return usuario;
    }

    private void perfil(Usuario usuario, String nombres, String apellidos) {
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setUsuario(usuario);
        perfil.setNombres(nombres);
        perfil.setApellidos(apellidos);
        perfilRepository.save(perfil);
    }

    private List<String> apodos(FiltroDelDirectorio filtro) {
        Page<Usuario> pagina = directorio.findAll(filtro.especificacion(cuentasDePrueba), PEDIDO);
        return pagina.getContent().stream().map(Usuario::getApodo).filter(a -> a.contains(marca))
                .map(a -> a.replace(marca, "")).toList();
    }

    /** Las cuentas de esta ejecucion: el texto de la marca esta en su apodo. */
    private FiltroDelDirectorio deEstaEjecucion(String rol, String estado, String desde, String hasta) {
        return FiltroDelDirectorio.de(marca, false, rol, estado, desde, hasta);
    }

    @Test
    void laBusquedaEncuentraPorNombreYApellidosDelPerfil() {
        assertThat(apodos(FiltroDelDirectorio.de("lucía pérez", false, null, null, null, null))).containsExactly("Ana");
        assertThat(apodos(FiltroDelDirectorio.de("ROBERTO", false, null, null, null, null))).contains("Beto");
        assertThat(apodos(FiltroDelDirectorio.de("díaz", false, null, null, null, null))).contains("Beto");
    }

    @Test
    void laBusquedaSigueEncontrandoPorApodoYCorreoTambienSinPerfil() {
        assertThat(apodos(FiltroDelDirectorio.de("dani." + marca, false, null, null, null, null)))
                .containsExactly("Dani");
        assertThat(apodos(deEstaEjecucion(null, null, null, null)))
                .containsExactlyInAnyOrder("Ana", "Beto", "Cris", "Dani", "Eva");
    }

    // ------------------------------------------- HU-USR-009: por identificador

    private static FiltroDelDirectorio buscando(String texto) {
        return FiltroDelDirectorio.de(texto, false, null, null, null, null);
    }

    @Test
    void laBusquedaEncuentraPorUidExactoSinDistinguirMayusculas() {
        String uid = ana.getPublicId().toString();

        assertThat(apodos(buscando(uid))).containsExactly("Ana");
        assertThat(apodos(buscando(uid.toUpperCase()))).containsExactly("Ana");
    }

    @Test
    void laBusquedaEncuentraPorLaClaveExacta() {
        assertThat(apodos(buscando(String.valueOf(beto.getId())))).containsExactly("Beto");
    }

    @Test
    void unIdentificadorQueNoExisteNoEncuentraANadie() {
        assertThat(apodos(buscando(UUID.randomUUID().toString()))).isEmpty();
        assertThat(apodos(buscando("999999999999999999"))).isEmpty();
    }

    @Test
    void unTextoQueNoEsIdentificadorSeBuscaComoTextoSinError() {
        String uid = ana.getPublicId().toString();
        // Un uid a medias, con letras que no son hexadecimales, con llaves o
        // una clave demasiado larga para un long: texto, sin error ni 500.
        assertThat(apodos(buscando(uid.substring(0, 18)))).isEmpty();
        assertThat(apodos(buscando("zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz"))).isEmpty();
        assertThat(apodos(buscando("{" + uid + "}"))).isEmpty();
        assertThat(apodos(buscando("12345678901234567890123"))).isEmpty();
        // Y lo de siempre sigue: apodo, correo y nombre.
        assertThat(apodos(buscando("ana" + marca))).containsExactly("Ana");
        assertThat(apodos(buscando("beto." + marca + "@ejemplo"))).containsExactly("Beto");
        assertThat(apodos(buscando("lucía pérez"))).containsExactly("Ana");
    }

    @Test
    void laBusquedaPorIdentificadorSeCombinaConLosFiltros() {
        String uidDeBeto = beto.getPublicId().toString();

        assertThat(apodos(FiltroDelDirectorio.de(uidDeBeto, false, "MODERADOR", "SUSPENDIDO", null, null)))
                .containsExactly("Beto");
        assertThat(apodos(FiltroDelDirectorio.de(uidDeBeto, false, "JUGADOR", null, null, null))).isEmpty();
        String claveDeAna = String.valueOf(ana.getId());
        assertThat(apodos(FiltroDelDirectorio.de(claveDeAna, false, null, "ACTIVO", "2026-09-01", "2026-09-01")))
                .containsExactly("Ana");
        assertThat(apodos(FiltroDelDirectorio.de(claveDeAna, false, null, "BANEADO", null, null))).isEmpty();
    }

    @Test
    void filtraPorRol() {
        assertThat(apodos(deEstaEjecucion("MODERADOR", null, null, null))).containsExactly("Beto");
    }

    @Test
    void filtraPorEstadoConLasFormasAnterioresAB2() {
        assertThat(apodos(deEstaEjecucion(null, "SUSPENDIDO", null, null))).containsExactlyInAnyOrder("Beto", "Cris");
        assertThat(apodos(deEstaEjecucion(null, "BANEADO", null, null))).containsExactly("Dani");
    }

    @Test
    void filtraPorFechaDeRegistroConLosDosExtremosIncluidos() {
        assertThat(apodos(deEstaEjecucion(null, null, "2026-09-15", "2026-09-15")))
                .containsExactlyInAnyOrder("Beto", "Eva");
        assertThat(apodos(deEstaEjecucion(null, null, "2026-09-01", "2026-09-30")))
                .containsExactlyInAnyOrder("Ana", "Beto", "Cris", "Eva");
        assertThat(apodos(deEstaEjecucion(null, null, "2026-09-16", null))).containsExactly("Cris");
    }

    @Test
    void losFiltrosSeCombinanConLasCuentasDePruebas() {
        FiltroDelDirectorio filtro = FiltroDelDirectorio.de(marca, true, "JUGADOR", "ACTIVO", "2026-09-01", null);

        assertThat(apodos(filtro)).containsExactly("Ana");
        assertThat(directorio.findAll(filtro.especificacion(cuentasDePrueba), PEDIDO).getTotalElements())
                .as("el total es el de las que cumplen, no el de la tabla").isEqualTo(1);
    }

    @Test
    void conteosPorEstadoGuardadoRespetanLaEspecificacion() {
        Map<String, Long> porEstado = conteos.porEstadoGuardado(BusquedaDelDirectorio.buscando(marca));

        assertThat(porEstado).containsOnly(
                Map.entry("ACTIVO", 2L), Map.entry("SUSPENDIDO", 1L), Map.entry("SUSPENDIDA", 1L),
                Map.entry("BANEADO", 1L));

        Map<String, Long> sinPruebas = conteos.porEstadoGuardado(
                BusquedaDelDirectorio.buscando(marca).and(cuentasDePrueba.excluidas()));
        assertThat(sinPruebas).containsEntry("ACTIVO", 1L);
    }

    @Test
    void altasEntreDosInstantesSinLasCuentasSinFecha() {
        List<LocalDateTime> altas = conteos.altasEntre(BusquedaDelDirectorio.buscando(marca),
                LocalDate.of(2026, 9, 15).atStartOfDay(), LocalDate.of(2026, 10, 1).atStartOfDay());

        assertThat(altas).containsExactlyInAnyOrder(
                LocalDateTime.of(2026, 9, 15, 23, 59),
                LocalDateTime.of(2026, 9, 30, 12, 0),
                LocalDateTime.of(2026, 9, 15, 8, 0));
    }
}
