package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
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

/**
 * El filtro de cuentas de pruebas, ejecutado de verdad contra una base — RFINAL-06.
 *
 * <p>Como {@code UsuarioRepositoryDirectorioTest}: un doble no ejecuta la
 * especificacion, asi que un {@code LIKE} mal escapado o una columna mal
 * nombrada solo se ven aqui. Las cuentas llevan una marca unica por ejecucion
 * porque la base en memoria la comparten otras pruebas: se comprueba lo que la
 * consulta hace con LAS SUYAS.
 *
 * <p>Corre en la integracion continua ({@code ./mvnw -B test}, H2 en modo
 * PostgreSQL).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DirectorioDeCuentasTest {

    private static final PageRequest PEDIDO = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "id"));

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DirectorioDeCuentas directorio;
    @Autowired private CuentasDePrueba cuentasDePrueba;

    private String marca;

    @BeforeEach
    void sembrar() {
        marca = UUID.randomUUID().toString().substring(0, 8);
        RolEntity jugador = rolRepository.findByNombre("JUGADOR")
                .orElseGet(() -> rolRepository.save(new RolEntity("JUGADOR", "pruebas")));
        usuarioRepository.saveAll(List.of(
                // Personas: ni el dominio ni el prefijo.
                usuario(jugador, "Ana" + marca, "ana." + marca + "@ejemplo.org"),
                // «qa» sin guion bajo: el _ del prefijo no es un comodin.
                usuario(jugador, "qa" + marca + "x", "qax." + marca + "@ejemplo.org"),
                // Pruebas: por el dominio reservado (con mayusculas)...
                usuario(jugador, "r18_" + marca, "r18." + marca + "@NEXUS.test"),
                // ...y por el prefijo del apodo.
                usuario(jugador, "QA_prof_" + marca, "prof." + marca + "@ejemplo.org"),
                usuario(jugador, "smoke_" + marca, "smoke." + marca + "@ejemplo.org"),
                usuario(jugador, "canario_" + marca, "canario." + marca + "@ejemplo.org")));
    }

    private static Usuario usuario(RolEntity rol, String apodo, String email) {
        Usuario usuario = new Usuario();
        usuario.setApodo(apodo);
        usuario.setEmail(email);
        usuario.setPassword("hash-de-prueba");
        usuario.setEstado("ACTIVO");
        usuario.setRol(rol);
        return usuario;
    }

    private List<String> apodos(Page<Usuario> pagina) {
        return pagina.getContent().stream().map(Usuario::getApodo).filter(a -> a.contains(marca)).toList();
    }

    @Test
    void sinExcluirDaLoMismoQueLaConsultaDeSiempre() {
        Page<Usuario> nueva = directorio.findAll(BusquedaDelDirectorio.buscando(marca), PEDIDO);
        Page<Usuario> deSiempre = usuarioRepository.buscarParaDirectorio(marca, PEDIDO);

        assertEquals(6, nueva.getTotalElements());
        assertEquals(apodos(deSiempre), apodos(nueva));
    }

    @Test
    void excluyeLasCuentasDePruebasYCuentaSoloLasQueQuedan() {
        Page<Usuario> pagina = directorio.findAll(
                BusquedaDelDirectorio.buscando(marca).and(cuentasDePrueba.excluidas()), PEDIDO);

        assertEquals(List.of("qa" + marca + "x", "Ana" + marca), apodos(pagina));
        assertEquals(2, pagina.getTotalElements(), "el total es el de las que quedan, no el de la tabla");
    }

    @Test
    void laBusquedaSigueSinDistinguirMayusculas() {
        Page<Usuario> pagina = directorio.findAll(
                BusquedaDelDirectorio.buscando(("ANA" + marca).toUpperCase()).and(cuentasDePrueba.excluidas()), PEDIDO);

        assertEquals(List.of("Ana" + marca), apodos(pagina));
    }
}
