package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiPublicacionDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ParticipacionDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.SubastaActivaDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Lo que dicen las consultas en vivo y la navegación asistida (7.4.4), en
 * español y en inglés (7.4.2, ms-chatbot.yaml 1.5.0).
 *
 * <p>Las secciones se nombran como las ve el jugador en la barra («Jugar
 * online», «Misiones», «Torneo», «Mi inventario», «Subasta», «Mi Cuenta») y
 * en el menú de su apodo. La interfaz solo está en español, así que en inglés
 * se cita la etiqueta española entre comillas angulares y se traduce al lado.
 */
final class TextosDeConsultas {

    // La hora de fin se dice en hora de Colombia, igual que las analiticas del panel.
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FIN_ES =
        DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm").withZone(BOGOTA);
    private static final DateTimeFormatter FIN_EN =
        DateTimeFormatter.ofPattern("MM/dd 'at' HH:mm").withZone(BOGOTA);

    private final boolean ingles;

    private TextosDeConsultas(boolean ingles) {
        this.ingles = ingles;
    }

    static TextosDeConsultas en(boolean ingles) {
        return new TextosDeConsultas(ingles);
    }

    boolean ingles() {
        return ingles;
    }

    private String elegir(String espanol, String english) {
        return ingles ? english : espanol;
    }

    // ---- Navegacion ---------------------------------------------------------

    String navegarInventario() {
        return elegir("Tu inventario está en «Mi inventario», en el menú principal.",
            "Your inventory is in «Mi inventario» (My inventory), in the main menu.");
    }

    String navegarSubastas() {
        return elegir("Las subastas están en «Subasta», en el menú principal; en «Mis subastas» ves tus pujas, "
                + "tus publicaciones y lo que tienes pendiente de recoger.",
            "Auctions are in «Subasta» (Auction), in the main menu; «Mis subastas» (My auctions) shows your bids, "
                + "your listings and what you still have to collect.");
    }

    String navegarNotificaciones() {
        return elegir("Tus notificaciones están en la campana de la barra superior: muestra cuántas tienes sin leer.",
            "Your notifications are in the bell on the top bar; it shows how many are unread.");
    }

    String navegarPerfil() {
        return elegir("Tu perfil está en «Mi Cuenta», pestaña «Perfil»; también llegas con «Mi perfil» en el menú "
                + "de tu apodo.",
            "Your profile is in «Mi Cuenta» (My Account), «Perfil» tab; you can also open «Mi perfil» from your "
                + "nickname menu.");
    }

    String navegarMisiones() {
        return elegir("Tus misiones están en «Misiones», en el menú principal: «En curso» muestra las que están "
                + "corriendo y «Historial» las que terminaron.",
            "Your missions are in «Misiones» (Missions), in the main menu: «En curso» shows the ones running and "
                + "«Historial» the finished ones.");
    }

    String navegarTorneos() {
        return elegir("Los torneos están en «Torneo», en el menú principal: ahí ves los abiertos, inscribes a tu "
                + "equipo y sigues el árbol.",
            "Tournaments are in «Torneo» (Tournament), in the main menu: there you see the open ones, register your "
                + "team and follow the bracket.");
    }

    // ---- Servicios que no respondieron -------------------------------------

    String noDisponible(String queSeConsulto, String whatWasChecked) {
        return elegir("No pude consultar " + queSeConsulto + " en este momento: el servicio no respondió a tiempo. "
                + "Inténtalo de nuevo en unos minutos.",
            "I couldn't check " + whatWasChecked + " right now: the service didn't answer in time. "
                + "Please try again in a few minutes.");
    }

    String inventarioNoDisponible() {
        return noDisponible("tu inventario", "your inventory");
    }

    String subastasNoDisponibles() {
        return noDisponible("tus subastas", "your auctions");
    }

    /** 1.16.0 — el listado publico de la subasta. */
    String subastaNoDisponible() {
        return noDisponible("la subasta", "the auction");
    }

    String notificacionesNoDisponibles() {
        return noDisponible("tus notificaciones", "your notifications");
    }

    String movimientosNoDisponibles() {
        return noDisponible("tus movimientos de créditos", "your credit movements");
    }

    String misionesNoDisponibles() {
        return noDisponible("tus misiones", "your missions");
    }

    String torneosNoDisponibles() {
        return noDisponible("los torneos", "the tournaments");
    }

    // ---- Inventario ----------------------------------------------------------

    String inventarioVacio() {
        return elegir("Tu inventario está vacío por ahora.", "Your inventory is empty for now.");
    }

