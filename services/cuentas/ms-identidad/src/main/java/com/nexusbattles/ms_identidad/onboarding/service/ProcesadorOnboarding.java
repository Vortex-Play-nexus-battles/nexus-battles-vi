package com.nexusbattles.ms_identidad.onboarding.service;

import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteCreditos;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteInventario;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteInventario.Elemento;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoPaso;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingJugador;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingPaso;
import com.nexusbattles.ms_identidad.onboarding.model.PasoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingJugadorRepository;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingPasoRepository;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido.Causa;
import com.nexusbattles.ms_identidad.onboarding.service.PoliticaInicial.KitInicial;
import com.nexusbattles.ms_identidad.onboarding.service.PoliticaInicial.ProductoDelKit;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Ejecuta el alta de un jugador: creditos de bienvenida, heroe inicial y su
 * equipo, cada uno contra el servicio que es su dueno.
 *
 * <p><b>No es una transaccion distribuida</b> y no lo intenta: no hay
 * {@code @Transactional} que abarque ms-finanzas y el inventario. Cada paso se
 * anota en {@code onboarding_paso} en cuanto termina, en su propia
 * transaccion local; si el proceso muere a mitad, el siguiente intento ve lo
 * que ya esta hecho y solo hace lo que falta. Lo que hace seguro repetir un
 * paso es la idempotencia de cada uno:
 * <ul>
 *   <li>CREDITOS: la clave {@code bono-registro-v{version}-{uid}} en
 *       ms-finanzas; repetirla devuelve el credito original, no suma otro.</li>
 *   <li>HEROE y EQUIPO: el inventario no tiene idempotencia, asi que antes de
 *       crear se mira lo que el jugador ya tiene y se adopta lo que un intento
 *       anterior dejo creado. Equipar algo ya equipado se comprueba, no se
 *       supone.</li>
 * </ul>
 *
 * <p><b>Un solo trabajador por jugador.</b> El turno se toma con un UPDATE
 * condicional ({@link OnboardingJugadorRepository#reclamar}); el registro, el
 * reintento programado, el login y el boton «Reintentar» pueden llegar a la
 * vez y solo uno procesa. El turno caduca a los {@link #TURNO} por si el
 * proceso muere con el en la mano.
 */
@Service
public class ProcesadorOnboarding {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorOnboarding.class);

    /** Mucho mas de lo que tarda un intento (7 llamadas con 5 s de lectura como mucho). */
    static final Duration TURNO = Duration.ofMinutes(2);
    static final Duration ESPERA_BASE = Duration.ofSeconds(15);
    static final Duration ESPERA_MAXIMA = Duration.ofMinutes(10);
    static final List<EstadoOnboarding> RECLAMABLES =
            List.of(EstadoOnboarding.PENDIENTE, EstadoOnboarding.ERROR_REINTENTABLE);

    public enum Resultado {
        /** Otro trabajador lo tiene, o ya estaba completo: no se toco nada. */
        NO_TOMADO,
        COMPLETO,
        /** Quedo algun paso por hacer; se reintentara. */
        APLAZADO
    }

    private final OnboardingJugadorRepository jugadores;
    private final OnboardingPasoRepository pasos;
    private final PoliticaInicial politica;
    private final ClienteCreditos creditos;
    private final ClienteInventario inventario;
    private final AuditoriaDeCuenta auditoria;
    private final TransactionTemplate enTransaccionPropia;
    private final Clock reloj;

    @Autowired
    public ProcesadorOnboarding(OnboardingJugadorRepository jugadores,
                                OnboardingPasoRepository pasos,
                                PoliticaInicial politica,
                                ClienteCreditos creditos,
                                ClienteInventario inventario,
                                AuditoriaDeCuenta auditoria,
                                PlatformTransactionManager transacciones) {
        this(jugadores, pasos, politica, creditos, inventario, auditoria, transacciones, Clock.systemUTC());
    }

    ProcesadorOnboarding(OnboardingJugadorRepository jugadores,
                         OnboardingPasoRepository pasos,
                         PoliticaInicial politica,
                         ClienteCreditos creditos,
                         ClienteInventario inventario,
                         AuditoriaDeCuenta auditoria,
                         PlatformTransactionManager transacciones,
                         Clock reloj) {
        this.jugadores = jugadores;
        this.pasos = pasos;
        this.politica = politica;
        this.creditos = creditos;
        this.inventario = inventario;
        this.auditoria = auditoria;
        // REQUIRES_NEW: cada anotacion se confirma en el momento, tambien
        // cuando se llama desde dentro de otra transaccion (el login) o desde
        // un AFTER_COMMIT, donde una transaccion REQUIRED no llegaria a
        // confirmarse nunca.
        TransactionTemplate plantilla = new TransactionTemplate(transacciones);
        plantilla.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.enTransaccionPropia = plantilla;
        this.reloj = reloj;
    }

    /** Clave de idempotencia de los creditos de bienvenida en ms-finanzas. */
    public static String refIdDelBono(UUID uid, int version) {
        return "bono-registro-v" + version + "-" + uid;
    }

    public Resultado procesar(UUID uid) {
        LocalDateTime ahora = ahora();
        Integer tomado = enTransaccionPropia.execute(estado ->
                jugadores.reclamar(uid, EstadoOnboarding.EN_PROCESO, RECLAMABLES, ahora, ahora.plus(TURNO)));
        if (!Integer.valueOf(1).equals(tomado)) {
            return Resultado.NO_TOMADO;
        }
        Optional<OnboardingJugador> leida = Optional
                .ofNullable(enTransaccionPropia.execute(estado -> jugadores.findById(uid)))
                .flatMap(encontrada -> encontrada);
        if (leida.isEmpty()) {
            return Resultado.NO_TOMADO;
        }
        OnboardingJugador alta = leida.get();
        Traza.abrir(alta.getTraza());
        try {
            Map<PasoOnboarding, OnboardingPaso> porPaso = pasosDe(uid);
            Intento intento = new Intento(uid, alta.getVersionBootstrap());

            ejecutar(uid, porPaso.get(PasoOnboarding.CREDITOS), () -> pasoCreditos(intento));
            ejecutar(uid, porPaso.get(PasoOnboarding.HEROE), () -> pasoHeroe(intento));
            OnboardingPaso heroe = porPaso.get(PasoOnboarding.HEROE);
            if (heroe.hecho()) {
                ejecutar(uid, porPaso.get(PasoOnboarding.EQUIPO), () -> pasoEquipo(intento, heroe.getDetalle()));
            } else {
                ejecutar(uid, porPaso.get(PasoOnboarding.EQUIPO), () -> {
                    throw new PasoFallido(Causa.DEPENDE_DE_OTRO_PASO, "espera a que el heroe inicial exista");
                });
            }
            return cerrar(alta, porPaso);
        } finally {
            Traza.cerrar();
        }
    }

    private Map<PasoOnboarding, OnboardingPaso> pasosDe(UUID uid) {
        List<OnboardingPaso> guardados = enTransaccionPropia.execute(estado -> pasos.findByUsuarioUid(uid));
        Map<PasoOnboarding, OnboardingPaso> porPaso = new EnumMap<>(PasoOnboarding.class);
        if (guardados != null) {
            guardados.forEach(paso -> porPaso.put(paso.getPaso(), paso));
        }
        // Defensivo: si falta la fila de un paso (un alta a medio crear), nace pendiente.
        for (PasoOnboarding paso : PasoOnboarding.values()) {
            porPaso.computeIfAbsent(paso, falta -> new OnboardingPaso(uid, falta,
                    falta == PasoOnboarding.PERFIL ? EstadoPaso.HECHO : EstadoPaso.PENDIENTE, null, ahora()));
        }
        return porPaso;
    }

    private void ejecutar(UUID uid, OnboardingPaso paso, Supplier<String> accion) {
        if (paso.hecho()) {
            return;
        }
        try {
            String detalle = accion.get();
            paso.marcarHecho(detalle, ahora());
            log.info("ONBOARDING_PASO_HECHO uid={} paso={} detalle={}", uid, paso.getPaso(), detalle);
        } catch (PasoFallido fallo) {
            paso.marcarError(fallo.paraGuardar(), ahora());
            log.warn("ONBOARDING_PASO_FALLIDO uid={} paso={} causa={} detalle={}",
                    uid, paso.getPaso(), fallo.causa(), fallo.getMessage());
        } catch (RuntimeException inesperado) {
            PasoFallido fallo = new PasoFallido(Causa.RECHAZADO, "error inesperado: "
                    + inesperado.getClass().getSimpleName() + ": " + inesperado.getMessage(), inesperado);
            paso.marcarError(fallo.paraGuardar(), ahora());
            log.error("ONBOARDING_PASO_FALLIDO uid={} paso={} causa=INESPERADA", uid, paso.getPaso(), inesperado);
        }
        enTransaccionPropia.executeWithoutResult(estado -> pasos.save(paso));
    }

    private String pasoCreditos(Intento intento) {
        PoliticaInicial.CreditosIniciales iniciales = politica.creditos();
        String refId = refIdDelBono(intento.uid, intento.version);
        ClienteCreditos.Acreditacion acreditacion = creditos.acreditar(intento.uid, iniciales.monto(), refId);
        long acreditado = acreditacion.montoAcreditado() == null
                ? iniciales.monto() : acreditacion.montoAcreditado().longValue();
        return "transaccion=" + acreditacion.transaccionId() + ";monto=" + acreditado
                + ";fuente=" + iniciales.fuente() + ";ref=" + refId;
    }

    private String pasoHeroe(Intento intento) {
        KitInicial kit = intento.kit();
        ProductoDelKit producto = kit.heroe();
        Elemento heroe = intento.elementos().stream()
                .filter(e -> PoliticaInicial.HEROE.equals(e.tipo()) && producto.id().equals(e.productoId()))
                .findFirst()
                .orElseGet(() -> intento.anotar(inventario.crear(
                        intento.uid, producto.id(), PoliticaInicial.HEROE, producto.nombre(), null)));
        return "heroe=" + heroe.id() + ";producto=" + producto.id();
    }

    private String pasoEquipo(Intento intento, String detalleHeroe) {
        String heroeId = valorDe(detalleHeroe, "heroe");
        if (heroeId == null) {
            throw new PasoFallido(Causa.DEPENDE_DE_OTRO_PASO, "el paso HEROE no dejo anotado el heroe");
        }
        KitInicial kit = intento.kit();
        ClienteInventario.Equipamiento equipado = inventario.equipamientoDe(intento.uid, heroeId);
        Set<String> usados = new HashSet<>();
        List<String> equipados = new ArrayList<>();
        for (ProductoDelKit pieza : kit.equipo()) {
            Elemento elemento = intento.elementos().stream()
                    .filter(e -> pieza.id().equals(e.productoId()) && !usados.contains(e.id())
                            && (e.disponible() || equipado.contiene(e.id())))
                    // Primero el que ya lleva este heroe: no se equipa dos veces lo mismo.
                    .sorted(Comparator.comparing((Elemento e) -> !equipado.contiene(e.id())))
                    .findFirst()
                    .orElseGet(() -> intento.anotar(inventario.crear(intento.uid, pieza.id(), pieza.tipo(),
                            pieza.nombre(), PoliticaInicial.ARMADURA.equals(pieza.tipo()) ? pieza.parte() : null)));
            usados.add(elemento.id());
            if (!equipado.contiene(elemento.id())) {
                inventario.equipar(intento.uid, heroeId, elemento.id());
            }
            equipados.add(elemento.id());
        }
        return "heroe=" + heroeId + ";equipados=" + String.join(",", equipados);
    }

    private Resultado cerrar(OnboardingJugador alta, Map<PasoOnboarding, OnboardingPaso> porPaso) {
        LocalDateTime ahora = ahora();
        List<OnboardingPaso> pendientes = porPaso.values().stream().filter(paso -> !paso.hecho()).toList();
        if (pendientes.isEmpty()) {
            alta.completar(ahora);
            enTransaccionPropia.executeWithoutResult(estado -> jugadores.save(alta));
            String resumen = resumen(porPaso);
            auditoria.altaCompletada(alta.getUsuarioUid(), resumen);
            log.info("ONBOARDING_COMPLETO uid={} intentos={} {}", alta.getUsuarioUid(), alta.getIntentos(), resumen);
            return Resultado.COMPLETO;
        }
        String motivo = pendientes.stream()
                .map(paso -> paso.getPaso() + "=" + paso.getUltimoError())
                .collect(Collectors.joining(" | "));
        LocalDateTime siguiente = ahora.plus(espera(alta.getIntentos()));
        alta.aplazar(motivo, siguiente, ahora);
        enTransaccionPropia.executeWithoutResult(estado -> jugadores.save(alta));
        String nombres = pendientes.stream().map(paso -> paso.getPaso().name()).collect(Collectors.joining(","));
        if (alta.getIntentos() == 1) {
            auditoria.altaFallida(alta.getUsuarioUid(), "pendientes=" + nombres);
        }
        log.warn("ONBOARDING_APLAZADO uid={} intento={} pendientes={} siguiente={}",
                alta.getUsuarioUid(), alta.getIntentos(), nombres, siguiente);
        return Resultado.APLAZADO;
    }

    /** 15 s, 30 s, 1 min, 2 min... hasta 10 min: un servicio caido no se martillea. */
    static Duration espera(int intentos) {
        int exponente = Math.max(0, Math.min(intentos - 1, 10));
        Duration espera = ESPERA_BASE.multipliedBy(1L << exponente);
        return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
    }

    private static String resumen(Map<PasoOnboarding, OnboardingPaso> porPaso) {
        OnboardingPaso creditos = porPaso.get(PasoOnboarding.CREDITOS);
        OnboardingPaso equipo = porPaso.get(PasoOnboarding.EQUIPO);
        return "creditos=" + valorDe(creditos.getDetalle(), "monto")
                + ";fuente=" + valorDe(creditos.getDetalle(), "fuente")
                + ";heroe=" + valorDe(equipo.getDetalle(), "heroe")
                + ";equipados=" + valorDe(equipo.getDetalle(), "equipados");
    }

    /** Lee {@code clave} de un detalle con forma {@code clave=valor;otra=valor}. */
    public static String valorDe(String detalle, String clave) {
        if (detalle == null) {
            return null;
        }
        for (String par : detalle.split(";")) {
            int igual = par.indexOf('=');
            if (igual > 0 && par.substring(0, igual).trim().equals(clave)) {
                String valor = par.substring(igual + 1).trim();
                return valor.isEmpty() ? null : valor;
            }
        }
        return null;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }

    /** Lo que un intento consulta una sola vez: el kit y los elementos del jugador. */
    private final class Intento {
        private final UUID uid;
        private final int version;
        private KitInicial kit;
        private List<Elemento> elementos;

        private Intento(UUID uid, int version) {
            this.uid = uid;
            this.version = version;
        }

        KitInicial kit() {
            if (kit == null) {
                kit = politica.kit();
            }
            return kit;
        }

        List<Elemento> elementos() {
            if (elementos == null) {
                elementos = new ArrayList<>(inventario.elementosDe(uid));
            }
            return elementos;
        }

        Elemento anotar(Elemento creado) {
            elementos().add(creado);
            return creado;
        }
    }
}
