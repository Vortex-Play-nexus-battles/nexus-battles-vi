package nexus.misiones.api;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import java.util.List;
import nexus.misiones.aplicacion.ConsultarMisiones;
import nexus.misiones.aplicacion.GestionarFavoritas;
import nexus.misiones.aplicacion.MisionParaJugador;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.EstadoMision;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tablon, destacadas, detalle y favoritas (seccion 7.8.9). Solo traduce: el
 * jugador es el {@code uid} de su token y el resto lo deciden los casos de uso.
 */
@RestController
@RequestMapping("/api/v1/misiones")
class MisionesController {

    private final ConsultarMisiones consultar;
    private final GestionarFavoritas favoritas;
    private final VistasDeMisiones vistas;

    MisionesController(ConsultarMisiones consultar, GestionarFavoritas favoritas, VistasDeMisiones vistas) {
        this.consultar = consultar;
        this.favoritas = favoritas;
        this.vistas = vistas;
    }

    /** Los tramos del filtro de duracion del contrato. */
    enum TramoDeDuracion { HASTA_12, DE_12_A_24, MAS_DE_24 }

    @GetMapping
    Respuestas.Pagina tablero(@AuthenticationPrincipal Jwt token,
                              @RequestParam Categoria categoria,
                              @RequestParam(required = false) Dificultad dificultad,
                              @RequestParam(required = false) EstadoMision estado,
                              @RequestParam(required = false) TramoDeDuracion duracion,
                              @RequestParam(defaultValue = "0") int pagina) {
        return vistas.pagina(consultar.tablero(jugador(token), categoria, dificultad, estado,
                duracion == null ? null : duracion.name(), pagina));
    }

    @GetMapping("/destacadas")
    List<Respuestas.Resumen> destacadas(@AuthenticationPrincipal Jwt token) {
        return vistas.resumenes(consultar.destacadas(jugador(token)));
    }

    @GetMapping("/{misionId}")
    Respuestas.Detalle detalle(@AuthenticationPrincipal Jwt token, @PathVariable String misionId) {
        String jugador = jugador(token);
        MisionParaJugador mision = consultar.detalle(jugador, misionId);
        return vistas.detalle(mision, consultar.intentosRestantes(jugador, mision.mision()));
    }

    @PutMapping("/{misionId}/favorita")
    ResponseEntity<Void> marcarFavorita(@AuthenticationPrincipal Jwt token, @PathVariable String misionId) {
        favoritas.marcar(jugador(token), misionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{misionId}/favorita")
    ResponseEntity<Void> desmarcarFavorita(@AuthenticationPrincipal Jwt token, @PathVariable String misionId) {
        favoritas.desmarcar(jugador(token), misionId);
        return ResponseEntity.noContent().build();
    }

    static String jugador(Jwt token) {
        return IdentidadDelToken.idDe(token).toString();
    }
}