    String inventario(long total, String algunos) {
        String base = elegir("Tienes " + total + " elemento(s) en tu inventario.",
            "You have " + total + " item(s) in your inventory.");
        if (algunos == null || algunos.isBlank()) {
            return base;
        }
        return base + elegir(" Algunos de ellos: " + algunos + ".", " Some of them: " + algunos + ".");
    }

    String busquedaMuyCorta() {
        return elegir("Para buscar en tu inventario escribe al menos 4 letras de lo que buscas, por ejemplo: "
                + "«busca espada en mi inventario».",
            "To search your inventory, type at least 4 letters of what you're looking for, for example: "
                + "«search my inventory for sword».");
    }

    String busquedaSinResultados(String criterio) {
        return elegir("No encontré «" + criterio + "» en tu inventario. La búsqueda mira el nombre, el tipo, los "
                + "efectos y las habilidades de cada producto.",
            "I didn't find «" + criterio + "» in your inventory. The search looks at each product's name, type, "
                + "effects and abilities.");
    }

    String busqueda(long total, String criterio, String algunos) {
        String base = elegir("En tu inventario hay " + total + " resultado(s) para «" + criterio + "»",
            "Your inventory has " + total + " result(s) for «" + criterio + "»");
        return base + (algunos == null || algunos.isBlank() ? "." : ": " + algunos + ".")
            + elegir(" En «Mi inventario» puedes ver su ficha.", " Open «Mi inventario» to see their details.");
    }

    // ---- Subastas -----------------------------------------------------------

    // 1.16.0: los importes como los escribe una persona («300», no «300.00»).
    String subastas(Object ganando, Object retenidos, Object saldo) {
        String texto = elegir(
            "Vas ganando " + ganando + " subasta(s) en este momento, con " + creditos(retenidos)
                + " créditos retenidos en pujas activas.",
            "You're winning " + ganando + " auction(s) right now, with " + creditos(retenidos)
                + " credits held in active bids.");
        if (saldo != null) {
            texto += elegir(" Tu saldo disponible es de " + creditos(saldo) + " créditos.",
                " Your available balance is " + creditos(saldo) + " credits.");
        }
        return texto;
    }

    // 1.16.0 (7.4.4 «estado de productos en la subasta») — cada subasta abierta
    // en la que puja o que publico, en tarjetas.
    String detalleDeMisSubastas(int pujando, int publicadas) {
        if (pujando == 0 && publicadas == 0) {
            return elegir(" No tienes pujas ni subastas publicadas que sigan abiertas.",
                " You have no bids or listings in auctions that are still open.");
        }
        return elegir(" Pujas en " + pujando + " subasta(s) abierta(s) y tienes " + publicadas
                + " publicada(s); las que cierran antes, abajo.",
            " You're bidding in " + pujando + " open auction(s) and have " + publicadas
                + " listing(s) of your own; the ones closing soonest are below.");
    }

    String sinDetalleDeMisSubastas() {
        return elegir(" No pude traer el detalle de cada una: lo ves en «Subasta», en «Mis subastas».",
            " I couldn't get the details of each one: you can see them in «Subasta» (Auction), «Mis subastas» "
                + "(My auctions).");
    }

    String tarjetaDePuja(ParticipacionDto puja) {
        String estado = switch (puja.estado() == null ? "" : puja.estado()) {
            case "GANANDO" -> elegir("Vas ganando con " + creditos(puja.tuMejorPuja()) + " créditos",
                "You're winning with " + creditos(puja.tuMejorPuja()) + " credits");
            case "SUPERADA" -> elegir("Te superaron: la oferta vigente es " + creditos(puja.ofertaVigente())
                    + " y tu mejor puja, " + creditos(puja.tuMejorPuja()),
                "You've been outbid: the current offer is " + creditos(puja.ofertaVigente()) + " and your best bid, "
                    + creditos(puja.tuMejorPuja()));
            case "AUTOMATICA" -> elegir("Tu puja automática aún no ha pujado; la oferta vigente es "
                    + creditos(puja.ofertaVigente()),
                "Your automatic bid hasn't bid yet; the current offer is " + creditos(puja.ofertaVigente()));
            default -> elegir("Oferta vigente: " + creditos(puja.ofertaVigente()),
                "Current offer: " + creditos(puja.ofertaVigente()));
        };
        return estado + " · " + pujas(puja.cantidadPujas()) + " · " + cierraEl(puja.fechaFin()) + ".";
    }

