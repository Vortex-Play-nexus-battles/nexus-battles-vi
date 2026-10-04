package com.nexusbattles.plataforma.torneos.torneo;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Los avisos que torneos le manda a cada participante en los hitos que piden
 * las historias: inscripcion confirmada con su comprobante (HU-TOR-002 CA-01),
 * inicio, cancelacion con su devolucion (HU-TOR-001 CA-04) y premio entregado
 * (HU-TOR-007 CA-04).
 *
 * <p>Cada aviso es una {@link Operacion} que se guarda en la misma transaccion
 * que el hito: si el hito se confirma, el aviso sale tarde o temprano; si no,
 * no sale. Va siempre a la bandeja de notificaciones y, si hay correo
 * configurado, tambien por correo.
 */
@Component
public class Hitos {

    static final String INSCRIPCION = "inscripcion";
    static final String INICIO = "inicio";
    static final String CANCELACION = "cancelacion";
    static final String PREMIO = "premio";

    private final AvisosAlJugador canales;

    public Hitos(AvisosAlJugador canales) {
        this.canales = canales;
    }

    public List<Operacion> inscripcion(Torneo torneo, Equipo equipo, OffsetDateTime ahora) {
        String cobro = torneo.costoInscripcion() > 0
                ? " La inscripción de " + torneo.costoInscripcion() + " créditos quedó reservada y se cobra cuando empiece el torneo."
                : " La inscripción es gratuita.";
        String cuerpo = "Tu equipo «" + equipo.nombre() + "» quedó inscrito en el torneo «" + torneo.nombre()
                + "» en la posición " + equipo.posicion() + "." + cobro;
        List<Operacion> avisos = new ArrayList<>();
        for (UUID integrante : equipo.integrantes()) {
            avisos.addAll(para(torneo, equipo, integrante, INSCRIPCION,
                    "Inscripción confirmada: " + torneo.nombre(), cuerpo, ahora));
        }
        return avisos;
    }

    public List<Operacion> inicio(Torneo torneo, Equipo equipo, Integer primerEncuentro, OffsetDateTime ahora) {
        String cuerpo = "El torneo «" + torneo.nombre() + "» ya empezó."
                + (primerEncuentro == null ? "" : " Tu equipo «" + equipo.nombre() + "» juega el encuentro " + primerEncuentro + ".");
        List<Operacion> avisos = new ArrayList<>();
        for (UUID integrante : equipo.integrantes()) {
            avisos.addAll(para(torneo, equipo, integrante, INICIO, "Empezó el torneo " + torneo.nombre(), cuerpo, ahora));
        }
        return avisos;
    }

    public List<Operacion> cancelacion(Torneo torneo, Equipo equipo, String motivo, boolean hayDevolucion,
                                       OffsetDateTime ahora) {
        List<Operacion> avisos = new ArrayList<>();
        for (UUID integrante : equipo.integrantes()) {
            boolean pago = hayDevolucion && integrante.equals(equipo.pagadoPor());
            String cuerpo = "El torneo «" + torneo.nombre() + "» se canceló: " + motivo + "."
                    + (pago ? " Tu inscripción de " + torneo.costoInscripcion() + " créditos se devuelve a tu saldo." : "");
            avisos.addAll(para(torneo, equipo, integrante, CANCELACION, "Se canceló el torneo " + torneo.nombre(),
                    cuerpo, ahora));
        }
        return avisos;
    }

    public List<Operacion> premioEntregado(Torneo torneo, Equipo equipo, Operacion premio, OffsetDateTime ahora) {
        List<String> partes = new ArrayList<>();
        if (premio.creditosEntregados() && premio.monto() != null && premio.monto() > 0) {
            partes.add(premio.monto() + " créditos");
        }
        if (premio.epicaEntregada()) {
            partes.add("la épica del premio, ya en tu inventario");
        }
        String cuerpo = "Tu equipo «" + equipo.nombre() + "» ganó el torneo «" + torneo.nombre() + "»."
                + (partes.isEmpty() ? "" : " Recibiste " + String.join(" y ", partes) + ".");
        return para(torneo, equipo, premio.jugadorUid(), PREMIO, "Premio del torneo " + torneo.nombre(), cuerpo, ahora);
    }

    private List<Operacion> para(Torneo torneo, Equipo equipo, UUID destinatario, String hito, String titulo,
                                 String cuerpo, OffsetDateTime ahora) {
        List<Operacion> avisos = new ArrayList<>(2);
        avisos.add(Operacion.aviso(torneo.id(), equipo.id(), destinatario, hito, titulo, cuerpo, ahora));
        if (canales.correoConfigurado()) {
            avisos.add(Operacion.correo(torneo.id(), equipo.id(), destinatario, hito, titulo, cuerpo, ahora));
        }
        return avisos;
    }
}
