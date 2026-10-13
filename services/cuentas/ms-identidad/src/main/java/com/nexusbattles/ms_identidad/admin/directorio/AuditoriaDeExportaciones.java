package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HU-USR-009 (#562) — cada exportacion del directorio queda en la auditoria
 * de ms-cumplimiento. Decision del PO del 5-oct (comentario en #562): son datos
 * personales (§7.3.6), asi que se registra quien exporto, cuando, con que
 * filtros y cuantas filas. <b>Nunca el contenido</b>: ni el CSV, ni los
 * apodos, correos o nombres de las cuentas exportadas.
 *
 * <h2>Que se manda</h2>
 *
 * Por la operacion que ya existe, {@code POST /api/v1/admin/auditoria/eventos}
 * (ms-cumplimiento-auditoria.yaml), con el mismo cliente y la misma forma que
 * la consulta de la ficha (#875):
 * <ul>
 *   <li>{@code tipoAccion} {@value #TIPO}: el enumerado de ms-cumplimiento es
 *       cerrado y no tiene uno de exportacion;</li>
 *   <li>{@code administradorId}: quien exporto, el apodo que deja el
 *       interceptor, como el resto de acciones de este panel;</li>
 *   <li>{@code afectado} {@value #AFECTADO}: la accion recae sobre el
 *       directorio, no sobre una cuenta;</li>
 *   <li>{@code valorNuevo}: los metadatos —filas, filtros, {@code uid} de quien
 *       exporto y {@code traceId}—; {@code valorAnterior} nulo;</li>
 *   <li>{@code motivo}: {@value #MOTIVO};</li>
 *   <li>la IP de origen. Cuando, lo fija ms-cumplimiento al guardar.</li>
 * </ul>
 *
 * <h2>Si ms-cumplimiento no responde</h2>
 *
 * La exportacion se entrega igual (decision del PO) y el hecho queda en la
 * bitacora del servicio con todos los metadatos y el {@code traceId}
 * ({@code EXPORTACION_DIRECTORIO_SIN_AUDITAR}): una perdida de auditoria se
 * ve, nunca pasa en silencio. Mismo criterio que la ficha administrativa.
 */
@Component
public class AuditoriaDeExportaciones {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaDeExportaciones.class);

    static final String TIPO = "OTRO";
    static final String AFECTADO = "DIRECTORIO_DE_CUENTAS";
    static final String MOTIVO = "EXPORTACION_JUGADORES: exportacion del directorio de cuentas (datos personales)";
    /** ms-cumplimiento exige administrador e IP: un nulo seria un 503 y la exportacion quedaria sin registrar. */
    static final String DESCONOCIDO = "DESCONOCIDO";
    static final String DESCONOCIDA = "DESCONOCIDA";
    static final String SIN_TRAZA = "sin-traza";
    /** Lo que se guarda del texto buscado: es un filtro, no hace falta entero. */
    static final int LARGO_MAXIMO_DEL_TEXTO = 100;

    private final AuditoriaClient auditoria;

    public AuditoriaDeExportaciones(AuditoriaClient auditoria) {
        this.auditoria = auditoria;
    }

    /**
     * Registra una exportacion ya hecha. Nunca lanza: si la auditoria falla,
     * lo deja en la bitacora y devuelve {@code false}.
     *
     * @param filtro           los filtros con que se exporto
     * @param filas            cuantas cuentas lleva el archivo
     * @param administrador    quien exporto (el apodo del token)
     * @param uidAdministrador su {@code uid}, o null si el token no lo trae
     * @param ipOrigen         desde donde
     * @return {@code true} si ms-cumplimiento la registro
     */
    public boolean registrar(FiltroDelDirectorio filtro, int filas, String administrador,
                             String uidAdministrador, String ipOrigen) {
        String quien = enBlanco(administrador) ? DESCONOCIDO : administrador;
        String desde = enBlanco(ipOrigen) ? DESCONOCIDA : ipOrigen;
        String metadatos = metadatos(filtro, filas, uidAdministrador, Traza.actual().orElse(SIN_TRAZA));
        try {
            auditoria.registrar(TIPO, quien, AFECTADO, null, metadatos, MOTIVO, desde);
            return true;
        } catch (RuntimeException bitacoraNoDisponible) {
            log.warn("EXPORTACION_DIRECTORIO_SIN_AUDITAR administrador={} ip={} {} motivo={}",
                    quien, desde, metadatos, bitacoraNoDisponible.getMessage());
            return false;
        }
    }

    /**
     * {@code filas=2; rol=JUGADOR; buscar=«ana»; administradorUid=…; traceId=…}.
     * Solo los filtros que se usaron; sin ninguno, {@code sin filtros}.
     */
    static String metadatos(FiltroDelDirectorio filtro, int filas, String uidAdministrador, String traceId) {
        List<String> partes = new ArrayList<>();
        partes.add("filas=" + filas);
        partes.addAll(filtros(filtro));
        partes.add("administradorUid=" + (enBlanco(uidAdministrador) ? DESCONOCIDO : uidAdministrador));
        partes.add("traceId=" + traceId);
        return String.join("; ", partes);
    }

    private static List<String> filtros(FiltroDelDirectorio filtro) {
        List<String> usados = new ArrayList<>();
        if (!filtro.texto().isEmpty()) {
            usados.add("buscar=«" + recortado(filtro.texto()) + "»");
        }
        if (filtro.rol() != null) {
            usados.add("rol=" + filtro.rol());
        }
        if (filtro.estado() != null) {
            usados.add("estado=" + filtro.estado());
        }
        if (filtro.registradoDesde() != null) {
            usados.add("registradoDesde=" + filtro.registradoDesde());
        }
        if (filtro.registradoHasta() != null) {
            usados.add("registradoHasta=" + filtro.registradoHasta());
        }
        if (filtro.ocultarPruebas()) {
            usados.add("ocultarPruebas=true");
        }
        if (usados.isEmpty()) {
            usados.add("sin filtros");
        }
        return usados;
    }

    /** Sin saltos de linea (la linea de la bitacora no se parte) y como mucho {@value #LARGO_MAXIMO_DEL_TEXTO}. */
    private static String recortado(String texto) {
        String enUnaLinea = texto.replaceAll("[\\p{Cntrl}]", " ");
        return enUnaLinea.length() <= LARGO_MAXIMO_DEL_TEXTO
                ? enUnaLinea
                : enUnaLinea.substring(0, LARGO_MAXIMO_DEL_TEXTO) + "…";
    }

    private static boolean enBlanco(String valor) {
        return valor == null || valor.isBlank();
    }
}
