package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * {@code POST /lista-negra/verificar} — moderacion-lista-negra.yaml 2.0.x.
 *
 * <p>Normaliza el texto con la misma funcion que los terminos, busca los
 * terminos activos (de la cache, o de PostgreSQL si Redis no esta) y aplica la
 * politica del contexto. El detalle —que termino coincidio y su categoria—
 * solo sale si quien llama puede verlo (token de servicio o de moderacion):
 * sin eso la respuesta seria un oraculo para recorrer la lista termino a
 * termino desde el formulario de registro.
 */
@Service
public class VerificacionListaNegraService {

    /** {@code maxLength} del contrato. */
    public static final int LONGITUD_MAXIMA = 2000;

    private final CatalogoDeTerminosActivos catalogo;
    private final PoliticaDeModeracion politica;

    public VerificacionListaNegraService(CatalogoDeTerminosActivos catalogo, PoliticaDeModeracion politica) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.politica = Objects.requireNonNull(politica);
    }

    /**
     * @param conDetalle si la respuesta puede llevar la categoria y los terminos
     * @throws IllegalArgumentException texto vacio o de mas de {@value #LONGITUD_MAXIMA} caracteres
     */
    public ResultadoVerificacion verificar(String texto, ContextoDeTexto contexto, boolean conDetalle) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El texto a verificar no puede estar vacío");
        }
        if (texto.length() > LONGITUD_MAXIMA) {
            throw new IllegalArgumentException(
                    "El texto a verificar no puede superar los " + LONGITUD_MAXIMA + " caracteres");
        }
        List<TerminoActivo> encontrados =
                DetectorDeTerminos.coincidencias(NormalizadorDeTexto.normalizar(texto), catalogo.activos());
        if (encontrados.isEmpty()) {
            return ResultadoVerificacion.APROBADO;
        }
        AccionDeModeracion accion = politica.siCoincide(contexto);
        String motivo = politica.motivo(contexto);
        if (!conDetalle) {
            return new ResultadoVerificacion(false, accion, motivo, null, null);
        }
        return new ResultadoVerificacion(false, accion, motivo, principal(encontrados).categoria(),
                encontrados.stream().map(TerminoActivo::termino).sorted().toList());
    }

    /**
     * La categoria que se informa cuando coinciden varios: la del termino mas
     * largo, que es el mas especifico («spiderman» antes que un termino corto
     * que tambien aparezca). Con empate, el primero en el orden del catalogo.
     */
    static TerminoActivo principal(List<TerminoActivo> encontrados) {
        TerminoActivo principal = encontrados.get(0);
        for (TerminoActivo termino : encontrados) {
            if (termino.normalizado().length() > principal.normalizado().length()) {
                principal = termino;
            }
        }
        return principal;
    }

    /**
     * @param categoria    y {@code coincidencias}: solo con detalle
     */
    public record ResultadoVerificacion(boolean aprobado, AccionDeModeracion accion, String motivo,
                                        CategoriaDeTermino categoria, List<String> coincidencias) {

        static final ResultadoVerificacion APROBADO =
                new ResultadoVerificacion(true, AccionDeModeracion.PERMITIR, null, null, null);
    }
}
