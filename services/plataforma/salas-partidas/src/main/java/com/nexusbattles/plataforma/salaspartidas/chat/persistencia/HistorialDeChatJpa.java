package com.nexusbattles.plataforma.salaspartidas.chat.persistencia;

import com.nexusbattles.plataforma.salaspartidas.chat.Canal;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Autor;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.LogroCompartido;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Tipo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Historial del chat en la tabla mensajes_de_chat del esquema salas_partidas. */
@Repository
class HistorialDeChatJpa implements HistorialDeChat {

    private final MensajesDeChatSpringData mensajes;

    HistorialDeChatJpa(MensajesDeChatSpringData mensajes) {
        this.mensajes = mensajes;
    }

    @Override
    @Transactional
    public void guardar(MensajeDeChat m) {
        LogroCompartido logro = m.logro();
        mensajes.save(new MensajeDeChatEntidad(m.id(), m.canal().clave(), m.autor().id(),
                m.autor().apodo(), m.tipo().name(), m.texto(),
                logro == null ? null : logro.mision(), logro == null ? null : logro.titulo(),
                m.enviadoEn()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MensajeDeChat> ultimos(Canal canal, int cantidad) {
        List<MensajeDeChat> resultado = new ArrayList<>(mensajes
                .findByCanalOrderByEnviadoEnDesc(canal.clave(), PageRequest.of(0, cantidad)).stream()
                .map(HistorialDeChatJpa::aDominio).toList());
        java.util.Collections.reverse(resultado);
        return resultado;
    }

    private static MensajeDeChat aDominio(MensajeDeChatEntidad e) {
        LogroCompartido logro = e.logroMision() == null && e.logroTitulo() == null
                ? null : new LogroCompartido(e.logroMision(), e.logroTitulo());
        return new MensajeDeChat(e.id(), Canal.desdeClave(e.canal()),
                new Autor(e.idAutor(), e.apodoAutor()), Tipo.valueOf(e.tipo()), e.texto(), logro,
                e.enviadoEn());
    }
}
