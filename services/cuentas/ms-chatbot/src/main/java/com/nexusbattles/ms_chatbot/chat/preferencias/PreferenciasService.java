package com.nexusbattles.ms_chatbot.chat.preferencias;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// ms-chatbot.yaml 1.3.6 (7.4.5 «recordar preferencias del usuario», «nivel de
// detalle»): lee y guarda las preferencias de respuesta de quien habla con el
// asistente.
//
// 1.15.0 (7.4.5 «adaptar el nivel de detalle segun la experiencia»): quien
// nunca las cambio tiene SIN_ELEGIR (idioma y nivel automaticos), y con
// `efectivasDe` el nivel automatico pasa al de su experiencia (D-CHA-13).
@Service
public class PreferenciasService {

    private final PreferenciasDelChatRepository repositorio;
    private final ExperienciaDelChat experiencia;
    private final Clock reloj;

    public PreferenciasService(PreferenciasDelChatRepository repositorio, ExperienciaDelChat experiencia,
                               Clock reloj) {
        this.repositorio = repositorio;
        this.experiencia = experiencia;
        this.reloj = reloj;
    }

    /** Las guardadas, tal cual (con AUTOMATICO si es lo elegido); SIN_ELEGIR si no hay. */
    @Transactional(readOnly = true)
    public PreferenciasDeRespuesta de(IdentidadDelChat identidad) {
        return repositorio.findById(identidad.claveDeConversacion())
            .map(PreferenciasDelChat::comoPreferencias)
            .orElse(PreferenciasDeRespuesta.SIN_ELEGIR);
    }

    /** 1.15.0 — con las que se responde: el nivel automatico ya traducido a uno de verdad. */
    @Transactional(readOnly = true)
    public PreferenciasDeRespuesta efectivasDe(IdentidadDelChat identidad) {
        return experiencia.efectivas(identidad, de(identidad));
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
