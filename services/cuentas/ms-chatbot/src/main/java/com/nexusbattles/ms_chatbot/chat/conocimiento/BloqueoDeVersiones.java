package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;

import java.util.Optional;

// ms-chatbot.yaml 1.6.0 (7.4.10): las transiciones de la base de conocimiento
// se hacen en fila, nunca a la vez.
//
// Antes, desplegar (el boton o la publicacion programada), revertir, crear o
// descartar la candidata y editarla podian cruzarse: un despliegue y una
// reversion simultaneos terminaban en un 500 (el indice parcial
// uk_versiones_una_en_produccion de V4 rechazaba la segunda produccion), dos
// despliegues publicaban dos veces, y editar un tema mientras se desplegaba
// la candidata podia cambiar produccion sin evaluarla.
//
// Ahora cada transicion EMPIEZA bloqueando la fila de la version que va a
// cambiar (VersionBaseConocimientoRepository.bloquearLaDeEstado, SELECT ...
// FOR NO KEY UPDATE). La que llega despues espera, y al seguir lee el estado
// ya confirmado. Siempre en el mismo orden, para no bloquearse en cruz:
//
//   produccion -> candidata   desplegar, publicacion programada
//   produccion                revertir, crear la candidata
//   candidata                 descartarla, editar sus temas, importar,
//                             evaluarla, programarla
//
// Si mientras esperaba la fila cambio de estado (la produccion que se iba a
// retirar ya la retiro otro despliegue), PostgreSQL ya no la devuelve: la
// operacion venia de un estado que ya no existe y falla limpia con 409
// CAMBIO_CONCURRENTE. El administrador ve el estado nuevo y decide otra vez.
final class BloqueoDeVersiones {

    private final VersionBaseConocimientoRepository versiones;

    BloqueoDeVersiones(VersionBaseConocimientoRepository versiones) {
        this.versiones = versiones;
    }

    /**
     * La version en ese estado, bloqueada hasta el final de la transacción.
     * Vacío si no hay ninguna.
     *
     * @throws CambioConcurrenteException si la que había cambió de estado
     *     mientras se esperaba el bloqueo y ahora hay otra en su lugar
     */
    Optional<VersionBaseConocimiento> bloquear(EstadoVersion estado) {
        Optional<VersionBaseConocimiento> bloqueada = versiones.bloquearLaDeEstado(estado);
        if (bloqueada.isEmpty() && versiones.existsByEstado(estado)) {
            throw new CambioConcurrenteException();
        }
        return bloqueada;
    }
}
