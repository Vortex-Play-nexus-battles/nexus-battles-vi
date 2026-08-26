package com.nexusbattles.ms_identidad.perfiles.model;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import jakarta.persistence.*;

@Entity
@Table(name = "perfiles_usuario")
public class PerfilUsuario {

    @Id
    private Long id; // comparte PK con Usuario (relación 1:1)

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String nombres;

    @Column(nullable = false)
    private String apellidos;

    private String avatar;

    @Column(columnDefinition = "TEXT")
    private String biografia;

    @Column(columnDefinition = "TEXT")
    private String preferencias; // JSON simple con las preferencias del usuario

    public PerfilUsuario() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getBiografia() { return biografia; }
    public void setBiografia(String biografia) { this.biografia = biografia; }

    public String getPreferencias() { return preferencias; }
    public void setPreferencias(String preferencias) { this.preferencias = preferencias; }
}