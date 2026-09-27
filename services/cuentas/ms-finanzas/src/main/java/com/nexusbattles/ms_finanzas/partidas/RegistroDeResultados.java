package com.nexusbattles.ms_finanzas.partidas;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Procesa el resultado de una partida repitiéndolo si otra escritura se cruzó
 * — B7.
 *
 * <p>Dos partidas del mismo jugador que terminan a la vez tocan el mismo
 * contador de cofres ({@link ContadorDeCofres}, con {@code @Version}): la
 * transacción que llega segunda falla entera —créditos incluidos— y no deja
 * nada a medias. Repetirla sobre el estado nuevo es seguro porque todo lo que
 * hace es idempotente ({@code partidaId} y {@code refId}). Lo mismo cuando dos
 * crean a la vez el contador de un jugador nuevo (clave primaria duplicada), o
 * cuando llega dos veces la misma partida: el segundo intento sale como
 * {@link PartidaYaProcesadaException}, que es la respuesta correcta.
 *
 * <p>Sin esto el conflicto salía como un 500 y salas-partidas dejaba la
 * recompensa pendiente hasta su siguiente reintento. Nunca como un 409:
 * salas-partidas lee cualquier 409 de esta ruta como «ya procesada».
 */
@Service
public class RegistroDeResultados {

    private static final Logger BITACORA = LoggerFactory.getLogger(RegistroDeResultados.class);

    /** Intentos en total antes de rendirse y dejar el conflicto al llamador. */
    static final int INTENTOS = 3;

    private final AcreditacionPartidaService acreditacion;

    public RegistroDeResultados(AcreditacionPartidaService acreditacion) {
        this.acreditacion = Objects.requireNonNull(acreditacion);
    }

    public ResultadoPartidaResponse registrar(ResultadoPartidaRequest req) {
        for (int intento = 1; ; intento++) {
            try {
                return acreditacion.procesarResultadoPartida(req);
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException seCruzo) {
                if (intento >= INTENTOS) {
                    throw seCruzo;
                }
                BITACORA.info("El resultado de la partida {} se cruzo con otra escritura (intento {}); se repite",
                        req.partidaId(), intento);
            }
        }
    }
}
