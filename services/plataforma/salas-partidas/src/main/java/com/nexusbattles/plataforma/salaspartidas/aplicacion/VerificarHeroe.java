package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;

import java.util.Objects;
import java.util.UUID;

/**
 * Verificacion previa de heroe — HU-SAL-003, RF-JUE-003.
 *
 * <p><b>No tiene efectos.</b> No mete a nadie en la sala, no reserva creditos y
 * no bloquea el heroe. Su unico trabajo es responder a tiempo: el criterio de
 * aceptacion dice que el jugador no debe descubrir el problema <i>despues</i> de
 * pulsar Entrar, asi que la interfaz pregunta antes y pinta el dialogo con el
 * motivo. El rechazo de verdad —el que si tiene efectos— sigue estando en el
 * ingreso.
 *
 * <p>La sala se carga aunque el veredicto no dependa de ella, por dos razones:
 * para devolver 404 cuando el enlace apunta a una sala que ya no existe, en vez
 * de un dialogo sobre una sala fantasma, y porque de ella sale la recompensa
 * comprometida que el dialogo muestra.
 */
public class VerificarHeroe {

    private final RepositorioDeSalas repositorio;
    private final HeroeDelJugador heroes;

    public VerificarHeroe(RepositorioDeSalas repositorio, HeroeDelJugador heroes) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.heroes = Objects.requireNonNull(heroes);
    }

    /**
     * @param idSala  sala que el jugador esta mirando
     * @param jugador jugador autenticado; pregunta por su propio heroe y nunca
     *                por el de otro, porque la identidad sale del token
     * @return el veredicto listo para pintar
     * @throws SalaNoEncontrada si la sala no existe o ya se cerro
     */
    public VerificacionDeIngreso ejecutar(UUID idSala, JugadorAutenticado jugador) {
        Objects.requireNonNull(idSala, "Hace falta la sala que se quiere verificar.");
        Objects.requireNonNull(jugador, "Hace falta el jugador que pregunta.");

        Sala sala = repositorio.buscarPorId(idSala)
                .orElseThrow(() -> new SalaNoEncontrada(idSala));

        EstadoDelHeroe estado = heroes.consultar(jugador);

        return VerificacionDeIngreso.de(estado, sala.recompensaCreditos());
    }
}
