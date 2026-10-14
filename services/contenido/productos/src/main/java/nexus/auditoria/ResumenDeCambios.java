package nexus.auditoria;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import nexus.dominio.Banner;
import nexus.dominio.EstadoProducto;
import nexus.dominio.ParteArmadura;
import nexus.dominio.Producto;
import nexus.dominio.Promocion;
import nexus.dominio.TipoProducto;
import nexus.recursos.RecursoGrafico;

/**
 * Los valores anteriores y nuevos de una operación del catálogo, escritos para
 * quien lee la bitácora — SA-18, §7.3.5 («valores anteriores y nuevos, en caso
 * de modificaciones»).
 *
 * <p>De un producto solo se nombran los campos que cambiaron, con su etiqueta
 * y su valor en palabras ({@code Precio en créditos: 900 · Poder de ataque:
 * 12}); un alta lleva lo que lo define (tipo, estado, tiraje y precio). Los
 * textos largos se recortan: el valor completo sigue en el historial del
 * producto.
 */
final class ResumenDeCambios {

        /** Separa los campos de un resumen. */
        static final String SEPARADOR = " · ";

        /** Hasta dónde se copia un texto largo (descripción, efectos, contenido de un banner). */
        static final int LARGO_DE_TEXTO = 160;

        private static final DateTimeFormatter FECHA =
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

        /** Los valores de un cambio: los de antes y los de después (cualquiera puede faltar). */
        record Cambio(String antes, String despues) {
        }

        private record Campo(String etiqueta, Function<Producto, Object> valor) {
        }

        /** Los campos que mueven el balance del combate, como los de las alertas del catálogo. */
        private static final List<Campo> CAMPOS_DE_BALANCE = List.of(
                        new Campo("Costo de poder", Producto::costoPoder),
                        new Campo("Multiplicador por nivel", Producto::multiplicadorNivel),
                        new Campo("Turnos de carga", Producto::turnosCarga),
                        new Campo("Turnos de recarga", Producto::turnosRecarga),
                        new Campo("Efecto general", Producto::efectoGeneral),
                        new Campo("Efecto potenciado", Producto::efectoPotenciado),
                        new Campo("Defensa", Producto::defensa),
                        new Campo("Efecto", Producto::efecto),
                        new Campo("Poder de ataque", Producto::poderDeAtaque),
                        new Campo("Tasa de caída", Producto::tasaDeCaida));

        private static final List<Campo> CAMPOS = campos();

        private ResumenDeCambios() {
        }

        private static List<Campo> campos() {
                List<Campo> todos = new ArrayList<>(List.of(
                                new Campo("Nombre", Producto::nombre),
                                new Campo("Descripción", Producto::descripcion),
                                new Campo("Imagen", Producto::imagen),
                                new Campo("Tipo", Producto::tipo),
                                new Campo("Tiraje", producto -> tiraje(producto.tiraje())),
                                new Campo("Precio en créditos", Producto::precioCreditos),
                                new Campo("Precio en moneda real", Producto::precioMonedaReal),
                                new Campo("Premium", Producto::premium),
                                new Campo("Prototipo", Producto::prototipo),
                                new Campo("Héroe", Producto::heroe),
                                new Campo("Parte de la armadura", Producto::parte)));
                todos.addAll(CAMPOS_DE_BALANCE);
                todos.add(new Campo("Estado", Producto::estado));
                todos.add(new Campo("Promoción", Producto::promocion));
                return List.copyOf(todos);
        }

        /** Lo que define un producto recién dado de alta. */
        static String altaDe(Producto producto) {
                if (producto == null) {
                        return null;
                }
                List<String> partes = new ArrayList<>();
                partes.add("Tipo: " + legible(producto.tipo()));
                partes.add("Estado: " + legible(producto.estado()));
                partes.add("Tiraje: " + legible(tiraje(producto.tiraje())));
                if (producto.precioCreditos() != null) {
                        partes.add("Precio en créditos: " + legible(producto.precioCreditos()));
                }
                if (producto.precioMonedaReal() != null) {
                        partes.add("Precio en moneda real: " + legible(producto.precioMonedaReal()));
                }
                if (producto.premium()) {
                        partes.add("Premium: sí");
                }
                return String.join(SEPARADOR, partes);
        }

