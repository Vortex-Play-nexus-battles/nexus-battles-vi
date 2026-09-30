package com.nexusbattles.ms_subastas.notificaciones;

import com.nexusbattles.ms_subastas.panel.model.PendienteDeRecoger;
import com.nexusbattles.ms_subastas.panel.repository.SeguimientoRepository;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.reglas.PoliticaAlVencer;
import com.nexusbattles.ms_subastas.reglas.ReglasDelDocumento;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nexusbattles.ms_subastas.notificaciones.Textos.anonimizar;
import static com.nexusbattles.ms_subastas.notificaciones.Textos.creditos;
import static com.nexusbattles.ms_subastas.notificaciones.Textos.producto;

/**
 * Quien se entera de que, en cada hecho de una subasta (7.7.8 del documento
 * del curso, B8).
 *
 * <p>Es la unica pieza que decide destinatarios y textos: los servicios de
 * negocio dicen «se registro esta puja» o «se cancelo esta subasta» y esto
 * encola los avisos que correspondan en {@link NotificacionOutbox}, dentro de
 * su misma transaccion. Cada destinatario recibe un solo aviso por hecho: el
 * vendedor que ademas sigue su propia subasta no recibe dos. La unica
 * excepcion es la venta: el vendedor recibe el resultado y, aparte, la
 * confirmacion de los creditos recibidos, porque 7.7.8 los enumera por
 * separado ({@link TipoNotificacion#CREDITOS_RECIBIDOS}).
 *
 * <p>Quien sigue una subasta (7.7.9, «Notificaciones cuando hay cambios en
 * estas subastas») recibe {@link TipoNotificacion#CAMBIO_EN_SUBASTA_SEGUIDA}
 * salvo que el hecho ya le llegue por otro camino (es el vendedor, el postor o
 * el superado).
 */
@Service
public class AvisosDeSubasta {

    private final NotificacionOutbox outbox;
    private final SeguimientoRepository seguimientos;
    private final PujaRepository pujas;
    private final PujaAutomaticaRepository automaticas;

    public AvisosDeSubasta(NotificacionOutbox outbox, SeguimientoRepository seguimientos, PujaRepository pujas,
                           PujaAutomaticaRepository automaticas) {
        this.outbox = Objects.requireNonNull(outbox);
        this.seguimientos = Objects.requireNonNull(seguimientos);
        this.pujas = Objects.requireNonNull(pujas);
        this.automaticas = Objects.requireNonNull(automaticas);
    }

    /** 7.7.8 vendedor: «Confirmacion de publicacion exitosa de la subasta». */
    public void publicada(Subasta subasta) {
        outbox.encolar(TipoNotificacion.SUBASTA_PUBLICADA, subasta.getVendedorId(), subasta.getId(), "publicacion",
                titulo(TipoNotificacion.SUBASTA_PUBLICADA, subasta),
                "Tu subasta de " + producto(subasta.getNombreProducto()) + " ya está en el listado. Precio mínimo: "
                        + creditos(subasta.getPrecioInicial()) + " créditos. Comisión cobrada: "
                        + creditos(subasta.getComisionCobrada()) + " créditos.");
    }

    /**
     * Una puja quedo registrada (manual o automatica).
     *
     * @param superada la que era la oferta vigente hasta ahora, o null
     */
    public void pujaRegistrada(Subasta subasta, Puja nueva, Puja superada) {
        String nombre = producto(subasta.getNombreProducto());
        String monto = creditos(nueva.getMonto());
        Set<UUID> avisados = new LinkedHashSet<>();

        outbox.encolar(TipoNotificacion.PUJA_REGISTRADA, nueva.getJugadorId(), subasta.getId(), "puja:" + nueva.getId(),
                titulo(TipoNotificacion.PUJA_REGISTRADA, subasta),
                (nueva.getTipo() == TipoPuja.AUTOMATICA ? "Tu puja automática ofreció " : "Registramos tu puja de ")
                        + monto + " créditos por " + nombre + ". Esos créditos quedan retenidos mientras seas el mejor postor.");
        avisados.add(nueva.getJugadorId());

        outbox.encolar(TipoNotificacion.NUEVA_PUJA, subasta.getVendedorId(), subasta.getId(), "puja:" + nueva.getId(),
                titulo(TipoNotificacion.NUEVA_PUJA, subasta),
                anonimizar(nueva.getApodoPostor()) + " pujó " + monto + " créditos por tu " + nombre + ". Van "
                        + subasta.getCantidadPujas() + " puja(s).");
        avisados.add(subasta.getVendedorId());

        if (superada != null && !superada.getJugadorId().equals(nueva.getJugadorId())) {
            outbox.encolar(TipoNotificacion.PUJA_SUPERADA, superada.getJugadorId(), subasta.getId(),
                    "superada:" + superada.getId(), titulo(TipoNotificacion.PUJA_SUPERADA, subasta),
                    "Otra puja superó tu oferta de " + creditos(superada.getMonto()) + " créditos por " + nombre
                            + ": la oferta vigente es " + monto + ". Tus créditos retenidos ya se liberaron. "
                            + "Puedes volver a pujar desde " + creditos(subasta.pujaMinimaSiguiente()) + ".");
            avisados.add(superada.getJugadorId());
        }

        avisarSeguidores(subasta, "puja:" + nueva.getId(), avisados,
                "Nueva puja de " + monto + " créditos por " + nombre + ", que sigues.");
    }

