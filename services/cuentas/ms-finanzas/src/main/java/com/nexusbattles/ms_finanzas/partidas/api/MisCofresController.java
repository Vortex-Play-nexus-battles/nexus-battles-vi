package com.nexusbattles.ms_finanzas.partidas.api;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.ms_finanzas.partidas.MisCofresConsultaService;
import com.nexusbattles.ms_finanzas.partidas.ResumenCofre;

/**
 * Sirve la pantalla "Mis cofres" (HU-JUE-012). El {@code uid} se lee del
 * {@link Principal} inyectado por Spring Security (ConversorRolesJwt marca
 * {@code principalClaimName = "uid"}), nunca del path ni del query — así
 * un usuario no puede consultar los cofres de otro.
 */
@RestController
@RequestMapping("/cofres")
public class MisCofresController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final MisCofresConsultaService consultaService;

    public MisCofresController(MisCofresConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping("/mios")
    public ResponseEntity<Page<ResumenCofre>> mios(
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "20") int tamanoPagina) {

        String uid = principal.getName();
        Pageable pageable = PageRequest.of(
                Math.max(pagina, 0),
                Math.min(Math.max(tamanoPagina, 1), TAMANO_MAXIMO_PAGINA));
        return ResponseEntity.ok(consultaService.listarPorUsuario(uid, pageable));
    }
}
