package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nexusbattles.plataforma.notificaciones.BandejaDeNotificaciones;
import com.nexusbattles.plataforma.notificaciones.Notificacion;

/**
 * Aplica las reglas de HU-NOT-006 sobre datos reales.
 *
 * <p>El flujo es siempre el mismo: cargar la bandeja guardada del jugador,
 * dejar que el dominio decida, guardar lo que decidio y recien entonces empujar
 * por el canal. Las reglas no se repiten aqui, viven en
 * {@link BandejaDeNotificaciones} y este servicio solo las alimenta.
 *
 * <p>El canal se toca cuando la transaccion ya confirmo, no antes. Asi el
 * jugador nunca recibe un aviso que al final no quedo en su bandeja. Y si el
 * canal es el que falla, el aviso ya esta guardado y la sesion lo recupera al
 * volver a anunciarse, por eso ese fallo se anota y no revienta la operacion.
 */
@Service
public class ServicioDeNotificaciones {

    private static final Logger log = LoggerFactory.getLogger(ServicioDeNotificaciones.class);

    private final RepositorioDeBandejas repositorio;
    private final CanalDeNotificaciones canal;

    public ServicioDeNotificaciones(RepositorioDeBandejas repositorio, CanalDeNotificaciones canal) {
        this.repositorio = repositorio;
        this.canal = canal;
    }

    /**
     * Registra un evento notificable y lo entrega a las sesiones conectadas.
     *
     * @return las sesiones a las que se entrego en el momento, vacio si el
     *     jugador no tenia ninguna abierta y el aviso quedo pendiente
     * @throws AvisoDuplicado si ese jugador ya tiene un aviso con ese identificador
     */
    @Transactional
    public Set<String> emitir(String usuarioId, Notificacion aviso) {
        if (repositorio.existeAviso(usuarioId, aviso.id())) {
            throw new AvisoDuplicado(
                    "el jugador ya tiene un aviso con el identificador " + aviso.id());
        }

        BandejaDeNotificaciones bandeja = repositorio.cargar(usuarioId);
        Set<String> notificadas = bandeja.recibir(aviso);

        repositorio.guardarAviso(usuarioId, aviso);
        repositorio.registrarEntregas(usuarioId, aviso.id(), notificadas);

        int noLeidas = bandeja.noLeidas();
        publicarTrasCommit(() -> canal.avisar(usuarioId, aviso, noLeidas));
        return notificadas;
    }

    /**
     * Marca un aviso como leido para todo el jugador.
     *
     * <p>Es idempotente. Marcar dos veces no cambia la cuenta ni falla, porque
     * el jugador puede tocar el mismo aviso desde dos sesiones a la vez.
     *
     * @return la cuenta de no leidos despues de marcar
     * @throws AvisoNoEncontrado si ese jugador no tiene ese aviso
     */
    @Transactional
    public int marcarLeida(String usuarioId, String notificacionId) {
        BandejaDeNotificaciones bandeja = repositorio.cargar(usuarioId);
        boolean existe = bandeja.historial().stream()
                .anyMatch(aviso -> aviso.id().equals(notificacionId));
        if (!existe) {
            throw new AvisoNoEncontrado(
                    "el jugador no tiene un aviso con el identificador " + notificacionId);
        }

        bandeja.marcarLeida(notificacionId);
        repositorio.marcarLeida(usuarioId, notificacionId);

        int noLeidas = bandeja.noLeidas();
        publicarTrasCommit(() -> canal.actualizarContador(usuarioId, noLeidas));
        return noLeidas;
    }

    /** Bandeja completa del jugador, para pintar la vista al entrar. */
    @Transactional(readOnly = true)
    public BandejaDeNotificaciones consultar(String usuarioId) {
        return repositorio.cargar(usuarioId);
    }

    /**
     * Da de alta una sesion y le entrega lo que se perdio.
     *
     * <p>Cubre los dos escenarios de la historia que no son el camino feliz. Si
     * el jugador no tenia sesiones abiertas, aqui recibe lo que quedo pendiente.
     * Si la sesion se cayo y vuelve, aqui recibe solo lo que llego mientras
     * estuvo fuera, porque la bandeja recuerda que le habia entregado antes.
     *
     * @return los avisos entregados en esta llamada, vacio si estaba al dia
     */
    @Transactional
    public List<Notificacion> registrarSesion(String usuarioId, String sesionId) {
        BandejaDeNotificaciones bandeja = repositorio.cargar(usuarioId);
        bandeja.abrirSesion(sesionId);

        List<Notificacion> pendientes = bandeja.entregarPendientes(sesionId);

        repositorio.abrirSesion(usuarioId, sesionId);
        for (Notificacion aviso : pendientes) {
            repositorio.registrarEntregas(usuarioId, aviso.id(), Set.of(sesionId));
        }

        int noLeidas = bandeja.noLeidas();
        publicarTrasCommit(() -> canal.entregarPendientes(usuarioId, pendientes, noLeidas));
        return pendientes;
    }

    /**
     * Cierra una sesion que se desconecto, sin borrar sus entregas. Lo llama
     * el ciclo de vida del canal cuando la conexion se cae, con o sin
     * despedida del cliente.
     */
    @Transactional
    public void cerrarSesion(String usuarioId, String sesionId) {
        repositorio.cerrarSesion(usuarioId, sesionId);
    }

    /**
     * Publica al canal despues del commit y no dentro de la transaccion. En
     * las pruebas unitarias no hay transaccion activa y el envio sale
     * directo, que observablemente es lo mismo.
     */
    private void publicarTrasCommit(Runnable envio) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publicarSinReventar(envio);
                }
            });
        } else {
            publicarSinReventar(envio);
        }
    }

    private void publicarSinReventar(Runnable envio) {
        try {
            envio.run();
        } catch (RuntimeException ex) {
            log.warn("El canal en tiempo real fallo despues de guardar,"
                    + " el aviso queda pendiente de entrega: {}", ex.getMessage());
        }
    }
}
