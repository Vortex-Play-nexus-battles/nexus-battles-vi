package com.nexusbattles.ms_identidad.admin.inventario;

import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient.ElementoEntregado;
import com.nexusbattles.ms_identidad.admin.inventario.InventarioDeLaCuentaClient.Entrega;
import com.nexusbattles.ms_identidad.admin.service.JerarquiaInsuficienteException;
import com.nexusbattles.ms_identidad.admin.service.UsuarioNoEncontradoException;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.rbac.model.JerarquiaDeRoles;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * La entrega administrativa de productos al inventario de una cuenta — SA-10,
 * §7.3.4 «Acciones disponibles: ver y modificar inventario del usuario».
 *
 * <p><b>Quien.</b> Administrador o Super Administrador (permiso
 * {@code GESTIONAR_CUENTAS}, que comprueba el controlador) y nunca sobre una
 * cuenta de rol igual o superior (7.3.1): la misma regla que editar el perfil.
 *
 * <p><b>Orden.</b> Primero el inventario, que es idempotente por clave; despues
 * la bitacora central, fail-closed (§7.3.5): si ms-cumplimiento no responde,
 * 503 {@code auditoria-no-disponible} y el panel repite con la MISMA clave. La
 * repeticion no entrega dos veces (el inventario devuelve la entrega original)
 * y la entrada de auditoria lleva la clave como {@code referencia}, asi que
 * tampoco se duplica. Al revés —auditar antes de entregar— dejaria en la
 * bitacora entregas que nunca ocurrieron cuando el inventario dice que no.
 *
 * <p><b>Aviso.</b> La cuenta recibe una notificacion solo la primera vez
 * (fail-open: si notificaciones no responde, la entrega ya esta hecha).
 */
@Service
public class EntregaAdministrativaService {

    /** La clave de idempotencia que genera el panel: hasta 64 caracteres sin espacios. */
    private static final Pattern CLAVE = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    private final UsuarioRepository usuarios;
    private final InventarioDeLaCuentaClient inventario;
    private final AuditoriaClient auditoria;
    private final NotificacionClient notificaciones;

    public EntregaAdministrativaService(UsuarioRepository usuarios, InventarioDeLaCuentaClient inventario,
                                        AuditoriaClient auditoria, NotificacionClient notificaciones) {
        this.usuarios = usuarios;
        this.inventario = inventario;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
    }

    public EntregaAdministrativaResponse entregar(Long usuarioId, String clave, EntregaAdministrativaRequest datos,
                                                  String autorizacion, String administradorId, String rolActor,
                                                  String ipOrigen) {
        if (clave == null || !CLAVE.matcher(clave).matches()) {
            throw new IllegalArgumentException(
                    "Falta la clave de la operación (Idempotency-Key, de 8 a 64 caracteres sin espacios).");
        }
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> new UsuarioNoEncontradoException(usuarioId));
        String rolObjetivo = usuario.getRol() == null ? null : usuario.getRol().getNombre();
        if (!JerarquiaDeRoles.puedeGestionar(rolActor, rolObjetivo)) {
            throw new JerarquiaInsuficienteException(
                    "No puedes modificar el inventario de una cuenta con un rol igual o superior al tuyo.");
        }
        if (usuario.getPublicId() == null) {
            throw new CuentaSinInventarioException(usuarioId);
        }
        if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
            throw new EntregaRechazadaException(403,
                    "Para entregar hace falta una sesión con token: el inventario registra quién entrega.");
        }

        String productoId = datos.productoId().trim();
        String uid = usuario.getPublicId().toString();
        Entrega entrega = inventario.entregar(autorizacion, clave, usuario.getPublicId(), productoId,
                datos.cantidad());

        auditoria.registrar("ACTUALIZACION", administradorId, uid, null,
                "Inventario: " + resumen(entrega, productoId) + " (entrega " + entrega.id() + ")",
                datos.motivo().trim(), ipOrigen, "ms-identidad:entrega-administrativa:" + clave);

        if (entrega.nueva()) {
            notificaciones.emitir(uid, "CUENTA", "Recibiste productos en tu inventario",
                    "La administración agregó " + resumen(entrega, productoId) + " a tu inventario.");
        }
        return EntregaAdministrativaResponse.de(entrega, productoId);
    }

    /** «2 × Hacha de Obsidiana»: las unidades y el nombre que puso el catalogo. */
    static String resumen(Entrega entrega, String productoId) {
        List<ElementoEntregado> elementos = entrega.elementos() == null ? List.of() : entrega.elementos();
        String nombre = elementos.stream()
                .map(ElementoEntregado::nombrePropio)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(productoId);
        return elementos.size() + " × " + nombre;
    }
}
