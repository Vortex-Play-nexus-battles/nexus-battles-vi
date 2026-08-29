package com.nexusbattles.ms_identidad.rbac.model;

public enum Action {
    CREAR_CUENTA_JUGADOR("Crear cuenta de jugador"),
    MODIFICAR_PERFIL_PROPIO("Modificar perfil propio"),
    PUBLICAR_COMENTARIOS("Publicar comentarios"),
    ELIMINAR_COMENTARIO_PROPIO("Eliminar comentario propio"),
    MODERAR_COMENTARIOS("Moderar comentarios ajenos"),
    EMITIR_ADVERTENCIAS("Emitir advertencias"),
    SUSPENDER_USUARIOS("Suspender usuarios"),
    BANEAR_DEFINITIVAMENTE("Banear definitivamente"),
    CREAR_ADMIN_MODERADOR("Crear cuentas Administrador/Moderador"),
    GESTIONAR_PRODUCTOS("Gestionar productos de la tienda"),
    ASIGNAR_ROL("Asignar un rol a un usuario existente"),
    GESTIONAR_CUENTAS("Gestionar cuentas de otros usuarios");

    private final String description;

    Action(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
