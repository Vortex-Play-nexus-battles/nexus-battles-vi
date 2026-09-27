package com.nexusbattles.ms_identidad.auth.correo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code POST /correos/confirmacion-cuenta} (correo.yaml 1.4.0).
 *
 * <p>{@code proposito} decide a donde lleva el enlace del correo:
 * {@value #VERIFICACION} (autorregistro, a {@code /verificar}) o
 * {@value #ACTIVACION} (cuenta creada por un Super Administrador, a
 * {@code /restablecer} para fijar su contrasena).
 *
 * <p>Sin {@code toString}: lleva el codigo en claro y no debe acabar en
 * ninguna linea de bitacora.
 */
@Getter
@AllArgsConstructor
public class CorreoConfirmacionCuentaRequest {

    public static final String VERIFICACION = "VERIFICACION";
    public static final String ACTIVACION = "ACTIVACION";

    private String email;
    private String apodo;
    private String codigo;
    private int minutosVigencia;
    private String proposito;
}