        /** Los campos que cambiaron entre dos estados del producto, con su valor antes y después. */
        static Cambio entre(Producto antes, Producto despues) {
                if (antes == null) {
                        return new Cambio(null, altaDe(despues));
                }
                if (despues == null) {
                        return new Cambio(null, null);
                }
                List<String> anteriores = new ArrayList<>();
                List<String> nuevos = new ArrayList<>();
                for (Campo campo : CAMPOS) {
                        Object valorAntes = campo.valor().apply(antes);
                        Object valorDespues = campo.valor().apply(despues);
                        if (!iguales(valorAntes, valorDespues)) {
                                anteriores.add(campo.etiqueta() + ": " + legible(valorAntes));
                                nuevos.add(campo.etiqueta() + ": " + legible(valorDespues));
                        }
                }
                if (anteriores.isEmpty()) {
                        return new Cambio(null, null);
                }
                return new Cambio(String.join(SEPARADOR, anteriores), String.join(SEPARADOR, nuevos));
        }

        /** Si el cambio tocó algún atributo de combate (lo que el catálogo avisa como cambio de balance). */
        static boolean tocaElBalance(Producto antes, Producto despues) {
                if (antes == null || despues == null) {
                        return false;
                }
                return CAMPOS_DE_BALANCE.stream()
                                .anyMatch(campo -> !iguales(campo.valor().apply(antes), campo.valor().apply(despues)));
        }

        /** Un banner: su texto y su periodo de publicación, y si está retirado. */
        static String deBanner(Banner banner) {
                if (banner == null) {
                        return null;
                }
                String texto = "«" + recortado(banner.contenido()) + "»"
                                + SEPARADOR + "Publicación: " + fecha(banner.publicarDesde())
                                + SEPARADOR + "Vigente hasta: " + fecha(banner.vigenteHasta());
                return banner.retirado() ? texto + SEPARADOR + "Retirado" : texto;
        }

        /** Un recurso gráfico: qué es, su formato y su tamaño. */
        static String deRecurso(RecursoGrafico recurso) {
                if (recurso == null) {
                        return null;
                }
                List<String> partes = new ArrayList<>();
                partes.add("Clase: " + (recurso.clase() == null ? "—" : recurso.clase().nombre()));
                partes.add("Formato: " + (recurso.formato() == null ? "—" : recurso.formato().nombre()));
                partes.add("Dimensiones: " + recurso.ancho() + "×" + recurso.alto() + " px");
                partes.add("Peso: " + recurso.bytes() + " bytes");
                if (recurso.productoId() != null) {
                        partes.add("Producto: " + recurso.productoId());
                }
                if (recurso.retirado()) {
                        partes.add("Retirado");
                }
                return String.join(SEPARADOR, partes);
        }

        /** Un texto largo hasta {@value #LARGO_DE_TEXTO} caracteres, con puntos suspensivos si se corta. */
        static String recortado(String texto) {
                if (texto == null) {
                        return null;
                }
                String limpio = texto.strip().replaceAll("\\s+", " ");
                return limpio.length() <= LARGO_DE_TEXTO
                                ? limpio
                                : limpio.substring(0, LARGO_DE_TEXTO - 1).stripTrailing() + "…";
        }

        private static boolean iguales(Object uno, Object otro) {
                if (uno instanceof BigDecimal a && otro instanceof BigDecimal b) {
                        return a.compareTo(b) == 0;
                }
                return Objects.equals(uno, otro);
        }

        private static Object tiraje(int tiraje) {
                return tiraje < 0 ? "ilimitado" : tiraje;
        }

        private static String fecha(Instant instante) {
                return instante == null ? "—" : FECHA.format(instante);
        }

        static String legible(Object valor) {
                return switch (valor) {
                        case null -> "—";
                        case Boolean verdad -> Boolean.TRUE.equals(verdad) ? "sí" : "no";
                        case BigDecimal numero -> numero.stripTrailingZeros().toPlainString();
                        case EstadoProducto estado -> switch (estado) {
                                case ACTIVO -> "Activo";
                                case UNICO -> "Pieza única";
                                case SUSPENDIDO -> "Suspendido";
                        };
                        case TipoProducto tipo -> switch (tipo) {
                                case HEROE -> "Héroe";
                                case HABILIDAD -> "Habilidad";
                                case ARMA -> "Arma";
                                case ARMADURA -> "Armadura";
                                case ITEM -> "Ítem";
                                case EPICA -> "Épica";
                        };
                        case ParteArmadura parte -> switch (parte) {
                                case CASCO -> "Casco";
                                case PECHO -> "Pecho";
                                case GUANTES -> "Guantes";
                                case BRAZALETES -> "Brazaletes";
                                case PANTALON -> "Pantalón";
                                case ZAPATOS -> "Zapatos";
                        };
                        case Promocion promocion -> promocion.porcentaje() + " % del " + fecha(promocion.desde())
                                        + " al " + fecha(promocion.hasta());
                        case String texto -> recortado(texto);
                        case Enum<?> otro -> otro.name().charAt(0) + otro.name().substring(1).toLowerCase();
                        default -> String.valueOf(valor);
                };
        }
}
