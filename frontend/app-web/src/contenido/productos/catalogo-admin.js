/**
 * UXC-7 — El catálogo para administrarlo: la lista con sus filtros y la ficha
 * de gestión de cada producto (ProductAdminSheet).
 *
 * Hasta aquí la consola podía crear productos y contar cuántos había por tipo
 * y estado, pero no ver cuáles eran ni tocar ninguno: modificar (HU-PRD-003),
 * suspender y reactivar existían en el servicio sin una sola pantalla que los
 * usara. Todo sale de `contracts/openapi/productos.yaml`:
 *
 *   - `GET  /api/v1/productos?page&size&tipo&estado` — la lista, 16 por página
 *     (RNF-USA-001). Sin `estado` salen activos y únicos; los suspendidos solo
 *     se ven pidiéndolos, y el filtro lo hace.
 *   - `PATCH /api/v1/productos/{id}` — solo los campos que cambian; el tipo no
 *     se modifica. El servidor valida el resultado fusionado: su motivo se
 *     enseña tal cual si lo rechaza.
 *   - `PUT /api/v1/productos/{id}/suspender|reactivar` — con confirmación.
 *   - `GET  /api/v1/productos/{id}/historial` y `POST /reversiones` — historial
 *     inmutable y restauración confirmada (HU-PRD-007).
 *
 * Aquí no se decide nada del catálogo: la validación de cada campo es la
 * misma del alta (`solicitud-producto.js`) y la última palabra es del servidor.
 *
 * @module contenido/productos/catalogo-admin
 */

import { h } from '../../comun/ui/dom.js';
import { abrirDialogo, confirmar, confirmarConMotivo } from '../../comun/ui/dialogo.js';
import { campo } from '../../comun/ui/campo.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../../comun/ui/aviso.js';
import { conCarga } from '../../comun/ui/boton.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { NOMBRE_DEL_TIPO, creditos, fechaHora } from '../../comun/ui/formato.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import {
  cambiarDisponibilidad,
  consultarHistorialProducto,
  consultarVersionesDiseno,
  exportarPlantillaDiseno,
  importarPlantillaDiseno,
  listarProductos,
  modificarProducto,
  revertirProducto,
} from './cliente-productos.js';
import {
  CAMPOS_DE_PROMOCION,
  LIMITES_DE_PROMOCION,
  construirSolicitudProducto,
  PARTES_ARMADURA,
  PROTOTIPOS,
  TIPOS_PRODUCTO,
  valorLocalDe,
} from './solicitud-producto.js';
import { consultar } from '../../plataforma/consola/cliente-consola.js';
import { directorioDeTitulares } from '../../plataforma/consola/titulares.js';
import { rellenarHeroes } from './heroes-del-catalogo.js';
import { miniaturaDeRecurso, selectorDeRecurso } from './recursos-graficos.js';
import { vistaPreviaDeProducto } from './vista-previa-producto.js';

/** RNF-USA-001: dieciséis por página. */
export const PRODUCTOS_POR_PAGINA = 16;

export const ETIQUETA_DE_TIPO = NOMBRE_DEL_TIPO;

export const ETIQUETA_DE_ESTADO = Object.freeze({
  ACTIVO: 'Activo',
  UNICO: 'Único',
  SUSPENDIDO: 'Suspendido',
});

const ETIQUETA_DE_PARTE = Object.freeze({
  CASCO: 'Casco',
  PECHO: 'Pecho',
  GUANTES: 'Guantes',
  BRAZALETES: 'Brazaletes',
  PANTALON: 'Pantalón',
  ZAPATOS: 'Zapatos',
});

/**
 * Lo que se puede editar de cada tipo, con las reglas del contrato. Los
 * comunes van primero; el tipo no está porque no se modifica.
 */
const CAMPOS_COMUNES = [
  { nombre: 'nombre', etiqueta: 'Nombre', atributos: { maxlength: 120 } },
  { nombre: 'descripcion', etiqueta: 'Descripción', multilinea: true },
  {
    nombre: 'tiraje',
    etiqueta: 'Tiraje',
    tipo: 'number',
    pista: '-1 es ilimitado; si no, el total de unidades.',
    atributos: { step: 1, min: -1 },
  },
];

const CAMPOS_DE_PRECIO = {
  precioCreditos: {
    nombre: 'precioCreditos',
    etiqueta: 'Precio en créditos',
    tipo: 'number',
    atributos: { step: 1, min: 0 },
  },
  precioMonedaReal: {
    nombre: 'precioMonedaReal',
    etiqueta: 'Precio en moneda real',
    tipo: 'number',
    atributos: { step: '0.01', min: 0 },
  },
};

const TASA_DE_CAIDA = {
  nombre: 'tasaDeCaida',
  etiqueta: 'Tasa de caída (%)',
  tipo: 'number',
  atributos: { step: '0.01', min: 0, max: 100 },
};

// SA-6 — el héroe se elige por su nombre (heroes-del-catalogo.js): antes
// había que escribir el identificador del producto de tipo héroe.
const HEROE_ASOCIADO = {
  nombre: 'heroe',
  etiqueta: 'Héroe asociado',
  heroe: true,
};

export const CAMPOS_POR_TIPO = Object.freeze({
  HEROE: [
    {
      nombre: 'prototipo',
      etiqueta: 'Prototipo',
      opciones: PROTOTIPOS.map((p) => ({ valor: p, texto: p })),
    },
  ],
  HABILIDAD: [
    HEROE_ASOCIADO,
    {
      nombre: 'costoPoder',
      etiqueta: 'Costo de poder',
      tipo: 'number',
      atributos: { step: 1, min: 1 },
    },
    {
      nombre: 'multiplicadorNivel',
      etiqueta: 'Multiplicador de nivel',
      tipo: 'number',
      atributos: { step: '0.01', min: '0.01' },
    },
    {
      nombre: 'turnosCarga',
      etiqueta: 'Turnos de carga',
      tipo: 'number',
      atributos: { step: 1, min: 0 },
    },
  ],
  ARMA: [
    {
      nombre: 'poderDeAtaque',
      etiqueta: 'Poder de ataque',
      tipo: 'number',
      atributos: { step: 1, min: 1 },
    },
    TASA_DE_CAIDA,
  ],
  ARMADURA: [
    { nombre: 'defensa', etiqueta: 'Defensa', tipo: 'number', atributos: { step: 1, min: 1 } },
    {
      nombre: 'parte',
      etiqueta: 'Parte',
      opciones: PARTES_ARMADURA.map((p) => ({ valor: p, texto: ETIQUETA_DE_PARTE[p] ?? p })),
    },
    TASA_DE_CAIDA,
  ],
  ITEM: [{ nombre: 'efecto', etiqueta: 'Efecto', multilinea: true }, TASA_DE_CAIDA],
  EPICA: [
    HEROE_ASOCIADO,
    {
      nombre: 'turnosRecarga',
      etiqueta: 'Turnos de recarga',
      tipo: 'number',
      atributos: { step: 1, min: 0 },
    },
    { nombre: 'efectoGeneral', etiqueta: 'Efecto general', multilinea: true },
    { nombre: 'efectoPotenciado', etiqueta: 'Efecto potenciado', multilinea: true },
  ],
});

