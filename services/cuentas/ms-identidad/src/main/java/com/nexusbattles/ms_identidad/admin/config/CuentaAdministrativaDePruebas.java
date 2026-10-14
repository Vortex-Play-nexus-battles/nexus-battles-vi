package com.nexusbattles.ms_identidad.admin.config;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.segundofactor.Base32;
import com.nexusbattles.ms_identidad.auth.segundofactor.CifradoDeSecretos;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactor;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRepository;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Las cuentas administrativas de las pruebas automaticas — solo fuera de
 * produccion (7.3.9, separacion de entornos).
 *
 * <h2>Por que existen</h2>
 *
 * Con el segundo factor obligatorio para Moderador, Administrador y Super
 * Administrador, una prueba de punta a punta que entra con uno de esos roles
 * necesita el codigo TOTP de esa cuenta. Usar la cuenta de una persona real
 * seria peor que imposible: enrolaria su propia aplicacion y la dejaria fuera
 * de su cuenta. Estas son cuentas de pruebas dedicadas, con el secreto TOTP ya
 * enrolado desde el entorno, que el E2E (banco y AWS DEV) usa sin tocar a
 * nadie.
 *
 * <h2>Como se configuran</h2>
 *
 * Variable {@code ADMINS_QA}: una o varias cuentas separadas por {@code ;},
 * cada una {@code apodo|correo|clave|secretoTotpBase32[|ROL]}. El rol es
 * {@code MODERADOR}, {@code ADMINISTRADOR} o {@code SUPER_ADMINISTRADOR} (este
 * ultimo si se omite). El correo deberia ser del dominio reservado
 * {@code .test}, que el directorio trata como cuenta de pruebas.
 *
 * <h2>Que garantiza</h2>
 *
 * <ul>
 *   <li><b>Nunca en produccion</b>: con el perfil {@code prod} activo no hace
 *       nada aunque la variable este definida, y lo dice en la bitacora.</li>
 *   <li><b>El entorno manda</b>: a diferencia de los administradores reales
 *       (que cambian su clave y no se les pisa), aqui cada arranque vuelve a
 *       aplicar la clave, el rol, el estado ACTIVO y el secreto TOTP. Es lo
 *       que permite rotar el secreto sin tocar la base.</li>
 *   <li><b>Todo o nada</b>: una entrada mal escrita detiene el arranque antes
 *       de tocar ninguna cuenta, sin mostrar el valor.</li>
 *   <li><b>Nada en la bitacora</b>: ni la clave ni el secreto se imprimen.</li>
 * </ul>
 */
@Component
@Order(21) // despues de RolSeeder y de AdministradoresIniciales
@ConditionalOnExpression("'${app.admins.qa:}' != ''")
public class CuentaAdministrativaDePruebas implements CommandLineRunner {

    private static final Logger BITACORA = LoggerFactory.getLogger(CuentaAdministrativaDePruebas.class);
    static final String ROL_POR_OMISION = "SUPER_ADMINISTRADOR";
    static final Set<String> ROLES = Set.of("MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR");
    private static final int LONGITUD_MINIMA_CLAVE = 12;
    private static final int BYTES_MINIMOS_DEL_SECRETO = 16;

    private final String configuracion;
    private final UsuarioRepository usuarios;
    private final RolService roles;
    private final PerfilUsuarioService perfiles;
    private final SegundoFactorRepository segundos;
    private final CifradoDeSecretos cifrado;
    private final Environment entorno;
    private final PasswordEncoder cifrador;

    @Autowired
    public CuentaAdministrativaDePruebas(@Value("${app.admins.qa:}") String configuracion,
                                         UsuarioRepository usuarios, RolService roles,
                                         PerfilUsuarioService perfiles, SegundoFactorRepository segundos,
                                         CifradoDeSecretos cifrado, Environment entorno) {
        this(configuracion, usuarios, roles, perfiles, segundos, cifrado, entorno, new BCryptPasswordEncoder());
    }

    CuentaAdministrativaDePruebas(String configuracion, UsuarioRepository usuarios, RolService roles,
                                  PerfilUsuarioService perfiles, SegundoFactorRepository segundos,
                                  CifradoDeSecretos cifrado, Environment entorno, PasswordEncoder cifrador) {
        this.configuracion = configuracion;
        this.usuarios = usuarios;
        this.roles = roles;
        this.perfiles = perfiles;
        this.segundos = segundos;
        this.cifrado = cifrado;
        this.entorno = entorno;
        this.cifrador = cifrador;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (Arrays.stream(entorno.getActiveProfiles()).anyMatch(p -> "prod".equalsIgnoreCase(p))) {
            BITACORA.warn("ADMINS_QA esta definida en produccion y se IGNORA: las cuentas de pruebas solo existen "
                    + "fuera de produccion.");
            return;
        }
        if (!cifrado.disponible()) {
            BITACORA.warn("ADMINS_QA definida pero sin IDENTIDAD_2FA_CLAVE: no se puede enrolar el segundo factor "
                    + "de las cuentas de pruebas y no se crean.");
            return;
        }
        // Todas se leen antes de tocar ninguna: una entrada mal escrita no deja
        // la mitad aplicada.
        List<CuentaQa> cuentas = leerTodas(configuracion);
        for (CuentaQa cuenta : cuentas) {
            aplicar(cuenta);
        }
    }

