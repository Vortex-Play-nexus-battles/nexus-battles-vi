package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.servicio.ActorDeServicio;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * La apelacion de una cuenta que no puede iniciar sesion — SA-4,
 * moderacion-sanciones-consulta.yaml 1.5.0 (§7.3.2: «Los usuarios
 * sancionados deben tener derecho a apelar»; RF-USR-007 deja sin decir si el
 * baneado conserva el acceso al portal, y no lo conserva).
 *
 * <p>Una cuenta suspendida o baneada no tiene sesion. Cuando escribe bien su
 * contrasena, ms-identidad le da un codigo opaco de un solo uso y con el
 * pregunta y apela aqui en su nombre, con SU credencial de servicio. Por eso
 * estas rutas no las llama nadie mas: rol {@code SERVICIO} (la cadena de
 * {@code SecurityConfig}) y, ademas, {@code azp} = {@value #IDENTIDAD}. Un
 * token de jugador o de otro servicio recibe 403 sin llegar al servicio. El
 * borde no publica {@code /api/v1/internal}.
 *
 * <p>Por que no un token restringido: la biblioteca de seguridad comun no
 * comprueba la audiencia, asi que un JWT «solo para apelar» lo aceptaria
 * cualquier ruta que pida estar autenticado. Un codigo opaco no abre nada.
 */
@RestController
@RequestMapping("/api/v1/internal/sanciones/usuarios/{usuarioId}")
public class ApelacionSinSesionController {

    /** El unico servicio que presenta apelaciones sin sesion. */
    static final String IDENTIDAD = "ms-identidad";

    private final SancionesService servicio;
    private final PlazoDeDecision plazo;
    private final Clock reloj;

    public ApelacionSinSesionController(SancionesService servicio, PlazoDeDecision plazo, Clock reloj) {
        this.servicio = servicio;
        this.plazo = plazo;
        this.reloj = reloj;
    }

    /** La sancion que impide entrar, hasta cuando se apela y como va la ultima apelacion. */
    @GetMapping("/apelacion")
    public EstadoDeApelacionResponse estado(@AuthenticationPrincipal Jwt quien, @PathVariable UUID usuarioId) {
        exigirIdentidad(quien);
        SancionesService.EstadoDeApelacion estado = servicio.estadoDeApelacionDe(usuarioId);
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        return new EstadoDeApelacionResponse(
                estado.sancion() == null ? null : SancionesAdminController.SancionResponse.desde(estado.sancion()),
                estado.apelableHasta(), estado.puedeApelar(), estado.motivoNoApelable(),
                estado.apelacion() == null ? null
                        : SancionesAdminController.ApelacionResponse.desde(estado.apelacion(), plazo, ahora));
    }

    /** Presenta la apelacion de la sancion que impide entrar, con las reglas de siempre. */
    @PostMapping("/apelaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public SancionesAdminController.ApelacionResponse apelar(@AuthenticationPrincipal Jwt quien,
                                                              @PathVariable UUID usuarioId,
                                                              @RequestBody ApelacionSinSesionRequest request) {
        exigirIdentidad(quien);
        Apelacion apelacion = servicio.apelarSinSesion(usuarioId, request.sancionId(), request.argumento());
        return SancionesAdminController.ApelacionResponse.desde(apelacion, plazo, OffsetDateTime.now(reloj));
    }

    private static void exigirIdentidad(Jwt token) {
        if (ActorDeServicio.desde(token).filter(IDENTIDAD::equals).isEmpty()) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE,
                    "solo ms-identidad presenta apelaciones de cuentas sin sesion");
        }
    }

    public record ApelacionSinSesionRequest(UUID sancionId, String argumento) {
    }

    /**
     * @param sancion          la que restringe hoy; nula si ya no hay ninguna
     * @param apelableHasta    ultimo instante para apelar (emision + plazo vigente)
     * @param puedeApelar      si se puede apelar ahora
     * @param motivoNoApelable por que no, si no se puede
     * @param apelacion        la ultima apelacion de esa sancion, abierta o resuelta
     */
    public record EstadoDeApelacionResponse(SancionesAdminController.SancionResponse sancion,
                                            OffsetDateTime apelableHasta, boolean puedeApelar,
                                            SancionesService.MotivoNoApelable motivoNoApelable,
                                            SancionesAdminController.ApelacionResponse apelacion) {
    }
}