/**
 * @param {number|null|undefined} tiraje
 * @returns {string}
 */
export function textoDeTiraje(tiraje) {
  if (tiraje === -1) {
    return 'Ilimitado';
  }
  const n = Number(tiraje);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return n === 1 ? '1 unidad' : `${n} unidades`;
}

/**
 * @param {object} producto
 * @returns {string}
 */
export function textoDePrecio(producto) {
  if (producto.premium) {
    const valor = Number(producto.precioMonedaReal);
    return Number.isFinite(valor)
      ? `${valor.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} en moneda real`
      : '—';
  }
  return `${creditos(producto.precioCreditos)} créditos`;
}

/**
 * ¿Dicen lo mismo dos promociones? Mismo descuento y mismos instantes (el
 * servidor puede devolverlos con otra forma: `Z` o `+00:00`, con o sin
 * milisegundos).
 *
 * @param {{porcentaje?: number, desde?: string, hasta?: string}|null|undefined} a
 * @param {{porcentaje?: number, desde?: string, hasta?: string}|null|undefined} b
 * @returns {boolean}
 */
export function mismaPromocion(a, b) {
  if (!a || !b) {
    return !a && !b;
  }
  const instante = (valor) => new Date(valor ?? '').getTime();
  return (
    Number(a.porcentaje) === Number(b.porcentaje) &&
    instante(a.desde) === instante(b.desde) &&
    instante(a.hasta) === instante(b.hasta)
  );
}

/**
 * Solo lo que cambió: el cuerpo de un PATCH (`minProperties: 1`).
 *
 * @param {object} original el producto tal como vino del servidor
 * @param {object} nueva la solicitud completa que arma el formulario
 * @returns {object} los campos distintos, sin `tipo`
 */
export function cambiosDe(original, nueva) {
  const cambios = {};
  for (const [clave, valor] of Object.entries(nueva)) {
    if (clave === 'tipo') {
      continue;
    }
    if (clave === 'promocion') {
      // UXC-9 — la promoción es un objeto: se compara por lo que dice.
      if (!mismaPromocion(original.promocion, valor)) {
        cambios.promocion = valor;
      }
      continue;
    }
    if (original[clave] !== valor) {
      cambios[clave] = valor;
    }
  }
  return cambios;
}

/**
 * La promoción de un producto dicha para quien administra: vigente,
 * programada o terminada. `vigente` lo calcula el servidor; lo demás es solo
 * cómo se dice.
 *
 * @param {{porcentaje: number, desde: string, hasta: string, vigente?: boolean}|null|undefined} promocion
 * @param {number} [ahora]
 * @returns {{texto: string, estado: 'vigente'|'programada'|'terminada'}|null}
 */
export function textoDePromocion(promocion, ahora = Date.now()) {
  if (!promocion || !Number.isFinite(Number(promocion.porcentaje))) {
    return null;
  }
  const descuento = `${promocion.porcentaje} % de descuento`;
  if (promocion.vigente) {
    return {
      texto: `${descuento}, vigente hasta el ${fechaHora(promocion.hasta)}`,
      estado: 'vigente',
    };
  }
  if (new Date(promocion.desde).getTime() > ahora) {
    return {
      texto: `${descuento}, programado del ${fechaHora(promocion.desde)} al ${fechaHora(promocion.hasta)}`,
      estado: 'programada',
    };
  }
  return { texto: `${descuento}, terminó el ${fechaHora(promocion.hasta)}`, estado: 'terminada' };
}

/**
 * Por qué no se pudo, dicho para actuar.
 *
 * @param {Error & {status?: number}} error
 * @param {string} queSeHacia
 */
function avisoDeFallo(error, queSeHacia) {
  const estado = error?.status ?? 0;
  if (estado === 401) {
    return {
      tono: 'advertencia',
      titulo: 'Tu sesión caducó',
      detalle: 'Vuelve a iniciar sesión para gestionar el catálogo.',
    };
  }
  if (estado === 403) {
    return {
      tono: 'advertencia',
      titulo: 'Tu rol no puede hacer esto',
      detalle: 'Gestionar productos es de administración.',
    };
  }
  if (estado === 404) {
    return {
      tono: 'advertencia',
      titulo: 'Este producto ya no existe',
      detalle: 'Cierra la ficha y vuelve a cargar la lista.',
    };
  }
  if (estado === 400) {
    return {
      tono: 'advertencia',
      titulo: `No se pudo ${queSeHacia}`,
      detalle: error.message,
    };
  }
  return {
    tono: tonoPorEstado(estado),
    titulo: `No se pudo ${queSeHacia}`,
    detalle: 'El servicio de productos no respondió. Vuelve a intentarlo en un momento.',
  };
}

/** Distintivo del estado, con palabra (no solo color). */
export function distintivoDeEstado(estado) {
  return distintivo(
    ETIQUETA_DE_ESTADO[estado] ?? estado,
    estado === 'SUSPENDIDO' ? 'suspendido' : 'activo',
  );
}

/**
 * El formulario de edición de un producto (dentro de la ficha).
 *
 * @param {object} producto
 * @returns {{formulario: HTMLFormElement, marcarErrores: (motivo: string|null) => void}}
 */
