/**
 * Configurador de estrategia — UXC-5 (RotationBuilder).
 *
 * §7.8.5: antes de una misión el jugador configura la IA de su héroe con
 * hasta tres rotaciones de habilidades —prioridad alta, media y baja—. En cada
 * turno la IA intenta la primera; si no es viable, la segunda; luego la
 * tercera; y si ninguna, ataque básico sin gastar poder.
 *
 * Elige uno de TUS héroes (inventario), trae las habilidades que ese
 * prototipo tiene en el nivel elegido —de entrada, el del propio héroe— y su
 * vista previa (servicio de héroes), deja ordenar las rotaciones y las valida
 * contra la regla del servidor (`/estrategias/validacion`).
 *
 * ## Guardarla
 *
 * Quien la guarda es el módulo de misiones (§7.8.12, «configuraciones de
 * rotaciones guardadas»; `misiones.yaml`, `/misiones/estrategias/{heroeId}`).
 * Con su fuente disponible, al elegir un héroe se carga la estrategia que
 * tenga guardada, y en la pestaña Estrategia una estrategia comprobada se
 * puede guardar. El servidor la vuelve a validar con el nivel REAL del héroe:
 * por eso solo se ofrece guardar la del nivel del héroe. En la matrícula no
 * hay botón: matricular ya la guarda. Si el servicio de misiones no responde,
 * se valida igual y la pantalla dice que todavía no se guarda.
 *
 * ## Qué decide la pantalla y qué no
 *
 * La pantalla no decide si una estrategia vale: lo decide el servidor, y su
 * motivo se enseña tal cual (el contrato lo da «apto para el jugador»). Lo
 * único que se controla aquí es la FORMA que el contrato ya fija —como mucho
 * tres rotaciones (`maxItems: 3`), cada una con al menos un paso
 * (`minItems: 1`)— y que cada paso tenga una habilidad elegida. Las opciones
 * de cada paso son las `habilidadesValidas` que devuelve el propio servidor
 * (ERS CU-61 E1), no una lista escrita aquí.
 *
 * @module contenido/misiones/constructor-estrategia
 */

import { h, vaciar } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { aviso } from '../../comun/ui/aviso.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../../comun/ui/estado-vista.js';
import { fecha } from '../../comun/ui/formato.js';
import { retratoDeHeroe } from '../../comun/ui/juego/heroe.js';
import { bloqueDeEstadisticas } from '../../comun/ui/juego/estadisticas.js';
import { identidadDePrototipo } from '../../comun/ui/juego/prototipos.js';
import { consultarPagina } from '../inventario/cliente-inventario.js';
import { consultarProducto as consultarProductoDelCatalogo } from '../inventario/cliente-productos.js';
import { esHeroe, reunirInventario } from '../inventario/coleccion-inventario.js';
import {
  NIVELES,
  NIVEL_POR_OMISION,
  validarEstrategia,
  vistaEnNivel,
} from './cliente-estrategias.js';
import { PRIORIDADES, nombreDeRotacion } from './modelo-misiones.js';

/** `maxItems: 3` de `SolicitudDeEstrategia.rotaciones`: alta, media y baja. */
export const ROTACIONES_MAXIMAS = PRIORIDADES.length;

/** Lo que el servidor ejecuta si ninguna rotación es viable, hasta que lo diga él. */
const ATAQUE_BASICO = 'Ataque básico';

let secuencia = 0;

/**
 * Pone o quita el estado «trabajando» de un botón. Función aparte para que la
 * escritura tras un `await` no parezca una carrera (require-atomic-updates).
 *
 * @param {HTMLButtonElement} boton
 * @param {boolean} activo
 */
function ocupado(boton, activo) {
  boton.disabled = activo;
  if (activo) {
    boton.setAttribute('aria-busy', 'true');
  } else {
    boton.removeAttribute('aria-busy');
  }
}

/**
 * Cómo se llama el héroe para quien lee: su nombre propio y, si se sabe, su
 * prototipo.
 *
 * @param {{nombre: string, prototipo: string|null}} heroe
 * @returns {string}
 */
function nombreCompleto(heroe) {
  const identidad = heroe.prototipo ? identidadDePrototipo(heroe.prototipo) : null;
  return identidad && identidad.nombre !== heroe.nombre
    ? `${heroe.nombre} (${identidad.nombre})`
    : heroe.nombre;
}

/**
 * El mensaje de un fallo al pedir o validar, en el idioma del jugador.
 *
 * @param {Error & {status?: number, detalle?: string}} fallo
 * @param {string} queSeHacia «comprobar la estrategia», «leer sus habilidades»
 * @returns {string}
 */
export function mensajeDeFallo(fallo, queSeHacia) {
  if (fallo?.status === 401 || fallo?.status === 403) {
    return `Tu sesión ya no es válida. Vuelve a iniciar sesión para ${queSeHacia}.`;
  }
  if (fallo?.detalle) {
    return fallo.detalle;
  }
  return `No pudimos ${queSeHacia}. Revisa tu conexión e inténtalo de nuevo.`;
}

