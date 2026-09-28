package nexus.inventario.api;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nexus.inventario.aplicacion.EntregarProductos;
import nexus.inventario.aplicacion.EntregarProductos.ResultadoEntrega;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/inventario/entregas} — B4, contrato de inventario 1.5.0.
 *
 * <p>Solo un servicio (rol SERVICIO) o un ADMINISTRADOR/SUPER_ADMINISTRADOR
 * llega aqui: lo exige la cadena de seguridad. 201 cuando esta llamada hizo la
 * entrega (nueva, o retomada tras un fallo a medias); 200 con la entrega
 * original cuando la clave ya estaba completada.
 */
@RestController
@RequestMapping("/api/v1/inventario/entregas")
public class EntregasController {

    private final EntregarProductos entregas;
    private final IdentidadDelLlamador identidad;

    public EntregasController(EntregarProductos entregas, IdentidadDelLlamador identidad) {
        this.entregas = entregas;
        this.identidad = identidad;
    }

    @PostMapping
    public ResponseEntity<EntregaResponse> entregar(
            Authentication autenticacion,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String clave,
            @Valid @RequestBody EntregaRequest solicitud) {
        ResultadoEntrega resultado = entregas.entregar(solicitud.aSolicitud(), clave.strip(), solicitante(autenticacion));
        return ResponseEntity
                .status(resultado.realizadaAhora() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(EntregaResponse.de(resultado.entrega()));
    }

    /**
     * Quien pidio la entrega, para la auditoria: el {@code azp} de un servicio
     * (su identificador de cliente) o el identificador estable de un
     * administrador. Nunca sale del cuerpo.
     */
    private String solicitante(Authentication autenticacion) {
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            String cliente = jwt.getToken().getClaimAsString("azp");
            if (identidad.esServicio(autenticacion) && cliente != null && !cliente.isBlank()) {
                return cliente;
            }
            String uid = jwt.getToken().getClaimAsString(IdentidadDelToken.CLAIM_UID);
            String candidato = uid != null && !uid.isBlank() ? uid : jwt.getToken().getSubject();
            if (candidato != null && !candidato.isBlank()) {
                return candidato;
            }
        }
        return "desconocido";
    }
}
