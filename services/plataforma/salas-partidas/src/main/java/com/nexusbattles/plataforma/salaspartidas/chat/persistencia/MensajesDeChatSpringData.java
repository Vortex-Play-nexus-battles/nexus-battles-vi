package com.nexusbattles.plataforma.salaspartidas.chat.persistencia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface MensajesDeChatSpringData extends JpaRepository<MensajeDeChatEntidad, UUID> {

    List<MensajeDeChatEntidad> findByCanalOrderByEnviadoEnDesc(String canal, Pageable pagina);
}