/**
 * Los héroes del jugador con su prototipo, que es lo que el servicio de
 * héroes entiende, y su nivel si el inventario lo trae (inventario.yaml
 * 1.6.0: lo sube la experiencia de las misiones). Un héroe cuyo producto no
 * se pudo leer se queda en la lista, deshabilitado y diciendo por qué.
 *
 * @param {object[]} elementos del inventario
 * @param {(productoId: string) => Promise<object>} consultarProducto
 * @returns {Promise<Array<{id: string, nombre: string, prototipo: string|null,
 *   imagen: string|null, nivel: number|null}>>}
 */
export async function heroesConPrototipo(elementos, consultarProducto) {
  const heroes = elementos.filter(esHeroe);
  const productos = await Promise.allSettled(
    heroes.map((heroe) =>
      heroe.productoId ? consultarProducto(heroe.productoId) : Promise.reject(new Error('')),
    ),
  );
  return heroes.map((heroe, indice) => {
    const producto = productos[indice].status === 'fulfilled' ? productos[indice].value : null;
    const prototipo =
      typeof producto?.prototipo === 'string' && producto.prototipo.trim() !== ''
        ? producto.prototipo.trim()
        : null;
    return {
      id: heroe.id,
      nombre: heroe.nombrePropio ?? producto?.nombre ?? 'Héroe',
      prototipo,
      imagen: producto?.imagen ?? null,
      nivel: NIVELES.includes(heroe.nivel) ? heroe.nivel : null,
    };
  });
}

/**
 * Monta el configurador.
 *
 * @param {object} opciones
 * @param {string|null} opciones.identidad apodo de la sesión (cabecera del inventario)
 * @param {'estrategia'|'matricula'} [opciones.modo] `matricula` dentro del
 *   detalle de una misión: el veredicto válido habilita «Iniciar misión».
 * @param {string} [opciones.titulo]
 * @param {number} [opciones.nivelTitulo] nivel del encabezado (2 en su pestaña, 3 en el detalle)
 * @param {(estrategia: ReturnType<ReturnType<typeof constructorDeEstrategia>['estrategia']>) => void}
 *   [opciones.alCambiar] cada vez que cambia lo que se podría enviar
 * @param {string} [opciones.hrefTienda]
 * @param {import('./fuente-misiones.js').FuenteDeMisiones|null} [opciones.fuente] la del
 *   servicio de misiones, para leer y guardar la estrategia de cada héroe; sin ella (o sin
 *   servicio) se valida igual pero no se guarda
 * @param {Function} [opciones.consultar] inyección para pruebas
 * @param {Function} [opciones.consultarProducto]
 * @param {Function} [opciones.validar]
 * @param {Function} [opciones.vista]
 * @returns {{elemento: HTMLElement, cargar: () => Promise<void>,
 *   estrategia: () => ({heroeId: string, heroeNombre: string, prototipo: string, nivel: number,
 *     rotaciones: Array<{pasos: string[]}>}|null)}}
 */
