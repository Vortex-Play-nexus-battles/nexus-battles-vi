package com.nexusbattles.plataforma.torneos.torneo;

/**
 * Puerto hacia la lista negra (HU-ADM-002): nombre y avatar del equipo
 * cumplen «la politica establecida para el registro de usuarios» (RF-TOR-003).
 */
public interface FiltroDeNombres {

    /**
     * @return true si el texto pasa la politica
     * @throws TorneoRechazado LISTA_NEGRA_NO_DISPONIBLE si no se pudo verificar
     */
    boolean aprobado(String texto);
}
