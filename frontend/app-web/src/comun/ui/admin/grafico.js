/**
 * Gráfico de barras de la consola — SA-2.
 *
 * El informe de revisión pidió que los registros por día dejaran de ser una
 * lista básica (punto 3) y que las métricas fueran un tablero de verdad
 * (punto 23). Este gráfico tiene lo que tiene un gráfico profesional:
 *
 *   - eje vertical con marcas redondas (0, 2, 4, 6…), no la cifra máxima a pelo;
 *   - líneas guía, y las etiquetas del eje horizontal aclaradas cuando hay
 *     muchas barras para que no se pisen;
 *   - el valor de cada barra al pasar el ratón;
 *   - una línea de resumen (total y máximo) que se lee sin mirar las barras;
 *   - **los mismos datos como tabla** («Ver los datos»), que es lo que lee un
 *     lector de pantalla y lo que se copia: las barras se ocultan a la
 *     tecnología de asistencia porque la tabla ya dice lo mismo.
 *
 * Es HTML y CSS, no un lienzo: el texto se ve nítido en cualquier anchura y
 * respeta el zoom del navegador.
 *
 * @module comun/ui/admin/grafico
 */

import { h } from '../dom.js';
import { numero } from '../formato.js';
import { tablaDeDatos } from './tabla-de-datos.js';

/**
 * Tope y paso «redondos» para un eje que empieza en cero: 1, 2, 2,5 o 5 por
 * una potencia de diez.
 *
 * @param {number} maximo el valor más alto
 * @param {number} [marcas] cuántas divisiones, aproximadamente
 * @returns {{tope: number, paso: number}}
 */
export function escalaAgradable(maximo, marcas = 4) {
  const alto = Number(maximo);
  if (!Number.isFinite(alto) || alto <= 0) {
    return { tope: marcas, paso: 1 };
  }
  const bruto = alto / marcas;
  const potencia = 10 ** Math.floor(Math.log10(bruto));
  const relativo = bruto / potencia;
  // El primer escalón «redondo» que cubre el paso bruto.
  const factor = [1, 2, 2.5, 5, 10].find((escalon) => relativo <= escalon) ?? 10;
  let paso = factor * potencia;
  // En cifras enteras (personas, sanciones) no hay medias marcas.
  if (Number.isInteger(alto) && paso < 1) {
    paso = 1;
  }
  return { tope: Math.ceil(alto / paso) * paso, paso };
}

/**
 * La etiqueta del eje de una barra: solo una de cada `salto` lleva texto, y de
 * esas, una de cada tres se queda también en un teléfono (`data-movil`), donde
 * diez fechas seguidas se pisarían.
 *
 * @param {string} texto
 * @param {number} indice
 * @param {number} salto
 * @returns {HTMLElement}
 */
function etiquetaDelEje(texto, indice, salto) {
  const visible = indice % salto === 0;
  const marca = indice / salto;
  return h('span', {
    clase: 'grafico-admin__etiqueta',
    texto: visible ? texto : '',
    datos: visible && marca % 3 === 0 ? { movil: 'si' } : {},
  });
}

/**
 * Hacia dónde se abre el globo de una barra: las del primer cuarto, hacia la
 * derecha, y las del último, hacia la izquierda. Centrado, el de una barra del
 * borde se saldría del gráfico (y en un teléfono, de la pantalla).
 *
 * @param {number} indice
 * @param {number} total
 * @returns {{lado?: 'inicio'|'fin'}}
 */
function ladoDelGlobo(indice, total) {
  if (indice < total / 4) {
    return { lado: 'inicio' };
  }
  if (indice >= (total * 3) / 4) {
    return { lado: 'fin' };
  }
  return {};
}

/**
 * @param {object} opciones
 * @param {string} opciones.titulo lo que se representa
 * @param {Array<{etiqueta: string, valor: number|null, detalle?: string}>} opciones.datos
 * @param {string} [opciones.unidad] cómo se llama cada cosa contada, en plural
 * @param {(valor: number) => string} [opciones.formatoValor]
 * @param {number} [opciones.maximoEtiquetas] cuántas etiquetas caben en el eje
 * @param {string} [opciones.columnaEtiqueta] encabezado de la tabla de datos
 * @returns {HTMLElement}
 */
