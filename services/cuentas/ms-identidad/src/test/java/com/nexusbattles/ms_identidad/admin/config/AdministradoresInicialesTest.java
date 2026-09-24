package com.nexusbattles.ms_identidad.admin.config;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.service.RolService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Las cuentas administrativas iniciales del entorno.
 *
 * Tres personas operan el sistema con el mismo alcance y credenciales
 * distintas. Esta clase es la que hace que eso sea cierto al arrancar, asi
 * que lo que se prueba aqui es exactamente aquello de lo que depende la
 * auditoria: que cada cuenta sea una identidad separada, que volver a
 * desplegar no reescriba la contrasena de nadie, que la clave nunca
 * aparezca en un mensaje, y que una clave debil detenga el arranque en vez
 * de dejar abierta una cuenta con permisos totales.
 */
@ExtendWith(MockitoExtension.class)
class AdministradoresInicialesTest {

    private static final String CLAVE_VALIDA = "ClaveLargaDeDoceOMas1";
    private static final String OTRA_CLAVE = "OtraClaveLargaValida2";
    private static final String ROL = "SUPER_ADMINISTRADOR";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolService rolService;

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdministradoresIniciales sembrador(String configuracion) {
        return new AdministradoresIniciales(
                configuracion, usuarioRepository, rolService, perfilUsuarioService, passwordEncoder);
    }

    @Test
    void sinConfiguracionNoHayCuentasQueSembrar() {
        assertTrue(AdministradoresIniciales.leer(null).isEmpty());
        assertTrue(AdministradoresIniciales.leer("").isEmpty());
        assertTrue(AdministradoresIniciales.leer("   ").isEmpty());
    }

    @Test
    void leeTresCuentasSeparadasPorPuntoYComa() {
        List<AdministradoresIniciales.CuentaInicial> cuentas = AdministradoresIniciales.leer(
                "uno|uno@nexus.test|" + CLAVE_VALIDA
                        + ";dos|dos@nexus.test|" + OTRA_CLAVE
                        + ";tres|tres@nexus.test|" + CLAVE_VALIDA);

        assertEquals(3, cuentas.size());
        assertEquals("uno", cuentas.get(0).apodo());
        assertEquals("dos@nexus.test", cuentas.get(1).email());
        assertEquals(CLAVE_VALIDA, cuentas.get(2).clave());
    }

    @Test
    void ignoraEntradasVaciasYRecortaEspacios() {
        List<AdministradoresIniciales.CuentaInicial> cuentas = AdministradoresIniciales.leer(
                "  uno | uno@nexus.test | " + CLAVE_VALIDA + " ;;   ;");

        assertEquals(1, cuentas.size());
        assertEquals("uno", cuentas.get(0).apodo());
        assertEquals("uno@nexus.test", cuentas.get(0).email());
        assertEquals(CLAVE_VALIDA, cuentas.get(0).clave());
    }

