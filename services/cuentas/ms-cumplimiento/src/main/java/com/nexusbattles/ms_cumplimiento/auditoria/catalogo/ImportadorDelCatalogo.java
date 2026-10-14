package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.exception.AuditWriteException;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Lleva a esta bitácora las operaciones de los administradores sobre el
 * catálogo — SA-18, §7.2.5 y §7.3.5 («registro detallado de todas las acciones
 * administrativas», inmutable, solo para Super Administradores, exportable).
 *
 * <p><b>Por qué la bitácora lee del catálogo y no al revés.</b> Los demás
 * servicios le envían sus eventos ({@code POST /eventos}) con su credencial.
 * Productos vive en el host de contenido y el emisor de credenciales en el de
 * plataforma: para escribir aquí necesitaría una credencial compartida entre
 * los dos hosts (un secreto más en GitHub, que el despliegue no puede crear
 * solo). La de esta bitácora nace en el mismo host que su emisor, y productos
 * ya expone lo suyo para servicios (como los cambios del catálogo que lee
 * notificaciones).
 *
 * <p><b>No perder ni repetir.</b> Cada operación entra con su referencia
 * ({@code productos:<id>}): una que ya está no se registra otra vez (índice
 * único de V3). Cada ronda pide desde la última que se importó <em>menos un
 * solape</em>: una operación que productos escribió unos milisegundos después
 * de una lectura, con una marca de tiempo anterior, entra en la ronda
 * siguiente en vez de perderse. Conserva el momento en que ocurrió
 * ({@code ocurridoEn}); la entrada tiene además su propia hora de registro.
 *
 * <p>Si productos o el emisor no responden, la ronda termina sin escribir nada
 * y la siguiente lo vuelve a pedir: nada se inventa ni se descarta.
 */
public class ImportadorDelCatalogo {

    private static final Logger log = LoggerFactory.getLogger(ImportadorDelCatalogo.class);

    /** El prefijo de la referencia de lo que viene de productos. */
    static final String ORIGEN = "productos:";

    /** Lo que no se sabe de dónde vino: nunca se deja vacío ni se inventa una IP. */
    static final String IP_NO_REGISTRADA = "no registrada";

    /** Cómo se nombra cada objeto del catálogo en lo afectado de la bitácora. */
    static final Map<String, String> PREFIJOS = Map.of(
        "PRODUCTO", "producto",
        "BANNER", "banner",
        "RECURSO_GRAFICO", "recurso");

    /** Los tipos del catálogo en los de esta bitácora. */
    static final Map<String, AuditActionType> TIPOS = Map.ofEntries(
        Map.entry("ALTA_PRODUCTO", AuditActionType.CREACION),
        Map.entry("MODIFICACION_PRODUCTO", AuditActionType.ACTUALIZACION),
        Map.entry("CAMBIO_DE_BALANCE", AuditActionType.ACTUALIZACION),
        Map.entry("IMPORTACION_DE_DISENO", AuditActionType.ACTUALIZACION),
        Map.entry("SUSPENSION_PRODUCTO", AuditActionType.SUSPENSION),
        Map.entry("REACTIVACION_PRODUCTO", AuditActionType.REACTIVACION),
        Map.entry("REVERSION_PRODUCTO", AuditActionType.REVERSION),
        Map.entry("ALTA_RECURSO", AuditActionType.CREACION),
        Map.entry("RETIRO_RECURSO", AuditActionType.ELIMINACION_LOGICA),
        Map.entry("ALTA_BANNER", AuditActionType.CREACION),
        Map.entry("MODIFICACION_BANNER", AuditActionType.ACTUALIZACION),
        Map.entry("RETIRO_BANNER", AuditActionType.ELIMINACION_LOGICA));

    private final ClienteDelCatalogo cliente;
    private final AuditLogService bitacora;
    private final AuditLogRepository repositorio;
    private final Duration solape;
    private final int limite;
    private final int lotesPorRonda;

