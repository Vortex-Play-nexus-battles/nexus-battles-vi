package com.nexusbattles.plataforma.comentarios.publicacion;

import org.springframework.stereotype.Component;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;

/**
 * Implementacion provisional de {@link ConsultaDeSanciones}.
 *
 * <p>El bloqueo por sancion depende de RF-USR-004, que es de Sprint 3, y el
 * modulo de sanciones todavia no publica contrato en contracts/openapi/. La
 * regla de plataforma dice contrato primero, asi que aqui no se inventa un
 * cliente contra una API que no existe: mientras tanto todo autor se considera
 * habilitado.
 *
 * <p>Cuando el contrato salga, esta clase se reemplaza por el cliente REST
 * correspondiente sin tocar el dominio ni el servicio de publicacion, porque
 * ambos dependen solo de la interfaz. El camino del rechazo por silencio ya
 * esta implementado y probado en el dominio desde el PR 163.
 */
@Component
class SancionesPendientesDeContrato implements ConsultaDeSanciones {

    @Override
    public HiloDeComentarios.EstadoDeAutor estadoDe(String autorId) {
        return HiloDeComentarios.EstadoDeAutor.HABILITADO;
    }
}