export function graficoDeBarras({
  titulo,
  datos,
  unidad = '',
  formatoValor = (valor) => numero(valor),
  maximoEtiquetas = 10,
  columnaEtiqueta = 'Periodo',
}) {
  const lista = (Array.isArray(datos) ? datos : []).filter((d) => d && d.etiqueta !== undefined);
  const figura = h('figure', { clase: 'grafico-admin', datos: { zona: 'grafico' } });
  figura.append(h('figcaption', { clase: 'grafico-admin__titulo', texto: titulo }));

  const conValor = lista.filter((d) => Number.isFinite(d.valor));
  if (conValor.length === 0) {
    figura.append(h('p', { clase: 'grafico-admin__resumen', texto: 'Sin datos en este periodo.' }));
    return figura;
  }

  const maximo = Math.max(...conValor.map((d) => d.valor));
  const total = conValor.reduce((suma, d) => suma + d.valor, 0);
  const { tope, paso } = escalaAgradable(maximo);
  const salto = Math.max(1, Math.ceil(lista.length / Math.max(1, maximoEtiquetas)));

  const area = h('div', {
    clase: 'grafico-admin__area',
    // Las barras repiten la tabla de abajo: el lector de pantalla lee la tabla.
    atributos: { 'aria-hidden': 'true' },
  });
  const eje = h('div', { clase: 'grafico-admin__eje-y' });
  for (let marca = tope; marca >= 0; marca -= paso) {
    eje.append(h('span', { texto: formatoValor(Number(marca.toFixed(6))) }));
  }
  const columnas = h('div', { clase: 'grafico-admin__columnas' });
  // Por el CSSOM y no con un atributo `style`: lo admite cualquier política
  // de contenido.
  columnas.style.setProperty('--grafico-marcas', String(Math.round(tope / paso)));
  lista.forEach((dato, indice) => {
    const valido = Number.isFinite(dato.valor);
    const proporcion = valido && tope > 0 ? Math.max(0, dato.valor) / tope : 0;
    const columna = h('div', {
      clase: 'grafico-admin__columna',
      datos: { valor: valido ? dato.valor : 'sin-dato', ...ladoDelGlobo(indice, lista.length) },
    });
    const barra = h('span', {
      clase: valido
        ? 'grafico-admin__barra'
        : 'grafico-admin__barra grafico-admin__barra--sin-dato',
    });
    barra.style.setProperty('--grafico-alto', `${(proporcion * 100).toFixed(2)}%`);
    columna.append(
      barra,
      h('span', {
        clase: 'grafico-admin__globo',
        texto: `${dato.etiqueta}: ${valido ? formatoValor(dato.valor) : 'sin dato'}${
          unidad && valido ? ` ${unidad}` : ''
        }`,
      }),
      etiquetaDelEje(dato.etiqueta, indice, salto),
    );
    columnas.append(columna);
  });
  area.append(eje, columnas);
  figura.append(area);

  const masAlto = conValor.find((d) => d.valor === maximo);
  figura.append(
    h('p', {
      clase: 'grafico-admin__resumen',
      texto: `${formatoValor(total)}${unidad ? ` ${unidad}` : ''} en total · máximo ${formatoValor(
        maximo,
      )} (${masAlto.etiqueta})`,
    }),
  );

  const detalle = h('details', { clase: 'grafico-admin__datos' });
  detalle.append(
    h('summary', { texto: 'Ver los datos' }),
    tablaDeDatos({
      titulo,
      columnas: [
        { clave: 'etiqueta', titulo: columnaEtiqueta, principal: true },
        {
          clave: 'valor',
          titulo: unidad ? unidad.charAt(0).toUpperCase() + unidad.slice(1) : 'Valor',
          tipo: 'numero',
          valor: (fila) => (Number.isFinite(fila.valor) ? formatoValor(fila.valor) : null),
        },
      ],
      filas: lista,
    }),
  );
  figura.append(detalle);
  return figura;
}
