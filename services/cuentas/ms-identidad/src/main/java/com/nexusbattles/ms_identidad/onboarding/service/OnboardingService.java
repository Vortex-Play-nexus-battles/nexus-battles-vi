package com.nexusbattles.ms_identidad.onboarding.service;

import com.nexusbattles.ms_identidad.onboarding.dto.OnboardingResponse;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoPaso;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingJugador;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingPaso;
import com.nexusbattles.ms_identidad.onboarding.model.PasoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingJugadorRepository;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingPasoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerta de entrada al alta del jugador: la crea junto con la cuenta, dice
 * como va y la reanuda cuando hace falta.
 *
 * <p>El trabajo de verdad lo hace {@link ProcesadorOnboarding}; aqui solo se
 * decide cuando lanzarlo.
 */
@Service
public class OnboardingService {

    /** Version del alta. Entra en la clave de idempotencia de cada paso. */
    public static final int VERSION_BOOTSTRAP = 1;

    /** Pulsar «Reintentar» dos veces seguidas no lanza dos intentos. */
    static final Duration PAUSA_MANUAL = Duration.ofSeconds(3);

    private static final List<PasoOnboarding> PASOS_REMOTOS =
            List.of(PasoOnboarding.CREDITOS, PasoOnboarding.HEROE, PasoOnboarding.EQUIPO);

    private final OnboardingJugadorRepository jugadores;
    private final OnboardingPasoRepository pasos;
    private final ApplicationEventPublisher eventos;
    private final LanzadorOnboarding lanzador;
    private final Clock reloj;

    @Autowired
    public OnboardingService(OnboardingJugadorRepository jugadores,
                             OnboardingPasoRepository pasos,
                             ApplicationEventPublisher eventos,
                             LanzadorOnboarding lanzador) {
        this(jugadores, pasos, eventos, lanzador, Clock.systemUTC());
    }

    OnboardingService(OnboardingJugadorRepository jugadores,
                      OnboardingPasoRepository pasos,
                      ApplicationEventPublisher eventos,
                      LanzadorOnboarding lanzador,
                      Clock reloj) {
        this.jugadores = jugadores;
        this.pasos = pasos;
        this.eventos = eventos;
        this.lanzador = lanzador;
        this.reloj = reloj;
    }

    /**
     * Crea el alta de un jugador recien registrado, dentro de la misma
     * transaccion que su cuenta: nacen las dos o ninguna. El perfil ya se creo
     * en esa transaccion, asi que su paso nace hecho; los demas, pendientes.
     * El procesamiento empieza cuando la transaccion se confirma
     * ({@link AlRegistrarJugador}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void iniciar(UUID uid, String apodo, String traceId, String ip) {
        LocalDateTime ahora = ahora();
        jugadores.save(new OnboardingJugador(uid, VERSION_BOOTSTRAP, traceId, ahora));
        pasos.save(new OnboardingPaso(uid, PasoOnboarding.PERFIL, EstadoPaso.HECHO,
                "perfil creado en el registro", ahora));
        for (PasoOnboarding paso : PASOS_REMOTOS) {
            pasos.save(new OnboardingPaso(uid, paso, EstadoPaso.PENDIENTE, null, ahora));
        }
        eventos.publishEvent(new JugadorRegistrado(uid, apodo, ip));
    }

    @Transactional(readOnly = true)
    public OnboardingResponse estadoDe(UUID uid) {
        return leerEstado(uid);
    }

    /**
     * Sin anotacion de transaccion a proposito: la llaman {@link #estadoDe}
     * (dentro de su transaccion de lectura) y {@link #solicitarReintento} (sin
     * ninguna); cada consulta del repositorio abre la suya si hace falta.
     */
    private OnboardingResponse leerEstado(UUID uid) {
        if (uid == null) {
            return OnboardingResponse.noAplica();
        }
        Optional<OnboardingJugador> alta = jugadores.findById(uid);
        return alta.map(encontrada -> OnboardingResponse.de(encontrada, pasos.findByUsuarioUid(uid)))
                .orElseGet(OnboardingResponse::noAplica);
    }

