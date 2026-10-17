package nexus.misiones.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import nexus.misiones.aplicacion.CancelarEjecucion;
import nexus.misiones.aplicacion.ConsultarEjecuciones;
import nexus.misiones.aplicacion.EjecucionConMision;
import nexus.misiones.aplicacion.Matricula;
import nexus.misiones.aplicacion.MatricularHeroe;
import nexus.misiones.aplicacion.SolicitudDeMatricula;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.EjecucionNoEncontrada;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.MisionNoEncontrada;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Matricula, misiones en curso, reporte, historial y cancelacion (7.8.6 a
 * 7.8.9).
 */
@RestController
@RequestMapping("/api/v1/misiones")
class EjecucionesController {

    /** Maximo de la cabecera {@code Idempotency-Key} en el contrato. */
    static final int MAXIMO_CLAVE = 100;

    private final MatricularHeroe matricular;
    private final ConsultarEjecuciones consultar;
    private final CancelarEjecucion cancelar;
    private final CatalogoDeMisiones catalogo;
    private final VistasDeMisiones vistas;

    EjecucionesController(MatricularHeroe matricular, ConsultarEjecuciones consultar, CancelarEjecucion cancelar,
                          CatalogoDeMisiones catalogo, VistasDeMisiones vistas) {
        this.matricular = matricular;
        this.consultar = consultar;
        this.cancelar = cancelar;
        this.catalogo = catalogo;
        this.vistas = vistas;
    }

    @PostMapping("/{misionId}/ejecuciones")
    ResponseEntity<Respuestas.MisionActiva> matricular(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String misionId,
            @RequestHeader(name = "Idempotency-Key", required = false) String clave,
            @Valid @RequestBody Solicitudes.Matricula solicitud) {
        if (clave != null && (clave.isBlank() || clave.length() > MAXIMO_CLAVE)) {
            throw new IllegalArgumentException("Idempotency-Key debe tener entre 1 y " + MAXIMO_CLAVE + " caracteres.");
        }
        Matricula matricula = matricular.matricular(MisionesController.jugador(token),
                new SolicitudDeMatricula(misionId, solicitud.heroeId(), Solicitudes.comoListas(solicitud.rotaciones()),
                        solicitud.escalon(), clave));
        Mision mision = catalogo.buscar(matricula.ejecucion().misionId())
                .orElseThrow(() -> new MisionNoEncontrada(matricula.ejecucion().misionId()));
        return ResponseEntity.status(matricula.repetida() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(vistas.activa(matricula.ejecucion(), mision, CancelarEjecucion.penalizacionDe(matricula.ejecucion())));
    }

    @GetMapping("/en-curso")
    List<Respuestas.MisionActiva> enCurso(@AuthenticationPrincipal Jwt token) {
        return consultar.enCurso(MisionesController.jugador(token)).stream()
                .map(t -> vistas.activa(t.ejecucion(), t.mision(), CancelarEjecucion.penalizacionDe(t.ejecucion())))
                .toList();
    }

    @GetMapping("/historial")
    Respuestas.Historial historial(@AuthenticationPrincipal Jwt token) {
        return vistas.historial(consultar.historial(MisionesController.jugador(token)));
    }

    @GetMapping("/ejecuciones/{ejecucionId}")
    Respuestas.Reporte reporte(@AuthenticationPrincipal Jwt token, @PathVariable String ejecucionId) {
        EjecucionConMision reporte = consultar.reporte(MisionesController.jugador(token), comoId(ejecucionId));
        return vistas.reporte(reporte);
    }

    @PostMapping("/ejecuciones/{ejecucionId}/cancelacion")
    Respuestas.Cancelacion cancelar(@AuthenticationPrincipal Jwt token, @PathVariable String ejecucionId) {
        return vistas.cancelacion(cancelar.cancelar(MisionesController.jugador(token), comoId(ejecucionId)));
    }

    /** Un identificador que no es un UUID no es de ninguna ejecucion: 404, como una ajena. */
    private static UUID comoId(String ejecucionId) {
        try {
            return UUID.fromString(ejecucionId);
        } catch (IllegalArgumentException noEsUnUuid) {
            throw new EjecucionNoEncontrada();
        }
    }
}
