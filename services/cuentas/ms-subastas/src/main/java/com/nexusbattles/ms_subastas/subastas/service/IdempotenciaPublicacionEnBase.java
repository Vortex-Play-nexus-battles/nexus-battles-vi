package com.nexusbattles.ms_subastas.subastas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import com.nexusbattles.ms_subastas.subastas.model.PublicacionIdempotente;
import com.nexusbattles.ms_subastas.subastas.port.IdempotenciaPublicacion;
import com.nexusbattles.ms_subastas.subastas.repository.PublicacionIdempotenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.Motivo.CONFLICTO;
import static com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.Motivo.DEPENDENCIA_NO_DISPONIBLE;

/**
 * La idempotencia de {@code POST /subastas} en la base de datos del servicio
 * (B8). Sustituye al mapa en memoria, que se perdia con cada reinicio y no se
 * compartia entre replicas.
 *
 * <p><b>Cada operacion va en su propia transaccion</b>
 * ({@code REQUIRES_NEW}). Adquirir tiene que verse fuera de la publicacion en
 * curso en el acto —es lo que hace que un duplicado concurrente reciba 409 en
 * vez de publicar otra vez—, y confirmar, liberar o marcar incierta ocurren
 * cuando la transaccion de la publicacion ya termino (despues del commit o del
 * rollback), asi que no tienen otra en la que apoyarse.
 *
 * <p><b>Una clave EN_CURSO no se queda asi para siempre.</b> Si el proceso
 * muere a mitad de una publicacion, la fila queda EN_CURSO y nadie la cierra.
 * Pasado el plazo ({@code app.subastas.idempotencia.en-curso-caduca-segundos},
 * mas que cualquier tiempo de espera de la publicacion), el siguiente
 * reintento la pasa a INCIERTA: no se sabe si llego a debitarse la comision,
 * y volver a publicar podria cobrarla dos veces. Mismo criterio que ya tenia
 * el mapa en memoria para los resultados inciertos: se para y se pide
 * conciliacion, no se adivina.
 *
 * <p>Los textos de los rechazos son los de siempre: la interfaz
 * ({@code cliente-publicacion-subastas.js}) los reconoce por el texto.
 */
@Component
public class IdempotenciaPublicacionEnBase implements IdempotenciaPublicacion {

    private static final Logger log = LoggerFactory.getLogger(IdempotenciaPublicacionEnBase.class);

    static final String OTRA_SOLICITUD = "La clave de idempotencia fue usada con otra solicitud";
    static final String EN_CURSO = "Publicacion con esta clave en curso; reintente tras su finalizacion";
    static final String INCIERTA = "Resultado transaccional desconocido; requiere conciliacion";

    private final PublicacionIdempotenteRepository repositorio;
    private final TransactionTemplate propia;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Duration caducidadEnCurso;

