package com.nexusbattles.ms_identidad.auth.segundofactor;

/**
 * Rechazo de una operacion de segundo factor (HU-AUT-007), con el
 * {@code type} estable del contrato ({@code ms-identidad-auth.yaml} 2.2.0) y
 * su estado HTTP. Mismo patron que {@code RecuperacionRechazadaException}.
 *
 * <p>El mismo motivo puede salir con dos estados: un codigo incorrecto en el
 * segundo paso del login es un 401 (es la autenticacion lo que falla), y en
 * una operacion con sesion ya abierta es un 422 (la peticion esta bien, el
 * dato no): un 401 o un 403 ahi lo tomaria la interfaz por una sesion
 * caducada.
 */
public class SegundoFactorRechazadoException extends RuntimeException {

    public enum Motivo {
        /** Falta la clave de cifrado (IDENTIDAD_2FA_CLAVE), o el secreto guardado no se puede leer. */
        NO_DISPONIBLE("segundo-factor-no-disponible", "Segundo factor no disponible", 503),
        YA_ACTIVO("segundo-factor-ya-activo", "El segundo factor ya está activo", 409),
        NO_ACTIVO("segundo-factor-no-activo", "El segundo factor no está activo", 409),
        SIN_ENROLAMIENTO("sin-enrolamiento-pendiente", "No hay un enrolamiento pendiente", 409),
        /** Con sesion abierta: el dato es incorrecto. */
        CODIGO_INVALIDO("codigo-segundo-factor-invalido", "Código incorrecto", 422),
        /** En el segundo paso del login: es la autenticacion la que falla. */
        CODIGO_INVALIDO_EN_EL_ACCESO("codigo-segundo-factor-invalido", "Código incorrecto", 401),
        DESAFIO_INVALIDO("desafio-invalido", "La verificación caducó", 401),
        CONTRASENA_INCORRECTA("contrasena-actual-incorrecta", "La contraseña actual es incorrecta", 422),
        CUENTA_BLOQUEADA("cuenta-bloqueada", "Cuenta bloqueada temporalmente", 423),
        DATOS_INVALIDOS("datos-invalidos", "Datos inválidos", 400);

        private final String tipo;
        private final String titulo;
        private final int estado;

        Motivo(String tipo, String titulo, int estado) {
            this.tipo = tipo;
            this.titulo = titulo;
            this.estado = estado;
        }

        public String tipo() {
            return tipo;
        }

        public String titulo() {
            return titulo;
        }

        public int estado() {
            return estado;
        }
    }

    private final Motivo motivo;

    public SegundoFactorRechazadoException(Motivo motivo, String detalle) {
        super(detalle);
        this.motivo = motivo;
    }

    /**
     * El desafio del login no sirve (no existe, caduco, ya se canjeo, la cuenta
     * cambio de contrasena o de segundo factor entre los dos pasos). Un solo
     * mensaje para todos: distinguirlos no le sirve a quien entra y si a quien
     * prueba desafios.
     */
    public static SegundoFactorRechazadoException desafioInvalido() {
        return new SegundoFactorRechazadoException(Motivo.DESAFIO_INVALIDO,
                "Tu verificación caducó o ya no es válida. Vuelve a escribir tu contraseña para empezar de nuevo.");
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
