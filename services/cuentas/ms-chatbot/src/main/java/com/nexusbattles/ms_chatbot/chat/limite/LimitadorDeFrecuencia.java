package com.nexusbattles.ms_chatbot.chat.limite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// B11 — 7.4.8: «limite de tasa de consultas para prevenir abuso».
//
// Ventana fija de un minuto por clave, en memoria de la instancia:
//
//   * MENSAJES: por usuario (uid del token) o por sesion de visitante. Es lo
//     caro: cada mensaje escribe en la base y puede consultar a otros
//     servicios.
//   * SESIONES_POR_ORIGEN: emitir sesiones de visitante, por direccion de
//     origen. Sin esto un cliente esquivaria el limite de mensajes fabricando
//     una sesion nueva para cada uno.
//   * SESIONES_GLOBAL: techo de sesiones nuevas por minuto para toda la
//     instancia, por si alguien rota direcciones.
//
// Las cifras no estan en el documento: son convencion tecnica, configurables
// (CHATBOT_LIMITE_*), documentadas como provisionales en el README. Con varias
// instancias el limite es por instancia; el borde (nginx) puede sumar el suyo.
//
// La memoria esta acotada: las ventanas vencidas se barren cada minuto y, si
// aun asi se pasan de MAXIMO_DE_CLAVES, se descartan todas (se pierde el
// conteo de un minuto, no el servicio).
@Component
public class LimitadorDeFrecuencia {

    public enum Regla { MENSAJES, SESIONES_POR_ORIGEN, SESIONES_GLOBAL }

    static final Duration VENTANA = Duration.ofMinutes(1);
    static final int MAXIMO_DE_CLAVES = 100_000;

    private record Ventana(long inicioMs, int cuenta) { }

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private final Map<Regla, Integer> limites;
    private final Clock reloj;

    public LimitadorDeFrecuencia(@Value("${chatbot.limite.mensajes-por-minuto:20}") int mensajes,
                                 @Value("${chatbot.limite.sesiones-por-minuto-por-origen:10}") int sesionesPorOrigen,
                                 @Value("${chatbot.limite.sesiones-por-minuto-global:300}") int sesionesGlobal,
                                 Clock reloj) {
        this.limites = Map.of(Regla.MENSAJES, mensajes, Regla.SESIONES_POR_ORIGEN, sesionesPorOrigen,
            Regla.SESIONES_GLOBAL, sesionesGlobal);
        this.reloj = reloj;
    }

    // Cuenta un uso y lanza LimiteDeFrecuenciaExcedido si con el se pasa del
    // limite de la regla. Atomico por clave (compute de ConcurrentHashMap).
    public void exigir(Regla regla, String clave) {
        long ahora = reloj.millis();
        long ventanaMs = VENTANA.toMillis();
        Ventana actual = ventanas.compute(regla + "|" + clave, (k, v) ->
            v == null || ahora - v.inicioMs() >= ventanaMs ? new Ventana(ahora, 1) : new Ventana(v.inicioMs(), v.cuenta() + 1));
        if (actual.cuenta() > limites.get(regla)) {
            long faltan = (actual.inicioMs() + ventanaMs - ahora + 999) / 1000;
            throw new LimiteDeFrecuenciaExcedido("Demasiadas peticiones seguidas; espera " + faltan
                + " segundo(s) antes de volver a intentarlo.", faltan);
        }
        if (ventanas.size() > MAXIMO_DE_CLAVES) {
            barrer();
            if (ventanas.size() > MAXIMO_DE_CLAVES) {
                ventanas.clear();
            }
        }
    }

    // Quita las ventanas que ya vencieron; la memoria no crece con claves viejas.
    @Scheduled(fixedDelay = 60_000)
    public void barrer() {
        long ahora = reloj.millis();
        ventanas.values().removeIf(v -> ahora - v.inicioMs() >= VENTANA.toMillis());
    }

    int clavesVivas() {
        return ventanas.size();
    }
}
