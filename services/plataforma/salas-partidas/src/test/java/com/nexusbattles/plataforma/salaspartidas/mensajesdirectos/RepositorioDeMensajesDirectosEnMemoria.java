package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble en memoria del almacen de mensajes privados, con la misma semantica que
 * el adaptador JPA (que se prueba aparte contra PostgreSQL).
 */
class RepositorioDeMensajesDirectosEnMemoria implements RepositorioDeMensajesDirectos {

    final List<MensajeDirecto> guardados = new ArrayList<>();

    /** Para simular que otro envio con el mismo idCliente gano la carrera. */
    MensajeDirecto ganadorDeLaCarrera;

    @Override
    public Guardado guardar(MensajeDirecto mensaje) {
        if (ganadorDeLaCarrera != null) {
            return new Guardado(ganadorDeLaCarrera, false);
        }
        if (mensaje.idCliente() != null) {
            Optional<MensajeDirecto> ya = buscarPorIdCliente(mensaje.remitente(), mensaje.idCliente());
            if (ya.isPresent()) {
                return new Guardado(ya.get(), false);
            }
        }
        guardados.add(mensaje);
        return new Guardado(mensaje, true);
    }

    @Override
    public Optional<MensajeDirecto> buscarPorIdCliente(UUID remitente, String idCliente) {
        return guardados.stream()
                .filter(m -> m.remitente().equals(remitente) && idCliente.equals(m.idCliente()))
                .findFirst();
    }

    @Override
    public List<MensajeDirecto> historial(Conversacion conversacion, Instant antesDe, int limite) {
        List<MensajeDirecto> deLaConversacion = guardados.stream()
                .filter(m -> m.conversacion().equals(conversacion))
                .filter(m -> antesDe == null || m.enviadoEn().isBefore(antesDe))
                .sorted(Comparator.comparing(MensajeDirecto::enviadoEn))
                .toList();
        int desde = Math.max(0, deLaConversacion.size() - limite);
        return List.copyOf(deLaConversacion.subList(desde, deLaConversacion.size()));
    }

    @Override
    public List<MensajeDirecto> ultimosPorConversacion(UUID participante) {
        Map<Conversacion, MensajeDirecto> ultimos = new LinkedHashMap<>();
        for (MensajeDirecto m : guardados) {
            if (!m.conversacion().incluye(participante)) {
                continue;
            }
            ultimos.merge(m.conversacion(), m,
                    (uno, otro) -> uno.enviadoEn().isAfter(otro.enviadoEn()) ? uno : otro);
        }
        return List.copyOf(ultimos.values());
    }

    @Override
    public Map<UUID, Long> noLeidosPorRemitente(UUID destinatario) {
        Map<UUID, Long> cuenta = new HashMap<>();
        for (MensajeDirecto m : guardados) {
            if (m.destinatario().equals(destinatario) && m.leidoEn() == null) {
                cuenta.merge(m.remitente(), 1L, Long::sum);
            }
        }
        return cuenta;
    }

    @Override
    public long noLeidos(UUID destinatario, UUID remitente) {
        return guardados.stream()
                .filter(m -> m.destinatario().equals(destinatario) && m.remitente().equals(remitente))
                .filter(m -> m.leidoEn() == null)
                .count();
    }

    @Override
    public int marcarLeidos(UUID destinatario, UUID remitente, Instant cuando) {
        int cambiados = 0;
        for (int i = 0; i < guardados.size(); i++) {
            MensajeDirecto m = guardados.get(i);
            if (m.destinatario().equals(destinatario) && m.remitente().equals(remitente) && m.leidoEn() == null) {
                guardados.set(i, new MensajeDirecto(m.id(), m.conversacion(), m.remitente(), m.apodoRemitente(),
                        m.destinatario(), m.apodoDestinatario(), m.texto(), m.enviadoEn(), cuando, m.idCliente()));
                cambiados++;
            }
        }
        return cambiados;
    }
}
