package com.nexusbattles.ms_chatbot.chat.repository;

import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MensajeRepository extends JpaRepository<Mensaje, UUID> {

    List<Mensaje> findByConversacionIdOrderByFechaEnvioAsc(UUID conversacionId);

    // Soporta el criterio "limpiar historial" de HU-CHA-001.
    void deleteByConversacionId(UUID conversacionId);

    // HU-CHA-011: busca el mensaje SOLO si pertenece a la conversacion de
    // quien llama. Un mensaje de otra sesion se comporta igual que uno
    // inexistente, asi nadie puede calificar (ni confirmar que existe) una
    // respuesta de una conversacion ajena adivinando su UUID.
    Optional<Mensaje> findByIdAndConversacionIdentificadorSesion(UUID id, String identificadorSesion);

    // HU-CHA-011: la pregunta del usuario que origino una respuesta del bot
    // es el mensaje de USUARIO mas reciente de la misma conversacion con
    // fecha anterior o igual a la de esa respuesta. Se usa "o igual" porque
    // ambos mensajes se guardan en la misma transaccion y, en teoria, podrian
    // compartir marca de tiempo; el filtro por remitente evita que la propia
    // respuesta del bot cuente como su pregunta.
    Optional<Mensaje> findFirstByConversacionIdAndRemitenteAndFechaEnvioLessThanEqualOrderByFechaEnvioDesc(
        UUID conversacionId, Remitente remitente, Instant fechaEnvio);
}
