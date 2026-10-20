package nexus.persistencia;

import java.util.List;

import nexus.dominio.Producto;

/**
 * Las tres escrituras del catalogo que Spring Data no sabe derivar — B4.
 */
public interface ProductoRepositoryPersonalizado {

        /**
         * Reemplaza el producto solo si su version sigue siendo
         * {@code versionEsperada} y ningun administrador lo edito
         * ({@code modificadoPor} vacio). Es la puesta al dia de la semilla: si
         * alguien escribio entre la lectura y este reemplazo, no se pisa nada.
         * Una version esperada 0 incluye los documentos sin version.
         *
         * @param reemplazo el producto nuevo, con la version ya incrementada
         * @return si se reemplazo
         */
        boolean reemplazarSemillaSiNoCambio(Producto reemplazo, int versionEsperada);

        /**
         * Lleva a la version 1 un producto anterior a {@code @Version} (sin
         * version, o con 0). Con un primitivo, 0 significa "nuevo" para Spring
         * Data: la primera edicion de ese documento se tomaria por un alta y
         * chocaria con su propio identificador. Es condicional: si ya tiene
         * version, no hace nada.
         */
        void normalizarVersion(String id);

        /**
         * Suspende los productos cuya imagen es una ruta suelta — un nombre de
         * archivo sin carpeta ni esquema, como {@code espada.png}— y que, por
         * tanto, no tienen ninguna imagen que el sitio sirva. Es la misma
         * suspension de la administracion: el producto sale de la tienda y de
         * la vitrina, se conserva en los inventarios y puede reactivarse;
         * {@code estadoAnteriorSuspension} guarda el estado al que vuelve. Sube
         * la version en uno y no toca los que ya estan suspendidos ni los que
         * alguien escribio entre la lectura y la escritura.
         *
         * @return los nombres de los productos suspendidos
         */
        List<String> suspenderConImagenSuelta();
}