    public IdempotenciaPublicacionEnBase(PublicacionIdempotenteRepository repositorio,
                                         PlatformTransactionManager transacciones, ObjectMapper mapper, Clock clock,
                                         @Value("${app.subastas.idempotencia.en-curso-caduca-segundos:120}")
                                         long caducaSegundos) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.propia = new TransactionTemplate(transacciones);
        this.propia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
        this.caducidadEnCurso = Duration.ofSeconds(caducaSegundos);
    }

    @Override
    public Adquisicion adquirir(String clave, String huella) {
        UUID titular = UUID.randomUUID();
        // Dos vueltas como mucho: si la fila que chocaba se libera justo entre
        // el INSERT y la lectura, se vuelve a intentar adquirirla una vez.
        for (int intento = 0; intento < 2; intento++) {
            Decision decision = propia.execute(tx -> decidir(clave, huella, titular));
            if (decision == null || decision.reintentar()) {
                continue;
            }
            if (decision.rechazo() != null) {
                throw decision.rechazo();
            }
            return decision.adquisicion();
        }
        throw new PublicacionSubastaException(CONFLICTO, EN_CURSO);
    }

    private Decision decidir(String clave, String huella, UUID titular) {
        Instant ahora = clock.instant();
        if (repositorio.insertarSiNoExiste(clave, huella, titular, ahora) == 1) {
            return Decision.adquirida(new Adquisicion(titular, Optional.empty()));
        }
        Optional<PublicacionIdempotente> encontrada = repositorio.findById(clave);
        if (encontrada.isEmpty()) {
            return Decision.otraVez();
        }
        PublicacionIdempotente previa = encontrada.get();
        if (!previa.getHuella().equals(huella)) {
            return Decision.rechazada(new PublicacionSubastaException(CONFLICTO, OTRA_SOLICITUD));
        }
        return switch (previa.getEstado()) {
            case CONFIRMADA -> Decision.adquirida(new Adquisicion(previa.getTitular(), Optional.of(resultadoDe(previa))));
            case INCIERTA -> Decision.rechazada(new PublicacionSubastaException(DEPENDENCIA_NO_DISPONIBLE, INCIERTA));
            case EN_CURSO -> {
                if (previa.getActualizadaEn().plus(caducidadEnCurso).isBefore(ahora)) {
                    previa.setEstado(PublicacionIdempotente.Estado.INCIERTA);
                    previa.setActualizadaEn(ahora);
                    repositorio.save(previa);
                    log.error("La publicacion con la clave {} quedo EN_CURSO desde {}: se marca incierta y "
                            + "requiere conciliacion", clave, previa.getCreadaEn());
                    yield Decision.rechazada(new PublicacionSubastaException(DEPENDENCIA_NO_DISPONIBLE, INCIERTA));
                }
                yield Decision.rechazada(new PublicacionSubastaException(CONFLICTO, EN_CURSO));
            }
        };
    }

    @Override
    public Optional<Resultado> buscar(String clave) {
        return propia.execute(tx -> repositorio.findById(clave)
                .filter(fila -> fila.getEstado() == PublicacionIdempotente.Estado.CONFIRMADA)
                .map(this::resultadoDe));
    }

    @Override
    public void confirmar(String clave, UUID titular, PublicarSubastaResponse respuesta) {
        propia.executeWithoutResult(tx -> delTitularEnCurso(clave, titular).ifPresent(fila -> {
            fila.setEstado(PublicacionIdempotente.Estado.CONFIRMADA);
            fila.setSubastaId(respuesta.id());
            fila.setRespuesta(serializar(respuesta));
            fila.setActualizadaEn(clock.instant());
            repositorio.save(fila);
        }));
    }

    @Override
    public void liberar(String clave, UUID titular) {
        propia.executeWithoutResult(tx -> delTitularEnCurso(clave, titular).ifPresent(repositorio::delete));
    }

    @Override
    public void marcarIncierta(String clave, UUID titular) {
        propia.executeWithoutResult(tx -> delTitularEnCurso(clave, titular).ifPresent(fila -> {
            fila.setEstado(PublicacionIdempotente.Estado.INCIERTA);
            fila.setActualizadaEn(clock.instant());
            repositorio.save(fila);
        }));
    }

    /** Solo el titular toca su clave, y solo mientras esta en curso. */
    private Optional<PublicacionIdempotente> delTitularEnCurso(String clave, UUID titular) {
        return repositorio.findById(clave)
                .filter(fila -> fila.getTitular().equals(titular))
                .filter(fila -> fila.getEstado() == PublicacionIdempotente.Estado.EN_CURSO);
    }

    private Resultado resultadoDe(PublicacionIdempotente fila) {
        try {
            return new Resultado(fila.getHuella(), fila.getSubastaId(),
                    mapper.readValue(fila.getRespuesta(), PublicarSubastaResponse.class));
        } catch (JsonProcessingException | IllegalArgumentException ilegible) {
            // Una respuesta guardada que no se puede leer no se reproduce ni se
            // publica otra vez encima: es un resultado desconocido.
            throw new PublicacionSubastaException(DEPENDENCIA_NO_DISPONIBLE, INCIERTA, ilegible);
        }
    }

    private String serializar(PublicarSubastaResponse respuesta) {
        try {
            return mapper.writeValueAsString(respuesta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la respuesta de la publicacion", e);
        }
    }

    /** Lo que resolvio la transaccion de adquirir; la excepcion se lanza fuera de ella. */
    private record Decision(Adquisicion adquisicion, PublicacionSubastaException rechazo, boolean reintentar) {
        static Decision adquirida(Adquisicion adquisicion) {
            return new Decision(adquisicion, null, false);
        }

        static Decision rechazada(PublicacionSubastaException rechazo) {
            return new Decision(null, rechazo, false);
        }

        static Decision otraVez() {
            return new Decision(null, null, true);
        }
    }
}