function formularioDeEdicion(
  producto,
  { fetchImpl, listarHeroes, subir, listar, alCambiar = () => {} } = {},
) {
  const campos = [];
  const crear = (definicion) => {
    const valor =
      producto[definicion.nombre] === undefined || producto[definicion.nombre] === null
        ? ''
        : String(producto[definicion.nombre]);
    const { heroe, ...resto } = definicion;
    const creado = campo({
      ...resto,
      ...(heroe ? { opciones: [{ valor, texto: valor ? 'Héroe actual' : 'Elige el héroe' }] } : {}),
      valor,
      requerido: true,
    });
    if (heroe) {
      void rellenarHeroes(creado.control, {
        valor: valor || null,
        listar: listarHeroes,
        fetchImpl,
      });
    }
    campos.push(creado);
    return creado.elemento;
  };
  const selectorImagen = selectorDeRecurso({
    valor: producto.imagen ?? '',
    productoId: producto.id,
    subir,
    listar,
    fetchImpl,
    alCambiar,
  });

  const casillaPremium = h('input', {
    clase: 'casilla__entrada',
    atributos: { type: 'checkbox', name: 'premium' },
  });
  casillaPremium.checked = Boolean(producto.premium);
  const precioCreditos = crear(CAMPOS_DE_PRECIO.precioCreditos);
  const precioMonedaReal = crear(CAMPOS_DE_PRECIO.precioMonedaReal);
  const repasarPrecio = () => {
    precioCreditos.hidden = casillaPremium.checked;
    precioMonedaReal.hidden = !casillaPremium.checked;
    precioCreditos.querySelector('input').disabled = casillaPremium.checked;
    precioMonedaReal.querySelector('input').disabled = !casillaPremium.checked;
  };
  casillaPremium.addEventListener('change', repasarPrecio);

  const formulario = h('form', {
    clase: 'hoja-producto__formulario',
    atributos: { novalidate: true, 'aria-label': 'Datos del producto' },
    hijos: [
      // El tipo va oculto: la validación del alta lo necesita para saber qué
      // campos exigir, y no se modifica (no viaja en el PATCH).
      h('input', { atributos: { type: 'hidden', name: 'tipo', value: producto.tipo } }),
      selectorImagen.elemento,
      h('div', {
        clase: 'hoja-producto__rejilla',
        hijos: CAMPOS_COMUNES.map(crear),
      }),
      h('label', {
        clase: 'casilla casilla--caja',
        hijos: [
          casillaPremium,
          h('span', {
            clase: 'casilla__etiqueta',
            hijos: [
              'Premium',
              h('small', {
                clase: 'campo__pista',
                texto: 'Se vende solo en moneda real y no se puede subastar.',
              }),
            ],
          }),
        ],
      }),
      h('div', { clase: 'hoja-producto__rejilla', hijos: [precioCreditos, precioMonedaReal] }),
      h('fieldset', {
        clase: 'hoja-producto__tipo',
        hijos: [
          h('legend', {
            texto: `Atributos de ${ETIQUETA_DE_TIPO[producto.tipo] ?? producto.tipo}`,
          }),
          h('div', {
            clase: 'hoja-producto__rejilla',
            hijos: (CAMPOS_POR_TIPO[producto.tipo] ?? []).map(crear),
          }),
        ],
      }),
      camposDePromocion(producto.promocion, campos),
    ],
  });
  repasarPrecio();
  return {
    formulario,
    selectorImagen,
    marcarErrores: (motivo) => {
      selectorImagen.marcarError(null);
      if (motivo && /imagen/i.test(motivo)) {
        selectorImagen.marcarError(motivo);
      }
      for (const c of campos) {
        c.marcarError(null);
      }
      if (motivo) {
        // La validación del alta dice el campo por su etiqueta: se marca el
        // que empieza así, si lo hay.
        const culpable = campos.find((c) => {
          const etiqueta = c.elemento
            .querySelector('label')
            .textContent.replace(/\s*\(.*\)\s*$/, '')
            .toLowerCase();
          return motivo.toLowerCase().includes(etiqueta);
        });
        culpable?.marcarError(motivo);
        culpable?.control.focus();
      }
    },
  };
}

/**
 * UXC-9 — §7.2.4 «fecha de publicación y vigencia» y §7.5 «marcador de
 * porcentaje de descuento»: la promoción del producto (productos.yaml 1.4.0),
 * editable en la ficha y en el alta. Los tres campos son opcionales juntos: o
 * los tres o ninguno (`promocionDelFormulario`).
 *
 * @param {{porcentaje?: number, desde?: string, hasta?: string}|null|undefined} promocion
 * @param {Array<object>} [campos] dónde apuntar los campos para marcar errores
 * @returns {HTMLFieldSetElement}
 */
export function camposDePromocion(promocion, campos = []) {
  const crear = (definicion) => {
    const creado = campo(definicion);
    campos.push(creado);
    return creado.elemento;
  };
  return h('fieldset', {
    clase: 'hoja-producto__tipo',
    datos: { zona: 'promocion' },
    hijos: [
      h('legend', { texto: 'Promoción' }),
      h('p', {
        clase: 'campo__pista',
        texto:
          'Opcional: un descuento con fecha de inicio y de fin. Para terminarla antes de tiempo, adelanta su fin.',
      }),
      h('div', {
        clase: 'hoja-producto__rejilla',
        hijos: [
          crear({
            nombre: CAMPOS_DE_PROMOCION.porcentaje,
            etiqueta: 'Descuento (%)',
            tipo: 'number',
            valor: promocion?.porcentaje === undefined ? '' : String(promocion.porcentaje),
            pista: `Entre ${LIMITES_DE_PROMOCION.minimo} y ${LIMITES_DE_PROMOCION.maximo}.`,
            atributos: {
              step: 1,
              min: LIMITES_DE_PROMOCION.minimo,
              max: LIMITES_DE_PROMOCION.maximo,
            },
          }),
          crear({
            nombre: CAMPOS_DE_PROMOCION.desde,
            etiqueta: 'Empieza',
            tipo: 'datetime-local',
            valor: valorLocalDe(promocion?.desde),
          }),
          crear({
            nombre: CAMPOS_DE_PROMOCION.hasta,
            etiqueta: 'Termina',
            tipo: 'datetime-local',
            valor: valorLocalDe(promocion?.hasta),
          }),
        ],
      }),
    ],
  });
}

/** Cómo se dice cada dato que puede cambiar en la ficha (para «Se publicarán: …»). */
export const ETIQUETA_DE_CAMPO = Object.freeze({
  nombre: 'nombre',
  descripcion: 'descripción',
  imagen: 'imagen',
  tiraje: 'tiraje',
  premium: 'premium',
  precioCreditos: 'precio en créditos',
  precioMonedaReal: 'precio en moneda real',
  promocion: 'promoción',
  prototipo: 'prototipo',
  heroe: 'héroe asociado',
  costoPoder: 'costo de poder',
  multiplicadorNivel: 'multiplicador de nivel',
  turnosCarga: 'turnos de carga',
  turnosRecarga: 'turnos de recarga',
  efectoGeneral: 'efecto general',
  efectoPotenciado: 'efecto potenciado',
  defensa: 'defensa',
  parte: 'parte',
  efecto: 'efecto',
  poderDeAtaque: 'poder de ataque',
  tasaDeCaida: 'tasa de caída',
});

/** Cómo se dice cada entrada del historial (HU-PRD-007). */
export const ETIQUETA_DE_CAMBIO = Object.freeze({
  CREACION: 'Alta del producto',
  MODIFICACION: 'Modificación',
  SUSPENSION: 'Suspensión',
  REACTIVACION: 'Reactivación',
  REVERSION: 'Reversión',
  IMPORTACION_DISENO: 'Diseño importado',
});

/**
 * «imagen», «imagen y descripción», «nombre, imagen y precio en créditos».
 *
 * @param {string[]} claves
 * @returns {string}
 */
