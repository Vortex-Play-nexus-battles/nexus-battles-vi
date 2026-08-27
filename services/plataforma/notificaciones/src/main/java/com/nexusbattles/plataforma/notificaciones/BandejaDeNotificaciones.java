package com.nexusbattles.plataforma.notificaciones;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bandeja de avisos de un jugador. HU-NOT-006, requisito RF-NOT-006.
 *
 * <p>Resuelve las tres situaciones que describe la historia. Cuando el jugador
 * tiene varias sesiones abiertas al mismo tiempo, el aviso se entrega a todas.
 * Cuando no tiene ninguna, el aviso no se pierde: queda pendiente y se entrega
 * en el siguiente ingreso. Y cuando una sesion pierde la conexion, al volver
 * recibe lo que se perdio y su cuenta de no leidos vuelve a coincidir con la de
 * las demas.
 *
 * <p>La decision de diseno que sostiene todo esto es que el estado de lectura
 * pertenece al usuario y no a la sesion. Marcar leido en el celular tiene que
 * verse en el computador, asi que se guarda una sola vez para toda la bandeja.
 *
 * <p>Esta clase es de dominio puro y no conoce el canal de transporte. Quien la
 * use decide si empuja el aviso por conexion persistente o si lo entrega por
 * consulta periodica cuando el canal esta caido.
 */
public final class BandejaDeNotificaciones {

    private final String usuarioId;
    private final Map<String, Notificacion> avisos = new LinkedHashMap<>();
    private final Set<String> leidas = new HashSet<>();
    private final Set<String> sesionesActivas = new LinkedHashSet<>();
    private final Map<String, Set<String>> entregadasPorSesion = new HashMap<>();

    private BandejaDeNotificaciones(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    /**
     * Crea la bandeja de un jugador.
     *
     * @param usuarioId identificador del jugador, no puede venir vacio
     * @return una bandeja sin avisos y sin sesiones abiertas
     */
    public static BandejaDeNotificaciones de(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException("el identificador del usuario es obligatorio");
        }
        return new BandejaDeNotificaciones(usuarioId);
    }

    public String usuarioId() {
        return usuarioId;
    }

    /**
     * Registra una sesion que acaba de conectarse.
     *
     * <p>Una sesion que se abre por primera vez no arrastra historial: se
     * considera que los avisos anteriores estan pendientes para ella, de modo
     * que al consultar sus pendientes reciba todo lo que hay sin leer.
     */
    public void abrirSesion(String sesionId) {
        exigirSesion(sesionId);
        sesionesActivas.add(sesionId);
        entregadasPorSesion.putIfAbsent(sesionId, new HashSet<>());
    }

    /** Retira una sesion que se desconecto. Lo ya entregado se conserva. */
    public void cerrarSesion(String sesionId) {
        exigirSesion(sesionId);
        sesionesActivas.remove(sesionId);
    }

    /** Sesiones conectadas en este momento, en el orden en que se abrieron. */
    public Set<String> sesionesActivas() {
        return Collections.unmodifiableSet(sesionesActivas);
    }

    /**
     * Recibe un evento notificable y lo entrega a las sesiones conectadas.
     *
     * @param aviso el aviso a guardar, con identificador unico dentro de la bandeja
     * @return las sesiones a las que se entrego. Vacio si el jugador no tenia
     *     ninguna abierta, en cuyo caso el aviso queda pendiente para su proximo ingreso
     */
    public Set<String> recibir(Notificacion aviso) {
        Objects.requireNonNull(aviso, "la notificacion es obligatoria");
        if (avisos.containsKey(aviso.id())) {
            throw new IllegalArgumentException(
                    "ya existe una notificacion con el identificador " + aviso.id());
        }
        avisos.put(aviso.id(), aviso);
        for (String sesionId : sesionesActivas) {
            entregadasPorSesion.get(sesionId).add(aviso.id());
        }
        return Set.copyOf(sesionesActivas);
    }

    /**
     * Marca un aviso como leido para todo el usuario.
     *
     * <p>No recibe la sesion a proposito. El estado de lectura es del jugador, no
     * del dispositivo, asi que marcar leido en cualquier sesion lo deja leido en
     * todas.
     */
    public void marcarLeida(String notificacionId) {
        if (!avisos.containsKey(notificacionId)) {
            throw new IllegalArgumentException(
                    "no existe una notificacion con el identificador " + notificacionId);
        }
        leidas.add(notificacionId);
    }

    public boolean estaLeida(String notificacionId) {
        return leidas.contains(notificacionId);
    }

    /** Cuantos avisos sin leer tiene el jugador. El mismo numero en todas sus sesiones. */
    public int noLeidas() {
        return avisos.size() - leidas.size();
    }

    /**
     * Avisos que esa sesion todavia no ha recibido, del mas antiguo al mas reciente.
     *
     * <p>Sirve tanto para el primer ingreso como para la reconexion despues de una
     * caida: en ambos casos son los avisos que la sesion se perdio.
     */
    public List<Notificacion> pendientesDeEntrega(String sesionId) {
        exigirSesion(sesionId);
        Set<String> yaEntregadas = entregadasPorSesion.getOrDefault(sesionId, Set.of());
        List<Notificacion> pendientes = new ArrayList<>();
        for (Notificacion aviso : avisos.values()) {
            if (!yaEntregadas.contains(aviso.id())) {
                pendientes.add(aviso);
            }
        }
        return List.copyOf(pendientes);
    }

    /**
     * Entrega a la sesion lo que se perdio y lo da por recibido.
     *
     * @return los avisos entregados en esta llamada, vacio si estaba al dia
     */
    public List<Notificacion> entregarPendientes(String sesionId) {
        List<Notificacion> pendientes = pendientesDeEntrega(sesionId);
        Set<String> entregadas =
                entregadasPorSesion.computeIfAbsent(sesionId, id -> new HashSet<>());
        for (Notificacion aviso : pendientes) {
            entregadas.add(aviso.id());
        }
        return pendientes;
    }

    /** Todos los avisos del jugador, del mas antiguo al mas reciente. */
    public List<Notificacion> historial() {
        return List.copyOf(avisos.values());
    }

    private static void exigirSesion(String sesionId) {
        if (sesionId == null || sesionId.isBlank()) {
            throw new IllegalArgumentException("el identificador de la sesion es obligatorio");
        }
    }
}
