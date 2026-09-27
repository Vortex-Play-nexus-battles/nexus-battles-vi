package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

/** Por donde sale una sancion hacia fuera del servicio (7.3.2). */
public enum CanalDeSalida {

    /** La bandeja del jugador en notificaciones (HU-NOT-005). */
    AVISO,

    /** El estado de acceso de la cuenta en ms-identidad (estado-sancion). */
    PROYECCION,

    /** El correo del jugador, por el servicio de correo (plantilla corporativa). */
    CORREO
}
