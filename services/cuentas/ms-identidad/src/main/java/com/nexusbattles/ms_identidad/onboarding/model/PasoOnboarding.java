package com.nexusbattles.ms_identidad.onboarding.model;

/**
 * Los pasos del alta, en el orden en que se ejecutan. Cada uno lo resuelve el
 * servicio dueno de ese dato; identidad solo coordina y apunta el resultado.
 *
 * <ul>
 *   <li>{@link #PERFIL}: identidad. Se hace en la misma transaccion del registro.</li>
 *   <li>{@link #CREDITOS}: ms-finanzas, {@code POST /creditos/acreditar} con clave
 *       idempotente por jugador.</li>
 *   <li>{@link #HEROE}: inventario; el heroe es un producto del catalogo de tipo HEROE.</li>
 *   <li>{@link #EQUIPO}: inventario; el equipo inicial queda equipado en el primer
 *       heroe, porque sin equipo la puerta de heroe (HU-SAL-003) no deja jugar.
 *       Depende de {@link #HEROE}.</li>
 * </ul>
 *
 * <p>No hay paso de misiones ni de progresion: esos servicios no existen todavia
 * (services/contenido/misiones y progresion-jugador estan vacios). Inventarlos
 * aqui seria fabricar un estado que nadie guarda.
 */
public enum PasoOnboarding {
    PERFIL,
    CREDITOS,
    HEROE,
    EQUIPO
}
