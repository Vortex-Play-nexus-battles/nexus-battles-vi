package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.util.Optional;
import java.util.UUID;

/**
 * Si una cuenta existe y puede recibir mensajes — puerto hacia ms-identidad
 * ({@code GET /api/v1/internal/usuarios/{uid}/contacto},
 * {@code contracts/openapi/ms-identidad-admin.yaml}).
 *
 * <p>Este servicio no guarda cuentas ni apodos de nadie (regla 7): pregunta.
 * De la respuesta solo le interesan el apodo y el estado; el correo, que el
 * contrato tambien trae, ni se lee.
 */
public interface DirectorioDeJugadores {

    /**
     * @return vacio si no existe una cuenta con ese uid
     * @throws DirectorioNoDisponible si no se pudo preguntar; nunca se devuelve
     *     «no existe» por no haber podido preguntar
     */
    Optional<CuentaDeJugador> buscar(UUID uid);

    /**
     * @param uid    identificador estable
     * @param apodo  nombre visible
     * @param estado el de la cuenta en ms-identidad ({@code ACTIVO},
     *               {@code PENDIENTE_VERIFICACION}, {@code SUSPENDIDO}...)
     */
    record CuentaDeJugador(UUID uid, String apodo, String estado) {

        public static final String ACTIVO = "ACTIVO";

        /** Solo una cuenta activa recibe: las demas no pueden ni iniciar sesion. */
        public boolean activa() {
            return ACTIVO.equals(estado);
        }
    }

    /** ms-identidad no contesto, o contesto algo que no sirve. */
    class DirectorioNoDisponible extends RuntimeException {

        public DirectorioNoDisponible(String motivo) {
            super(motivo);
        }
    }
}
