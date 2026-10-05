package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Consultas del directorio administrativo que se arman por partes — RFINAL-06.
 *
 * <p>El filtro de cuentas de pruebas ({@link CuentasDePrueba}) es una lista de
 * patrones de longitud variable, que JPQL no puede expresar con parametros:
 * por eso va como especificacion ({@link BusquedaDelDirectorio}). Es un
 * repositorio aparte, de solo lectura, para no tocar {@code UsuarioRepository},
 * que comparten el registro, el login y los perfiles.
 *
 * <p>Sin el filtro, el directorio sigue usando
 * {@code UsuarioRepository#buscarParaDirectorio}, la misma consulta de siempre.
 */
public interface DirectorioDeCuentas extends Repository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {
}
