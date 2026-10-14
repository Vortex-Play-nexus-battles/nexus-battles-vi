package com.nexusbattles.ms_identidad.admin.titulares;

import com.nexusbattles.ms_identidad.admin.directorio.ConsultaInvalidaException;
import com.nexusbattles.ms_identidad.admin.directorio.DirectorioDeCuentas;
import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Quien es cada cuenta de la consola de moderacion — SA-4 (ms-identidad-admin.yaml 1.9.0).
 *
 * <p>La consola de sanciones lista sanciones y apelaciones de
 * moderacion-sanciones, que nombran a la cuenta por su {@code uid}, y quien
 * emite una sancion tiene que encontrar antes la cuenta. El directorio
 * ({@code /admin/jugadores}) sirve para eso a la administracion, pero exige
 * gestionar cuentas, y el Moderador no gestiona cuentas: emite advertencias y
 * suspensiones temporales (Tabla 24). Sin esto, su pantalla solo podia pedir
 * un {@code uid} escrito a mano.
 *
 * <p>Aqui se responde lo minimo para reconocer a la persona
 * ({@link TitularDeCuentaResponse}: apodo, estado y rol, sin correo) a quien
 * puede emitir una advertencia, que son los tres roles de moderacion. Dos
 * formas, una por consulta:
 *
 * <ul>
 *   <li>{@code uids}: hasta {@value TitularesDelListado#MAXIMO}, para nombrar
 *       las filas de una pagina de otro servicio de una vez;</li>
 *   <li>{@code buscar}: un apodo (o parte, sin distinguir mayusculas) o un
 *       {@code uid} completo; las {@value #RESULTADOS_DE_BUSQUEDA} primeras por
 *       apodo.</li>
 * </ul>
 *
 * <p>Solo lee. Lo que no se puede aplicar es 400 {@code datos-invalidos} con
 * el motivo, nunca una lista vacia que pareceria un resultado.
 */
@RestController
@RequestMapping("/api/v1/admin/titulares")
public class AdminTitularesController {

    /** Cuantas cuentas devuelve una busqueda: para elegir una, no para recorrer el directorio. */
    static final int RESULTADOS_DE_BUSQUEDA = 10;

    /** Por debajo de esto una busqueda por apodo devolveria medio juego. */
    static final int MINIMO_DE_LA_BUSQUEDA = 2;

    private final DirectorioDeCuentas directorio;

    public AdminTitularesController(DirectorioDeCuentas directorio) {
        this.directorio = directorio;
    }

    /**
     * @param uids   hasta 100 {@code uid} separados por comas
     * @param buscar un apodo o parte de el, o un {@code uid} completo
     */
    @GetMapping
    @RequirePermission(Action.EMITIR_ADVERTENCIAS)
    public ResponseEntity<List<TitularDeCuentaResponse>> titulares(
            @RequestParam(name = "uids", required = false) String uids,
            @RequestParam(name = "buscar", required = false) String buscar) {

        boolean porUids = uids != null && !uids.isBlank();
        String texto = buscar == null ? "" : buscar.trim();
        if (porUids == !texto.isEmpty()) {
            throw new ConsultaInvalidaException(porUids
                    ? "Pide las cuentas por «uids» o búscalas con «buscar», no las dos cosas a la vez."
                    : "Indica las cuentas («uids») o lo que buscas («buscar»).");
        }

        List<Usuario> cuentas;
        if (porUids) {
            Set<UUID> titulares = TitularesDelListado.de(uids);
            cuentas = titulares.isEmpty()
                    ? List.of()
                    : directorio.findAll(TitularesDelListado.solo(titulares),
                            PageRequest.of(0, titulares.size(), Sort.by("apodo"))).getContent();
        } else {
            if (texto.length() < MINIMO_DE_LA_BUSQUEDA) {
                throw new ConsultaInvalidaException(
                        "Escribe al menos " + MINIMO_DE_LA_BUSQUEDA + " letras del apodo para buscar.");
            }
            cuentas = directorio.findAll(porApodoOUid(texto),
                    PageRequest.of(0, RESULTADOS_DE_BUSQUEDA, Sort.by("apodo"))).getContent();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(cuentas.stream().map(TitularDeCuentaResponse::desde).toList());
    }

    /**
     * El apodo contiene el texto (sin distinguir mayusculas) o, si el texto es
     * un {@code uid} completo, la cuenta que lo tiene.
     */
    static Specification<Usuario> porApodoOUid(String texto) {
        String patron = "%" + escaparComodines(texto.toLowerCase(Locale.ROOT)) + "%";
        UUID uid = uidSiLoEs(texto);
        return (raiz, consulta, cb) -> uid == null
                ? cb.like(cb.lower(raiz.get("apodo")), patron, '\\')
                : cb.or(cb.equal(raiz.get("publicId"), uid), cb.like(cb.lower(raiz.get("apodo")), patron, '\\'));
    }

    /** Un {@code %} o un {@code _} escritos se buscan tal cual, no como comodines. */
    static String escaparComodines(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static UUID uidSiLoEs(String texto) {
        if (texto.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException noLoEs) {
            return null;
        }
    }

    /** 400 {@code datos-invalidos}: la consulta no se puede aplicar, y se dice por que. */
    @ExceptionHandler(ConsultaInvalidaException.class)
    public ResponseEntity<ProblemDetail> consultaInvalida(ConsultaInvalidaException error,
                                                          HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.BAD_REQUEST, "datos-invalidos", "Consulta inválida",
                error.getMessage(), peticion.getRequestURI());
    }
}
