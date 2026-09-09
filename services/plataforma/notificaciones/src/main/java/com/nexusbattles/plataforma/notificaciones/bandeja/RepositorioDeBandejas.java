package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nexusbattles.plataforma.notificaciones.BandejaDeNotificaciones;
import com.nexusbattles.plataforma.notificaciones.Notificacion;

/**
 * Traduce entre la bandeja del dominio y las tres tablas que la sostienen.
 *
 * <p>Guarda por diferencia y no borrando y reescribiendo. Un aviso ya guardado
 * no vuelve a insertarse, una entrega ya registrada tampoco, y la lectura solo
 * cambia la fila del aviso que se marco. Asi dos sesiones que trabajan sobre la
 * misma bandeja no se pisan lo que la otra acaba de escribir.
 */
@Component
public class RepositorioDeBandejas {

    private final NotificacionRepository avisos;
    private final EntregaRepository entregas;
    private final SesionRepository sesiones;

    public RepositorioDeBandejas(NotificacionRepository avisos, EntregaRepository entregas,
            SesionRepository sesiones) {
        this.avisos = avisos;
        this.entregas = entregas;
        this.sesiones = sesiones;
    }

    /** Carga la bandeja de un jugador con todo su historial y sus sesiones. */
    public BandejaDeNotificaciones cargar(String usuarioId) {
        List<RegistroDeNotificacion> filas = avisos.findByUsuarioIdOrderByCreadaEnAsc(usuarioId);

        List<Notificacion> historial = filas.stream()
                .map(f -> new Notificacion(f.getAvisoId(), f.getTipo(), f.getTitulo(),
                        f.getCuerpo(), f.getCreadaEn()))
                .toList();

        Set<String> leidas = filas.stream()
                .filter(RegistroDeNotificacion::isLeida)
                .map(RegistroDeNotificacion::getAvisoId)
                .collect(java.util.stream.Collectors.toSet());

        Set<String> abiertas = sesiones.findByUsuarioId(usuarioId).stream()
                .map(RegistroDeSesion::getSesionId)
                .collect(java.util.stream.Collectors.toSet());

        Map<String, Set<String>> entregadas = new HashMap<>();
        for (RegistroDeEntrega entrega : entregas.findByUsuarioId(usuarioId)) {
            entregadas.computeIfAbsent(entrega.getSesionId(), id -> new HashSet<>())
                    .add(entrega.getAvisoId());
        }

        return BandejaDeNotificaciones.reconstituir(
                usuarioId, historial, leidas, abiertas, entregadas);
    }

    /** Guarda un aviso que acaba de entrar en la bandeja. */
    public void guardarAviso(String usuarioId, Notificacion aviso) {
        avisos.save(new RegistroDeNotificacion(usuarioId, aviso.id(), aviso.tipo(),
                aviso.titulo(), aviso.cuerpo(), aviso.creadaEn(), false));
    }

    /** Deja constancia de que esas sesiones ya recibieron ese aviso. */
    public void registrarEntregas(String usuarioId, String avisoId, Set<String> sesionesId) {
        for (String sesionId : sesionesId) {
            if (!entregas.existsByUsuarioIdAndAvisoIdAndSesionId(usuarioId, avisoId, sesionId)) {
                entregas.save(new RegistroDeEntrega(usuarioId, avisoId, sesionId));
            }
        }
    }

    /** Registra la sesion si es la primera vez que se anuncia. */
    public void abrirSesion(String usuarioId, String sesionId) {
        if (!sesiones.existsByUsuarioIdAndSesionId(usuarioId, sesionId)) {
            sesiones.save(new RegistroDeSesion(usuarioId, sesionId, Instant.now()));
        }
    }

    /**
     * Borra el registro de la sesion que se desconecto. Las entregas quedan:
     * gracias a ellas la reconexion recibe solo lo que se perdio y no todo lo
     * que el jugador tiene sin leer.
     */
    public void cerrarSesion(String usuarioId, String sesionId) {
        sesiones.deleteByUsuarioIdAndSesionId(usuarioId, sesionId);
    }

    /** Marca el aviso como leido para todo el jugador. */
    public void marcarLeida(String usuarioId, String avisoId) {
        avisos.findByUsuarioIdAndAvisoId(usuarioId, avisoId)
                .ifPresent(fila -> {
                    fila.marcarLeida();
                    avisos.save(fila);
                });
    }

    /** Si ese jugador ya tiene un aviso con ese identificador. */
    public boolean existeAviso(String usuarioId, String avisoId) {
        return avisos.existsByUsuarioIdAndAvisoId(usuarioId, avisoId);
    }
}
