package com.nexusbattles.ms_identidad.auth.repository;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La consulta del directorio, ejecutada de verdad contra una base.
 *
 * ## Por que existe
 *
 * La primera version decia {@code WHERE :filtro IS NULL OR ...}. Compilaba, y
 * ninguna prueba con dobles lo podia detectar: un doble devuelve lo que se le
 * diga sin ejecutar JPQL. En dev, contra PostgreSQL, el controlador mandaba un
 * null sin tipo, el servidor no podia deducir el tipo del parametro, y la
 * consulta SIN filtro -- la primera pantalla que ve quien abre el directorio
 * -- devolvia 500.
 *
 * Esta prueba ejecuta la consulta. Es el unico sitio donde un error de JPQL
 * aparece antes del despliegue.
 *
 * Las cuentas se crean con una marca unica por ejecucion porque la base en
 * memoria la comparten otras pruebas del servicio: se comprueba lo que esta
 * consulta hace con LAS SUYAS, no cuantas filas hay en la tabla.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsuarioRepositoryDirectorioTest {

    private static final String SIN_FILTRO = "";

    @Autowired private UsuarioRepository usuarioRepository;

    @Autowired private RolRepository rolRepository;

    private String marca;

    @BeforeEach
    void sembrar() {
        marca = "dir" + UUID.randomUUID().toString().substring(0, 8);
        RolEntity jugador =
                rolRepository
                        .findByNombre("JUGADOR")
                        .orElseGet(() -> rolRepository.save(new RolEntity("JUGADOR", "pruebas")));
        usuarioRepository.saveAll(
                List.of(
                        usuario(jugador, "Ana" + marca, "ana." + marca + "@nexus.test"),
                        usuario(jugador, "Beto" + marca, "beto." + marca + "@nexus.test"),
                        usuario(jugador, "Carla" + marca, "carla." + marca + "@ejemplo.org")));
    }

    private Usuario usuario(RolEntity rol, String apodo, String email) {
        Usuario usuario = new Usuario();
        usuario.setApodo(apodo);
        usuario.setEmail(email);
        usuario.setPassword("hash-de-prueba");
        usuario.setEstado("ACTIVO");
        usuario.setRol(rol);
        return usuario;
    }

    /** El caso que se caia: sin filtro la consulta corre y devuelve cuentas. */
    @Test
    void sinFiltroLaConsultaCorreYDevuelveCuentas() {
        Page<Usuario> pagina =
                usuarioRepository.buscarParaDirectorio(
                        SIN_FILTRO, PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "id")));

        assertTrue(pagina.getTotalElements() >= 3, "tienen que salir al menos las tres sembradas");
        assertEquals(
                3,
                pagina.getContent().stream().filter(u -> u.getApodo().contains(marca)).count(),
                "sin filtro no se excluye ninguna cuenta");
    }

    @Test
    void filtraPorApodoSinDistinguirMayusculas() {
        Page<Usuario> pagina =
                usuarioRepository.buscarParaDirectorio(
                        ("ANA" + marca).toUpperCase(), PageRequest.of(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals("Ana" + marca, pagina.getContent().get(0).getApodo());
    }

    @Test
    void filtraTambienPorCorreo() {
        Page<Usuario> pagina =
                usuarioRepository.buscarParaDirectorio(
                        "carla." + marca + "@ejemplo.org", PageRequest.of(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals("Carla" + marca, pagina.getContent().get(0).getApodo());
    }

    @Test
    void unFiltroQueNoCoincideDevuelvePaginaVacia() {
        Page<Usuario> pagina =
                usuarioRepository.buscarParaDirectorio(
                        "nadie-" + marca, PageRequest.of(0, 10));

        assertTrue(pagina.isEmpty());
        assertEquals(0, pagina.getTotalElements());
    }

    /** Pagina de verdad: con tamano dos no devuelve las tres de golpe. */
    @Test
    void respetaElTamanoDePagina() {
        Page<Usuario> pagina =
                usuarioRepository.buscarParaDirectorio(marca, PageRequest.of(0, 2));

        assertEquals(2, pagina.getContent().size());
        assertEquals(3, pagina.getTotalElements());
        assertEquals(2, pagina.getTotalPages());
    }
}