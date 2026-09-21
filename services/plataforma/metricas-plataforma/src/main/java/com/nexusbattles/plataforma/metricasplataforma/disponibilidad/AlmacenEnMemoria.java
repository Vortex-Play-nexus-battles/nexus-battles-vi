package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Almacen que no sobrevive al proceso. Para las pruebas de dominio y como
 * respaldo explicito cuando no hay base de datos (nunca en el host: alli
 * el bean es {@link AlmacenEnPostgres}).
 */
public final class AlmacenEnMemoria implements AlmacenDeDisponibilidad {

    private final List<Interrupcion> interrupciones = new ArrayList<>();
    private final List<VentanaDeMantenimiento> ventanas = new ArrayList<>();
    private long siguienteId = 1;

    @Override
    public long abrir(Interrupcion interrupcion) {
        interrupciones.add(interrupcion);
        return siguienteId++;
    }

    @Override
    public void cerrar(long id, Instant fin) {
        // La instancia que guarda el registro es la misma que cierra: nada que copiar.
    }

    @Override
    public void guardarVentana(VentanaDeMantenimiento ventana) {
        ventanas.add(ventana);
    }

    @Override
    public List<Interrupcion> interrupciones() {
        return List.copyOf(interrupciones);
    }

    @Override
    public List<VentanaDeMantenimiento> ventanas() {
        return List.copyOf(ventanas);
    }
}