export function listaDeCampos(claves) {
  const nombres = claves.map((clave) => ETIQUETA_DE_CAMPO[clave] ?? clave);
  if (nombres.length <= 1) {
    return nombres.join('');
  }
  return `${nombres.slice(0, -1).join(', ')} y ${nombres[nombres.length - 1]}`;
}

/**
 * Quién hizo un cambio: el apodo de su cuenta, o de dónde salió si no lo hizo
 * una persona (la semilla del catálogo no tiene autor).
 *
 * @param {string|null|undefined} autor
 * @param {{de: (uid: string) => ({apodo?: string}|null)}|null} titulares
 * @returns {string}
 */
export function autorLegible(autor, titulares) {
  if (!autor) {
    return 'Catálogo inicial';
  }
  return titulares?.de(autor)?.apodo ?? 'Cuenta sin apodo';
}

/** Lo que se lleva escrito en la ficha, para la vista previa (sin validar). */
function borradorDeLaFicha(formulario, actual) {
  const leer = (nombre) => {
    const candidatos = [...formulario.querySelectorAll(`[name="${nombre}"]`)];
    const activo = candidatos.find((c) => !c.disabled) ?? candidatos[0];
    if (!activo) {
      return null;
    }
    return activo.type === 'checkbox' ? activo.checked : String(activo.value ?? '').trim();
  };
  const premium = Boolean(leer('premium'));
  return {
    ...actual,
    nombre: leer('nombre') || actual.nombre,
    descripcion: leer('descripcion') ?? actual.descripcion,
    imagen: leer('imagen') || null,
    premium,
    precioCreditos: premium ? null : Number(leer('precioCreditos')) || null,
    precioMonedaReal: premium ? Number(leer('precioMonedaReal')) || null : null,
  };
}

/**
 * ProductAdminSheet: la ficha de gestión de un producto.
 *
 * SA-6 — HU-PRD-009/010 y §7.2.5:
 *   - la imagen se sube o se elige de la biblioteca (`selectorDeRecurso`);
 *   - los cambios se previsualizan con la tarjeta de la tienda y no se
 *     publican hasta confirmarlos; «Descartar cambios» deja la ficha como está
 *     publicada;
 *   - suspender, reactivar y revertir piden el motivo, que queda en el
 *     historial;
 *   - el historial y las versiones dicen quién hizo cada cambio por su apodo,
 *     y cada versión de diseño se ve y se puede restaurar.
 *
 * @param {object} producto `ProductoCreado`
 * @param {{alCambiar?: (producto: object) => void, fetchImpl?: Function,
 *   confirmarAccion?: typeof confirmar, pedirMotivo?: typeof confirmarConMotivo,
 *   titulares?: ReturnType<typeof directorioDeTitulares>|null,
 *   listarHeroes?: Function, subir?: Function, listar?: Function}} [opciones]
 *   `alCambiar` recibe el producto como quedó tras publicar, suspender, reactivar o revertir.
 * @returns {{cerrar: () => void, elemento: HTMLElement}}
 */
