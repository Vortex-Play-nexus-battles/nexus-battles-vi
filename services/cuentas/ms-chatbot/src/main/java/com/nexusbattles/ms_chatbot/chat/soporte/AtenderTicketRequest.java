package com.nexusbattles.ms_chatbot.chat.soporte;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

// ms-chatbot.yaml 1.3.0: AtenderTicket. Todos opcionales; solo cambia lo que
// viene, y al menos uno tiene que venir (minProperties 1).
//
// No es un record a proposito: en `asignadoA` hay que distinguir "no vino"
// (no tocar la asignacion) de "vino null" (desasignar), y un record no puede.
public class AtenderTicketRequest {

    private EstadoTicket estado;

    @Size(max = 2000)
    private String respuesta;

    @Size(max = 64)
    private String asignadoA;

    private boolean asignadoAPresente;

    public EstadoTicket getEstado() {
        return estado;
    }

    public void setEstado(EstadoTicket estado) {
        this.estado = estado;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }

    public String getAsignadoA() {
        return asignadoA;
    }

    @JsonSetter("asignadoA")
    public void setAsignadoA(String asignadoA) {
        this.asignadoA = asignadoA;
        this.asignadoAPresente = true;
    }

    /** true si llego `"asignadoA": null`: hay que quitar la asignacion. */
    public boolean desasignar() {
        return asignadoAPresente && asignadoA == null;
    }

    @AssertTrue(message = "Manda al menos uno de estado, respuesta o asignadoA.")
    public boolean isAlgoQueCambiar() {
        return estado != null || respuesta != null || asignadoAPresente;
    }
}