    /**
     * Boton «Reintentar» de la pantalla de preparacion.
     *
     * <p>{@code NOT_SUPPORTED} en este metodo y en los dos siguientes: se leen
     * con su propia transaccion corta y nunca con el contexto de persistencia
     * de quien llama. El login los llama desde dentro de su transaccion; si
     * compartieran contexto, el alta leida antes de relanzarla se quedaria en
     * cache y {@link #listo} diria «no» aunque el alta acabara de completarse.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OnboardingResponse solicitarReintento(UUID uid) {
        if (uid == null) {
            return OnboardingResponse.noAplica();
        }
        jugadores.findById(uid)
                .filter(alta -> admiteIntento(alta, PAUSA_MANUAL))
                .ifPresent(alta -> lanzador.lanzar(uid));
        return leerEstado(uid);
    }

    /**
     * Al iniciar sesion: si el alta quedo a medias, se relanza sin esperar al
     * reintento programado. No retrasa el login (el lanzador no bloquea).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reanudarSiHaceFalta(UUID uid) {
        if (uid == null) {
            return;
        }
        Optional<OnboardingJugador> alta = jugadores.findById(uid);
        if (alta.isEmpty()) {
            // Cuenta anterior al alta automatica: nunca tuvo fila, asi que
            // nunca recibio creditos, ni heroe, ni equipo. Entrar es la
            // ocasion natural para darselos, y es idempotente por
            // construccion -- el bono va con su refId y el inventario se mira
            // antes de crear -- asi que no puede duplicar nada.
            crearAltaDeCuentaAnterior(uid);
            lanzador.lanzar(uid);
            return;
        }
        alta.filter(encontrada -> admiteIntento(encontrada, Duration.ZERO))
                .ifPresent(encontrada -> lanzador.lanzar(uid));
    }

    /**
     * Da de alta a una cuenta que existia antes de que el alta existiera.
     *
     * <p>Se separa de {@link #iniciar} porque alli el perfil nace hecho dentro
     * de la transaccion del registro. Aqui la cuenta ya tiene perfil desde
     * hace tiempo: el paso nace hecho igual, pero no hay evento de registro
     * que publicar ni transaccion de la que colgarse.
     *
     * <p>Sin transaccion propia a proposito. Las dos tablas tienen clave
     * asignada -- el uid, y (uid, paso) -- asi que guardar es reescribir la
     * misma fila: dos sesiones que entren a la vez escriben lo mismo y ninguna
     * duplica nada. Envolverlo en REQUIRES_NEW desde este mismo objeto ademas
     * no haria nada, porque una llamada interna no pasa por el proxy de Spring.
     */
    void crearAltaDeCuentaAnterior(UUID uid) {
        if (jugadores.existsById(uid)) {
            return;
        }
        LocalDateTime ahora = ahora();
        jugadores.save(new OnboardingJugador(uid, VERSION_BOOTSTRAP, "alta-diferida", ahora));
        pasos.save(new OnboardingPaso(uid, PasoOnboarding.PERFIL, EstadoPaso.HECHO,
                "perfil anterior al alta automatica", ahora));
        for (PasoOnboarding paso : PASOS_REMOTOS) {
            pasos.save(new OnboardingPaso(uid, paso, EstadoPaso.PENDIENTE, null, ahora));
        }
    }

    private boolean admiteIntento(OnboardingJugador alta, Duration pausa) {
        LocalDateTime ahora = ahora();
        return switch (alta.getEstado()) {
            case COMPLETO -> false;
            case PENDIENTE -> true;
            case ERROR_REINTENTABLE -> alta.getActualizadoEn() == null
                    || !alta.getActualizadoEn().plus(pausa).isAfter(ahora);
            case EN_PROCESO -> alta.getEnProcesoHasta() != null && alta.getEnProcesoHasta().isBefore(ahora);
        };
    }

    /**
     * Si el alta de este jugador ya termino.
     *
     * <p>Sin fila responde {@code true} a proposito: es una cuenta anterior
     * que todavia no ha entrado, y bloquearla seria peor que dejarla pasar. En
     * cuanto entre, {@link #reanudarSiHaceFalta} le crea el alta y la lanza.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean listo(UUID uid) {
        return uid == null || jugadores.findById(uid)
                .map(alta -> alta.getEstado() == EstadoOnboarding.COMPLETO)
                .orElse(true);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
