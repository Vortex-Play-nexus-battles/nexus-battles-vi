package com.nexusbattles.ms_identidad.auth.exception;

import java.net.URI;

/**
 * Rechazo de un autorregistro (HU-AUT-001), con el motivo y el campo del
 * formulario al que se refiere.
 *
 * <p>El mensaje es el mismo texto que el registro devolvia antes (contrato
 * 1.0.0, texto plano): quien no pide problem details sigue recibiendo
 * exactamente lo mismo. Quien los pide ({@code Accept:
 * application/problem+json}) recibe ademas el {@code type} estable y el
 * {@code campo}, para marcar el campo exacto en la pantalla sin depender de
 * la redaccion del mensaje. Mismo patron que
 * {@link CambioDePasswordRechazadoException}.
 */
public class RegistroRechazadoException extends RuntimeException {

    public enum Motivo {
        CORREO_EN_USO("correo-en-uso", "El correo ya está registrado", "email"),
        APODO_EN_USO("apodo-en-uso", "El apodo ya está en uso", "apodo"),
        APODO_NO_PERMITIDO("apodo-no-permitido", "El apodo no está permitido", "apodo"),
        /** Mismo {@code type} que el rechazo por politica de HU-AUT-006. */
        CONTRASENA_DEBIL("contrasena-no-cumple-politica", "La contraseña no cumple la política", "password"),
        AVATAR_INVALIDO("avatar-invalido", "La imagen de avatar no es válida", "avatar"),
        DATOS_INVALIDOS("datos-de-registro-invalidos", "Datos de registro inválidos", null);

        private final String slug;
        private final String titulo;
        private final String campo;

        Motivo(String slug, String titulo, String campo) {
            this.slug = slug;
            this.titulo = titulo;
            this.campo = campo;
        }

        public URI tipo() {
            return URI.create("https://nexusbattles.upb.edu.co/errors/" + slug);
        }

        public String titulo() {
            return titulo;
        }

        public String campo() {
            return campo;
        }
    }

    private final Motivo motivo;
    private final String campo;

    public RegistroRechazadoException(Motivo motivo, String mensaje) {
        this(motivo, mensaje, motivo.campo());
    }

    public RegistroRechazadoException(Motivo motivo, String mensaje, String campo) {
        super(mensaje);
        this.motivo = motivo;
        this.campo = campo;
    }

    public Motivo getMotivo() {
        return motivo;
    }

    public String getCampo() {
        return campo;
    }
}
