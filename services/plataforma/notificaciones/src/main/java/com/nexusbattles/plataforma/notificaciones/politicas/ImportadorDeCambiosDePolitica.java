package com.nexusbattles.plataforma.notificaciones.politicas;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import com.nexusbattles.plataforma.notificaciones.Notificacion;
import com.nexusbattles.plataforma.notificaciones.bandeja.AvisoDuplicado;
import com.nexusbattles.plataforma.notificaciones.bandeja.AvisosPorIncorporar;
import com.nexusbattles.plataforma.notificaciones.bandeja.ManejadorErroresNotificaciones;
import com.nexusbattles.plataforma.notificaciones.bandeja.ServicioDeNotificaciones;

/**
 * Lleva a la bandeja del jugador los cambios en las politicas de uso —
 * §7.3.7 «el sistema debe notificar a los usuarios sobre cambios en
 * politicas de uso que afecten al usuario» (SA-11, RF-NOT-005).
 *
 * <p>El mismo camino que los cambios del catalogo (HU-NOT-001): corre al dar
 * de alta una sesion, antes de entregar lo pendiente, fuera de transaccion
 * (en medio hay una llamada HTTP) y con un cursor por jugador.
 *
 * <ol>
 *   <li>Si consulto hace menos del intervalo minimo, no hace nada.</li>
 *   <li>Sin cursor lee lo publicado en la ventana configurada (30 dias): el
 *       jugador que vuelve despues de un cambio se entera aunque sea su
 *       primer ingreso desde que existe este aviso. Con cursor, lo posterior
 *       a el, con un minuto de solape por si una publicacion confirmo su
 *       transaccion despues de otra mas reciente; lo repetido se ignora.</li>
 *   <li>Cada cambio es un aviso {@value #TIPO} con id
 *       {@code politica:{idDelCambio}}: idempotente.</li>
 *   <li>Guarda el {@code hasta} del lote. Si el lote vino cortado no renueva
 *       la hora de consulta: la siguiente sesion trae el resto.</li>
 * </ol>
 *
 * <p><b>Degradacion controlada.</b> Si admin-parametros no responde, la
 * entrega sigue sin estos avisos y se intenta en una sesion siguiente; si la
 * bandeja falla a mitad, el cursor no avanza. Nunca lanza.
 */
public class ImportadorDeCambiosDePolitica implements AvisosPorIncorporar {

    /** Tipo de aviso de la bandeja (notificaciones.yaml 1.5.0). */
    public static final String TIPO = "CAMBIO_POLITICA";

    /** Prefijo del id del aviso: {@code politica:{idDelCambio}}. */
    static final String PREFIJO_ID = "politica:";

    /** Lo que se vuelve a leer antes del cursor; lo ya incorporado se ignora como repetido. */
    static final Duration SOLAPE = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(ImportadorDeCambiosDePolitica.class);

    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");

    private final CambiosDePolitica politicas;
    private final CursorPoliticasRepository cursores;
    private final ServicioDeNotificaciones servicio;
    private final Clock reloj;
    private final Duration intervaloMinimo;
    private final Duration ventana;
    private final DateTimeFormatter fechaLegible;

    public ImportadorDeCambiosDePolitica(CambiosDePolitica politicas,
                                         CursorPoliticasRepository cursores,
                                         ServicioDeNotificaciones servicio,
                                         Clock reloj,
                                         Duration intervaloMinimo,
                                         Duration ventana,
                                         ZoneId zona) {
        this.politicas = Objects.requireNonNull(politicas, "politicas");
        this.cursores = Objects.requireNonNull(cursores, "cursores");
        this.servicio = Objects.requireNonNull(servicio, "servicio");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.intervaloMinimo = Objects.requireNonNull(intervaloMinimo, "intervaloMinimo");
        this.ventana = Objects.requireNonNull(ventana, "ventana");
        this.fechaLegible = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", ESPANOL)
                .withZone(Objects.requireNonNull(zona, "zona"));
    }

    @Override
    public void incorporar(String usuarioId) {
        try {
            importar(usuarioId);
        } catch (RuntimeException fallo) {
            log.atWarn()
                    .addKeyValue("evento", "politicas-importacion-fallida")
                    .addKeyValue("usuarioId", usuarioId)
                    .addKeyValue("causa", fallo.getClass().getSimpleName())
                    .log("No se pudieron incorporar los cambios en las politicas; la entrega sigue sin ellos");
        }
    }

    private void importar(String usuarioId) {
        Instant ahora = reloj.instant();
        Optional<RegistroDeCursorPoliticas> cursor = cursores.findById(usuarioId);
        if (cursor.isPresent() && consultadoHaceMenosDelIntervalo(cursor.get(), ahora)) {
            return;
        }

        LoteDeCambiosDePolitica lote;
        try {
            lote = leer(cursor, ahora);
        } catch (RuntimeException noResponde) {
            log.atWarn()
                    .addKeyValue("evento", "politicas-no-disponibles")
                    .addKeyValue("usuarioId", usuarioId)
                    .addKeyValue("causa", noResponde.getMessage())
                    .log("Admin-parametros no entrego los cambios en las politicas; se intentara en una sesion siguiente");
            cursor.ifPresent(anterior -> cursores.marcarConsulta(usuarioId, ahora));
            return;
        }

        for (CambioDePoliticaDeUso cambio : lote.cambios()) {
            if (!incorporarCambio(usuarioId, cambio)) {
                return;
            }
        }

        Instant consultadoEn = lote.completo()
                ? ahora
                : cursor.map(RegistroDeCursorPoliticas::getConsultadoEn).orElse(ahora);
        cursores.guardar(usuarioId, lote.hasta(), consultadoEn);
    }

