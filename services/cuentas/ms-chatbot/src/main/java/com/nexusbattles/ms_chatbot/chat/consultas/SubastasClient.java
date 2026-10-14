package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiPublicacionDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaDeSubastasDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ParticipacionDto;

import java.util.List;

public interface SubastasClient {

    MiResumenDto consultarMiResumen(String tokenBearer);

    /** 1.16.0 — las subastas en las que participa (tambien cerradas), con su token. */
    List<ParticipacionDto> misParticipaciones(String tokenBearer);

    /** 1.16.0 — las que publico y siguen activas, con su token. */
    List<MiPublicacionDto> misPublicacionesActivas(String tokenBearer);

    /**
     * 1.16.0 — el listado publico de las activas, las que cierran antes
     * primero, sin credencial. {@code busqueda} es la del propio listado
     * (nombre, descripcion, tipo, habilidades); null o vacia, todas.
     */
    PaginaDeSubastasDto activas(String busqueda, int cuantas);
}