    private void aplicar(CuentaQa cuenta) {
        Optional<Usuario> existente = usuarios.findByEmail(cuenta.email())
                .or(() -> usuarios.findByApodo(cuenta.apodo()));
        Usuario usuario = existente.orElseGet(Usuario::new);
        boolean nueva = existente.isEmpty();
        usuario.setApodo(cuenta.apodo());
        usuario.setEmail(cuenta.email());
        usuario.setEstado("ACTIVO");
        usuario.setSuspendidoHasta(null);
        usuario.setRol(roles.obtenerRolPorNombre(cuenta.rol()));
        usuario.setPassword(cifrador.encode(cuenta.clave()));
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        Usuario guardado = usuarios.save(usuario);
        if (nueva) {
            perfiles.crearPerfil(guardado, "Cuenta", "de pruebas", null);
        }

        LocalDateTime ahora = LocalDateTime.now();
        String secretoCifrado = cifrado.cifrar(cuenta.secreto(), "usuario:" + guardado.getId());
        segundos.borrarDe(guardado.getId());
        SegundoFactor segundo = new SegundoFactor(guardado.getId(), secretoCifrado, ahora);
        segundo.activar(ahora, 0L);
        segundos.save(segundo);

        BITACORA.info("Cuenta administrativa de pruebas {} ({}, {}), con segundo factor enrolado.",
                nueva ? "creada" : "actualizada", cuenta.apodo(), cuenta.rol());
    }

    static List<CuentaQa> leerTodas(String texto) {
        List<CuentaQa> cuentas = new ArrayList<>();
        Set<String> apodos = new HashSet<>();
        for (String entrada : (texto == null ? "" : texto).split(";")) {
            if (entrada.isBlank()) {
                continue;
            }
            CuentaQa cuenta = leer(entrada);
            if (!apodos.add(cuenta.apodo().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("app.admins.qa: el apodo " + cuenta.apodo() + " esta repetido.");
            }
            cuentas.add(cuenta);
        }
        if (cuentas.isEmpty()) {
            throw new IllegalStateException("app.admins.qa no tiene ninguna cuenta.");
        }
        return cuentas;
    }

    static CuentaQa leer(String texto) {
        String[] partes = texto == null ? new String[0] : texto.strip().split("\\|");
        if (partes.length != 4 && partes.length != 5) {
            throw new IllegalStateException("app.admins.qa: cada cuenta es apodo|correo|clave|secretoTotpBase32[|ROL] "
                    + "(valor invalido; no se muestra por seguridad).");
        }
        String apodo = partes[0].strip();
        String email = partes[1].strip().toLowerCase(Locale.ROOT);
        String clave = partes[2].strip();
        String rol = partes.length == 5 ? partes[4].strip().toUpperCase(Locale.ROOT) : ROL_POR_OMISION;
        byte[] secreto;
        try {
            secreto = Base32.decodificar(partes[3].strip());
        } catch (RuntimeException ilegible) {
            throw new IllegalStateException("app.admins.qa: el secreto TOTP de " + apodo + " no es Base32 valido.");
        }
        if (apodo.isEmpty() || email.isEmpty()) {
            throw new IllegalStateException("app.admins.qa: apodo y correo no pueden ir vacios.");
        }
        if (!ROLES.contains(rol)) {
            throw new IllegalStateException("app.admins.qa: el rol de " + apodo + " debe ser MODERADOR, "
                    + "ADMINISTRADOR o SUPER_ADMINISTRADOR.");
        }
        if (clave.length() < LONGITUD_MINIMA_CLAVE) {
            throw new IllegalStateException("app.admins.qa: la clave de " + apodo + " tiene menos de "
                    + LONGITUD_MINIMA_CLAVE + " caracteres.");
        }
        if (secreto.length < BYTES_MINIMOS_DEL_SECRETO) {
            throw new IllegalStateException("app.admins.qa: el secreto TOTP de " + apodo
                    + " es demasiado corto (minimo " + BYTES_MINIMOS_DEL_SECRETO + " bytes).");
        }
        return new CuentaQa(apodo, email, clave, secreto, rol);
    }

    record CuentaQa(String apodo, String email, String clave, byte[] secreto, String rol) {
    }
}
