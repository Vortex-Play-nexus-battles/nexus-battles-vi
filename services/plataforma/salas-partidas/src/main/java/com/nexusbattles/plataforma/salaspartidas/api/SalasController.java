package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.IngresarASala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ListarSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * API de salas de batalla — HU-SAL-001 y HU-SAL-002.
 *
 * <p>Regla 2 de plataforma: todo bajo {@code /api/v1}. Un cambio incompatible
 * abre version nueva, no modifica esta.
 *
 * <p>Ni el anfitrion ni el jugador que entra viajan en el cuerpo: salen del
 * token. Si vinieran del cliente, cualquiera podria crear salas o entrar a
 * nombre de otro cambiando un campo del JSON.
 */
@RestController
@RequestMapping("/api/v1/salas")
public class SalasController {

    private final CrearSala crearSala;
    private final ListarSalas listarSalas;
    private final IngresarASala ingresarASala;

    SalasController(CrearSala crearSala, ListarSalas listarSalas, IngresarASala ingresarASala) {
        this.crearSala = crearSala;
        this.listarSalas = listarSalas;
        this.ingresarASala = ingresarASala;
    }

    /**
     * Crea una sala de batalla (RF-JUE-001).
     *
     * @return 201 con la sala creada y su cabecera {@code Location}
     */
    @PostMapping
    public ResponseEntity<SalaResponse> crear(@RequestBody CrearSalaRequest peticion,
                                              @AuthenticationPrincipal Jwt token) {

        Sala sala = crearSala.ejecutar(peticion.aParametros(), idDe(token));

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/salas/{id}")
                        .buildAndExpand(sala.id())
                        .toUri())
                .body(SalaResponse.desde(sala));
    }

    /**
     * Lista las salas para que el jugador elija una (RF-JUE-002).
     *
     * <p>Los cuatro parametros se pasan tal cual, incluso nulos: los valores por
     * defecto los decide el caso de uso, que es donde vive esa regla. Si el
     * controlador rellenara aqui el 16, habria dos sitios donde cambiarlo.
     */
    @GetMapping
    public PaginaDeSalasResponse listar(@RequestParam(required = false) Integer pagina,
                                        @RequestParam(required = false) Integer tamano,
                                        @RequestParam(required = false) Modalidad modalidad,
                                        @RequestParam(required = false) EstadoSala estado) {

        return PaginaDeSalasResponse.desde(listarSalas.ejecutar(pagina, tamano, modalidad, estado));
    }

    /**
     * Ingresa al jugador autenticado en la sala elegida (RF-JUE-002).
     *
     * <p>Los tres rechazos posibles los traduce {@code ManejadorDeErrores} a los
     * codigos que fija el contrato, y la interfaz los distingue por el campo
     * {@code type}: 404 la sala no existe, 403 es privada y falta la
     * invitacion, 409 esta llena o la partida ya empezo.
     */
    @PostMapping("/{idSala}/participantes")
    public SalaResponse ingresar(@PathVariable UUID idSala,
                                 @AuthenticationPrincipal Jwt token) {

        return SalaResponse.desde(ingresarASala.ejecutar(idSala, idDe(token)));
    }

    /** La identidad del jugador es el sujeto del token, nunca un dato del cuerpo. */
    private static UUID idDe(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
