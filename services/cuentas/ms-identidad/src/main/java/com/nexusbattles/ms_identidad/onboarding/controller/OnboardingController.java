package com.nexusbattles.ms_identidad.onboarding.controller;

import com.nexusbattles.ms_identidad.onboarding.dto.OnboardingResponse;
import com.nexusbattles.ms_identidad.onboarding.service.OnboardingService;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * El alta del jugador que ha iniciado sesion (R17): como va y reintentarla.
 *
 * <p>Solo lo propio: el uid sale del token ya validado por el interceptor
 * ({@code uidActual}), nunca de la ruta ni del cuerpo, asi que no hay forma
 * de mirar ni de relanzar el alta de otro. Cualquier rol con sesion puede
 * llamar; una cuenta sin alta (administracion, cuentas anteriores a R17)
 * recibe {@code NO_APLICA} con {@code listo=true}.
 *
 * <p>{@code no-store}: es un estado que cambia en segundos y la pantalla lo
 * consulta varias veces; una copia en cache la dejaria atascada.
 */
@RestController
@RequestMapping("/api/v1/auth/onboarding")
public class OnboardingController {

    private final OnboardingService servicio;

    public OnboardingController(OnboardingService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<OnboardingResponse> consultar(HttpServletRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.estadoDe(uidDe(peticion)));
    }

    /** 202: el intento se lanza en segundo plano; el avance se sigue con el GET. */
    @PostMapping("/reintentos")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<OnboardingResponse> reintentar(HttpServletRequest peticion) {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(servicio.solicitarReintento(uidDe(peticion)));
    }

    static UUID uidDe(HttpServletRequest peticion) {
        Object uid = peticion.getAttribute("uidActual");
        if (uid == null) {
            return null;
        }
        try {
            return UUID.fromString(uid.toString());
        } catch (IllegalArgumentException noEsUuid) {
            return null;
        }
    }
}
