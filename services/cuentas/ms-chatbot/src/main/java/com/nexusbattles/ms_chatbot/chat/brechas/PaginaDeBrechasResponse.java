package com.nexusbattles.ms_chatbot.chat.brechas;

import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import org.springframework.data.domain.Page;

import java.util.List;

// ms-chatbot.yaml 1.7.0: PaginaDeBrechas.
public record PaginaDeBrechasResponse(List<BrechaResponse> contenido, int pagina, int tamano, long totalElementos) {

    public static PaginaDeBrechasResponse desde(Page<BrechaConocimiento> pagina) {
        return new PaginaDeBrechasResponse(pagina.getContent().stream().map(BrechaResponse::desde).toList(),
            pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
    }
}
