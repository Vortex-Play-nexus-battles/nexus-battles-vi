package nexus.recursos;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un recurso gráfico del catálogo (colección {@code recursos_graficos},
 * HU-PRD-009): una imagen, un icono o una animación ya examinados, con sus
 * bytes.
 *
 * <p><b>Nunca se borra.</b> Retirarlo lo saca de la biblioteca
 * ({@code retiradoEn}), pero sigue sirviéndose: una versión anterior del
 * diseño de un producto (HU-PRD-011) o un respaldo que se restaure
 * (HU-PRD-007) pueden apuntar a él, y restaurarlos no debe dejar una imagen
 * rota.
 *
 * <p>Los bytes van en el propio documento (BSON Binary): el mayor recurso
 * admitido son unos pocos megas, muy lejos de los 16 MB de un documento, y así
 * un recurso es una sola lectura sin GridFS. Los listados los excluyen
 * ({@link GestionarRecursosServicio}). La colección es pequeña (decenas de
 * recursos por catálogo), así que no lleva índices propios.
 *
 * @param huella SHA-256 en hexadecimal: la ETag con la que se sirve y la que
 *               evita guardar dos veces el mismo archivo para el mismo producto
 * @param productoId el producto al que se asoció al subirlo; nulo si se subió
 *                   a la biblioteca general
 * @param nombre el nombre del archivo, saneado; solo para reconocerlo en la
 *               biblioteca (nunca decide el tipo)
 */
@Document(collection = "recursos_graficos")
public record RecursoGrafico(
        @Id String id,
        ClaseDeRecurso clase,
        FormatoDeRecurso formato,
        int bytes,
        int ancho,
        int alto,
        String huella,
        byte[] contenido,
        String productoId,
        String nombre,
        String subidoPor,
        Instant subidoEn,
        Instant retiradoEn,
        String retiradoPor,
        String motivoRetiro) {

    /** La ruta pública con la que se sirve y la que se guarda en {@code Producto.imagen}. */
    public static final String RUTA = "/api/v1/productos/recursos/";

    public String url() {
        return RUTA + id;
    }

    public boolean retirado() {
        return retiradoEn != null;
    }

    /** El mismo recurso, retirado por alguien, cuándo y por qué. */
    public RecursoGrafico retirar(String quien, Instant cuando, String motivo) {
        return new RecursoGrafico(id, clase, formato, bytes, ancho, alto, huella, contenido, productoId,
                nombre, subidoPor, subidoEn, cuando, quien, motivo);
    }

    /** Un record con arreglo: igualdad y hash por identificador, texto sin los bytes. */
    @Override
    public boolean equals(Object otro) {
        return otro instanceof RecursoGrafico r && java.util.Objects.equals(id, r.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "RecursoGrafico[id=" + id + ", clase=" + clase + ", formato=" + formato + ", bytes=" + bytes + "]";
    }
}