    /** La marca de la última operación importada; se lee de la base la primera vez. */
    private Instant ultima;
    private boolean ultimaLeida;

    public ImportadorDelCatalogo(ClienteDelCatalogo cliente, AuditLogService bitacora, AuditLogRepository repositorio,
                                 Duration solape, int limite, int lotesPorRonda) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.bitacora = Objects.requireNonNull(bitacora, "bitacora");
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.solape = Objects.requireNonNull(solape, "solape");
        this.limite = limite;
        this.lotesPorRonda = lotesPorRonda;
    }

    /** Una ronda programada; nunca lanza: un fallo se registra y espera la siguiente. */
    public void importarProgramado() {
        try {
            int nuevas = importar();
            if (nuevas > 0) {
                log.info("Bitácora: {} operaciones nuevas del catálogo", nuevas);
            }
        } catch (CatalogoNoDisponibleException | AuditWriteException fallo) {
            log.warn("Bitácora: no se importaron las operaciones del catálogo en esta ronda: {}", fallo.getMessage());
        }
    }

    /**
     * Una ronda: pide desde la última importada (menos el solape) hasta
     * {@code lotesPorRonda} lotes, y registra cada operación que falte.
     *
     * @return cuántas entradas nuevas dejó
     */
    public synchronized int importar() {
        if (!ultimaLeida) {
            ultima = repositorio.ultimoMomentoConReferencia(ORIGEN + "%");
            ultimaLeida = true;
        }
        Instant desde = ultima == null ? null : ultima.minus(solape);
        int nuevas = 0;
        for (int lote = 0; lote < lotesPorRonda; lote++) {
            LoteDelCatalogo recibido = cliente.consultar(desde, limite);
            for (OperacionDelCatalogo operacion : recibido.operaciones()) {
                if (registrar(operacion)) {
                    nuevas++;
                }
                if (operacion.ocurridaEn() != null && (ultima == null || operacion.ocurridaEn().isAfter(ultima))) {
                    ultima = operacion.ocurridaEn();
                }
            }
            if (recibido.estaCompleto()) {
                break;
            }
            desde = recibido.hasta();
        }
        return nuevas;
    }

    private boolean registrar(OperacionDelCatalogo operacion) {
        try {
            return bitacora.registrarEvento(aEvento(operacion)).nueva();
        } catch (IllegalArgumentException invalida) {
            // Un dato que la bitácora no admite (no debería pasar: productos
            // siempre envía autor e identificador). Se deja constancia y se sigue
            // con las demás; no se registra a medias.
            log.error("Bitácora: la operación {} del catálogo no se pudo registrar: {}",
                operacion.id(), invalida.getMessage());
            return false;
        }
    }

    /** La operación del catálogo como un evento de esta bitácora. */
    static RegistrarAuditoriaRequest aEvento(OperacionDelCatalogo operacion) {
        String prefijo = PREFIJOS.getOrDefault(String.valueOf(operacion.objeto()), "catalogo");
        AuditActionType tipo = TIPOS.getOrDefault(String.valueOf(operacion.tipo()), AuditActionType.OTRO);
        return new RegistrarAuditoriaRequest(
            tipo.name(),
            operacion.autor(),
            prefijo + ":" + operacion.objetoId(),
            operacion.valorAnterior(),
            operacion.valorNuevo(),
            motivoDe(operacion),
            texto(operacion.ipOrigen()) == null ? IP_NO_REGISTRADA : operacion.ipOrigen().strip(),
            operacion.ocurridaEn(),
            operacion.id() == null ? null : ORIGEN + operacion.id(),
            operacion.nombre());
    }

    /** El motivo que escribió quien actuó; si no escribió ninguno, qué hizo. */
    private static String motivoDe(OperacionDelCatalogo operacion) {
        if (texto(operacion.motivo()) != null) {
            return operacion.motivo().strip();
        }
        if (texto(operacion.descripcion()) != null) {
            return operacion.descripcion().strip();
        }
        return "Operación del catálogo";
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