    String tarjetaDePublicacion(MiPublicacionDto publicacion) {
        return elegir("Tu subasta: oferta vigente " + creditos(publicacion.ofertaVigente()) + " créditos",
            "Your auction: current offer " + creditos(publicacion.ofertaVigente()) + " credits")
            + " · " + pujas(publicacion.cantidadPujas()) + " · " + cierraEl(publicacion.fechaFin()) + ".";
    }

    // ---- La subasta: lo que hay ahora (1.16.0, publico) -------------------------

    String subastasAbiertas(long total, String lista) {
        return elegir("Hay " + total + " subasta(s) abierta(s) ahora. Las que cierran antes: " + lista + ".",
            "There are " + total + " open auction(s) right now. Closing soonest: " + lista + ".");
    }

    String sinSubastasAbiertas() {
        return elegir("Ahora no hay ninguna subasta abierta. Puedes publicar una en «Subasta», con «Publicar "
                + "subasta».",
            "There are no open auctions right now. You can list one in «Subasta» (Auction), with «Publicar "
                + "subasta» (List an auction).");
    }

    String subastasDe(String buscado, int cuantas, String lista) {
        return elegir("Hay " + cuantas + " subasta(s) abierta(s) de «" + buscado + "»: " + lista + ".",
            "There are " + cuantas + " open auction(s) for «" + buscado + "»: " + lista + ".");
    }

    String ningunaDe(String buscado, long abiertas) {
        if (abiertas == 0) {
            return elegir("Ahora no hay subastas abiertas de «" + buscado + "»; de hecho, no hay ninguna abierta.",
                "There are no open auctions for «" + buscado + "» right now; in fact, there are none open.");
        }
        return elegir("Ahora no hay subastas abiertas de «" + buscado + "». Hay " + abiertas
                + " abierta(s) de otros productos.",
            "There are no open auctions for «" + buscado + "» right now. There are " + abiertas
                + " open auction(s) for other products.");
    }

    String enLista(SubastaActivaDto subasta) {
        return "«" + subasta.nombreProducto() + "» (" + elegir("oferta vigente ", "current offer ")
            + creditos(subasta.ofertaVigente()) + ", " + pujas(subasta.cantidadPujas()) + ", "
            + cierraEl(subasta.fechaFin()) + ")";
    }

    String tarjetaDeSubasta(SubastaActivaDto subasta) {
        String texto = elegir("Oferta vigente: " + creditos(subasta.ofertaVigente()) + " créditos",
            "Current offer: " + creditos(subasta.ofertaVigente()) + " credits")
            + " · " + pujas(subasta.cantidadPujas()) + " · " + cierraEl(subasta.fechaFin());
        if (subasta.precioCompraInmediata() != null) {
            texto += elegir(" · compra inmediata: " + creditos(subasta.precioCompraInmediata()) + " créditos",
                " · buy now: " + creditos(subasta.precioCompraInmediata()) + " credits");
        }
        return texto + ".";
    }

    private String pujas(Integer cantidad) {
        int n = cantidad == null ? 0 : cantidad;
        return elegir(n + " puja(s)", n + " bid(s)");
    }

    private String cierraEl(Instant fin) {
        if (fin == null) {
            return elegir("sin fecha de cierre", "no closing date");
        }
        return elegir("cierra el " + FIN_ES.format(fin), "closes on " + FIN_EN.format(fin) + " (Colombia time)");
    }

    // Un importe como lo escribe una persona: «120», no «120.00». Lo que no es
    // un numero se dice tal cual; lo que falta, como guion.
    static String creditos(Object importe) {
        if (importe == null) {
            return "—";
        }
        try {
            BigDecimal valor = importe instanceof BigDecimal decimal ? decimal : new BigDecimal(importe.toString().strip());
            return valor.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException noEsNumero) {
            return importe.toString();
        }
    }

    // ---- Notificaciones -------------------------------------------------------

    String sinNotificaciones() {
        return elegir("No tienes notificaciones sin leer.", "You have no unread notifications.");
    }

    String notificaciones(long noLeidas, String recientes) {
        String texto = elegir("Tienes " + noLeidas + " notificación(es) sin leer.",
            "You have " + noLeidas + " unread notification(s).");
        if (recientes != null && !recientes.isBlank()) {
            texto += elegir(" Las más recientes: " + recientes + ".", " The latest: " + recientes + ".");
        }
        return texto;
    }

    // ---- Movimientos de creditos --------------------------------------------