    /** 7.7.6 y 7.7.8: la compra inmediata, para el comprador, el vendedor, los postores y quien la seguia. */
    public void compraInmediata(Subasta subasta, Puja compra, Collection<UUID> postores) {
        String nombre = producto(subasta.getNombreProducto());
        String precio = creditos(compra.getMonto());
        Set<UUID> avisados = new LinkedHashSet<>();

        outbox.encolar(TipoNotificacion.COMPRA_INMEDIATA_EXITOSA, compra.getJugadorId(), subasta.getId(), "compra",
                titulo(TipoNotificacion.COMPRA_INMEDIATA_EXITOSA, subasta),
                "Compraste " + nombre + " por " + precio + " créditos. El producto ya está en tu inventario.");
        avisados.add(compra.getJugadorId());

        outbox.encolar(TipoNotificacion.COMPRA_INMEDIATA_EJECUTADA, subasta.getVendedorId(), subasta.getId(), "compra",
                titulo(TipoNotificacion.COMPRA_INMEDIATA_EJECUTADA, subasta),
                anonimizar(compra.getApodoPostor()) + " compró tu " + nombre + " de forma inmediata por " + precio
                        + " créditos. Los créditos ya están en tu saldo.");
        creditosRecibidos(subasta, "compra", compra.getMonto());
        avisados.add(subasta.getVendedorId());

        // Los postores: el cierre anticipado de HU-SUB-004 criterio 2 y 7.7.6.
        for (UUID postor : postores) {
            if (avisados.add(postor)) {
                outbox.encolar(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, postor, subasta.getId(),
                        "compra-inmediata", titulo(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, subasta),
                        "La subasta de " + nombre + " se cerró porque otro jugador la compró de forma inmediata. "
                                + "Tus créditos retenidos ya se liberaron.");
            }
        }
        // Quien tenia una automatica configurada sin haber llegado a pujar.
        for (UUID jugador : automaticas.jugadoresConAutomatica(subasta.getId())) {
            if (avisados.add(jugador)) {
                outbox.encolar(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, jugador, subasta.getId(),
                        "compra-inmediata", titulo(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, subasta),
                        "La subasta de " + nombre + " se cerró por una compra inmediata; tu puja automática quedó "
                                + "desactivada.");
            }
        }

        avisarSeguidores(subasta, "compra", avisados, nombre + " se vendió por compra inmediata.");
    }

    /** 7.7.7 «Subasta con ganador»: ganador, vendedor, demas participantes y quien la seguia. */
    public void cerradaConGanador(Subasta subasta, Puja ganadora, PendienteDeRecoger pendiente) {
        String nombre = producto(subasta.getNombreProducto());
        String monto = creditos(ganadora.getMonto());
        Set<UUID> avisados = new LinkedHashSet<>();

        outbox.encolar(TipoNotificacion.SUBASTA_GANADA, ganadora.getJugadorId(), subasta.getId(), "cierre",
                titulo(TipoNotificacion.SUBASTA_GANADA, subasta),
                "Ganaste " + nombre + " por " + monto + " créditos. Ya es tuyo y está pagado: recógelo en "
                        + "«Pendientes de recoger» antes de " + ReglasDelDocumento.PLAZO_PARA_RECOGER.toDays()
                        + " días.");
        avisados.add(ganadora.getJugadorId());

        outbox.encolar(TipoNotificacion.SUBASTA_VENDIDA, subasta.getVendedorId(), subasta.getId(), "cierre",
                titulo(TipoNotificacion.SUBASTA_VENDIDA, subasta),
                "Tu subasta de " + nombre + " terminó con " + subasta.getCantidadPujas() + " puja(s) y se adjudicó a "
                        + anonimizar(ganadora.getApodoPostor()) + " por " + monto
                        + " créditos. Los créditos ya están en tu saldo.");
        creditosRecibidos(subasta, "cierre", ganadora.getMonto());
        avisados.add(subasta.getVendedorId());

        for (UUID participante : participantes(subasta.getId())) {
            if (avisados.add(participante)) {
                outbox.encolar(TipoNotificacion.SUBASTA_FINALIZADA, participante, subasta.getId(), "cierre",
                        titulo(TipoNotificacion.SUBASTA_FINALIZADA, subasta),
                        nombre + " se adjudicó a otro jugador por " + monto
                                + " créditos. Tus créditos retenidos ya se liberaron.");
            }
        }

        avisarSeguidores(subasta, "cierre", avisados, nombre + " terminó: se adjudicó por " + monto + " créditos.");
    }

