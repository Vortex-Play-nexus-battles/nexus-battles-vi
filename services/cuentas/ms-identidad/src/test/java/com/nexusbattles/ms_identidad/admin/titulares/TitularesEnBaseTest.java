package com.nexusbattles.ms_identidad.admin.titulares;

import com.nexusbattles.ms_identidad.admin.directorio.DirectorioDeCuentas;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las dos consultas de los titulares contra la base (H2 en modo PostgreSQL,
 * como {@code FiltrosDelDirectorioEnBaseTest}): un doble no ejecuta un
 * {@code IN} ni un {@code LIKE} con escape.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Titulares de cuentas en la base (SA-4)")
class TitularesEnBaseTest {

    private static final PageRequest PEDIDO = PageRequest.of(0, 50, Sort.by("apodo"));

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DirectorioDeCuentas directorio;

    private String marca;
    private Usuario ana;
    private Usuario beto;
    private Usuario porcentaje;

    @BeforeEach
    void sembrar() {
        marca = "t" + UUID.randomUUID().toString().substring(0, 8).replaceAll("[0-9-]", "x");
        RolEntity jugador = rolRepository.findByNombre("JUGADOR")
                .orElseGet(() -> rolRepository.save(new RolEntity("JUGADOR", "pruebas")));
        ana = usuarioRepository.save(usuario(jugador, "Ana" + marca));
        beto = usuarioRepository.save(usuario(jugador, "Beto" + marca));
        porcentaje = usuarioRepository.save(usuario(jugador, "Cien%" + marca));
    }

    private static Usuario usuario(RolEntity rol, String apodo) {
        Usuario usuario = new Usuario();
        usuario.setApodo(apodo);
        usuario.setEmail(apodo.replace("%", "") + "@ejemplo.org");
        usuario.setPassword("hash-de-prueba");
        usuario.setEstado("ACTIVO");
        usuario.setRol(rol);
        return usuario;
    }

    private List<String> apodos(Specification<Usuario> consulta) {
        return directorio.findAll(consulta, PEDIDO).getContent().stream().map(Usuario::getApodo)
                .filter(a -> a.contains(marca)).map(a -> a.replace(marca, "")).toList();
    }

    @Test
    @DisplayName("por uids: exactamente esas cuentas, y un uid de nadie no aparece")
    void porUids() {
        LinkedHashSet<UUID> uids = new LinkedHashSet<>(List.of(beto.getPublicId(), ana.getPublicId(),
                UUID.randomUUID()));
        assertThat(apodos(TitularesDelListado.solo(uids))).containsExactly("Ana", "Beto");
    }

    @Test
    @DisplayName("por apodo: parte del apodo, sin distinguir mayusculas")
    void porApodo() {
        assertThat(apodos(AdminTitularesController.porApodoOUid(("ANA" + marca).toUpperCase())))
                .containsExactly("Ana");
        assertThat(apodos(AdminTitularesController.porApodoOUid(marca))).containsExactly("Ana", "Beto", "Cien%");
    }

    @Test
    @DisplayName("un % escrito se busca como %, no como comodin")
    void porcentajeLiteral() {
        assertThat(apodos(AdminTitularesController.porApodoOUid("%" + marca))).containsExactly("Cien%");
    }

    @Test
    @DisplayName("un uid completo encuentra su cuenta aunque el apodo no lo contenga")
    void porUid() {
        assertThat(directorio.findAll(AdminTitularesController.porApodoOUid(beto.getPublicId().toString()), PEDIDO)
                .getContent()).extracting(Usuario::getApodo).containsExactly("Beto" + marca);
        assertThat(porcentaje.getPublicId()).isNotNull();
    }
}