    String sinMovimientos() {
        return elegir("Todavía no tienes movimientos de créditos.", "You have no credit movements yet.");
    }

    String movimientos(String lista) {
        return elegir("Tus últimos movimientos de créditos: " + lista + ". El detalle está en «Mi Cuenta», "
                + "pestaña «Historial».",
            "Your latest credit movements: " + lista + ". The details are in «Mi Cuenta» (My Account), "
                + "«Historial» tab.");
    }

    String apartados(String monto) {
        return elegir(monto + " apartados", monto + " held");
    }

    String sinMoverSaldo(String monto) {
        return elegir(monto + " sin mover saldo", monto + " without changing the balance");
    }

    // ---- Misiones -------------------------------------------------------------

    String sinMisiones() {
        return elegir("No tienes misiones en curso. ", "You have no missions in progress. ") + navegarMisiones();
    }

    String misiones(int total, String lista) {
        return elegir("Tienes " + total + " misión(es) en curso: " + lista + ".",
            "You have " + total + " mission(s) in progress: " + lista + ".");
    }

    String conHeroe(String heroe) {
        return elegir(" con ", " with ") + heroe;
    }

    String nivel(Object nivel) {
        return elegir(" (nivel " + nivel + ")", " (level " + nivel + ")");
    }

    String completada(long porcentaje) {
        return elegir(", " + porcentaje + " % completada", ", " + porcentaje + "% done");
    }

    String terminaEl(Instant fin) {
        return elegir(", termina el " + FIN_ES.format(fin), ", ends on " + FIN_EN.format(fin) + " (Colombia time)");
    }

    // ---- Torneos --------------------------------------------------------------

    String sinTorneosActivos() {
        return elegir("Ahora mismo no hay ningún torneo con inscripciones abiertas ni en curso.",
            "Right now there is no tournament open for registration or in progress.");
    }

    String noEstasEnTorneo(String nombre, boolean enCurso, Object inscritos, Object cupos, Object costo) {
        String detalle = enCurso
            ? elegir("ya está en curso.", "is already in progress.")
            : elegir("tiene inscripciones abiertas (" + inscritos + " de " + cupos + " equipos; la inscripción "
                    + "cuesta " + costo + " créditos).",
                "is open for registration (" + inscritos + " of " + cupos + " teams; registration costs " + costo
                    + " credits).");
        return elegir("No estás en ningún torneo activo. El torneo «" + nombre + "» ",
            "You're not in any active tournament. The «" + nombre + "» tournament ") + detalle + " "
            + navegarTorneos();
    }

    String enTorneo(String torneo, String equipo) {
        return elegir("Estás en el torneo «" + torneo + "» con tu equipo «" + equipo + "».",
            "You're in the «" + torneo + "» tournament with your team «" + equipo + "».");
    }

    String inscrito(Object posicion) {
        return elegir(" Tu equipo ya está inscrito, en la posición " + posicion + ".",
            " Your team is already registered, in position " + posicion + ".");
    }

    String sinInscribir() {
        return elegir(" Tu equipo está registrado pero todavía no se ha inscrito: la inscripción se paga en «Torneo».",
            " Your team is registered but not enrolled yet: you pay the registration in «Torneo» (Tournament).");
    }

    String eliminado() {
        return elegir(" Tu equipo ya quedó eliminado.", " Your team has already been eliminated.");
    }

    String encuentroListo(Object numero) {
        return elegir(" Tu próximo encuentro es el " + numero + " y ya está listo para jugarse.",
            " Your next match is number " + numero + " and it's ready to be played.");
    }

    String sigueEnCarrera() {
        return elegir(" Tu equipo sigue en carrera; tu próximo encuentro espera a que se jueguen los anteriores.",
            " Your team is still in the running; your next match waits for the previous ones to be played.");
    }

    // ---- Informe de actividad ------------------------------------------------

    String informe(String inventario, String subastas, String notificaciones, String creditos, String misiones) {
        String salto = System.lineSeparator() + System.lineSeparator();
        return elegir("Este es un resumen de tu actividad reciente:", "Here's a summary of your recent activity:")
            + salto + elegir("Inventario: ", "Inventory: ") + inventario
            + salto + elegir("Subastas: ", "Auctions: ") + subastas
            + salto + elegir("Notificaciones: ", "Notifications: ") + notificaciones
            + salto + elegir("Créditos: ", "Credits: ") + creditos
            + salto + elegir("Misiones: ", "Missions: ") + misiones;
    }
}
