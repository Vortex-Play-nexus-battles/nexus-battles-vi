package com.nexusbattles.ms_chatbot.chat.brechas;

import com.nexusbattles.ms_chatbot.chat.analitica.PeriodoDelPanel;
import com.nexusbattles.ms_chatbot.chat.model.EstadoBrecha;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

// ms-chatbot.yaml 1.7.0 (7.4.5, RF-CHA-011): las preguntas sin respuesta en
// el panel del asistente. Rol exigido por SecurityConfig para todo
// /chatbot/admin/** (ADMINISTRADOR o SUPER_ADMINISTRADOR; sin token 401, con
// otro rol 403).
//
// El periodo es el mismo del tablero de analiticas (PeriodoDelPanel): sin
// fechas, los ultimos 30 dias.
@RestController
@RequestMapping("/chatbot/admin/brechas")
public class BrechasAdminController {

    static final String CLAIM_UID = "uid";

    private final BrechasAdminService servicio;

    public BrechasAdminController(BrechasAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public PaginaDeBrechasResponse listar(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        @RequestParam(required = false) EstadoBrecha estado,
        @RequestParam(defaultValue = "0") @Min(0) int pagina,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {

        return PaginaDeBrechasResponse.desde(
            servicio.listar(PeriodoDelPanel.resolver(desde, hasta), estado, pagina, tamano));
    }

    @PatchMapping("/{brechaId}")
    public BrechaResponse cambiarEstado(@PathVariable UUID brechaId,
                                        @RequestBody @Valid CambiarEstadoDeBrechaRequest cuerpo,
                                        Authentication authentication) {
        return BrechaResponse.desde(servicio.cambiarEstado(brechaId, cuerpo.estado(), quienAdministra(authentication)));
    }

    // Quien marca: el claim `uid` del token del administrador o, si el token
    // no lo trae, su `sub`. Nunca un dato del cuerpo de la peticion.
    static String quienAdministra(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt
            && jwt.getToken().getClaims().get(CLAIM_UID) instanceof String uid && !uid.isBlank()) {
            return uid.strip();
        }
        return authentication == null ? null : authentication.getName();
    }
}