export function abrirHojaDeProducto(
  producto,
  {
    alCambiar = () => {},
    fetchImpl,
    confirmarAccion = confirmar,
    pedirMotivo = confirmarConMotivo,
    titulares = directorioDeTitulares({ consultarApi: consultar }),
    listarHeroes,
    subir,
    listar,
  } = {},
) {
  let actual = { ...producto };
  const zonaAviso = h('div', { datos: { zona: 'aviso-hoja' }, atributos: { hidden: true } });
  const resumen = h('div', { clase: 'hoja-producto__resumen' });
  const zonaFormulario = h('section', {
    clase: 'hoja-producto__datos',
    atributos: { 'aria-labelledby': 'datos-producto-titulo' },
  });
  const zonaVistaPrevia = h('div', { datos: { zona: 'vista-previa-hoja' } });
  const estadoDePublicacion = h('p', {
    clase: 't-meta hoja-producto__pendientes',
    atributos: { 'aria-live': 'polite' },
  });
  const descartar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Descartar cambios',
    atributos: { type: 'button', disabled: true },
    datos: { accion: 'descartar-cambios' },
  });
  const publicar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Publicar cambios',
    atributos: { type: 'button', disabled: true },
    datos: { accion: 'guardar-producto' },
  });
  const vistaPrevia = h('section', {
    clase: 'hoja-producto__vista-previa',
    atributos: { 'aria-labelledby': 'vista-previa-hoja-titulo' },
    hijos: [
      h('h3', { texto: 'Vista previa', atributos: { id: 'vista-previa-hoja-titulo' } }),
      zonaVistaPrevia,
      estadoDePublicacion,
      h('div', { clase: 'hoja-producto__acciones', hijos: [descartar, publicar] }),
    ],
  });
  const disponibilidad = h('section', {
    clase: 'hoja-producto__disponibilidad',
    atributos: { 'aria-label': 'Disponibilidad' },
  });
  const historial = h('section', {
    clase: 'hoja-producto__historial',
    atributos: { 'aria-labelledby': 'historial-producto-titulo' },
  });
  const diseno = h('section', {
    clase: 'hoja-producto__diseno',
    atributos: { 'aria-labelledby': 'versiones-diseno-titulo' },
  });

  /** @type {{formulario: HTMLFormElement, marcarErrores: Function, selectorImagen: object}} */
  let edicion;

  function pintarResumen() {
    const promocion = textoDePromocion(actual.promocion);
    // RFINAL-07 — `replaceChildren` convierte un `null` en el texto «null»: sin
    // promoción, la ficha lo pintaba bajo el título (revisión de DEV del 4-oct).
    // Las líneas condicionales se filtran antes de pintarlas.
    const lineas = [
      h('p', {
        clase: 'hoja-producto__distintivos',
        hijos: [
          distintivo(ETIQUETA_DE_TIPO[actual.tipo] ?? actual.tipo),
          distintivoDeEstado(actual.estado),
          actual.premium ? distintivo('Premium', 'promocion') : null,
          promocion?.estado === 'vigente'
            ? distintivo(`−${actual.promocion.porcentaje} %`, 'promocion')
            : null,
        ],
      }),
      promocion
        ? h('p', {
            clase: 't-meta',
            datos: { zona: 'promocion-actual', estado: promocion.estado },
            texto: `Promoción: ${promocion.texto}.`,
          })
        : null,
      h('p', {
        clase: 't-meta',
        texto: `Versión ${actual.version ?? '—'} · modificado el ${fechaHora(actual.modificadoEn)} · creado el ${fechaHora(actual.creadoEn)}`,
      }),
    ];
    resumen.replaceChildren(...lineas.filter(Boolean));
  }

  /** Lo que cambiaría al publicar, o el motivo por el que todavía no se puede. */
  function pendientes() {
    try {
      const solicitud = construirSolicitudProducto(edicion.formulario);
      return { cambios: cambiosDe(actual, solicitud), problema: null };
    } catch (error) {
      return { cambios: null, problema: error.message };
    }
  }

  function repintarVistaPrevia() {
    zonaVistaPrevia.replaceChildren(
      vistaPreviaDeProducto(borradorDeLaFicha(edicion.formulario, actual)),
    );
    const { cambios, problema } = pendientes();
    const claves = cambios ? Object.keys(cambios) : [];
    if (problema) {
      estadoDePublicacion.textContent = `Para publicar, revisa el formulario: ${problema}`;
    } else if (claves.length) {
      estadoDePublicacion.textContent = `Cambios sin publicar: ${listaDeCampos(claves)}.`;
    } else {
      estadoDePublicacion.textContent = 'Es lo que hoy ven los jugadores.';
    }
    publicar.disabled = Boolean(problema) || claves.length === 0;
    descartar.disabled = !problema && claves.length === 0;
  }

  function montarFormulario() {
    edicion = formularioDeEdicion(actual, {
      fetchImpl,
      listarHeroes,
      subir,
      listar,
      alCambiar: () => repintarVistaPrevia(),
    });
    edicion.formulario.addEventListener('input', () => repintarVistaPrevia());
    edicion.formulario.addEventListener('change', () => repintarVistaPrevia());
    edicion.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      void publicarCambios();
    });
    zonaFormulario.replaceChildren(
      h('h3', { texto: 'Diseño y datos', atributos: { id: 'datos-producto-titulo' } }),
      edicion.formulario,
    );
    repintarVistaPrevia();
  }

  async function publicarCambios() {
    limpiarAviso(zonaAviso);
    let solicitud;
    try {
      solicitud = construirSolicitudProducto(edicion.formulario);
    } catch (error) {
      edicion.marcarErrores(error.message);
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: 'Revisa el formulario',
        detalle: error.message,
      });
      return;
    }
    edicion.marcarErrores(null);
    const cambios = cambiosDe(actual, solicitud);
    const claves = Object.keys(cambios);
    if (claves.length === 0) {
      pintarAviso(zonaAviso, {
        tono: 'info',
        titulo: 'No hay nada que publicar',
        detalle: 'Los datos son los mismos que ya tiene el producto.',
      });
      return;
    }
    const seguro = await confirmarAccion({
      titulo: `¿Publicar los cambios de «${actual.nombre}»?`,
      mensaje: `Se publicará: ${listaDeCampos(claves)}. Los jugadores lo verán en cuanto confirmes; la versión anterior queda como respaldo y se puede revertir.`,
      textoConfirmar: 'Publicar',
    });
    if (!seguro) {
      return;
    }
    conCarga(publicar, true, 'Publicando…');
    try {
      const guardado = await modificarProducto(actual.id, cambios, { fetchImpl });
      aplicar(guardado);
      montarFormulario();
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Cambios publicados',
        detalle: `Queda en la versión ${guardado.version ?? '—'}; la anterior se conserva como respaldo.`,
      });
    } catch (error) {
      pintarAviso(zonaAviso, avisoDeFallo(error, 'publicar los cambios'));
    } finally {
      conCarga(publicar, false);
      repintarVistaPrevia();
    }
  }

  descartar.addEventListener('click', () => {
    limpiarAviso(zonaAviso);
    montarFormulario();
    pintarAviso(zonaAviso, {
      tono: 'info',
      titulo: 'Cambios descartados',
      detalle: 'La ficha vuelve a mostrar lo que está publicado.',
    });
  });
  publicar.addEventListener('click', () => void publicarCambios());

  function pintarDisponibilidad() {
    const suspendido = actual.estado === 'SUSPENDIDO';
    const boton = h('button', {
      clase: `boton ${suspendido ? 'boton--primario' : 'boton--peligro'} boton--pequeno`,
      texto: suspendido ? 'Reactivar' : 'Suspender',
      atributos: { type: 'button' },
      datos: { accion: suspendido ? 'reactivar' : 'suspender' },
    });
    boton.addEventListener('click', () =>
      cambiarEstado(suspendido ? 'reactivar' : 'suspender', boton),
    );
    disponibilidad.replaceChildren(
      h('h3', { texto: 'Disponibilidad' }),
      h('p', {
        texto: suspendido
          ? 'Suspendido: nadie puede adquirirlo. Al reactivarlo vuelve al estado que tenía (activo o único) con el mismo tiraje.'
          : `${ETIQUETA_DE_ESTADO[actual.estado] ?? actual.estado}: se puede adquirir. Tiraje: ${textoDeTiraje(actual.tiraje)}.`,
      }),
      boton,
    );
  }

  async function cambiarEstado(accion, boton) {
    const suspender = accion === 'suspender';
    // §7.2.5 — acción crítica: confirmación con sus consecuencias y motivo.
    const motivo = await pedirMotivo({
      titulo: suspender ? `¿Suspender «${actual.nombre}»?` : `¿Reactivar «${actual.nombre}»?`,
      mensaje: suspender
        ? 'Nadie podrá adquirirlo mientras esté suspendido.'
        : 'Vuelve al estado que tenía antes de suspenderse.',
      consecuencias: suspender
        ? [
            'Desaparece de la tienda.',
            'Quien ya lo tiene lo conserva en su inventario.',
            'No se borra y su tiraje no cambia.',
          ]
        : ['Vuelve a la tienda con el mismo tiraje.'],
      etiquetaMotivo: suspender ? 'Motivo de la suspensión' : 'Motivo de la reactivación',
      pistaMotivo: 'Queda en el historial del producto con tu nombre y la fecha.',
      textoConfirmar: suspender ? 'Suspender' : 'Reactivar',
      peligro: suspender,
    });
    if (motivo === null || motivo === undefined) {
      return;
    }
    limpiarAviso(zonaAviso);
    conCarga(boton, true, suspender ? 'Suspendiendo…' : 'Reactivando…');
    try {
      const disponible = await cambiarDisponibilidad(actual.id, accion, { fetchImpl, motivo });
      aplicar({ ...actual, estado: disponible.estado, tiraje: disponible.tiraje ?? actual.tiraje });
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: suspender ? 'Producto suspendido' : 'Producto reactivado',
        detalle: suspender
          ? 'Ya no se puede adquirir. Puedes reactivarlo cuando quieras.'
          : 'Ya se puede adquirir de nuevo.',
      });
      if (historial.dataset.cargado === 'si') {
        void cargarHistorial();
      }
    } catch (error) {
      conCarga(boton, false);
      pintarAviso(zonaAviso, avisoDeFallo(error, suspender ? 'suspender' : 'reactivar'));
    }
  }

  function aplicar(nuevo) {
    actual = nuevo;
    pintarResumen();
    pintarDisponibilidad();
    alCambiar(actual);
  }

  const tituloHistorial = () =>
    h('h3', { texto: 'Historial y reversión', atributos: { id: 'historial-producto-titulo' } });

  async function revertir(cambio, boton) {
    const motivo = await pedirMotivo({
      titulo: `¿Revertir «${actual.nombre}»?`,
      mensaje: `Deshace «${(ETIQUETA_DE_CAMBIO[cambio.tipo] ?? 'el cambio').toLowerCase()}» del ${fechaHora(cambio.fecha)}: el producto vuelve a como estaba antes.`,
      consecuencias: [
        'Los jugadores verán el producto como era antes de ese cambio.',
        'La reversión también queda en el historial y se puede deshacer.',
      ],
      etiquetaMotivo: 'Motivo de la reversión',
      pistaMotivo: 'Queda en el historial del producto con tu nombre y la fecha.',
      textoConfirmar: 'Revertir',
      peligro: true,
    });
    if (motivo === null || motivo === undefined) {
      return;
    }
    conCarga(boton, true, 'Revirtiendo…');
    try {
      const restaurado = await revertirProducto(actual.id, cambio.id, { fetchImpl, motivo });
      aplicar(restaurado);
      montarFormulario();
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Producto restaurado',
        detalle: `El estado anterior quedó aplicado como versión ${restaurado.version}.`,
      });
      await cargarHistorial();
    } catch (error) {
      pintarAviso(zonaAviso, avisoDeFallo(error, 'revertir el producto'));
      conCarga(boton, false);
    }
  }

  async function cargarHistorial() {
    historial.dataset.cargado = 'si';
    historial.replaceChildren(
      tituloHistorial(),
      h('p', { clase: 't-meta', texto: 'Consultando los cambios registrados…' }),
    );
    try {
      const cambios = await consultarHistorialProducto(actual.id, { fetchImpl });
      await titulares?.nombrar(cambios.map((cambio) => cambio.autor).filter(Boolean));
      const elementos = cambios.map((cambio) => {
        const acciones = [];
        if (cambio.revertible) {
          const boton = h('button', {
            clase: 'boton boton--secundario boton--pequeno',
            texto: 'Revertir',
            atributos: { type: 'button' },
            datos: { accion: 'revertir-producto', respaldo: cambio.id },
          });
          boton.addEventListener('click', () => void revertir(cambio, boton));
          acciones.push(boton);
        }
        const campos = (cambio.campos ?? []).filter(
          (c) => !['producto creado', 'estado del producto'].includes(c),
        );
        return h('li', {
          datos: { cambio: cambio.id, tipo: cambio.tipo },
          hijos: [
            h('div', {
              hijos: [
                h('strong', { texto: ETIQUETA_DE_CAMBIO[cambio.tipo] ?? 'Modificación' }),
                h('p', {
                  clase: 't-meta',
                  texto: `${fechaHora(cambio.fecha)} · ${autorLegible(cambio.autor, titulares)}`,
                }),
                campos.length && cambio.tipo !== 'CREACION'
                  ? h('p', { clase: 't-meta', texto: `Cambió: ${campos.join(', ')}.` })
                  : null,
                cambio.motivo
                  ? h('p', { clase: 'hoja-producto__motivo', texto: `Motivo: ${cambio.motivo}` })
                  : null,
              ],
            }),
            ...acciones,
          ],
        });
      });
      const partes = [
        tituloHistorial(),
        cambios.length
          ? h('ul', { clase: 'hoja-producto__cambios', hijos: elementos })
          : h('p', {
              clase: 't-meta',
              texto: 'Este producto todavía no tiene cambios registrados.',
            }),
        cambios.some((cambio) => cambio.revertible)
          ? h('p', {
              clase: 't-meta',
              texto: 'Solo se revierte el último cambio: así nunca se pisa uno posterior.',
            })
          : null,
      ];
      historial.replaceChildren(...partes.filter(Boolean));
    } catch (error) {
      historial.replaceChildren(
        tituloHistorial(),
        h('p', { clase: 't-meta', texto: avisoDeFallo(error, 'consultar el historial').detalle }),
        botonConsultarHistorial,
      );
    }
  }

  const botonConsultarHistorial = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Ver historial',
    atributos: { type: 'button' },
    datos: { accion: 'consultar-historial' },
  });
  botonConsultarHistorial.addEventListener('click', () => void cargarHistorial());
  historial.append(
    tituloHistorial(),
    h('p', {
      clase: 't-meta',
      texto: 'Quién cambió el producto, cuándo y por qué; el último cambio se puede revertir.',
    }),
    botonConsultarHistorial,
  );

  const listaVersionesDiseno = h('div', { datos: { zona: 'versiones-diseno' } });
  const archivoPlantilla = h('input', {
    atributos: { type: 'file', accept: 'application/json,.json', hidden: true },
    datos: { accion: 'archivo-plantilla-diseno' },
  });
  const botonVersionesDiseno = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Ver versiones',
    atributos: { type: 'button' },
    datos: { accion: 'consultar-versiones-diseno' },
  });
  const botonExportarDiseno = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Exportar plantilla',
    atributos: { type: 'button' },
    datos: { accion: 'exportar-plantilla-diseno' },
  });
  const botonImportarDiseno = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Importar plantilla',
    atributos: { type: 'button' },
    datos: { accion: 'importar-plantilla-diseno' },
  });

  async function restaurarDiseno(version, boton) {
    const seguro = await confirmarAccion({
      titulo: `¿Restaurar el diseño de la versión ${version.version}?`,
      mensaje:
        'Vuelven la imagen y la descripción de esa versión. Precios, tiraje y reglas no cambian, y el diseño de ahora queda como una versión más.',
      textoConfirmar: 'Restaurar diseño',
    });
    if (!seguro) {
      return;
    }
    conCarga(boton, true, 'Restaurando…');
    try {
      const actualizado = await importarPlantillaDiseno(
        actual.id,
        {
          formato: 'nexus-producto-diseno',
          versionFormato: 1,
          productoOrigenId: actual.id,
          versionProducto: version.version,
          imagen: version.imagen,
          descripcion: version.descripcion,
          exportadaEn: new Date().toISOString(),
        },
        { fetchImpl },
      );
      aplicar(actualizado);
      montarFormulario();
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Diseño restaurado',
        detalle: `Queda publicado como versión ${actualizado.version}.`,
      });
      await cargarVersionesDiseno();
    } catch (error) {
      conCarga(boton, false);
      pintarAviso(zonaAviso, avisoDeFallo(error, 'restaurar el diseño'));
    }
  }

  async function cargarVersionesDiseno() {
    conCarga(botonVersionesDiseno, true, 'Consultando…');
    try {
      const versiones = await consultarVersionesDiseno(actual.id, { fetchImpl });
      await titulares?.nombrar(versiones.map((version) => version.autor).filter(Boolean));
      listaVersionesDiseno.replaceChildren(
        versiones.length
          ? h('ol', {
              clase: 'hoja-producto__versiones',
              hijos: versiones.map((version) => {
                const restaurar = version.actual
                  ? null
                  : h('button', {
                      clase: 'boton boton--secundario boton--pequeno',
                      texto: 'Restaurar este diseño',
                      atributos: {
                        type: 'button',
                        'aria-label': `Restaurar el diseño de la versión ${version.version}`,
                      },
                      datos: { accion: 'restaurar-diseno', version: version.version },
                    });
                restaurar?.addEventListener(
                  'click',
                  () => void restaurarDiseno(version, restaurar),
                );
                return h('li', {
                  clase: 'version-diseno',
                  datos: { version: version.version },
                  hijos: [
                    miniaturaDeRecurso(version.imagen, `Imagen de la versión ${version.version}`),
                    h('div', {
                      clase: 'version-diseno__datos',
                      hijos: [
                        h('p', {
                          hijos: [
                            h('strong', { texto: `Versión ${version.version}` }),
                            version.actual ? distintivo('Publicada') : null,
                          ],
                        }),
                        h('p', {
                          clase: 't-meta',
                          texto: `${fechaHora(version.fecha)} · ${autorLegible(version.autor, titulares)}`,
                        }),
                        h('p', {
                          clase: 'version-diseno__descripcion',
                          texto: version.descripcion,
                        }),
                      ],
                    }),
                    restaurar,
                  ],
                });
              }),
            })
          : h('p', { clase: 't-meta', texto: 'Todavía no hay versiones de diseño.' }),
      );
    } catch (error) {
      listaVersionesDiseno.replaceChildren(
        h('p', { clase: 't-meta', texto: avisoDeFallo(error, 'consultar las versiones').detalle }),
      );
    } finally {
      conCarga(botonVersionesDiseno, false);
    }
  }

  botonVersionesDiseno.addEventListener('click', () => void cargarVersionesDiseno());
  botonExportarDiseno.addEventListener('click', async () => {
    conCarga(botonExportarDiseno, true, 'Exportando…');
    try {
      const plantilla = await exportarPlantillaDiseno(actual.id, { fetchImpl });
      const url = URL.createObjectURL(
        new Blob([JSON.stringify(plantilla, null, 2)], { type: 'application/json' }),
      );
      const enlace = h('a', {
        atributos: {
          href: url,
          download: `diseno-${actual.nombre
            .toLowerCase()
            .normalize('NFD')
            .replace(/[̀-ͯ]/g, '')
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/(^-|-$)/g, '')}-v${plantilla.versionProducto}.json`,
        },
      });
      document.body.append(enlace);
      enlace.click();
      enlace.remove();
      URL.revokeObjectURL(url);
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Plantilla exportada',
        detalle: 'El archivo lleva la imagen y la descripción; no lleva precios ni reglas.',
      });
    } catch (error) {
      pintarAviso(zonaAviso, avisoDeFallo(error, 'exportar la plantilla'));
    } finally {
      conCarga(botonExportarDiseno, false);
    }
  });
  botonImportarDiseno.addEventListener('click', () => archivoPlantilla.click());
  archivoPlantilla.addEventListener('change', async () => {
    const archivo = archivoPlantilla.files?.[0];
    archivoPlantilla.value = '';
    if (!archivo) {
      return;
    }
    let plantilla;
    try {
      plantilla = JSON.parse(await archivo.text());
    } catch {
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: 'Plantilla inválida',
        detalle: 'Elige un archivo de plantilla exportado desde el catálogo.',
      });
      return;
    }
    const seguro = await confirmarAccion({
      titulo: `¿Importar el diseño en «${actual.nombre}»?`,
      mensaje:
        'Se reemplazan la imagen y la descripción. Precios, tiraje y reglas no cambian, y el diseño de ahora queda como una versión más.',
      textoConfirmar: 'Importar',
    });
    if (!seguro) {
      return;
    }
    conCarga(botonImportarDiseno, true, 'Importando…');
    try {
      const actualizado = await importarPlantillaDiseno(actual.id, plantilla, { fetchImpl });
      aplicar(actualizado);
      montarFormulario();
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Diseño importado',
        detalle: `Queda publicado como versión ${actualizado.version}.`,
      });
      await cargarVersionesDiseno();
    } catch (error) {
      pintarAviso(zonaAviso, avisoDeFallo(error, 'importar la plantilla'));
    } finally {
      conCarga(botonImportarDiseno, false);
    }
  });

  diseno.append(
    h('h3', { texto: 'Versiones de diseño', atributos: { id: 'versiones-diseno-titulo' } }),
    h('p', {
      clase: 't-meta',
      texto:
        'Cada cambio de imagen o descripción deja una versión: se puede ver, restaurar o llevar a otro producto como plantilla.',
    }),
    h('div', {
      clase: 'hoja-producto__acciones-diseno',
      hijos: [botonVersionesDiseno, botonExportarDiseno, botonImportarDiseno, archivoPlantilla],
    }),
    listaVersionesDiseno,
  );

  pintarResumen();
  pintarDisponibilidad();
  montarFormulario();
  const cuerpo = h('div', {
    clase: 'hoja-producto',
    hijos: [resumen, zonaAviso, zonaFormulario, vistaPrevia, disponibilidad, historial, diseno],
  });
  const dialogo = abrirDialogo({ titulo: actual.nombre, cuerpo, cerrableFuera: false });
  dialogo.elemento.classList.add('dialogo--hoja');
  return dialogo;
}

