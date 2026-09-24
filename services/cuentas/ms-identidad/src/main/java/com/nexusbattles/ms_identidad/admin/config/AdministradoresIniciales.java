package com.nexusbattles.ms_identidad.admin.config;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.service.RolService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Crea las cuentas administrativas iniciales del entorno, si se configuran.
 *
 * ## Por que existe
 *
 * Tres personas operan el sistema y necesitan el mismo alcance. La salida
 * comoda -- una sola cuenta compartida -- destruye justo lo que la auditoria
 * viene a demostrar: si los tres entran como «admin», el registro dice que
 * «admin» suspendio una cuenta, y eso no sirve para nada. Cada responsable
 * tiene su identidad y su credencial; el rol es el mismo, la persona no.
 *
 * {@code AdminCuentaService} no sirve para esto: solo crea MODERADOR y
 * ADMINISTRADOR -- a proposito, es el endpoint que usa un super administrador
 * que ya existe -- y deja la cuenta INACTIVA esperando el correo de
 * activacion. Aqui hace falta lo contrario: arrancar de cero, cuando todavia
 * no hay ningun super administrador que pueda crear a los demas.
 *
 * ## Como se configura
 *
 * Variable de entorno {@code ADMINS_INICIALES}, una cuenta por entrada:
 *
 * <pre>apodo|correo|clave;apodo|correo|clave;apodo|correo|clave</pre>
 *
 * Mismo separador «;» que {@code AUTH_CLIENTES_SERVICIO}, para no inventar
 * una convencion nueva. Sin la variable no hace absolutamente nada: en un
 * entorno que no la define, no aparece ninguna cuenta.
 *
 * ## Que garantiza
 *
 * - **Idempotente**: si el correo o el apodo ya existen, no los toca. Volver
 *   a desplegar no reescribe una contrasena que alguien pudo cambiar.
 * - **Sin secretos en el codigo ni en la bitacora**: las claves llegan por el
 *   entorno y nunca se imprimen; el registro dice el apodo y nada mas.
 * - **Rechaza lo inseguro**: una clave de menos de 12 caracteres detiene el
 *   arranque, en vez de dejar una cuenta debil con permisos totales.
 */
@Component
@Order(20) // despues de RolSeeder: los roles tienen que existir ya
// Sin cuentas configuradas, este componente ni siquiera se construye. No es
// una optimizacion: las rebanadas de prueba que no levantan la cadena de
// seguridad no tienen PasswordEncoder, y pedirlo ahi rompia el contexto de
// tests que no tienen nada que ver con administradores. Que el bean exista
// solo cuando hay algo que sembrar tambien dice mejor lo que hace.
@ConditionalOnExpression("'${app.admins.iniciales:}' != ''")
public class AdministradoresIniciales implements CommandLineRunner {

    private static final Logger BITACORA = LoggerFactory.getLogger(AdministradoresIniciales.class);
    private static final String ROL = "SUPER_ADMINISTRADOR";
    private static final int LONGITUD_MINIMA_CLAVE = 12;

    private final String configuracion;
    private final UsuarioRepository usuarioRepository;
    private final RolService rolService;
    private final PerfilUsuarioService perfilUsuarioService;
    private final PasswordEncoder passwordEncoder;

    /**
     * El que usa Spring.
     *
     * El cifrador se construye aqui, no se inyecta. Este servicio NO publica
     * un bean de {@code PasswordEncoder}: RegistroService, LoginService y
     * TokenCredencialService crean cada uno el suyo con
     * {@code new BCryptPasswordEncoder()}. Pedirlo por constructor compilaba,
     * pasaba CI -- porque sin {@code ADMINS_INICIALES} este componente ni se
     * construye -- y tumbaba el arranque en el primer entorno que SI define
     * la variable, dejando la autenticacion del producto entero sin servicio.
     * Se sigue la convencion del servicio en vez de introducir un bean global
     * que cambiaria como se resuelve el cifrado para todo lo demas.
     */
    @Autowired
    public AdministradoresIniciales(
            @Value("${app.admins.iniciales:}") String configuracion,
            UsuarioRepository usuarioRepository,
            RolService rolService,
            PerfilUsuarioService perfilUsuarioService) {
        this(configuracion, usuarioRepository, rolService, perfilUsuarioService,
                new BCryptPasswordEncoder());
    }

    /** Con cifrador explicito: lo usan las pruebas para no cifrar de verdad. */
    AdministradoresIniciales(
            String configuracion,
            UsuarioRepository usuarioRepository,
            RolService rolService,
            PerfilUsuarioService perfilUsuarioService,
            PasswordEncoder passwordEncoder) {
        this.configuracion = configuracion;
        this.usuarioRepository = usuarioRepository;
        this.rolService = rolService;
        this.perfilUsuarioService = perfilUsuarioService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<CuentaInicial> cuentas = leer(configuracion);
        if (cuentas.isEmpty()) {
            return;
        }

        List<String> creados = new ArrayList<>();
        List<String> yaEstaban = new ArrayList<>();
        for (CuentaInicial cuenta : cuentas) {
            if (usuarioRepository.findByEmail(cuenta.email()).isPresent()
                    || usuarioRepository.findByApodo(cuenta.apodo()).isPresent()) {
                yaEstaban.add(cuenta.apodo());
                continue;
            }
            Usuario usuario = new Usuario();
            usuario.setApodo(cuenta.apodo());
            usuario.setEmail(cuenta.email());
            usuario.setEstado("ACTIVO");
            usuario.setRol(rolService.obtenerRolPorNombre(ROL));
            usuario.setPassword(passwordEncoder.encode(cuenta.clave()));
            Usuario guardado = usuarioRepository.save(usuario);
            perfilUsuarioService.crearPerfil(guardado, cuenta.apodo(), "", null);
            creados.add(cuenta.apodo());
        }

        if (!creados.isEmpty()) {
            BITACORA.info("Administradores creados con rol {}: {}", ROL, creados);
        }
        if (!yaEstaban.isEmpty()) {
            BITACORA.info("Administradores que ya existian, no se tocan: {}", yaEstaban);
        }
    }

    static List<CuentaInicial> leer(String texto) {
        List<CuentaInicial> cuentas = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return cuentas;
        }
        for (String entrada : texto.split(";")) {
            String linea = entrada.strip();
            if (linea.isEmpty()) {
                continue;
            }
            String[] partes = linea.split("\\|");
            if (partes.length != 3) {
                throw new IllegalStateException(
                        "app.admins.iniciales: cada entrada debe ser apodo|correo|clave "
                                + "(entrada invalida; no se muestra por seguridad).");
            }
            String apodo = partes[0].strip();
            String email = partes[1].strip();
            String clave = partes[2].strip();
            if (apodo.isEmpty() || email.isEmpty()) {
                throw new IllegalStateException("app.admins.iniciales: apodo y correo no pueden ir vacios.");
            }
            if (clave.length() < LONGITUD_MINIMA_CLAVE) {
                throw new IllegalStateException(
                        "app.admins.iniciales: la clave de '" + apodo + "' tiene menos de "
                                + LONGITUD_MINIMA_CLAVE + " caracteres. Una cuenta con permisos "
                                + "totales no arranca con una clave debil.");
            }
            cuentas.add(new CuentaInicial(apodo, email, clave));
        }
        return cuentas;
    }

    record CuentaInicial(String apodo, String email, String clave) { }
}
