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
    private final HistorialDeSancionesService historiales;
    private final UsuarioRepository usuarios;

    public InternoUsuariosController(ProyeccionDeSancionService proyecciones,
                                     HistorialDeSancionesService historiales, UsuarioRepository usuarios) {
        this.proyecciones = proyecciones;
        this.historiales = historiales;
        this.usuarios = usuarios;
    }

    /** moderacion-sanciones proyecta una sancion sobre la cuenta. Idempotente. */
    @PutMapping("/{uid}/estado-sancion")
    @SoloServicio(azp = MODERACION)
    public EstadoDeCuentaResponse proyectar(@PathVariable String uid,
                                            @RequestBody ProyeccionDeSancionRequest proyeccion) {
        return proyecciones.proyectar(uidDe(uid), proyeccion);
    }

    /**
     * moderacion-sanciones fija el resumen del historial de sanciones de la
     * cuenta (1.7.0): el total y la fecha de la ultima, valores absolutos.
     * Idempotente. Es lo que deja filtrar el directorio por historial de
     * sanciones (§7.3.4) sin consultar otro servicio por cada fila.
     */
    @PutMapping("/{uid}/historial-sanciones")
    @SoloServicio(azp = MODERACION)
    public HistorialDeSancionesService.HistorialDeSancionesResponse historial(
            @PathVariable String uid, @RequestBody HistorialDeSancionesRequest historial) {
        return historiales.fijar(uidDe(uid), historial);
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

    /**
     * El rol vigente de la cuenta (ms-identidad-admin.yaml 1.6.0): lo mira
     * moderacion-sanciones antes de emitir, levantar o resolver, para que nadie
     * sancione un rol igual o superior al suyo (jerarquia de 7.3.1). Ruta
     * propia y no el contacto: para decidir no hace falta el correo de nadie.
     */
    @GetMapping("/{uid}/rol")
    @SoloServicio(azp = MODERACION)
    public ResponseEntity<RolDeCuentaResponse> rol(@PathVariable String uid) {
        Usuario usuario = usuarios.findByPublicId(uidDe(uid)).orElseThrow(CuentaNoEncontradaException::new);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new RolDeCuentaResponse(
                usuario.getPublicId(), usuario.getRol() == null ? null : usuario.getRol().getNombre(),
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
