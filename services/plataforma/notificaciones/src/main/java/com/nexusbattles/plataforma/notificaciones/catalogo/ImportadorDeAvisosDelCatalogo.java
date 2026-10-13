package com.nexusbattles.plataforma.notificaciones.catalogo;

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
 * Lleva los cambios del catalogo a la bandeja del jugador — HU-NOT-001 (#532),
 * CA-01 y CA-04: el jugador ve en su bandeja, con descripcion y fecha, lo que
 * cambio en el catalogo mientras no estaba, y lo puede marcar leido (CA-02,
 * ya existente).
 *
 * <p>Corre al dar de alta una sesion (HTTP {@code pending} y alta por STOMP),
 * antes de entregar lo pendiente y <b>fuera de transaccion</b>: en medio hay
 * una llamada HTTP a productos y no debe tener una conexion de la base
 * tomada mientras espera. Cada escritura es corta y va por su lado.
 *
 * <ol>
 *   <li>Lee el cursor del jugador. Si consulto hace menos del intervalo
 *       minimo, no hace nada: no se pregunta a productos por cada pagina.</li>
 *   <li>Sin cursor pide la linea base (no se vuelca el historial del
 *       catalogo, la misma regla que el primer ingreso de inicio-sesion); con
 *       cursor, lo posterior a el.</li>
 *   <li>Cada cambio, en orden, es un aviso {@value #TIPO} con id
 *       {@code catalogo:{alerta}}: idempotente. Si ya estaba (otra sesion a la
 *       vez, una consulta repetida) se ignora; nadie espera a nadie.</li>
 *   <li>Guarda el {@code hasta} del lote como cursor. Si el lote vino cortado
 *       no renueva la hora de consulta: la siguiente sesion trae el resto sin
 *       esperar el intervalo.</li>
 * </ol>
 *
 * <p><b>Degradacion controlada.</b> Si productos no responde, la entrega
 * sigue sin estos avisos, se anota en la bitacora y la hora de consulta, y se
 * intenta en una sesion siguiente. Si la bandeja falla a mitad, el cursor no
 * avanza: lo ya importado se ignora como repetido la proxima vez y nada se
 * pierde. Nunca lanza: el contrato de {@link AvisosPorIncorporar}.
 */
public class ImportadorDeAvisosDelCatalogo implements AvisosPorIncorporar {

    /** Tipo de aviso de la bandeja (notificaciones.yaml 1.4.0). */
    public static final String TIPO = "CAMBIO_CATALOGO";

    /** Prefijo del id del aviso: {@code catalogo:{idDeAlerta}}. */
    static final String PREFIJO_ID = "catalogo:";

    private static final Logger log = LoggerFactory.getLogger(ImportadorDeAvisosDelCatalogo.class);

    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");

    private final CambiosDelCatalogo catalogo;
    private final CursorCatalogoRepository cursores;
    private final ServicioDeNotificaciones servicio;
    private final Clock reloj;
    private final Duration intervaloMinimo;
    private final DateTimeFormatter fechaLegible;

    public ImportadorDeAvisosDelCatalogo(CambiosDelCatalogo catalogo,
                                         CursorCatalogoRepository cursores,
                                         ServicioDeNotificaciones servicio,
                                         Clock reloj,
                                         Duration intervaloMinimo,
                                         ZoneId zona) {
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.cursores = Objects.requireNonNull(cursores, "cursores");
        this.servicio = Objects.requireNonNull(servicio, "servicio");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.intervaloMinimo = Objects.requireNonNull(intervaloMinimo, "intervaloMinimo");
        this.fechaLegible = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", ESPANOL)
                .withZone(Objects.requireNonNull(zona, "zona"));
    }

    @Override
    public void incorporar(String usuarioId) {
        try {
            importar(usuarioId);
        } catch (RuntimeException fallo) {
            log.atWarn()
                    .addKeyValue("evento", "catalogo-importacion-fallida")
                    .addKeyValue("usuarioId", usuarioId)
                    .addKeyValue("causa", fallo.getClass().getSimpleName())
                    .log("No se pudieron incorporar los avisos del catalogo; la entrega sigue sin ellos");
        }
    }

    private void importar(String usuarioId) {
        Instant ahora = reloj.instant();
        Optional<RegistroDeCursorCatalogo> cursor = cursores.findById(usuarioId);
        if (cursor.isPresent() && consultadoHaceMenosDelIntervalo(cursor.get(), ahora)) {
            return;
        }

        LoteDeCambios lote;
        try {
            lote = catalogo.consultar(cursor.map(RegistroDeCursorCatalogo::getHasta).orElse(null));
        } catch (RuntimeException noResponde) {
            log.atWarn()
                    .addKeyValue("evento", "catalogo-no-disponible")
                    .addKeyValue("usuarioId", usuarioId)
                    .addKeyValue("causa", noResponde.getMessage())
                    .log("Productos no entrego los cambios del catalogo; se intentara en una sesion siguiente");
            cursor.ifPresent(anterior -> cursores.marcarConsulta(usuarioId, ahora));
            return;
        }

        for (CambioDelCatalogo cambio : lote.alertas()) {
            if (!incorporarCambio(usuarioId, cambio)) {
                return;
            }
        }

        Instant consultadoEn = lote.completo()
                ? ahora
                : cursor.map(RegistroDeCursorCatalogo::getConsultadoEn).orElse(ahora);
        cursores.guardar(usuarioId, lote.hasta(), consultadoEn);
    }

    private boolean consultadoHaceMenosDelIntervalo(RegistroDeCursorCatalogo cursor, Instant ahora) {
        return Duration.between(cursor.getConsultadoEn(), ahora).compareTo(intervaloMinimo) < 0;
    }

    /**
     * @return false si la bandeja fallo de una forma que puede ser pasajera:
     *     se para sin mover el cursor para no perder este cambio
     */
    private boolean incorporarCambio(String usuarioId, CambioDelCatalogo cambio) {
        Notificacion aviso;
        try {
            aviso = avisoDe(cambio);
        } catch (RuntimeException invalido) {
            // Un cambio que la bandeja nunca podra guardar (sin id, sin
            // fecha, un id que no cabe) no puede frenar a los demas.
            log.atWarn()
                    .addKeyValue("evento", "catalogo-cambio-invalido")
                    .addKeyValue("usuarioId", usuarioId)
                    .addKeyValue("causa", invalido.getMessage())
                    .log("Un cambio del catalogo no cabe en la bandeja y se salta");
            return true;
        }
        try {
            servicio.emitir(usuarioId, aviso);
        } catch (AvisoDuplicado | IllegalArgumentException yaEstaba) {
            // Ya estaba: lo trajo otra sesion o una consulta anterior. Si la
            // otra sesion lo guardo justo antes, la bandeja recien cargada lo
            // rechaza como repetido (IllegalArgumentException del dominio).
            log.debug("El aviso {} ya estaba en la bandeja de {}", aviso.id(), usuarioId);
        } catch (DataIntegrityViolationException carrera) {
            if (!ManejadorErroresNotificaciones.esClaveRepetida(carrera)) {
                log.atWarn()
                        .addKeyValue("evento", "catalogo-cambio-invalido")
                        .addKeyValue("usuarioId", usuarioId)
                        .addKeyValue("aviso", aviso.id())
                        .log("La bandeja rechazo un cambio del catalogo y se salta");
            }
        } catch (RuntimeException fallo) {
            log.atWarn()
                    .addKeyValue("evento", "catalogo-bandeja-no-disponible")
                    .addKeyValue("usuarioId", usuarioId)
                    .addKeyValue("aviso", aviso.id())
                    .addKeyValue("causa", fallo.getClass().getSimpleName())
                    .log("La bandeja no guardo un cambio del catalogo; el cursor no avanza");
            return false;
        }
        return true;
    }

    private Notificacion avisoDe(CambioDelCatalogo cambio) {
        if (cambio.id() == null || cambio.id().isBlank()) {
            throw new IllegalArgumentException("el cambio no trae identificador");
        }
        Objects.requireNonNull(cambio.implementadaEn(), "el cambio no trae fecha de implementacion");
        String descripcion = Objects.requireNonNull(cambio.descripcion(), "el cambio no trae descripcion").strip();
        String cuerpo = conPunto(descripcion)
                + " Fecha de implementación: " + fechaLegible.format(cambio.implementadaEn()) + ".";
        return new Notificacion(PREFIJO_ID + cambio.id(), TIPO, tituloDe(cambio.tipo()), cuerpo,
                cambio.implementadaEn());
    }

    /** Titulo del aviso segun el tipo de cambio de productos.yaml. */
    static String tituloDe(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case "NUEVO_PRODUCTO" -> "Nuevo producto disponible";
            case "PRODUCTO_MODIFICADO" -> "Producto modificado";
            case "PRODUCTO_SUSPENDIDO" -> "Producto suspendido";
            case "PRODUCTO_REACTIVADO" -> "Producto disponible de nuevo";
            case "CAMBIO_BALANCE" -> "Cambio en el balance del juego";
            // Un tipo que productos agregue despues no frena la importacion.
            default -> "Cambio en el catálogo";
        };
    }

    private static String conPunto(String texto) {
        return texto.endsWith(".") || texto.endsWith("!") || texto.endsWith("?") ? texto : texto + ".";
    }
}