    /**
     * Con cursor, desde un minuto antes de el. Si ese solape solo ya llena un
     * lote sin avanzar (mas de un lote de publicaciones en un minuto), se lee
     * otra vez sin solape para no quedarse en el mismo punto.
     */
    private LoteDeCambiosDePolitica leer(Optional<RegistroDeCursorPoliticas> cursor, Instant ahora) {
        if (cursor.isEmpty()) {
            return politicas.consultar(ahora.minus(ventana));
        }
        Instant hasta = cursor.get().getHasta();
        LoteDeCambiosDePolitica lote = politicas.consultar(hasta.minus(SOLAPE));
        if (!lote.completo() && !lote.hasta().isAfter(hasta)) {
            return politicas.consultar(hasta);
        }
        return lote;
    }

    private boolean consultadoHaceMenosDelIntervalo(RegistroDeCursorPoliticas cursor, Instant ahora) {
        return Duration.between(cursor.getConsultadoEn(), ahora).compareTo(intervaloMinimo) < 0;
    }

    /** @return false si la bandeja fallo de una forma que puede ser pasajera: el cursor no avanza */
    private boolean incorporarCambio(String usuarioId, CambioDePoliticaDeUso cambio) {
        Notificacion aviso;
        try {
            aviso = avisoDe(cambio);
        } catch (RuntimeException invalido) {
            log.atWarn()
                    .addKeyValue("evento", "politicas-cambio-invalido")
                    .addKeyValue("usuarioId", usuarioId)
                    .addKeyValue("causa", invalido.getMessage())
                    .log("Un cambio en las politicas no cabe en la bandeja y se salta");
            return true;
        }
        try {
            servicio.emitir(usuarioId, aviso);
        } catch (AvisoDuplicado | IllegalArgumentException yaEstaba) {
            log.debug("El aviso {} ya estaba en la bandeja de {}", aviso.id(), usuarioId);
        } catch (DataIntegrityViolationException carrera) {
            if (!ManejadorErroresNotificaciones.esClaveRepetida(carrera)) {
                log.atWarn()
                        .addKeyValue("evento", "politicas-cambio-invalido")
                        .addKeyValue("usuarioId", usuarioId)
                        .addKeyValue("aviso", aviso.id())
                        .log("La bandeja rechazo un cambio en las politicas y se salta");
            }
        } catch (RuntimeException fallo) {
            log.atWarn()
                    .addKeyValue("evento", "politicas-bandeja-no-disponible")
                    .addKeyValue("usuarioId", usuarioId)
                    .addKeyValue("aviso", aviso.id())
                    .addKeyValue("causa", fallo.getClass().getSimpleName())
                    .log("La bandeja no guardo un cambio en las politicas; el cursor no avanza");
            return false;
        }
        return true;
    }

    /**
     * «Actualizamos la política de privacidad» · «Datos del chat. Los
     * mensajes del chat se guardan noventa días. Rige desde el 20 de octubre
     * de 2026, 00:00.» La fecha del aviso es la de la publicacion.
     */
    Notificacion avisoDe(CambioDePoliticaDeUso cambio) {
        if (cambio.id() == null || cambio.id().isBlank()) {
            throw new IllegalArgumentException("el cambio no trae identificador");
        }
        Instant publicado = Objects.requireNonNull(cambio.publicadoEn(), "el cambio no trae fecha de publicacion");
        Instant vigencia = cambio.vigenteDesde() == null ? publicado : cambio.vigenteDesde();
        String titulo = Objects.requireNonNull(cambio.titulo(), "el cambio no trae titulo").strip();
        String descripcion = Objects.requireNonNull(cambio.descripcion(), "el cambio no trae descripcion").strip();
        String cuerpo = conPunto(titulo) + " " + conPunto(descripcion)
                + " Rige desde el " + fechaLegible.format(vigencia) + ".";
        return new Notificacion(PREFIJO_ID + cambio.id(), TIPO, tituloDe(cambio.politica(), cambio.nombreDeLaPolitica()),
                cuerpo, publicado);
    }

    /** Titulo del aviso segun la politica de admin-parametros.yaml. */
    static String tituloDe(String politica, String nombre) {
        return switch (politica == null ? "" : politica) {
            case "TERMINOS_Y_CONDICIONES" -> "Actualizamos los términos y condiciones del servicio";
            case "POLITICA_DE_PRIVACIDAD" -> "Actualizamos la política de privacidad";
            // Una politica que admin-parametros agregue despues no frena la importacion.
            default -> nombre == null || nombre.isBlank()
                    ? "Cambio en las políticas de uso"
                    : "Cambio en " + nombre.strip();
        };
    }

    private static String conPunto(String texto) {
        return texto.endsWith(".") || texto.endsWith("!") || texto.endsWith("?") ? texto : texto + ".";
    }
}
