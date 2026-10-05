package com.nexusbattles.ms_identidad.privacidad;

import com.nexusbattles.ms_identidad.auth.codigos.IpDelCliente;
import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * {@code /api/v1/perfiles/{usuario}/cierre} — el cierre de la propia cuenta
 * (HU-PRV-005; ms-identidad-perfiles.yaml 1.4.0).
 *
 * <p><b>Solo la propia cuenta.</b> El interceptor valida el token (firma,
 * vigencia, version) con el permiso {@code MODIFICAR_PERFIL_PROPIO}, que tienen
 * los cuatro roles. Aqui se exige ademas que la cuenta de la ruta sea la del
 * token: por su {@code uid} y, para un token anterior al uid, por el apodo.
 * Con el uid de otra persona, 403 antes de mirar si existe.
 *
 * <p>Los rechazos salen en problem details con el {@code type} estable de
 * cada motivo ({@link CierreRechazadoException.Motivo}).
 */
@RestController
@RequestMapping("/api/v1/perfiles/{usuario}/cierre")
public class CierreDeCuentaController {

    /** Lo que se sugiere esperar si ms-subastas no responde (igual que la lista negra). */
    static final String REINTENTAR_EN_SEGUNDOS = "30";

    private final CierreDeCuentaService servicio;
    private final UsuarioRepository usuarios;

    public CierreDeCuentaController(CierreDeCuentaService servicio, UsuarioRepository usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }

    @GetMapping
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<EstadoDelCierre> consultar(@PathVariable String usuario, HttpServletRequest peticion) {
        return sinCache(HttpStatus.OK, servicio.consultar(propia(usuario, peticion)));
    }

    @PostMapping
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<EstadoDelCierre> solicitar(@PathVariable String usuario,
                                                     @Valid @RequestBody SolicitarCierreRequest datos,
                                                     HttpServletRequest peticion) {
        Usuario cuenta = propia(usuario, peticion);
        CierreDeCuentaService.Solicitud solicitud = servicio.solicitar(cuenta, datos.passwordActual(),
                peticion.getHeader(HttpHeaders.AUTHORIZATION), IpDelCliente.de(peticion));
        return sinCache(solicitud.nueva() ? HttpStatus.CREATED : HttpStatus.OK, solicitud.estado());
    }

    @DeleteMapping
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<EstadoDelCierre> cancelar(@PathVariable String usuario, HttpServletRequest peticion) {
        return sinCache(HttpStatus.OK, servicio.cancelar(propia(usuario, peticion), IpDelCliente.de(peticion)));
    }

    @ExceptionHandler(CierreRechazadoException.class)
    public ResponseEntity<ProblemDetail> rechazado(CierreRechazadoException rechazo, HttpServletRequest peticion) {
        CierreRechazadoException.Motivo motivo = rechazo.getMotivo();
        ResponseEntity<ProblemDetail> respuesta = Problemas.de(HttpStatus.valueOf(motivo.estado()), motivo.tipo(),
                motivo.titulo(), rechazo.getMessage(), peticion.getRequestURI());
        ProblemDetail problema = respuesta.getBody();
        if (motivo == CierreRechazadoException.Motivo.OPERACIONES_PENDIENTES && problema != null) {
            problema.setProperty("subastasActivas", rechazo.getSubastasActivas());
            problema.setProperty("pujasVigentes", rechazo.getPujasVigentes());
        }
        if (motivo == CierreRechazadoException.Motivo.SUBASTAS_NO_DISPONIBLES) {
            return ResponseEntity.status(respuesta.getStatusCode())
                    .headers(respuesta.getHeaders())
                    .header(HttpHeaders.RETRY_AFTER, REINTENTAR_EN_SEGUNDOS)
                    .body(problema);
        }
        return respuesta;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> malFormada(HttpServletRequest peticion) {
        return Problemas.datosInvalidos(peticion.getRequestURI());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> sinAcceso(ResponseStatusException error, HttpServletRequest peticion) {
        HttpStatus estado = HttpStatus.valueOf(error.getStatusCode().value());
        String tipo = estado == HttpStatus.FORBIDDEN ? "forbidden" : "cuenta-no-encontrada";
        String titulo = estado == HttpStatus.FORBIDDEN ? "Acceso denegado" : "Cuenta no encontrada";
        return Problemas.de(estado, tipo, titulo, error.getReason(), peticion.getRequestURI());
    }

    /**
     * La cuenta de la ruta, solo si es la del token. Un segmento que no es un
     * UUID no nombra a nadie: 404, igual que una cuenta que no existe.
     */
    private Usuario propia(String usuario, HttpServletRequest peticion) {
        UUID uidRuta = comoUuid(usuario);
        if (uidRuta == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa cuenta.");
        }
        Object uidToken = peticion.getAttribute("uidActual");
        if (uidToken != null && !uidRuta.toString().equalsIgnoreCase(uidToken.toString().trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes gestionar tu propia cuenta.");
        }
        Usuario cuenta = usuarios.findByPublicId(uidRuta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa cuenta."));
        if (uidToken == null) {
            // Token anterior al claim uid: el apodo del interceptor (ya el vigente).
            Object apodo = peticion.getAttribute("usuarioActual");
            if (!(apodo instanceof String quien) || !quien.equalsIgnoreCase(cuenta.getApodo())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes gestionar tu propia cuenta.");
            }
        }
        return cuenta;
    }

    private static UUID comoUuid(String valor) {
        if (valor == null || valor.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException noEsUuid) {
            return null;
        }
    }

    private static ResponseEntity<EstadoDelCierre> sinCache(HttpStatus estado, EstadoDelCierre cuerpo) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
