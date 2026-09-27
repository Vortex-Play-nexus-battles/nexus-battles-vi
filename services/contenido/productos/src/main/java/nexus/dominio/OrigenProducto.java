package nexus.dominio;

/**
 * De donde salio un producto del catalogo — B4.
 *
 * <p>Lo necesita la semilla versionada: un producto {@link #SEMILLA} que nadie
 * ha editado se puede poner al dia cuando cambia la version de la semilla; uno
 * {@link #ADMINISTRACION} (dado de alta por un administrador) no es asunto de la
 * semilla. Los documentos anteriores a B4 no tienen origen: la semilla los
 * reconoce por su identificador, que deriva del slug del catalogo inicial.
 */
public enum OrigenProducto {
        SEMILLA,
        ADMINISTRACION
}