    /** 7.7.7 «Subasta sin ofertas»: el vendedor, y quien la seguia o tenia una automatica sin emitir. */
    public void cerradaSinOfertas(Subasta subasta) {
        String nombre = producto(subasta.getNombreProducto());
        Set<UUID> avisados = new LinkedHashSet<>();

        outbox.encolar(TipoNotificacion.SUBASTA_SIN_OFERTAS, subasta.getVendedorId(), subasta.getId(), "cierre",
                titulo(TipoNotificacion.SUBASTA_SIN_OFERTAS, subasta),
                "Tu subasta de " + nombre + " terminó sin ofertas. El producto volvió a tu inventario y la comisión "
                        + "no se reembolsa. Puedes volver a publicarlo con otro precio.");
        avisados.add(subasta.getVendedorId());

        for (UUID participante : participantes(subasta.getId())) {
            if (avisados.add(participante)) {
                outbox.encolar(TipoNotificacion.SUBASTA_FINALIZADA, participante, subasta.getId(), "cierre",
                        titulo(TipoNotificacion.SUBASTA_FINALIZADA, subasta),
                        nombre + " terminó sin ofertas; tu puja automática quedó desactivada.");
            }
        }
        avisarSeguidores(subasta, "cierre", avisados, nombre + " terminó sin ofertas.");
    }

    /** 7.7.10: la cancelacion, para el vendedor y para quien la seguia o tenia una automatica. */
    public void cancelada(Subasta subasta) {
        String nombre = producto(subasta.getNombreProducto());
        Set<UUID> avisados = new LinkedHashSet<>();

        outbox.encolar(TipoNotificacion.SUBASTA_CANCELADA, subasta.getVendedorId(), subasta.getId(), "cancelacion",
                titulo(TipoNotificacion.SUBASTA_CANCELADA, subasta),
                "Cancelaste tu subasta de " + nombre + ". Penalización cobrada: "
                        + creditos(subasta.getPenalizacionCobrada()) + " créditos ("
                        + ReglasDelDocumento.PENALIZACION_CANCELACION_PORCENTAJE
                        + " % de la comisión). El producto volvió a tu inventario.");
        avisados.add(subasta.getVendedorId());

        for (UUID participante : participantes(subasta.getId())) {
            if (avisados.add(participante)) {
                outbox.encolar(TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA, participante, subasta.getId(),
                        "cancelacion", titulo(TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA, subasta),
                        "El vendedor canceló la subasta de " + nombre + "; tu puja automática quedó desactivada.");
            }
        }
        avisarSeguidores(subasta, "cancelacion", avisados, "El vendedor canceló la subasta de " + nombre + ".");
    }

    /**
     * 7.7.8: «Aviso 1 hora antes de finalizar subastas en las que se participa»
     * y «Recordatorio de subastas guardadas en lista de seguimiento».
     *
     * <p>Va «a ambos», vendedor y comprador (RF-NOT-003, ficha oficial): quien
     * pujo o tiene una automatica, quien la sigue y el vendedor, una sola vez
     * cada uno. El vendedor recibe su propio texto: la proxima puja valida no
     * le sirve, porque no puede pujar en su propia subasta.
     */
    public void recordatorio(Subasta subasta) {
        String nombre = producto(subasta.getNombreProducto());
        Set<UUID> destinatarios = new LinkedHashSet<>(participantes(subasta.getId()));
        destinatarios.addAll(seguimientos.seguidoresDe(subasta.getId()));
        // El vendedor recibe el suyo abajo, aunque tambien siga su propia subasta.
        destinatarios.remove(subasta.getVendedorId());
        String cuerpo = nombre + " cierra en menos de 1 hora. Oferta vigente: " + creditos(subasta.getOfertaVigente())
                + " créditos; la próxima puja válida es de " + creditos(subasta.pujaMinimaSiguiente()) + ".";
        for (UUID destinatario : destinatarios) {
            outbox.encolar(TipoNotificacion.RECORDATORIO_CIERRE, destinatario, subasta.getId(), "recordatorio",
                    titulo(TipoNotificacion.RECORDATORIO_CIERRE, subasta), cuerpo);
        }

        String alVendedor = subasta.tieneOfertas()
                ? "Tu subasta de " + nombre + " cierra en menos de 1 hora. Oferta vigente: "
                        + creditos(subasta.getOfertaVigente()) + " créditos. Van " + subasta.getCantidadPujas()
                        + " puja(s)."
                : "Tu subasta de " + nombre + " cierra en menos de 1 hora y todavía no tiene ofertas.";
        outbox.encolar(TipoNotificacion.RECORDATORIO_CIERRE, subasta.getVendedorId(), subasta.getId(), "recordatorio",
                titulo(TipoNotificacion.RECORDATORIO_CIERRE, subasta), alVendedor);
    }