    @Test
    void unaEntradaMalFormadaDetieneElArranque() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AdministradoresIniciales.leer("uno|uno@nexus.test"));

        assertTrue(error.getMessage().contains("apodo|correo|clave"));
    }

    /**
     * El mensaje de error no puede llevar la entrada: la entrada contiene la
     * clave, y los mensajes de arranque acaban en la bitacora del host.
     */
    @Test
    void elErrorDeFormatoNoImprimeLaClave() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AdministradoresIniciales.leer("uno|uno@nexus.test|" + CLAVE_VALIDA + "|sobra"));

        assertFalse(error.getMessage().contains(CLAVE_VALIDA), "la clave no puede viajar en el error");
    }

    @Test
    void apodoOCorreoVaciosDetienenElArranque() {
        assertThrows(IllegalStateException.class,
                () -> AdministradoresIniciales.leer("|uno@nexus.test|" + CLAVE_VALIDA));
        assertThrows(IllegalStateException.class,
                () -> AdministradoresIniciales.leer("uno||" + CLAVE_VALIDA));
    }

    /** Una cuenta con permisos totales no arranca con una clave corta. */
    @Test
    void unaClaveDebilDetieneElArranque() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AdministradoresIniciales.leer("uno|uno@nexus.test|corta"));

        assertTrue(error.getMessage().contains("uno"));
        assertTrue(error.getMessage().contains("12"));
    }

    @Test
    void sinCuentasConfiguradasNoTocaLaBaseDeDatos() {
        sembrador("").run();

        verifyNoInteractions(usuarioRepository, rolService, perfilUsuarioService, passwordEncoder);
    }

    @Test
    void creaCadaCuentaComoSuperAdministradorConSuPropiaClave() {
        RolEntity superAdmin = new RolEntity(ROL, "todo el alcance");
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByApodo(anyString())).thenReturn(Optional.empty());
        when(rolService.obtenerRolPorNombre(ROL)).thenReturn(superAdmin);
        when(passwordEncoder.encode(CLAVE_VALIDA)).thenReturn("hash-uno");
        when(passwordEncoder.encode(OTRA_CLAVE)).thenReturn("hash-dos");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(llamada -> llamada.getArgument(0));

        sembrador("uno|uno@nexus.test|" + CLAVE_VALIDA + ";dos|dos@nexus.test|" + OTRA_CLAVE).run();

        ArgumentCaptor<Usuario> guardados = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, times(2)).save(guardados.capture());
        List<Usuario> cuentas = guardados.getAllValues();

        assertEquals("uno", cuentas.get(0).getApodo());
        assertEquals("uno@nexus.test", cuentas.get(0).getEmail());
        assertEquals("ACTIVO", cuentas.get(0).getEstado());
        assertEquals(superAdmin, cuentas.get(0).getRol());
        assertEquals("hash-uno", cuentas.get(0).getPassword());
        assertEquals("hash-dos", cuentas.get(1).getPassword());
        assertNotEquals(cuentas.get(0).getEmail(), cuentas.get(1).getEmail(),
                "tres administradores, tres identidades: nunca una cuenta compartida");
        verify(perfilUsuarioService).crearPerfil(cuentas.get(0), "uno", "", null);
        verify(perfilUsuarioService).crearPerfil(cuentas.get(1), "dos", "", null);
    }

    /** La clave en claro no se guarda: lo que va a la fila es el hash. */
    @Test
    void nuncaGuardaLaClaveEnClaro() {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByApodo(anyString())).thenReturn(Optional.empty());
        when(rolService.obtenerRolPorNombre(anyString())).thenReturn(new RolEntity(ROL, "todo el alcance"));
        when(passwordEncoder.encode(CLAVE_VALIDA)).thenReturn("hash-uno");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(llamada -> llamada.getArgument(0));

        sembrador("uno|uno@nexus.test|" + CLAVE_VALIDA).run();

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertNotEquals(CLAVE_VALIDA, guardado.getValue().getPassword());
        assertEquals("hash-uno", guardado.getValue().getPassword());
    }

    /**
     * Idempotencia. Volver a desplegar no puede reescribir una contrasena que
     * su dueno ya cambio, ni duplicar la cuenta.
     */
    @Test
    void noTocaUnaCuentaCuyoCorreoYaExiste() {
        when(usuarioRepository.findByEmail("uno@nexus.test")).thenReturn(Optional.of(new Usuario()));

        sembrador("uno|uno@nexus.test|" + CLAVE_VALIDA).run();

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verifyNoInteractions(passwordEncoder, perfilUsuarioService);
    }

    /** El apodo tambien es unico: si esta tomado, tampoco se crea. */
    @Test
    void noTocaUnaCuentaCuyoApodoYaExiste() {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByApodo("uno")).thenReturn(Optional.of(new Usuario()));

        sembrador("uno|uno@nexus.test|" + CLAVE_VALIDA).run();

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verifyNoInteractions(passwordEncoder, perfilUsuarioService);
    }

    /** Una que ya existe y otra que no: se crea solo la que falta. */
    @Test
    void siembraSoloLasQueFaltan() {
        when(usuarioRepository.findByEmail("uno@nexus.test")).thenReturn(Optional.of(new Usuario()));
        when(usuarioRepository.findByEmail("dos@nexus.test")).thenReturn(Optional.empty());
        when(usuarioRepository.findByApodo("dos")).thenReturn(Optional.empty());
        when(rolService.obtenerRolPorNombre(anyString())).thenReturn(new RolEntity(ROL, "todo el alcance"));
        when(passwordEncoder.encode(OTRA_CLAVE)).thenReturn("hash-dos");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(llamada -> llamada.getArgument(0));

        sembrador("uno|uno@nexus.test|" + CLAVE_VALIDA + ";dos|dos@nexus.test|" + OTRA_CLAVE).run();

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertEquals("dos", guardado.getValue().getApodo());
    }
}
