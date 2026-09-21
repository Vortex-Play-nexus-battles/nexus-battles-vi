package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Instant;
import java.util.List;

/**
 * Donde sobreviven las interrupciones y las ventanas de mantenimiento —
 * HU-DIS-001.
 *
 * <p>Lo que #441 dejó anotado: «registro en memoria (se pierde al
 * redesplegar)». Un informe de disponibilidad que se vacia con cada
 * despliegue no sirve para medir el 99,95 % del Charter, porque cada
 * despliegue es precisamente uno de los momentos en que algo puede caerse.
 * El registro sigue calculando en memoria (es dominio puro y asi se prueba),
 * pero cada interrupcion que abre o cierra, y cada ventana que programa, pasa
 * por aqui; al arrancar, se recarga lo guardado.
 *
 * <p>Las comprobaciones sueltas no se guardan: son una por servicio cada 30 s
 * y solo interesa la ultima («estado actual»). Lo que mide la disponibilidad
 * son las interrupciones.
 */
public interface AlmacenDeDisponibilidad {

    /** Guarda una interrupcion recien abierta y devuelve su identificador. */
    long abrir(Interrupcion interrupcion);

    /** Cierra la interrupcion con ese identificador en el instante dado. */
    void cerrar(long id, Instant fin);

    void guardarVentana(VentanaDeMantenimiento ventana);

    /** Todas las interrupciones guardadas, abiertas incluidas, en orden de inicio. */
    List<Interrupcion> interrupciones();

    List<VentanaDeMantenimiento> ventanas();
}