    /** 7.7.8: «Confirmacion de producto agregado al inventario», al recogerlo. */
    public void recogido(Subasta subasta, PendienteDeRecoger pendiente) {
        outbox.encolar(TipoNotificacion.PRODUCTO_RECOGIDO, pendiente.getGanadorId(), subasta.getId(), "recogida",
                titulo(TipoNotificacion.PRODUCTO_RECOGIDO, subasta),
                producto(subasta.getNombreProducto()) + " ya está disponible en tu inventario.");
    }

    /** 7.7.9: vencieron los 7 dias; se aplico la politica del PO. */
    public void pendienteVencido(Subasta subasta, PendienteDeRecoger pendiente, PoliticaAlVencer politica) {
        String nombre = producto(subasta.getNombreProducto());
        String dias = String.valueOf(ReglasDelDocumento.PLAZO_PARA_RECOGER.toDays());
        if (politica == PoliticaAlVencer.DEVOLVER_AL_VENDEDOR) {
            outbox.encolar(TipoNotificacion.PENDIENTE_VENCIDO, pendiente.getGanadorId(), subasta.getId(), "vencido",
                    titulo(TipoNotificacion.PENDIENTE_VENCIDO, subasta),
                    "Pasaron " + dias + " días sin que recogieras " + nombre + " y volvió al vendedor.");
            outbox.encolar(TipoNotificacion.PRODUCTO_DEVUELTO, subasta.getVendedorId(), subasta.getId(), "vencido",
                    titulo(TipoNotificacion.PRODUCTO_DEVUELTO, subasta),
                    "El ganador no recogió " + nombre + " en " + dias + " días y volvió a tu inventario.");
            return;
        }
        outbox.encolar(TipoNotificacion.PENDIENTE_VENCIDO, pendiente.getGanadorId(), subasta.getId(), "vencido",
                titulo(TipoNotificacion.PENDIENTE_VENCIDO, subasta),
                "Pasaron " + dias + " días sin que recogieras " + nombre
                        + ": lo pasamos a tu inventario, ya disponible.");
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * 7.7.8 vendedor: «Confirmacion de transferencia de creditos recibidos».
     * Solo se llama despues de que ms-finanzas los movio: MotorPujasService
     * consume la reserva del comprador a favor del vendedor antes de que se
     * encole ningun aviso, y si ese consumo falla no se encola nada.
     */
    private void creditosRecibidos(Subasta subasta, String discriminante, BigDecimal monto) {
        outbox.encolar(TipoNotificacion.CREDITOS_RECIBIDOS, subasta.getVendedorId(), subasta.getId(), discriminante,
                titulo(TipoNotificacion.CREDITOS_RECIBIDOS, subasta),
                "Recibiste " + creditos(monto) + " créditos por la venta de " + producto(subasta.getNombreProducto())
                        + ". Ya están en tu saldo.");
    }

    /** Quien pujo o tiene una puja automatica configurada en la subasta. */
    private Set<UUID> participantes(UUID subastaId) {
        Set<UUID> participantes = new LinkedHashSet<>(pujas.findDistinctJugadorIdBySubastaId(subastaId));
        participantes.addAll(automaticas.jugadoresConAutomatica(subastaId));
        return participantes;
    }

    private void avisarSeguidores(Subasta subasta, String discriminante, Set<UUID> yaAvisados, String cuerpo) {
        for (UUID seguidor : seguimientos.seguidoresDe(subasta.getId())) {
            if (!yaAvisados.contains(seguidor)) {
                outbox.encolar(TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA, seguidor, subasta.getId(), discriminante,
                        titulo(TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA, subasta), cuerpo);
            }
        }
    }

    /** El titulo del tipo con el producto: «Te superaron en una subasta · Espada del Alba». */
    private static String titulo(TipoNotificacion tipo, Subasta subasta) {
        String nombre = subasta.getNombreProducto();
        return nombre == null || nombre.isBlank() ? tipo.getTitulo() : tipo.getTitulo() + " · " + nombre;
    }
}