export function constructorDeEstrategia({
  identidad,
  modo = 'estrategia',
  titulo = 'Estrategia de combate',
  nivelTitulo = 2,
  alCambiar = () => {},
  hrefTienda = '../../cuentas/tienda.html',
  fuente = null,
  consultar = consultarPagina,
  consultarProducto = consultarProductoDelCatalogo,
  validar = validarEstrategia,
  vista = vistaEnNivel,
} = {}) {
  secuencia += 1;
  const prefijo = `estrategia-${secuencia}`;

  /** Lo que la pantalla sabe en cada momento. */
  const estado = {
    heroes: [],
    heroe: null,
    nivel: NIVEL_POR_OMISION,
    habilidades: [],
    porDefecto: ATAQUE_BASICO,
    rotaciones: [['']],
    veredicto: null,
    vigente: false,
    peticion: 0,
  };

  const idTitulo = `${prefijo}-titulo`;
  const encabezado = h(`h${nivelTitulo}`, {
    clase: 'estrategia__titulo',
    texto: titulo,
    atributos: { id: idTitulo, tabindex: '-1' },
  });

  /** ¿El servicio de misiones responde y trae esta operación de estrategias? */
  const conMisiones = (operacion) =>
    fuente?.disponible === true && typeof fuente[operacion] === 'function';

  const zonaHeroes = h('div', { clase: 'estrategia__heroes', datos: { zona: 'heroes' } });
  const textoDeNivel = (nivel, delHeroe) =>
    nivel === delHeroe ? `Nivel ${nivel} (el de hoy)` : `Nivel ${nivel}`;
  const selectorNivel = h('select', {
    clase: 'desplegable__control',
    atributos: { id: `${prefijo}-nivel`, name: 'nivel', 'aria-describedby': `${prefijo}-pista` },
    hijos: NIVELES.map((nivel) =>
      h('option', {
        texto: textoDeNivel(nivel, NIVEL_POR_OMISION),
        atributos: { value: String(nivel) },
      }),
    ),
  });
  /** «(el de hoy)» va en el nivel que tiene el héroe elegido. */
  function marcarNivelDelHeroe(delHeroe) {
    for (const opcion of selectorNivel.options) {
      opcion.textContent = textoDeNivel(Number(opcion.value), delHeroe);
    }
  }
  const campoNivel = h('div', {
    clase: 'desplegable estrategia__nivel',
    hijos: [
      h('label', {
        clase: 'campo__etiqueta',
        texto: 'Nivel con el que validar',
        atributos: { for: `${prefijo}-nivel` },
      }),
      selectorNivel,
      h('p', {
        clase: 'campo__pista',
        texto:
          'Los héroes suben de nivel con la experiencia de las misiones. ' +
          'Puedes preparar ya la estrategia de un nivel más alto: en el 4 y en el 8 se desbloquean habilidades. ' +
          'La que se guarda y sale de misión es la del nivel que tiene hoy tu héroe.',
        atributos: { id: `${prefijo}-pista` },
      }),
    ],
  });
  campoNivel.hidden = true;

  const zonaVistaPrevia = h('section', {
    clase: 'estrategia__vista-previa',
    datos: { zona: 'vista-previa' },
    atributos: { 'aria-live': 'polite' },
  });
  zonaVistaPrevia.hidden = true;

  const listaRotaciones = h('ol', { clase: 'estrategia__rotaciones' });
  const botonAnadirRotacion = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    datos: { accion: 'anadir-rotacion' },
    atributos: { type: 'button' },
    hijos: [icono('mas', { etiqueta: null }), h('span', { texto: 'Añadir rotación' })],
  });
  const notaPorDefecto = h('p', { clase: 'estrategia__por-defecto' });
  /** «Es la que guardaste»: se enseña al cargarla y se quita al cambiarla. */
  const notaGuardada = h('p', { clase: 'estrategia__nota', datos: { zona: 'guardada' } });
  notaGuardada.hidden = true;
  const editor = h('fieldset', {
    clase: 'estrategia__editor',
    datos: { zona: 'editor' },
    hijos: [
      h('legend', { clase: 'estrategia__paso-titulo', texto: '2. Ordena sus rotaciones' }),
      h('p', {
        clase: 'estrategia__explicacion',
        texto:
          'En cada turno la IA intenta la rotación de prioridad alta; si no tiene poder o la ' +
          'habilidad está recargando, pasa a la media y luego a la baja.',
      }),
      notaGuardada,
      listaRotaciones,
      botonAnadirRotacion,
      notaPorDefecto,
    ],
  });
  editor.hidden = true;

  const botonComprobar = h('button', {
    clase: 'boton boton--primario',
    datos: { accion: 'comprobar-estrategia' },
    atributos: { type: 'submit' },
    hijos: [
      icono('escudo-check', { etiqueta: null }),
      h('span', { texto: 'Comprobar estrategia' }),
    ],
  });
  const zonaVeredicto = h('div', {
    clase: 'estrategia__veredicto',
    datos: { zona: 'veredicto' },
    atributos: { 'aria-live': 'polite' },
  });
  const acciones = h('div', { clase: 'estrategia__acciones', hijos: [botonComprobar] });
  acciones.hidden = true;

  const formulario = h('form', {
    clase: 'estrategia__formulario',
    atributos: { novalidate: true, 'aria-labelledby': idTitulo },
    hijos: [
      h('fieldset', {
        clase: 'estrategia__eleccion',
        hijos: [
          h('legend', { clase: 'estrategia__paso-titulo', texto: '1. Elige el héroe' }),
          zonaHeroes,
          campoNivel,
        ],
      }),
      zonaVistaPrevia,
      editor,
      acciones,
      zonaVeredicto,
    ],
  });

  const elemento = h('section', {
    clase: 'estrategia tarjeta',
    datos: { componente: 'estrategia', modo },
    atributos: { 'aria-labelledby': idTitulo },
    hijos: [
      h('header', {
        clase: 'estrategia__cabecera',
        hijos: [
          encabezado,
          h('p', {
            clase: 't-meta',
            texto:
              modo === 'matricula'
                ? 'Elige qué héroe envías y cómo pelea. La IA lo dirige con estas rotaciones durante toda la misión.'
                : 'En las misiones tu héroe pelea solo: la IA lo dirige con las rotaciones que le prepares aquí.',
          }),
        ],
      }),
      formulario,
    ],
  });

  /* ---------------------------------------------------------------- estado */

  /** Lo que se podría enviar ahora mismo, o `null` si no está comprobado. */
  function estrategia() {
    if (!estado.heroe || !estado.veredicto?.valida || !estado.vigente) {
      return null;
    }
    return {
      heroeId: estado.heroe.id,
      heroeNombre: estado.heroe.nombre,
      prototipo: estado.heroe.prototipo,
      nivel: estado.nivel,
      rotaciones: (estado.veredicto.rotaciones ?? []).map((r) => ({ pasos: [...r.pasos] })),
    };
  }

  /** La configuración cambió: el veredicto anterior ya no la describe. */
  function invalidarVeredicto() {
    // Tampoco es ya «la que guardaste».
    notaGuardada.hidden = true;
    if (estado.veredicto && estado.vigente) {
      estado.vigente = false;
      vaciar(zonaVeredicto).append(
        aviso({
          tono: 'info',
          titulo: 'Cambiaste la estrategia',
          detalle: 'Vuelve a comprobarla antes de usarla.',
        }),
      );
    }
    alCambiar(estrategia());
  }

  /* --------------------------------------------------------------- héroes */

  function pintarHeroes() {
    vaciar(zonaHeroes);
    if (estado.heroes.length === 0) {
      zonaHeroes.append(
        estadoVacio({
          titulo: 'Todavía no tienes héroes',
          detalle:
            'La estrategia es de un héroe concreto. Las cuentas nuevas reciben uno al prepararse; también se consiguen en la tienda.',
          accion: { texto: 'Ir a la tienda', href: hrefTienda },
        }),
      );
      return;
    }
    const nombre = `${prefijo}-heroe`;
    const lista = h('div', { clase: 'estrategia__lista-heroes' });
    for (const heroe of estado.heroes) {
      const entrada = h('input', {
        clase: 'estrategia__heroe-entrada',
        atributos: {
          type: 'radio',
          name: nombre,
          value: heroe.id,
          disabled: heroe.prototipo ? null : true,
          'aria-describedby': heroe.prototipo ? null : `${prefijo}-sin-prototipo-${heroe.id}`,
        },
        datos: { heroe: heroe.id },
      });
      entrada.checked = estado.heroe?.id === heroe.id;
      entrada.addEventListener('change', () => {
        if (entrada.checked) {
          elegirHeroe(heroe);
        }
      });
      const identidadPrototipo = heroe.prototipo ? identidadDePrototipo(heroe.prototipo) : null;
      lista.append(
        h('label', {
          clase: 'estrategia__heroe',
          datos: { estado: heroe.prototipo ? 'elegible' : 'sin-prototipo' },
          hijos: [
            entrada,
            h('span', {
              clase: 'estrategia__heroe-cara',
              hijos: [
                retratoDeHeroe(
                  { nombre: heroe.nombre, prototipo: heroe.prototipo, imagen: heroe.imagen },
                  { conNombre: false },
                ),
                h('span', {
                  clase: 'estrategia__heroe-texto',
                  hijos: [
                    h('span', { clase: 'estrategia__heroe-nombre', texto: heroe.nombre }),
                    h('span', {
                      clase: 'estrategia__heroe-prototipo',
                      texto: identidadPrototipo?.nombre ?? 'Prototipo desconocido',
                    }),
                    heroe.prototipo
                      ? null
                      : h('span', {
                          clase: 'estrategia__heroe-motivo',
                          texto: 'No pudimos leer su prototipo del catálogo.',
                          atributos: { id: `${prefijo}-sin-prototipo-${heroe.id}` },
                        }),
                  ],
                }),
              ],
            }),
          ],
        }),
      );
    }
    zonaHeroes.append(lista);
  }

  async function cargar() {
    vaciar(zonaHeroes).append(estadoDeCarga({ filas: 2, etiqueta: 'Cargando tus héroes…' }));
    let elementos;
    try {
      ({ elementos } = await reunirInventario(consultar, identidad));
    } catch (fallo) {
      console.error('No se pudo leer el inventario para la estrategia', fallo);
      vaciar(zonaHeroes).append(
        estadoDeError({
          titulo: 'No pudimos traer tus héroes',
          detalle: 'Sin ellos no hay a quién preparar la estrategia. Inténtalo de nuevo.',
          alReintentar: cargar,
        }),
      );
      return;
    }
    const heroes = await heroesConPrototipo(elementos, consultarProducto);
    guardarHeroes(heroes);
    pintarHeroes();
    const primero = heroes.find((heroe) => heroe.prototipo);
    if (primero) {
      // Se busca por valor y no con un selector: el id viene del servidor y
      // no hay por qué escaparlo para meterlo en CSS.
      const entrada = [...zonaHeroes.querySelectorAll('input[type="radio"]')].find(
        (radio) => radio.value === primero.id,
      );
      entrada.checked = true;
      await elegirHeroe(primero);
    }
  }

  function guardarHeroes(heroes) {
    estado.heroes = heroes;
  }

  /* ---------------------------------------------- habilidades y vista previa */

  async function elegirHeroe(heroe) {
    estado.heroe = heroe;
    // De entrada, su nivel de hoy: es con el que se guarda y sale de misión.
    estado.nivel = heroe.nivel ?? NIVEL_POR_OMISION;
    selectorNivel.value = String(estado.nivel);
    marcarNivelDelHeroe(estado.nivel);
    campoNivel.hidden = false;
    await cargarGuardada(heroe);
    if (estado.heroe !== heroe) {
      // Mientras se leía su estrategia, el jugador eligió a otro.
      return;
    }
    await traerHabilidades();
  }

  /**
   * La estrategia que el jugador guardó para este héroe (§7.8.12), si la hay
   * y el servicio de misiones responde. Si no se puede leer, se sigue sin
   * ella: no es motivo para no dejar preparar otra.
   *
   * @param {{id: string}} heroe
   */
  async function cargarGuardada(heroe) {
    notaGuardada.hidden = true;
    if (!conMisiones('estrategiaGuardada')) {
      return;
    }
    let guardada = null;
    try {
      guardada = await fuente.estrategiaGuardada(heroe.id);
    } catch (fallo) {
      console.error('No se pudo leer la estrategia guardada del héroe', fallo);
      return;
    }
    if (guardada && estado.heroe === heroe) {
      aplicarGuardada(guardada);
    }
  }

  /**
   * Pone en el editor las rotaciones guardadas. Las habilidades que el nivel
   * no traiga las vaciará `aplicarHabilidades`, diciendo cuáles.
   *
   * @param {import('./fuente-misiones.js').EstrategiaGuardada} guardada
   */
  function aplicarGuardada(guardada) {
    const rotaciones = (Array.isArray(guardada.rotaciones) ? guardada.rotaciones : [])
      .map((r) =>
        Array.isArray(r?.pasos) ? r.pasos.filter((p) => typeof p === 'string' && p) : [],
      )
      .filter((pasos) => pasos.length > 0)
      .slice(0, ROTACIONES_MAXIMAS);
    if (rotaciones.length === 0) {
      // Guardada sin rotaciones es «solo ataque básico»: no hay nada que poner.
      return;
    }
    estado.rotaciones = rotaciones;
    notaGuardada.textContent = `Es la estrategia que guardaste${
      guardada.actualizadaEn ? ` el ${fecha(guardada.actualizadaEn)}` : ''
    }. Compruébala antes de usarla.`;
    notaGuardada.hidden = false;
  }

  /**
   * Pregunta al servidor qué puede usar el héroe en el nivel elegido (la
   * validación sin rotaciones) y, en paralelo, su vista previa.
   */
  async function traerHabilidades() {
    const { heroe, nivel } = estado;
    const numero = siguientePeticion();
    estado.veredicto = null;
    estado.vigente = false;
    vaciar(zonaVeredicto);
    editor.hidden = true;
    acciones.hidden = true;
    zonaVistaPrevia.hidden = false;
    vaciar(zonaVistaPrevia).append(
      estadoDeCarga({ filas: 2, etiqueta: 'Leyendo sus habilidades…' }),
    );
    alCambiar(null);

    const [consulta, vistaPrevia] = await Promise.allSettled([
      validar({ heroe: heroe.prototipo, nivel }),
      vista(heroe.prototipo, nivel),
    ]);
    if (numero !== estado.peticion) {
      // Otra elección más reciente ya está en camino: esta respuesta es vieja.
      return;
    }
    if (consulta.status === 'rejected') {
      console.error('No se pudieron leer las habilidades del héroe', consulta.reason);
      vaciar(zonaVistaPrevia).append(
        estadoDeError({
          titulo: 'No pudimos leer sus habilidades',
          detalle: mensajeDeFallo(consulta.reason, 'leer sus habilidades'),
          alReintentar: traerHabilidades,
        }),
      );
      return;
    }
    aplicarHabilidades(consulta.value);
    pintarVistaPrevia(vistaPrevia.status === 'fulfilled' ? vistaPrevia.value : null);
    pintarRotaciones();
    editor.hidden = false;
    acciones.hidden = false;
  }

  function siguientePeticion() {
    estado.peticion += 1;
    return estado.peticion;
  }

  /**
   * Lo que el servidor dice que el héroe puede usar. Un paso que ya tenía una
   * habilidad que este nivel no trae se vacía, y se dice cuál.
   *
   * @param {{habilidadesValidas?: string[], comportamientoPorDefecto?: string}} veredicto
   */
  function aplicarHabilidades(veredicto) {
    estado.habilidades = Array.isArray(veredicto.habilidadesValidas)
      ? veredicto.habilidadesValidas
      : [];
    estado.porDefecto = veredicto.comportamientoPorDefecto || ATAQUE_BASICO;
    const perdidas = new Set();
    estado.rotaciones = estado.rotaciones.map((pasos) =>
      pasos.map((paso) => {
        if (paso && !estado.habilidades.includes(paso)) {
          perdidas.add(paso);
          return '';
        }
        return paso;
      }),
    );
    vaciar(zonaVeredicto);
    if (perdidas.size > 0) {
      zonaVeredicto.append(
        aviso({
          tono: 'info',
          titulo: `En nivel ${estado.nivel} no tiene ${[...perdidas].join(', ')}`,
          detalle: 'Esos pasos quedaron sin habilidad: elige otra para cada uno.',
        }),
      );
    }
  }

  /**
   * Las cifras, las acciones con su costo y la épica del prototipo en el
   * nivel elegido. Si la vista previa no llegó, se dice y el editor sigue: las
   * habilidades ya vinieron por la otra consulta.
   *
   * @param {object|null} datos `VistaPorNivel`
   */
  function pintarVistaPrevia(datos) {
    vaciar(zonaVistaPrevia);
    const heroe = estado.heroe;
    zonaVistaPrevia.append(
      h('h3', {
        clase: 'estrategia__vista-previa-titulo',
        texto: `Vista previa: ${nombreCompleto(heroe)} en nivel ${estado.nivel}`,
      }),
    );
    if (!datos) {
      zonaVistaPrevia.append(
        h('p', {
          clase: 'estrategia__nota',
          texto:
            'No pudimos traer sus estadísticas en este nivel. Las habilidades de abajo sí son las suyas.',
        }),
      );
      return;
    }
    const bloque = bloqueDeEstadisticas(datos.estadisticas, { compacto: true });
    if (bloque) {
      zonaVistaPrevia.append(bloque);
    }
    const accionesDelNivel = Array.isArray(datos.accionesDisponibles)
      ? datos.accionesDisponibles
      : [];
    if (accionesDelNivel.length > 0) {
      zonaVistaPrevia.append(
        h('ul', {
          clase: 'estrategia__habilidades',
          atributos: { 'aria-label': 'Habilidades desbloqueadas en este nivel' },
          hijos: accionesDelNivel.map((accion) =>
            h('li', {
              clase: 'estrategia__habilidad',
              hijos: [
                h('span', { clase: 'estrategia__habilidad-nombre', texto: accion.nombre }),
                h('span', {
                  clase: 'estrategia__habilidad-costo',
                  hijos: [icono('rayo', { etiqueta: null }), h('span', { texto: accion.costo })],
                }),
                h('span', { clase: 'estrategia__habilidad-efecto', texto: accion.efecto }),
              ],
            }),
          ),
        }),
      );
    }
    if (datos.epica?.nombre) {
      zonaVistaPrevia.append(
        h('p', {
          clase: 'estrategia__epica',
          hijos: [
            icono('estrella', { etiqueta: null }),
            h('span', { texto: `Épica afín: ${datos.epica.nombre}` }),
          ],
        }),
      );
    }
  }

  /* ------------------------------------------------------------ rotaciones */

  /**
   * Un paso: su desplegable con las habilidades válidas y el botón de quitarlo
   * (si no es el único: una rotación sin pasos no existe, `minItems: 1`).
   */
  function pasoDeRotacion(indiceRotacion, indicePaso, valor) {
    const id = `${prefijo}-r${indiceRotacion}-p${indicePaso}`;
    const selector = h('select', {
      clase: 'desplegable__control',
      atributos: { id, required: true },
      datos: { rotacion: indiceRotacion, paso: indicePaso },
      hijos: [
        h('option', { texto: 'Elige una habilidad', atributos: { value: '' } }),
        ...estado.habilidades.map((habilidad) =>
          h('option', { texto: habilidad, atributos: { value: habilidad } }),
        ),
      ],
    });
    selector.value = valor;
    selector.addEventListener('change', () => {
      estado.rotaciones[indiceRotacion][indicePaso] = selector.value;
      selector.removeAttribute('aria-invalid');
      invalidarVeredicto();
    });
    const pasos = estado.rotaciones[indiceRotacion];
    const quitar =
      pasos.length > 1
        ? h('button', {
            clase: 'boton boton--icono boton--secundario estrategia__quitar-paso',
            datos: { accion: 'quitar-paso' },
            atributos: {
              type: 'button',
              'aria-label': `Quitar el paso ${indicePaso + 1} de la rotación ${indiceRotacion + 1}`,
            },
            hijos: [icono('cerrar', { etiqueta: null })],
          })
        : null;
    quitar?.addEventListener('click', () => {
      estado.rotaciones[indiceRotacion].splice(indicePaso, 1);
      pintarRotaciones({
        foco: `[data-rotacion="${indiceRotacion}"][data-paso="${Math.max(0, indicePaso - 1)}"]`,
      });
      invalidarVeredicto();
    });
    return h('li', {
      clase: 'estrategia__paso',
      hijos: [
        h('label', {
          clase: 'estrategia__paso-etiqueta',
          texto: `Paso ${indicePaso + 1}`,
          atributos: { for: id },
        }),
        selector,
        quitar,
      ],
    });
  }

  function rotacion(indice) {
    const pasos = estado.rotaciones[indice];
    const idTituloRotacion = `${prefijo}-rotacion-${indice}`;
    const anadirPaso = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      datos: { accion: 'anadir-paso' },
      atributos: { type: 'button', 'aria-describedby': idTituloRotacion },
      hijos: [icono('mas', { etiqueta: null }), h('span', { texto: 'Añadir paso' })],
    });
    anadirPaso.addEventListener('click', () => {
      estado.rotaciones[indice].push('');
      pintarRotaciones({
        foco: `[data-rotacion="${indice}"][data-paso="${estado.rotaciones[indice].length - 1}"]`,
      });
      invalidarVeredicto();
    });
    const quitarRotacion =
      estado.rotaciones.length > 1
        ? h('button', {
            clase: 'boton boton--secundario boton--pequeno estrategia__quitar-rotacion',
            datos: { accion: 'quitar-rotacion' },
            atributos: { type: 'button', 'aria-describedby': idTituloRotacion },
            hijos: [icono('papelera', { etiqueta: null }), h('span', { texto: 'Quitar rotación' })],
          })
        : null;
    quitarRotacion?.addEventListener('click', () => {
      estado.rotaciones.splice(indice, 1);
      pintarRotaciones({ foco: '[data-accion="anadir-rotacion"]' });
      invalidarVeredicto();
    });

    return h('li', {
      clase: 'estrategia__rotacion',
      datos: { prioridad: (PRIORIDADES[indice] ?? '').toLowerCase() },
      hijos: [
        h('div', {
          clase: 'estrategia__rotacion-cabecera',
          hijos: [
            h('h4', {
              clase: 'estrategia__rotacion-titulo',
              texto: nombreDeRotacion(indice),
              atributos: { id: idTituloRotacion },
            }),
            quitarRotacion,
          ],
        }),
        h('ol', {
          clase: 'estrategia__pasos',
          hijos: pasos.map((valor, indicePaso) => pasoDeRotacion(indice, indicePaso, valor)),
        }),
        anadirPaso,
      ],
    });
  }

  /**
   * Repinta el editor entero. `foco` es el selector de lo que tiene que
   * recibir el foco después: al quitar un paso, el foco no puede quedarse en
   * un botón que ya no existe.
   *
   * @param {{foco?: string|null}} [opciones]
   */
  function pintarRotaciones({ foco = null } = {}) {
    vaciar(listaRotaciones);
    estado.rotaciones.forEach((_, indice) => listaRotaciones.append(rotacion(indice)));
    botonAnadirRotacion.hidden = estado.rotaciones.length >= ROTACIONES_MAXIMAS;
    notaPorDefecto.replaceChildren(
      icono('espada', { etiqueta: null }),
      h('span', {
        texto: `Si ninguna rotación es viable: ${estado.porDefecto}, sin gastar poder.`,
      }),
    );
    if (foco) {
      (elemento.querySelector(foco) ?? botonAnadirRotacion).focus();
    }
  }

  botonAnadirRotacion.addEventListener('click', () => {
    if (estado.rotaciones.length >= ROTACIONES_MAXIMAS) {
      return;
    }
    estado.rotaciones.push(['']);
    const nueva = estado.rotaciones.length - 1;
    pintarRotaciones({ foco: `[data-rotacion="${nueva}"][data-paso="0"]` });
    invalidarVeredicto();
  });

  selectorNivel.addEventListener('change', () => {
    estado.nivel = Number(selectorNivel.value);
    if (estado.heroe) {
      traerHabilidades();
    }
  });

  /* ------------------------------------------------------------- veredicto */

  /** El primer paso sin habilidad, o `null`. */
  function pasoSinElegir() {
    for (const [indiceRotacion, pasos] of estado.rotaciones.entries()) {
      const indicePaso = pasos.findIndex((paso) => !paso);
      if (indicePaso >= 0) {
        return { indiceRotacion, indicePaso };
      }
    }
    return null;
  }

  function pintarVeredicto(veredicto) {
    vaciar(zonaVeredicto);
    if (!veredicto.valida) {
      zonaVeredicto.append(
        aviso({
          tono: 'error',
          titulo: 'Esta estrategia no vale',
          detalle: veredicto.motivo ?? 'El servidor la rechazó sin decir por qué.',
        }),
        h('p', {
          clase: 'estrategia__nota',
          texto: `Habilidades válidas en nivel ${veredicto.nivel}: ${(veredicto.habilidadesValidas ?? []).join(', ')}.`,
        }),
      );
      return;
    }
    const rotaciones = veredicto.rotaciones ?? [];
    const resumen = h('ol', {
      clase: 'estrategia__resumen',
      atributos: { 'aria-label': 'Estrategia comprobada' },
      hijos: rotaciones.map((r) =>
        h('li', {
          hijos: [
            h('strong', { texto: `Prioridad ${r.prioridad.toLowerCase()}: ` }),
            h('span', { texto: r.pasos.join(' → ') }),
          ],
        }),
      ),
    });
    const respaldo = h('p', {
      clase: 'estrategia__por-defecto',
      hijos: [
        h('strong', { texto: 'Si ninguna es viable: ' }),
        h('span', { texto: veredicto.comportamientoPorDefecto }),
      ],
    });
    const seGuarda = conMisiones('guardarEstrategia');
    let detalle;
    if (modo === 'matricula') {
      detalle = seGuarda
        ? 'Se enviará con tu héroe al iniciar la misión y quedará guardada para la próxima.'
        : 'Se enviará con tu héroe al iniciar la misión.';
    } else {
      detalle = seGuarda
        ? `Guárdala para tenerla lista cuando envíes a ${estado.heroe.nombre} a una misión.`
        : 'Todavía no se guarda: se guarda cuando envías a tu héroe a una misión.';
    }
    // `append` escribiría «null» como texto: solo van las piezas que hay.
    zonaVeredicto.append(
      ...[
        aviso({
          tono: 'exito',
          titulo: `Estrategia válida para ${nombreCompleto(estado.heroe)} en nivel ${veredicto.nivel}`,
          detalle,
        }),
        rotaciones.length > 0 ? resumen : null,
        respaldo,
        modo === 'estrategia' && seGuarda ? zonaDeGuardado() : null,
      ].filter(Boolean),
    );
  }

  /* --------------------------------------------------------------- guardar */

  /**
   * El botón de guardar de una estrategia comprobada, o por qué no se ofrece:
   * el servidor la valida con el nivel REAL del héroe, así que la de otro
   * nivel no se ofrece a guardar (se rechazaría).
   *
   * @returns {HTMLElement}
   */
  function zonaDeGuardado() {
    const zona = h('div', {
      clase: 'estrategia__acciones',
      datos: { zona: 'guardado' },
      atributos: { 'aria-live': 'polite' },
    });
    const delHeroe = estado.heroe.nivel;
    if (Number.isInteger(delHeroe) && estado.nivel !== delHeroe) {
      zona.append(
        h('p', {
          clase: 'estrategia__nota',
          texto: `Solo se guarda la estrategia del nivel que tiene hoy ${estado.heroe.nombre} (nivel ${delHeroe}). Compruébala en ese nivel para guardarla.`,
        }),
      );
      return zona;
    }
    const boton = h('button', {
      clase: 'boton boton--secundario',
      datos: { accion: 'guardar-estrategia' },
      atributos: { type: 'button' },
      hijos: [icono('check', { etiqueta: null }), h('span', { texto: 'Guardar estrategia' })],
    });
    boton.addEventListener('click', () => guardar(boton, zona));
    zona.append(boton);
    return zona;
  }

  /**
   * Guarda lo comprobado. El servidor tiene la última palabra (la valida otra
   * vez con el nivel del inventario): su motivo se enseña tal cual.
   *
   * @param {HTMLButtonElement} boton
   * @param {HTMLElement} zona
   */
  async function guardar(boton, zona) {
    const envio = estrategia();
    if (!envio) {
      return;
    }
    ocupado(boton, true);
    try {
      const guardada = await fuente.guardarEstrategia(envio.heroeId, envio.rotaciones);
      mostrarGuardada(zona, envio.heroeNombre, guardada);
    } catch (fallo) {
      console.error('No se pudo guardar la estrategia', fallo);
      ocupado(boton, false);
      mostrarFalloAlGuardar(zona, boton, fallo);
    }
  }

  function mostrarGuardada(zona, nombre, guardada) {
    zona.replaceChildren(
      aviso({
        tono: 'exito',
        titulo: 'Estrategia guardada',
        detalle: `${nombre} la tendrá lista en sus misiones${
          Number.isInteger(guardada?.nivel) ? ` (validada en nivel ${guardada.nivel})` : ''
        }.`,
      }),
    );
  }

  function mostrarFalloAlGuardar(zona, boton, fallo) {
    const validas = Array.isArray(fallo?.habilidadesValidas) ? fallo.habilidadesValidas : [];
    zona.replaceChildren(
      ...[
        aviso({
          tono: 'error',
          titulo: 'No pudimos guardar la estrategia',
          detalle: mensajeDeFallo(fallo, 'guardar la estrategia'),
        }),
        validas.length > 0
          ? h('p', {
              clase: 'estrategia__nota',
              texto: `Habilidades válidas en su nivel: ${validas.join(', ')}.`,
            })
          : null,
        boton,
      ].filter(Boolean),
    );
  }

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (!estado.heroe) {
      return;
    }
    const falta = pasoSinElegir();
    if (falta) {
      const selector = elemento.querySelector(
        `[data-rotacion="${falta.indiceRotacion}"][data-paso="${falta.indicePaso}"]`,
      );
      selector.setAttribute('aria-invalid', 'true');
      vaciar(zonaVeredicto).append(
        aviso({
          tono: 'advertencia',
          titulo: `Falta elegir el paso ${falta.indicePaso + 1} de la rotación ${falta.indiceRotacion + 1}`,
          detalle: 'Cada paso necesita una habilidad. Si sobra, quítalo.',
        }),
      );
      selector.focus();
      return;
    }
    ocupado(botonComprobar, true);
    try {
      const veredicto = await validar({
        heroe: estado.heroe.prototipo,
        nivel: estado.nivel,
        rotaciones: estado.rotaciones.map((pasos) => ({ pasos })),
      });
      registrarVeredicto(veredicto);
      pintarVeredicto(veredicto);
    } catch (fallo) {
      console.error('No se pudo validar la estrategia', fallo);
      vaciar(zonaVeredicto).append(
        aviso({
          tono: 'error',
          titulo: 'No pudimos comprobar la estrategia',
          detalle: mensajeDeFallo(fallo, 'comprobar la estrategia'),
        }),
      );
    } finally {
      ocupado(botonComprobar, false);
    }
    alCambiar(estrategia());
  });

  function registrarVeredicto(veredicto) {
    estado.veredicto = veredicto;
    estado.vigente = true;
  }

  return { elemento, cargar, estrategia };
}
