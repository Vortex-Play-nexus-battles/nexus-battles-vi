package com.nexusbattles.ms_identidad.admin.config;

import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El servicio arranca con administradores configurados.
 *
 * ## Por que existe esta prueba
 *
 * La primera version del sembrador pedia un {@code PasswordEncoder} por
 * constructor. Este servicio no publica ese bean -- RegistroService,
 * LoginService y TokenCredencialService crean cada uno el suyo -- pero nadie
 * se entero: el componente solo se construye cuando {@code ADMINS_INICIALES}
 * trae algo, y en CI esa variable esta vacia. Compilo, todas las pruebas
 * pasaron, la puerta de calidad dio verde, y el arranque se cayo en el primer
 * entorno que SI definia la variable, dejando sin autenticacion al producto
 * entero.
 *
 * El agujero no era el bean: era que ninguna prueba levantaba el contexto en
 * la configuracion real de un entorno. Esta lo hace. Cualquier dependencia
 * que el sembrador pida y el servicio no tenga se cae aqui, en CI, y no en
 * el despliegue.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "app.admins.iniciales=prueba.uno|prueba.uno@nexus.test|ClaveDePruebaLarga1"
                    + ";prueba.dos|prueba.dos@nexus.test|ClaveDePruebaLarga2"
        })
class ArranqueConAdministradoresTest {

    @Autowired private AdministradoresIniciales sembrador;

    @Autowired private UsuarioRepository usuarioRepository;

    @Test
    void elContextoLevantaConAdministradoresConfigurados() {
        assertNotNull(sembrador, "con la variable puesta, el sembrador tiene que existir");
    }

    /**
     * Y ademas siembra: el arranque deja las dos cuentas creadas, cada una
     * con su identidad. Es lo que la auditoria necesita para atribuir cada
     * accion a una persona y no a un "admin" compartido.
     */
    @Test
    void dejaCadaAdministradorComoUnaCuentaDistinta() {
        assertTrue(
                usuarioRepository.findByEmail("prueba.uno@nexus.test").isPresent(),
                "la primera cuenta administrativa tiene que quedar creada al arrancar");
        assertTrue(
                usuarioRepository.findByEmail("prueba.dos@nexus.test").isPresent(),
                "la segunda cuenta administrativa tiene que quedar creada al arrancar");
    }
}