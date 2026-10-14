package com.nexusbattles.ms_chatbot.chat.catalogo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 1.10.0 (7.4.5 «actualización automática de la base de conocimiento con nueva
 * información del sistema»): los productos que se venden hoy, leídos del
 * catálogo y no copiados a mano en un tema.
 *
 * <p>Es dato público (lo que ve cualquier visitante en la tienda), igual para
 * todos, así que se guarda una sola copia para todo el servicio durante
 * {@link #VIGENCIA}. Un producto nuevo, uno con otro precio o uno retirado se
 * ve en el chat como mucho tras ese tiempo, sin que nadie edite la base.
 *
 * <p>Si el catálogo no responde, se sigue usando la última copia mientras no
 * pase de {@link #VIGENCIA_SI_FALLA}; sin copia, vacío, y quien pregunta recibe
 * «no disponible» (o la respuesta de la base, según la consulta).
 *
 * <p>Las cifras son una convención técnica provisional: D-CHA-10.
 */
@Component
public class CatalogoEnVivo {

    private static final Logger log = LoggerFactory.getLogger(CatalogoEnVivo.class);

    static final Duration VIGENCIA = Duration.ofMinutes(5);
    static final Duration VIGENCIA_SI_FALLA = Duration.ofMinutes(30);
    static final int TAMANO_DE_PAGINA = 50;
    static final int MAXIMO_DE_PAGINAS = 10;

    private record Copia(List<ProductoDelCatalogoDto> productos, Instant leidaEn) {
    }

    private final CatalogoClient cliente;
    private final Clock reloj;
    private volatile Copia copia;

    public CatalogoEnVivo(CatalogoClient cliente, Clock reloj) {
        this.cliente = cliente;
        this.reloj = reloj;
    }

    /** Los productos a la venta, o vacío si el catálogo no respondió y no hay copia reciente. */
    public Optional<List<ProductoDelCatalogoDto>> productos() {
        Instant ahora = reloj.instant();
        Copia actual = copia;
        if (actual != null && actual.leidaEn().plus(VIGENCIA).isAfter(ahora)) {
            return Optional.of(actual.productos());
        }
        synchronized (this) {
            actual = copia;
            if (actual != null && actual.leidaEn().plus(VIGENCIA).isAfter(ahora)) {
                return Optional.of(actual.productos());
            }
            try {
                Copia nueva = new Copia(List.copyOf(leerTodo()), ahora);
                copia = nueva;
                return Optional.of(nueva.productos());
            } catch (RestClientException fallo) {
                log.warn("El catalogo de productos no respondio; se usa la ultima copia si es reciente: {}",
                    fallo.getClass().getSimpleName());
                if (actual != null && actual.leidaEn().plus(VIGENCIA_SI_FALLA).isAfter(ahora)) {
                    return Optional.of(actual.productos());
                }
                return Optional.empty();
            }
        }
    }

    private List<ProductoDelCatalogoDto> leerTodo() {
        List<ProductoDelCatalogoDto> todos = new ArrayList<>();
        for (int numero = 0; numero < MAXIMO_DE_PAGINAS; numero++) {
            PaginaDelCatalogoDto pagina = cliente.pagina(numero, TAMANO_DE_PAGINA);
            if (pagina == null || pagina.content() == null || pagina.content().isEmpty()) {
                break;
            }
            pagina.content().stream()
                .filter(p -> p != null && p.nombre() != null && !p.nombre().isBlank())
                .forEach(todos::add);
            if (numero + 1 >= pagina.totalPages()) {
                break;
            }
        }
        return todos;
    }
}
