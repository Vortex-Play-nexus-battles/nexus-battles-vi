package nexus.recursos;

import java.util.EnumSet;
import java.util.Set;

/**
 * Las tres clases de recurso gráfico de §7.2.3 («imágenes, iconos,
 * animaciones») y HU-PRD-009, con los formatos que admite cada una.
 *
 * <p>El peso y las dimensiones máximas no están aquí: son configuración
 * ({@link LimitesDeRecursos}, regla 10), porque el documento del curso no los
 * fija y un despliegue puede necesitar otros.
 */
public enum ClaseDeRecurso {

    /** La imagen de un producto: fija, en PNG, JPEG o WebP. */
    IMAGEN("Imagen", "una imagen", EnumSet.of(FormatoDeRecurso.PNG, FormatoDeRecurso.JPEG, FormatoDeRecurso.WEBP)),

    /** Un icono: pequeño y con transparencia posible, en PNG o WebP. */
    ICONO("Icono", "un icono", EnumSet.of(FormatoDeRecurso.PNG, FormatoDeRecurso.WEBP)),

    /** Una animación: GIF, PNG animado (APNG) o WebP animado. */
    ANIMACION("Animación", "una animación",
            EnumSet.of(FormatoDeRecurso.GIF, FormatoDeRecurso.PNG, FormatoDeRecurso.WEBP));

    private final String nombre;
    private final String conArticulo;
    private final Set<FormatoDeRecurso> formatos;

    ClaseDeRecurso(String nombre, String conArticulo, Set<FormatoDeRecurso> formatos) {
        this.nombre = nombre;
        this.conArticulo = conArticulo;
        this.formatos = formatos;
    }

    /** «Imagen», «Icono», «Animación». */
    public String nombre() {
        return nombre;
    }

    /** «una imagen», «un icono», «una animación», para los mensajes. */
    public String conArticulo() {
        return conArticulo;
    }

    /** Los formatos que admite, en el orden del enumerado. */
    public Set<FormatoDeRecurso> formatos() {
        return EnumSet.copyOf(formatos);
    }

    /** ¿Admite este formato? (Para ANIMACION, PNG y WebP además tienen que estar animados.) */
    public boolean admite(FormatoDeRecurso formato) {
        return formatos.contains(formato);
    }

    /** «PNG, JPEG o WebP», para decirle a quien sube el archivo qué se admite. */
    public String formatosEnPalabras() {
        if (this == ANIMACION) {
            return "GIF, PNG animado (APNG) o WebP animado";
        }
        String[] nombres = formatos.stream().map(FormatoDeRecurso::nombre).toArray(String[]::new);
        if (nombres.length == 1) {
            return nombres[0];
        }
        return String.join(", ", java.util.Arrays.copyOf(nombres, nombres.length - 1))
                + " o " + nombres[nombres.length - 1];
    }
}
