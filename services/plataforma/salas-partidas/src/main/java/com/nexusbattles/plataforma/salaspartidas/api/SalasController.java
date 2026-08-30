package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * API de salas de batalla — HU-SAL-001.
 *
 * <p>Regla 2 de plataforma: todo bajo {@code /api/v1}. Un cambio incompatible
 * abre version nueva, no modifica esta.
 *
 * <p>El anfitrion NO viaja en el cuerpo: sale del token. Si viniera del cliente,
 * cualquiera podria crear salas a nombre de otro cambiando un campo del JSON.
 */
@RestController
@RequestMapping("/api/v1/salas")
public class SalasController {

    private final CrearSala crearSala;

    SalasController(CrearSala crearSala) {
        this.crearSala = crearSala;
    }

    /**
     * Crea una sala de batalla (RF-JUE-001).
     *
     * @return 201 con la sala creada y su cabecera {@code Location}
     */
    @PostMapping
    public ResponseEntity<SalaResponse> crear(@RequestBody CrearSalaRequest peticion,
                                              @AuthenticationPrincipal Jwt token) {

        UUID idAnfitrion = UUID.fromString(token.getSubject());
        Sala sala = crearSala.ejecutar(peticion.aParametros(), idAnfitrion);

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/salas/{id}")
                        .buildAndExpand(sala.id())
                        .toUri())
                .body(SalaResponse.desde(sala, apodoDe(token)));
    }

    /** Keycloak trae el apodo en {@code preferred_username}. */
    private static String apodoDe(Jwt token) {
        String apodo = token.getClaimAsString("preferred_username");
        return apodo == null ? token.getSubject() : apodo;
    }
}
