package com.nexusbattles.ms_identidad.sanciones;

import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.security.SoloServicio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Rutas entre servicios de ms-identidad-admin.yaml (tag «interno», B2). Solo
 * credenciales de servicio: lo comprueba {@code InterceptorDeServicio}, que
 * esta registrado sobre todo {@code /api/v1/internal/**}. El borde no publica
 * este prefijo.
 */
@RestController
@RequestMapping("/api/v1/internal/usuarios")
public class InternoUsuariosController {

    /** El unico servicio que puede fijar el estado de sancion de una cuenta. */
    static final String MODERACION = "moderacion-sanciones";

    private final ProyeccionDeSancionService proyecciones;
    private final UsuarioRepository usuarios;

    public InternoUsuariosController(ProyeccionDeSancionService proyecciones, UsuarioRepository usuarios) {
        this.proyecciones = proyecciones;
        this.usuarios = usuarios;
    }

    /** moderacion-sanciones proyecta una sancion sobre la cuenta. Idempotente. */
    @PutMapping("/{uid}/estado-sancion")
    @SoloServicio(azp = MODERACION)
    public EstadoDeCuentaResponse proyectar(@PathVariable String uid,
                                            @RequestBody ProyeccionDeSancionRequest proyeccion) {
        return proyecciones.proyectar(uidDe(uid), proyeccion);
    }

    /** Correo y apodo para escribirle a una cuenta, sin guardar copias en otros servicios. */
    @GetMapping("/{uid}/contacto")
    @SoloServicio
    public ResponseEntity<ContactoResponse> contacto(@PathVariable String uid) {
        Usuario usuario = usuarios.findByPublicId(uidDe(uid)).orElseThrow(CuentaNoEncontradaException::new);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ContactoResponse(
                usuario.getPublicId(), usuario.getEmail(), usuario.getApodo(),
                EstadoCuenta.normalizado(usuario.getEstado())));
    }

    @ExceptionHandler(CuentaNoEncontradaException.class)
    public ResponseEntity<ProblemDetail> noEncontrada(CuentaNoEncontradaException error, HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.NOT_FOUND, "cuenta-no-encontrada", "Cuenta no encontrada",
                error.getMessage(), peticion.getRequestURI());
    }

    @ExceptionHandler(ProyeccionInvalidaException.class)
    public ResponseEntity<ProblemDetail> invalida(ProyeccionInvalidaException error, HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.BAD_REQUEST, "datos-invalidos", "Proyección inválida",
                error.getMessage(), peticion.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> ilegible(HttpServletRequest peticion) {
        return Problemas.datosInvalidos(peticion.getRequestURI());
    }

    /** Un uid mal formado no nombra a nadie: 404, igual que uno que no existe. */
    private static UUID uidDe(String texto) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException malFormado) {
            throw new CuentaNoEncontradaException();
        }
    }
}