/**
 * La lista del catálogo con filtros y paginación, en la consola.
 *
 * @param {HTMLElement} zona
 * @param {{fetchImpl?: Function, alCambiarCatalogo?: () => void, hrefCrear?: string}} [opciones]
 *   `alCambiarCatalogo` avisa de que algo cambió (para repintar los contadores).
 */
export function montarCatalogoAdmin(
  zona,
  {
    fetchImpl,
    alCambiarCatalogo = () => {},
    hrefCrear = './productos.html',
    titulares = directorioDeTitulares({ consultarApi: consultar }),
    ...dependencias
  } = {},
) {
  const criterios = { tipo: '', estado: '', pagina: 0 };
  let peticion = 0;

  const selectorTipo = campo({
    nombre: 'tipo',
    etiqueta: 'Tipo',
    opciones: [
      { valor: '', texto: 'Todos los tipos' },
      ...TIPOS_PRODUCTO.map((t) => ({ valor: t, texto: ETIQUETA_DE_TIPO[t] })),
    ],
  });
  const selectorEstado = campo({
    nombre: 'estado',
    etiqueta: 'Estado',
    opciones: [
      { valor: '', texto: 'Activos y únicos' },
      { valor: 'ACTIVO', texto: 'Solo activos' },
      { valor: 'UNICO', texto: 'Solo únicos' },
      { valor: 'SUSPENDIDO', texto: 'Suspendidos' },
    ],
  });
  const filtros = h('form', {
    clase: 'catalogo-admin__filtros',
    atributos: { role: 'search', 'aria-label': 'Filtrar el catálogo' },
    hijos: [selectorTipo.elemento, selectorEstado.elemento],
  });
  const cuenta = h('p', {
    clase: 'catalogo-admin__cuenta t-meta',
    atributos: { tabindex: '-1', 'aria-live': 'polite' },
  });
  const contenido = h('div', { datos: { zona: 'catalogo' } });
  const paginacion = h('div', { clase: 'catalogo-admin__paginacion' });
  zona.replaceChildren(
    h('div', {
      clase: 'catalogo-admin__encabezado',
      hijos: [
        h('h2', { texto: 'Productos del catálogo', atributos: { id: 'titulo-catalogo-admin' } }),
      ],
    }),
    filtros,
    cuenta,
    contenido,
    paginacion,
  );
  zona.setAttribute('aria-labelledby', 'titulo-catalogo-admin');

  filtros.addEventListener('change', () => {
    criterios.tipo = selectorTipo.control.value;
    criterios.estado = selectorEstado.control.value;
    criterios.pagina = 0;
    cargar();
  });
  filtros.addEventListener('submit', (evento) => evento.preventDefault());

  function hayFiltros() {
    return Boolean(criterios.tipo || criterios.estado);
  }

  function quitarFiltros() {
    selectorTipo.control.value = '';
    selectorEstado.control.value = '';
    criterios.tipo = '';
    criterios.estado = '';
    criterios.pagina = 0;
    cargar();
  }

  function filaDe(producto) {
    const gestionar = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: 'Gestionar',
      atributos: { type: 'button', 'aria-label': `Gestionar ${producto.nombre}` },
      datos: { accion: 'gestionar', producto: producto.id },
    });
    gestionar.addEventListener('click', () =>
      abrirHojaDeProducto(producto, {
        ...dependencias,
        fetchImpl,
        titulares,
        alCambiar: () => {
          alCambiarCatalogo();
          cargar({ conservarFoco: true });
        },
      }),
    );
    return h('tr', {
      datos: { producto: producto.id, estado: producto.estado },
      hijos: [
        h('th', {
          atributos: { scope: 'row' },
          hijos: [
            h('span', {
              clase: 'catalogo-admin__producto',
              hijos: [
                miniaturaDeRecurso(producto.imagen ?? null, ''),
                h('span', {
                  hijos: [
                    h('span', { clase: 'catalogo-admin__nombre', texto: producto.nombre }),
                    h('span', {
                      clase: 'catalogo-admin__tipo t-meta',
                      texto: ETIQUETA_DE_TIPO[producto.tipo] ?? producto.tipo,
                    }),
                  ],
                }),
              ],
            }),
          ],
        }),
        h('td', { hijos: [distintivoDeEstado(producto.estado)] }),
        h('td', { texto: textoDeTiraje(producto.tiraje) }),
        h('td', { texto: textoDePrecio(producto) }),
        h('td', { clase: 't-meta', texto: fechaHora(producto.modificadoEn) }),
        h('td', { clase: 'catalogo-admin__accion', hijos: [gestionar] }),
      ],
    });
  }

  function pintarPagina(pagina) {
    const productos = pagina?.content ?? [];
    const total = Number(pagina?.totalElements) || 0;
    cuenta.textContent = total === 1 ? '1 producto' : `${total} productos`;
    if (productos.length === 0) {
      paginacion.replaceChildren();
      pintarEstado(
        contenido,
        hayFiltros()
          ? estadoVacio({
              titulo: 'Ningún producto cumple estos filtros',
              detalle: 'Prueba con otro tipo o estado.',
              accion: {
                texto: 'Quitar filtros',
                nombre: 'quitar-filtros',
                alPulsar: quitarFiltros,
              },
            })
          : estadoVacio({
              titulo: 'El catálogo está vacío',
              detalle: 'Todavía no hay productos activos. Crea el primero.',
              accion: { texto: 'Crear producto', href: hrefCrear },
            }),
      );
      return;
    }
    const tabla = h('table', {
      clase: 'catalogo-admin__tabla',
      hijos: [
        h('caption', { clase: 'solo-lectores', texto: 'Productos del catálogo' }),
        h('thead', {
          hijos: [
            h('tr', {
              hijos: ['Producto', 'Estado', 'Tiraje', 'Precio', 'Modificado', 'Acción'].map((t) =>
                h('th', { texto: t, atributos: { scope: 'col' } }),
              ),
            }),
          ],
        }),
        h('tbody', { hijos: productos.map(filaDe) }),
      ],
    });
    contenido.replaceChildren(
      h('div', {
        clase: 'catalogo-admin__desplazable',
        atributos: {
          tabindex: '0',
          role: 'region',
          'aria-label': 'Tabla de productos, desplazable',
        },
        hijos: [tabla],
      }),
    );
    const totalPaginas = Number(pagina.totalPages) || 0;
    if (totalPaginas > 1) {
      const control = construirPaginacion(
        { paginaActual: Number(pagina.page) || 0, totalPaginas },
        (nueva) => {
          criterios.pagina = nueva;
          cargar().then(() => cuenta.focus());
        },
      );
      control.setAttribute('aria-label', 'Páginas del catálogo');
      paginacion.replaceChildren(control);
    } else {
      paginacion.replaceChildren();
    }
  }

  async function cargar({ conservarFoco = false } = {}) {
    peticion += 1;
    const esta = peticion;
    if (!conservarFoco) {
      pintarEstado(contenido, estadoDeCarga({ filas: 4, etiqueta: 'Cargando el catálogo…' }));
    }
    let pagina;
    try {
      pagina = await listarProductos(
        {
          pagina: criterios.pagina,
          tamano: PRODUCTOS_POR_PAGINA,
          tipo: criterios.tipo || null,
          estado: criterios.estado || null,
        },
        { fetchImpl },
      );
    } catch {
      if (esta === peticion) {
        cuenta.textContent = '';
        paginacion.replaceChildren();
        pintarEstado(
          contenido,
          estadoDeError({
            titulo: 'No pudimos cargar el catálogo',
            detalle: 'El servicio de productos no respondió. Vuelve a intentarlo.',
            alReintentar: () => cargar(),
          }),
        );
      }
      return;
    }
    if (esta === peticion) {
      pintarPagina(pagina);
    }
  }

  cargar();
  return { recargar: cargar };
}
