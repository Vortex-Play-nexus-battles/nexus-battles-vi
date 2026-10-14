package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.preferencias.ExperienciaDelChat;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static com.nexusbattles.ms_chatbot.chat.api.ChatController.CABECERA_SESION_ANONIMA;

// ms-chatbot.yaml 1.3.6 (7.4.5, 7.4.6): preferencias de respuesta del
// asistente. Misma identidad que el chat (B11): el uid del token o una sesion
// de visitante registrada; X-Id-Sesion-Anonima nunca abre las de un jugador.
//
//   GET  sin identidad valida responde las de quien nunca eligio (no revela
//        nada): idioma y nivel automaticos, con el nivel de quien empieza.
//   PUT  como el primer mensaje: si el visitante aun no tiene sesion
//        registrada, se le emite una y vuelve en X-Id-Sesion-Anonima.
//
// 1.15.0 (D-CHA-13): las dos devuelven tambien el nivel con el que se le
// responde hoy (`nivelDetalleEfectivo`).
@RestController
@RequestMapping("/chat/preferencias")
public class PreferenciasController {

    private final PreferenciasService servicio;
    private final ExperienciaDelChat experiencia;
    private final ResolutorDeIdentidad resolutor;

    public PreferenciasController(PreferenciasService servicio, ExperienciaDelChat experiencia,
                                  ResolutorDeIdentidad resolutor) {
        this.servicio = servicio;
        this.experiencia = experiencia;
        this.resolutor = resolutor;
    }

    @GetMapping
    public ResponseEntity<PreferenciasResponse> obtener(
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication) {

        Optional<IdentidadDelChat> quien = resolutor.resolver(authentication, idSesionAnonima);
        PreferenciasDeRespuesta guardadas = quien.map(servicio::de).orElse(PreferenciasDeRespuesta.SIN_ELEGIR);
        return ResponseEntity.ok(PreferenciasResponse.desde(guardadas,
            experiencia.efectivas(quien.orElse(null), guardadas)));
    }

    @PutMapping
    public ResponseEntity<PreferenciasResponse> guardar(
        @RequestBody @Valid PreferenciasRequest request,
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication,
        HttpServletRequest peticion) {

        ResolutorDeIdentidad.Resultado quien = resolutor.resolverOEmitir(authentication, idSesionAnonima,
            ChatController.origenDe(peticion));
        PreferenciasDeRespuesta guardadas = servicio.guardar(quien.identidad(), request.comoPreferencias());

        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        if (quien.sesionEmitida() != null) {
            ok.header(CABECERA_SESION_ANONIMA, quien.sesionEmitida());
        }
        return ok.body(PreferenciasResponse.desde(guardadas, experiencia.efectivas(quien.identidad(), guardadas)));
    }
}
