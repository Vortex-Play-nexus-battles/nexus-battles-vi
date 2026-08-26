package com.nexusbattles.ms_identidad.rbac.model;

public enum Role {
    JUGADOR("Jugador", "Usuario estándar con acceso a funciones básicas de juego, comentarios propios y gestión de su perfil."),
    MODERADOR("Moderador", "Usuario con facultades de moderación de contenido, advertencias y suspensiones temporales."),
    ADMINISTRADOR("Administrador", "Usuario con control administrativo sobre usuarios, productos de tienda y sanciones definitivas."),
    SUPER_ADMINISTRADOR("Super Administrador", "Máxima autoridad del sistema con control total y capacidad de nombrar otros administradores.");

    private final String displayName;
    private final String description;

    Role(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
