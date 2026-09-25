package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.persistencia;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Conversacion;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.RepositorioDeMensajesDirectos;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adaptador del puerto de mensajes privados contra PostgreSQL (V13).
 *
 * <p>Traduce entre dominio y entidad y nada mas. La unica decision que toma es
 * la de la idempotencia, porque la toma la base: el indice unico
 * {@code (id_remitente, id_cliente)} es quien sabe si dos envios concurrentes
 * con el mismo {@code idCliente} son el mismo.
 */
@Repository
class RepositorioMensajesDirectosJpa implements RepositorioDeMensajesDirectos {

    private final MensajesDirectosSpringData almacen;

    RepositorioMensajesDirectosJpa(MensajesDirectosSpringData almacen) {
        this.almacen = almacen;
    }

    /**
     * Sin {@code @Transactional} a proposito: si el insert choca con el indice
     * unico, la transaccion de {@code saveAndFlush} queda perdida, y la lectura
     * del que gano tiene que ir en otra. Cada llamada a Spring Data abre la suya.
     */
    @Override
    public Guardado guardar(MensajeDirecto mensaje) {
        try {
            return new Guardado(aDominio(almacen.saveAndFlush(aEntidad(mensaje))), true);
        } catch (DataIntegrityViolationException choque) {
            if (mensaje.idCliente() == null) {
                throw choque;
            }
            return buscarPorIdCliente(mensaje.remitente(), mensaje.idCliente())
                    .map(existente -> new Guardado(existente, false))
                    .orElseThrow(() -> choque);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MensajeDirecto> buscarPorIdCliente(UUID remitente, String idCliente) {
        return almacen.findByIdRemitenteAndIdCliente(remitente, idCliente)
                .map(RepositorioMensajesDirectosJpa::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MensajeDirecto> historial(Conversacion conversacion, Instant antesDe, int limite) {
        PageRequest pagina = PageRequest.of(0, limite);
        List<MensajeDirectoEntidad> filas = antesDe == null
                ? almacen.findByConversacionOrderByEnviadoEnDescIdDesc(conversacion.clave(), pagina)
                : almacen.findByConversacionAndEnviadoEnBeforeOrderByEnviadoEnDescIdDesc(
                        conversacion.clave(), antesDe, pagina);
        List<MensajeDirecto> resultado = new ArrayList<>(filas.stream()
                .map(RepositorioMensajesDirectosJpa::aDominio).toList());
        // Se piden los mas recientes y se les da la vuelta: el orden de lectura.
        Collections.reverse(resultado);
        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MensajeDirecto> ultimosPorConversacion(UUID participante) {
        Map<String, MensajeDirecto> porConversacion = new LinkedHashMap<>();
        for (MensajeDirectoEntidad fila : almacen.ultimosPorConversacion(participante)) {
            MensajeDirecto mensaje = aDominio(fila);
            // Dos en el mismo instante: se queda uno, siempre el mismo.
            porConversacion.merge(fila.conversacion(), mensaje,
                    (uno, otro) -> uno.id().toString().compareTo(otro.id().toString()) >= 0 ? uno : otro);
        }
        return List.copyOf(porConversacion.values());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> noLeidosPorRemitente(UUID destinatario) {
        return almacen.contarNoLeidosPorRemitente(destinatario).stream()
                .collect(Collectors.toMap(MensajesDirectosSpringData.NoLeidosDeUnRemitente::getRemitente,
                        MensajesDirectosSpringData.NoLeidosDeUnRemitente::getCantidad));
    }

    @Override
    @Transactional(readOnly = true)
    public long noLeidos(UUID destinatario, UUID remitente) {
        return almacen.countByIdDestinatarioAndIdRemitenteAndLeidoEnIsNull(destinatario, remitente);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MensajeDirecto> primerNoLeido(UUID destinatario, UUID remitente) {
        return almacen.findFirstByIdDestinatarioAndIdRemitenteAndLeidoEnIsNullOrderByEnviadoEnAscIdAsc(
                destinatario, remitente).map(RepositorioMensajesDirectosJpa::aDominio);
    }

    @Override
    @Transactional
    public int marcarLeidos(UUID destinatario, UUID remitente, Instant cuando) {
        return almacen.marcarLeidos(destinatario, remitente, cuando);
    }

    private static MensajeDirectoEntidad aEntidad(MensajeDirecto m) {
        return new MensajeDirectoEntidad(m.id(), m.conversacion().clave(), m.remitente(), m.apodoRemitente(),
                m.destinatario(), m.apodoDestinatario(), m.texto(), m.enviadoEn(), m.leidoEn(), m.idCliente());
    }

    private static MensajeDirecto aDominio(MensajeDirectoEntidad e) {
        return new MensajeDirecto(e.id(), Conversacion.desdeClave(e.conversacion()), e.idRemitente(),
                e.apodoRemitente(), e.idDestinatario(), e.apodoDestinatario(), e.texto(), e.enviadoEn(),
                e.leidoEn(), e.idCliente());
    }
}
