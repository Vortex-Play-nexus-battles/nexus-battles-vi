package com.nexusbattles.ms_identidad.admin.ficha;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import com.nexusbattles.ms_identidad.sanciones.CuentaNoEncontradaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HU-USR-010 (RF-USR-010) — la ficha administrativa de una cuenta, con la
 * consulta registrada en la auditoria.
 *
 * <h2>Por que la lectura audita</h2>
 *
 * La ficha oficial lo dice como observacion: «el acceso a esta vista debe
 * quedar registrado en auditoria por tratarse de datos personales». Por eso
 * no es la lectura de siempre ({@code GET /admin/usuarios/{usuarioId}}, que
 * usa la gestion para editar): cada consulta deja en ms-cumplimiento quien
 * miro, a quien y desde donde.
 *
 * <h2>Por que una bitacora caida no tumba la ficha</h2>
 *
 * El criterio CA-03 de la historia pide lo contrario de fallar entero: si uno
 * de los modulos consultados no responde, esa parte se degrada y el resto
 * sigue. La bitacora es uno de esos modulos. Se intenta registrar (con los
 * reintentos y el cortacircuitos de {@link AuditoriaClient}); si no se puede,
 * la ficha sale con {@code accesoAuditado = false}, la vista lo dice y el
 * evento completo queda en la bitacora JSON del servicio (regla 6). Nunca en
 * silencio. Si el Product Owner prefiere negar la ficha cuando la auditoria
 * no la registra, el cambio es esta unica decision.
 */
@Service
public class FichaAdministrativaService {

    private static final Logger log = LoggerFactory.getLogger(FichaAdministrativaService.class);

    /** Tipo de la bitacora: el enumerado de ms-cumplimiento no tiene uno de consulta. */
    static final String TIPO = "OTRO";
    static final String MOTIVO = "Consulta de la ficha administrativa (datos personales)";
    /** ms-cumplimiento exige administrador e IP: un nulo seria un 503 y la consulta quedaria sin registrar. */
    static final String DESCONOCIDO = "DESCONOCIDO";
    static final String DESCONOCIDA = "DESCONOCIDA";

    /** La clave interna: un entero positivo como los que publica el directorio. */
    private static final Pattern CLAVE = Pattern.compile("[0-9]{1,18}");
    private static final int LARGO_UUID = 36;

    private final UsuarioRepository usuarios;
    private final PerfilUsuarioRepository perfiles;
    private final AuditoriaClient auditoria;
    private final Clock reloj;

    @Autowired
    public FichaAdministrativaService(UsuarioRepository usuarios, PerfilUsuarioRepository perfiles,
                                      AuditoriaClient auditoria) {
        this(usuarios, perfiles, auditoria, Clock.systemDefaultZone());
    }

    FichaAdministrativaService(UsuarioRepository usuarios, PerfilUsuarioRepository perfiles,
                               AuditoriaClient auditoria, Clock reloj) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * @param usuario       la clave interna de la cuenta o su {@code uid}
     * @param administrador quien consulta (el apodo que deja el interceptor)
     * @param ipOrigen      desde donde
     * @throws CuentaNoEncontradaException si no nombra a ninguna cuenta; entonces no se audita nada
     */
    public FichaAdministrativa consultar(String usuario, String administrador, String ipOrigen) {
        Usuario cuenta = resolver(usuario).orElseThrow(CuentaNoEncontradaException::new);
        PerfilUsuario perfil = perfiles.findByIdConUsuario(cuenta.getId()).orElse(null);
        boolean auditado = auditar(cuenta, administrador, ipOrigen);
        return FichaAdministrativa.desde(cuenta, perfil, auditado, LocalDateTime.now(reloj));
    }

    /**
     * Un valor que no es ni clave ni uid no nombra a nadie: vacio, igual que
     * una cuenta que no existe, para no distinguir «mal escrito» de «no existe».
     */
    private Optional<Usuario> resolver(String usuario) {
        if (usuario == null) {
            return Optional.empty();
        }
        String limpio = usuario.trim();
        if (CLAVE.matcher(limpio).matches()) {
            return usuarios.findById(Long.parseLong(limpio));
        }
        UUID uid = comoUuid(limpio);
        return uid == null ? Optional.empty() : usuarios.findByPublicId(uid);
    }

    private static UUID comoUuid(String valor) {
        if (valor.length() != LARGO_UUID) {
            return null;
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException malFormado) {
            return null;
        }
    }

    private boolean auditar(Usuario cuenta, String administrador, String ipOrigen) {
        String quien = administrador == null || administrador.isBlank() ? DESCONOCIDO : administrador;
        String desde = ipOrigen == null || ipOrigen.isBlank() ? DESCONOCIDA : ipOrigen;
        String afectado = String.valueOf(cuenta.getId());
        try {
            auditoria.registrar(TIPO, quien, afectado, null, null, MOTIVO, desde);
            return true;
        } catch (RuntimeException bitacoraNoDisponible) {
            log.warn("CONSULTA_FICHA_ADMINISTRATIVA sin registrar en ms-cumplimiento administrador={} afectado={}"
                    + " ip={} motivo={}", quien, afectado, desde, bitacoraNoDisponible.getMessage());
            return false;
        }
    }
}
