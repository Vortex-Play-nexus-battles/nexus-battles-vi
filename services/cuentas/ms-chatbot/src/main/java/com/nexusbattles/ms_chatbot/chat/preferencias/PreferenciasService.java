package com.nexusbattles.ms_chatbot.chat.preferencias;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// ms-chatbot.yaml 1.3.6 (7.4.5 «recordar preferencias del usuario», «nivel de
// detalle»): lee y guarda las preferencias de respuesta de quien habla con el
// asistente. Quien nunca las cambio tiene las de por defecto, que responden
// igual que antes de 1.3.6.
@Service
public class PreferenciasService {

    private final PreferenciasDelChatRepository repositorio;
    private final Clock reloj;

    public PreferenciasService(PreferenciasDelChatRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public PreferenciasDeRespuesta de(IdentidadDelChat identidad) {
        return repositorio.findById(identidad.claveDeConversacion())
            .map(PreferenciasDelChat::comoPreferencias)
            .orElse(PreferenciasDeRespuesta.POR_DEFECTO);
    }

    @Transactional
    public PreferenciasDeRespuesta guardar(IdentidadDelChat identidad, PreferenciasDeRespuesta nuevas) {
        Instant ahora = Instant.now(reloj);
        String clave = identidad.claveDeConversacion();
        PreferenciasDelChat fila = repositorio.findById(clave)
            .map(existente -> {
                existente.cambiar(nuevas, ahora);
                return existente;
            })
            .orElseGet(() -> new PreferenciasDelChat(clave, nuevas, ahora));
        return repositorio.save(fila).comoPreferencias();
    }
}
