package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.AbandonarSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CancelarSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CrearSala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.IngresarASala;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ListarSalas;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ObtenerSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final ObtenerSala obtenerSala;
    private final AbandonarSala abandonarSala;
    private final CancelarSala cancelarSala;

    SalasController(CrearSala crearSala, ListarSalas listarSalas, IngresarASala ingresarASala,
                    ObtenerSala obtenerSala, AbandonarSala abandonarSala,
                    CancelarSala cancelarSala) {
        this.crearSala = crearSala;
        this.listarSalas = listarSalas;
        this.ingresarASala = ingresarASala;
        this.obtenerSala = obtenerSala;
        this.abandonarSala = abandonarSala;
        this.cancelarSala = cancelarSala;
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
                // Quien crea la sala es su anfitrion: esta es la unica respuesta
                // en la que el codigo de invitacion se entrega sin pedirlo, y es
                // la que hace utilizable una sala privada.
                .body(SalaResponse.paraElAnfitrion(sala));
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
                                 @RequestBody(required = false) IngresoRequest peticion,
                                 @AuthenticationPrincipal Jwt token) {

        UUID idJugador = idDe(token);
        Sala sala = ingresarASala.ejecutar(idSala, idJugador, IngresoRequest.codigoDe(peticion));
        return SalaResponse.segunQuienPregunta(sala, idJugador);
    }

    /**
     * Devuelve una sala concreta (operacion {@code obtenerSala}).
     *
     * <p>Es el destino de la cabecera {@code Location} de la creacion y lo que
     * necesita la vista de sala cuando se llega por enlace directo, sin pasar
     * por el listado.
     *
     * <p>Al anfitrion le llega ademas el codigo de invitacion, para que pueda
     * volver a consultarlo sin tener que guardar la respuesta de la creacion.
     */
    @GetMapping("/{idSala}")
    public SalaResponse obtener(@PathVariable UUID idSala,
                                @AuthenticationPrincipal Jwt token) {

        UUID idJugador = idDe(token);
        return SalaResponse.segunQuienPregunta(obtenerSala.ejecutar(idSala), idJugador);
    }

    /**
     * Cancela la sala (operacion {@code cancelarSala}).
     *
     * <p>Solo el anfitrion: 403 si lo pide otro, 409 si la partida ya arranco.
     * Devuelve 204 porque despues de cancelarla no queda nada util que
     * representar — el estado final ya viaja por el canal, a todos los que
     * estaban dentro, en el aviso {@code sala.cancelada}.
     */
    @DeleteMapping("/{idSala}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable UUID idSala, @AuthenticationPrincipal Jwt token) {
        cancelarSala.ejecutar(idSala, idDe(token));
    }

    /**
     * Saca de la sala al jugador autenticado (operacion {@code abandonarSala}).
     *
     * <p>El anfitrion no usa este camino: recibe 409 y se le remite a cancelar.
     * Ver {@code Sala#abandonar}.
     */
    @DeleteMapping("/{idSala}/participantes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandonar(@PathVariable UUID idSala, @AuthenticationPrincipal Jwt token) {
        abandonarSala.ejecutar(idSala, idDe(token));
    }

    /** La identidad del jugador es el sujeto del token, nunca un dato del cuerpo. */
    private static UUID idDe(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
