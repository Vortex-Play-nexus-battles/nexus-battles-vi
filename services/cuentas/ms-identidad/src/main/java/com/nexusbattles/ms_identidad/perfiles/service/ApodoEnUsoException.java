package com.nexusbattles.ms_identidad.perfiles.service;

/**
 * Otro jugador ya usa ese apodo (sin distinguir mayusculas, igual que el
 * registro). Es una {@link IllegalArgumentException} por compatibilidad con
 * quien ya la capturaba; el controlador del perfil la distingue para su
 * problem details (RFINAL-03).
 */
public class ApodoEnUsoException extends IllegalArgumentException {

    public ApodoEnUsoException() {
        super("El apodo ya está en uso.");
    }
}
