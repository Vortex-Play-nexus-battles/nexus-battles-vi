package com.nexusbattles.ms_identidad.admin.config;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.segundofactor.CifradoDeSecretos;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactor;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRepository;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Las cuentas administrativas de las pruebas automaticas (7.3.9): con el
 * segundo factor ya enrolado, nunca en produccion y sin mostrar sus valores.
 */
@DisplayName("Cuentas administrativas de pruebas (ADMINS_QA)")
class CuentaAdministrativaDePruebasTest {

    /** 20 bytes en Base32 (RFC 6238, apendice B). */
    private static final String SECRETO = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String CLAVE = "Clave-QA-larga-2026";

    private UsuarioRepository usuarios;
    private RolService roles;
    private PerfilUsuarioService perfiles;
    private SegundoFactorRepository segundos;
    private Environment entorno;
    private PasswordEncoder cifrador;
    private CifradoDeSecretos cifrado;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        roles = mock(RolService.class);
        perfiles = mock(PerfilUsuarioService.class);
        segundos = mock(SegundoFactorRepository.class);
        entorno = mock(Environment.class);
        cifrador = mock(PasswordEncoder.class);
        byte[] clave = new byte[32];
        new SecureRandom().nextBytes(clave);
        cifrado = new CifradoDeSecretos(Base64.getEncoder().encodeToString(clave));
        when(entorno.getActiveProfiles()).thenReturn(new String[] {"dev"});
        when(cifrador.encode(anyString())).thenReturn("$2a$resumen");
        when(roles.obtenerRolPorNombre(anyString())).thenAnswer(i -> new RolEntity(i.getArgument(0), "rol"));
        when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarios.findByApodo(anyString())).thenReturn(Optional.empty());
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            if (u.getId() == null) {
                u.setId(40L);
            }
            return u;
        });
    }

    private CuentaAdministrativaDePruebas con(String configuracion) {
        return new CuentaAdministrativaDePruebas(configuracion, usuarios, roles, perfiles, segundos, cifrado,
            entorno, cifrador);
    }

    @Test
    @DisplayName("crea las tres cuentas con su rol, ACTIVAS, con el segundo factor activado y cifrado")
    void creaLasTresCuentas() {
        con("mod_qa|mod_qa@nexus.test|" + CLAVE + "|" + SECRETO + "|MODERADOR;"
            + "admin_qa|admin_qa@nexus.test|" + CLAVE + "|" + SECRETO + "|ADMINISTRADOR;"
            + "super_qa|Super_QA@Nexus.test|" + CLAVE + "|" + SECRETO).run();

        ArgumentCaptor<Usuario> guardadas = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios, times(3)).save(guardadas.capture());
        List<Usuario> cuentas = guardadas.getAllValues();
        assertThat(cuentas).extracting(u -> u.getRol().getNombre())
            .containsExactly("MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR");
        assertThat(cuentas).allSatisfy(u -> {
            assertThat(u.getEstado()).isEqualTo("ACTIVO");
            assertThat(u.getPassword()).isEqualTo("$2a$resumen");
        });
        assertThat(cuentas.get(2).getEmail()).isEqualTo("super_qa@nexus.test");

        ArgumentCaptor<SegundoFactor> factores = ArgumentCaptor.forClass(SegundoFactor.class);
        verify(segundos, times(3)).save(factores.capture());
        assertThat(factores.getAllValues()).allSatisfy(sf -> {
            assertThat(sf.isActivo()).isTrue();
            assertThat(sf.getSecretoCifrado()).doesNotContain(SECRETO);
        });
        verify(perfiles, times(3)).crearPerfil(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("una cuenta que ya existe se reajusta (clave, rol, estado y secreto) sin crear otro perfil")
    void reajustaLaExistente() {
        Usuario existente = new Usuario();
        existente.setId(9L);
        existente.setEstado("SUSPENDIDO");
        when(usuarios.findByEmail("super_qa@nexus.test")).thenReturn(Optional.of(existente));

        con("super_qa|super_qa@nexus.test|" + CLAVE + "|" + SECRETO).run();

        assertThat(existente.getEstado()).isEqualTo("ACTIVO");
        assertThat(existente.getRol().getNombre()).isEqualTo("SUPER_ADMINISTRADOR");
        verify(segundos).borrarDe(9L);
        verify(perfiles, never()).crearPerfil(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("con el perfil prod no crea nada aunque la variable este definida")
    void nuncaEnProduccion() {
        when(entorno.getActiveProfiles()).thenReturn(new String[] {"prod"});

        con("super_qa|super_qa@nexus.test|" + CLAVE + "|" + SECRETO).run();

        verifyNoInteractions(usuarios, segundos, perfiles);
    }

    @Test
    @DisplayName("sin clave de cifrado del segundo factor no crea nada")
    void sinCifrado() {
        CuentaAdministrativaDePruebas sinClave = new CuentaAdministrativaDePruebas(
            "super_qa|super_qa@nexus.test|" + CLAVE + "|" + SECRETO, usuarios, roles, perfiles, segundos,
            new CifradoDeSecretos(""), entorno, cifrador);

        sinClave.run();

        verifyNoInteractions(usuarios, segundos);
    }

    @Test
    @DisplayName("una entrada invalida detiene el arranque antes de tocar ninguna cuenta, sin mostrar valores")
    void todoONada() {
        CuentaAdministrativaDePruebas mal = con("mod_qa|mod_qa@nexus.test|" + CLAVE + "|" + SECRETO
            + "|MODERADOR;admin_qa|admin_qa@nexus.test|corta|" + SECRETO + "|ADMINISTRADOR");

        assertThatThrownBy(mal::run).isInstanceOf(IllegalStateException.class)
            .hasMessageNotContaining("corta").hasMessageNotContaining(SECRETO);
        verifyNoInteractions(usuarios, segundos);
    }

    @Test
    @DisplayName("solo roles administrativos, sin apodos repetidos ni secretos cortos")
    void validaCadaEntrada() {
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leer(
            "j|j@nexus.test|" + CLAVE + "|" + SECRETO + "|JUGADOR")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leer("a|a@nexus.test|" + CLAVE + "|GEZDGNBV"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leer("a|a@nexus.test|" + CLAVE + "|no-es-base32!"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leerTodas(
            "a|a@nexus.test|" + CLAVE + "|" + SECRETO + ";A|b@nexus.test|" + CLAVE + "|" + SECRETO))
            .isInstanceOf(IllegalStateException.class);
        assertThat(CuentaAdministrativaDePruebas.leer("a|a@nexus.test|" + CLAVE + "|" + SECRETO).rol())
            .isEqualTo("SUPER_ADMINISTRADOR");
    }

    @Test
    @DisplayName("sin entradas, o con entradas incompletas o vacias, no arranca y no muestra ningun valor")
    void entradasIncompletas() {
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leerTodas(null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("ninguna cuenta");
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leerTodas(" ; ;"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("ninguna cuenta");
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leer(null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("apodo|correo|clave");
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leer("a|a@nexus.test|" + CLAVE))
            .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(CLAVE);
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leer(" |a@nexus.test|" + CLAVE + "|" + SECRETO))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("vacios");
        assertThatThrownBy(() -> CuentaAdministrativaDePruebas.leer("a| |" + CLAVE + "|" + SECRETO))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("vacios");
        // Un separador de mas no es una cuenta.
        assertThat(CuentaAdministrativaDePruebas.leerTodas("a|a@nexus.test|" + CLAVE + "|" + SECRETO + ";;"))
            .hasSize(1);
    }

    @Test
    @DisplayName("la cuenta leida nunca imprime la clave ni el secreto, y se compara por su contenido")
    void cuentaSinSecretosEnTexto() {
        String otroSecreto = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        CuentaAdministrativaDePruebas.CuentaQa una =
            CuentaAdministrativaDePruebas.leer("a|a@nexus.test|" + CLAVE + "|" + SECRETO + "|MODERADOR");
        CuentaAdministrativaDePruebas.CuentaQa igual =
            CuentaAdministrativaDePruebas.leer(" a | A@Nexus.test |" + CLAVE + "|" + SECRETO + "|moderador");

        assertThat(una).isEqualTo(igual).hasSameHashCodeAs(igual);
        assertThat(una.toString()).contains("a@nexus.test", "MODERADOR")
            .doesNotContain(CLAVE).doesNotContain(SECRETO);
        // Otro tipo de objeto nunca es la misma cuenta (rama «no es una CuentaQa» del equals).
        Object deOtroTipo = new Object();
        assertThat(una)
            .isNotEqualTo(deOtroTipo)
            .isNotEqualTo(CuentaAdministrativaDePruebas.leer("b|a@nexus.test|" + CLAVE + "|" + SECRETO + "|MODERADOR"))
            .isNotEqualTo(CuentaAdministrativaDePruebas.leer("a|b@nexus.test|" + CLAVE + "|" + SECRETO + "|MODERADOR"))
            .isNotEqualTo(CuentaAdministrativaDePruebas.leer("a|a@nexus.test|" + CLAVE + "x|" + SECRETO + "|MODERADOR"))
            .isNotEqualTo(CuentaAdministrativaDePruebas.leer("a|a@nexus.test|" + CLAVE + "|" + otroSecreto + "|MODERADOR"))
            .isNotEqualTo(CuentaAdministrativaDePruebas.leer("a|a@nexus.test|" + CLAVE + "|" + SECRETO));
    }

    @Test
    @DisplayName("el constructor que usa Spring guarda la clave con BCrypt, nunca en claro")
    void constructorDeSpring() {
        CuentaAdministrativaDePruebas runner = new CuentaAdministrativaDePruebas(
            "qa_mod|qa_mod@nexus.test|" + CLAVE + "|" + SECRETO + "|MODERADOR",
            usuarios, roles, perfiles, segundos, cifrado, entorno);

        runner.run();

        org.mockito.ArgumentCaptor<Usuario> guardado = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(guardado.capture());
        assertThat(guardado.getValue().getPassword()).startsWith("$2").isNotEqualTo(CLAVE);
        assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
            .matches(CLAVE, guardado.getValue().getPassword())).isTrue();
    }
}
