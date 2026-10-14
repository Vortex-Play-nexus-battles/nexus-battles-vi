package com.nexusbattles.plataforma.comentarios.moderacion;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.plataforma.comentarios.moderacion.ModeracionController.HistorialDelAutorResponse;

/**
 * Los comentarios propios — contrato 1.16.0 (SA-14, §7.3.6 «exportación de
 * inventario, estadísticas, comentarios e historial de transacciones»).
 *
 * <p>Es el mismo historial que ve quien modera sobre un autor (1.7.0), pero
 * el autor sale SIEMPRE del token: cada persona lee los suyos y nadie lee los
 * de otro por esta ruta. Trae todos sus estados, también los ocultos y los
 * retirados, porque son datos suyos que el servicio guarda. Vive en este
 * paquete solo porque reutiliza ese historial.
 */
@RestController
public class MisComentariosController {

    private final ServicioDeModeracion servicio;

    public MisComentariosController(ServicioDeModeracion servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/v1/comentarios/mios")
    public HistorialDelAutorResponse misComentarios(
            @AuthenticationPrincipal Jwt quien,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        String uid = IdentidadDelToken.idDe(quien).toString();
        return HistorialDelAutorResponse.desde(servicio.historialDelAutor(uid, pagina, Math.min(tamano, 100)));
    }
}
