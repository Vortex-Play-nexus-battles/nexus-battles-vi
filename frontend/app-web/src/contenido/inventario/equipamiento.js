/**
 * Equipamiento del heroe — HU-INV-005 #85 (RF-INV-005), UX-R2.5.
 *
 * ## Que habia antes
 *
 * Una lista plana: cada objeto equipable de la pagina actual con un boton
 * «Equipar» o «Desequipar» al lado, y arriba una linea de texto con el
 * recuento, «Armas 1/2 · Armadura 3/6 · Ítems 0/2».
 *
 * Era correcto y respetaba los limites, pero no se parecia en nada a equipar
 * a un heroe: no se veia **que ranuras hay**, ni **cuales estan vacias**, ni
 * **que va en cada una**. Para saber si al heroe le faltaba el casco habia que
 * leer un contador y acordarse de cuantas piezas son seis.
 *
 * ## Que hay ahora
 *
 * Las diez ranuras del contrato, dibujadas: 2 armas, 6 armaduras —una por
 * `ParteArmadura`— y 2 items. `EquipamientoHeroe` de `inventario.yaml` las
 * declara exactamente asi (`maxItems: 2`, `maxProperties: 6`), y los limites
 * dejan de ser un texto para ser la forma de la pantalla.
 *
 * Se usa `.ranura` del kit, que llevaba dibujada desde el Figma —vacia,
 * ocupada, seleccionada, bloqueada— sin que ninguna vista la usara.
 *
 * ## Raton, teclado y dedo
 *
 * Cada ranura es un `<button>`: se alcanza con Tab, se activa con Enter y con
 * espacio, y en un movil se toca. **No hay arrastrar y soltar**, ni hace
 * falta: arrastrar sobre una cuadricula de diez casillas en una pantalla de
 * telefono es un ejercicio de punteria, y como unica via dejaria fuera a
 * quien navega con teclado. Si algun dia se anade, sera un atajo encima de
 * esto.
 *
 * ## Lo que decide el servidor
 *
 * Aqui no se valida si un objeto cabe: se ofrece solo lo que **tiene sentido**
 * ofrecer —un arma no aparece entre los candidatos de un casco— y lo demas lo
 * rechaza el servicio con 400, 403 o 409, que estan en el contrato con su
 * significado. Cada uno se traduce a una frase; ninguno ensena su numero.
 */

import { h, vaciar, clases } from '../../comun/ui/dom.js';
import { grupoDeRanuras } from '../../comun/ui/juego/ranura.js';
import { abrirDialogo } from '../../comun/ui/dialogo.js';
import { ICONO_DEL_TIPO, etiquetaDeParte } from './vitrina.js';

/** Las seis partes de `ParteArmadura`, en el orden en que se viste uno. */
export const PARTES_DE_ARMADURA = Object.freeze([
  'CASCO',
  'PECHO',
  'BRAZALETES',
  'GUANTES',
  'PANTALON',
  'ZAPATOS',
]);

/** Cuantas ranuras hay de cada cosa, segun `EquipamientoHeroe`. */
export const LIMITES = Object.freeze({ armas: 2, armaduras: 6, items: 2 });

/**
 * Traduce `EquipamientoHeroe` a la lista de ranuras que se pinta.
 *
 * Se devuelve como dato —no como DOM— para poder comprobarlo sin navegador:
 * que siempre salgan diez, que las ocupadas lleven su elemento y que las
 * vacias sepan que aceptan.
 *
 * @param {{armas: string[], armaduras: Record<string,string>, items: string[]}} equipo
 * @param {Array<object>} elementos elementos conocidos, para resolver los ids
 * @returns {Array<{id: string, grupo: 'armas'|'armaduras'|'items', etiqueta: string,
 *                  acepta: string, parte: string|null, elemento: object|null}>}
 */
export function ranurasDelHeroe(equipo, elementos = []) {
  const porId = new Map((elementos ?? []).map((e) => [e.id, e]));
  const armas = equipo?.armas ?? [];
  const armaduras = equipo?.armaduras ?? {};
  const items = equipo?.items ?? [];

  const ranuras = [];

  for (let i = 0; i < LIMITES.armas; i += 1) {
    const id = armas[i] ?? null;
    ranuras.push({
      id: `arma-${i + 1}`,
      grupo: 'armas',
      etiqueta: `Arma ${i + 1}`,
      acepta: 'ARMA',
      parte: null,
      elemento: id
        ? (porId.get(id) ?? { id, nombrePropio: 'Objeto equipado', tipo: 'ARMA' })
        : null,
    });
  }

  for (const parte of PARTES_DE_ARMADURA) {
    const id = armaduras[parte] ?? null;
    ranuras.push({
      id: `armadura-${parte.toLowerCase()}`,
      grupo: 'armaduras',
      etiqueta: etiquetaDeParte(parte),
      acepta: 'ARMADURA',
      parte,
      elemento: id
        ? (porId.get(id) ?? { id, nombrePropio: 'Pieza equipada', tipo: 'ARMADURA' })
        : null,
    });
  }

  for (let i = 0; i < LIMITES.items; i += 1) {
    const id = items[i] ?? null;
    ranuras.push({
      id: `item-${i + 1}`,
      grupo: 'items',
      etiqueta: `Ítem ${i + 1}`,
      acepta: 'ITEM',
      parte: null,
      elemento: id ? (porId.get(id) ?? { id, nombrePropio: 'Ítem equipado', tipo: 'ITEM' }) : null,
    });
  }

  return ranuras;
}

/**
 * Los elementos que de verdad pueden entrar en una ranura.
 *
 * Un arma no se ofrece para un casco, y una pieza ya equipada en otra ranura
 * tampoco: ofrecerla seria invitar a un 409 que el jugador no ha provocado.
 * Lo no disponible (bloqueado por una subasta, HU-INV-010) se ofrece igual
 * pero marcado, porque el motivo importa mas que esconderlo.
 *
 * @param {{acepta: string, parte: string|null}} ranura
 * @param {Array<object>} elementos
 * @param {Set<string>} yaEquipados
 * @returns {Array<object>}
 */
export function candidatosPara(ranura, elementos = [], yaEquipados = new Set()) {
  return (elementos ?? []).filter((elemento) => {
    if (elemento.tipo !== ranura.acepta) {
      return false;
    }
    if (yaEquipados.has(elemento.id)) {
      return false;
    }
    // La parte manda: un peto no entra en la ranura del casco aunque los dos
    // sean ARMADURA.
    if (ranura.parte && elemento.parteArmadura !== ranura.parte) {
      return false;
    }
    return true;
  });
}

/**
 * Que decirle al jugador cuando el servicio rechaza el cambio.
 *
 * Los cuatro codigos estan en `inventario.yaml` con su significado; aqui se
 * traducen, nunca se ensenan.
 *
 * @param {{status?: number}} fallo
 * @returns {string}
 */
export function motivoDelRechazo(fallo) {
  switch (fallo?.status) {
    case 400:
      return 'Ese objeto no ocupa esa ranura.';
    case 403:
      return 'Ese inventario no es tuyo.';
    case 404:
      return 'Ese objeto ya no está en tu inventario.';
    case 409:
      return 'Esa ranura ya está ocupada o superarías el límite.';
    case 503:
      return 'El inventario no responde ahora mismo. Vuelve a intentarlo en un momento.';
    default:
      return 'No pudimos cambiar el equipamiento. Inténtalo de nuevo.';
  }
}

/**
 * Todos los ids que el heroe lleva puestos.
 *
 * @param {object} equipo
 * @returns {Set<string>}
 */
export function idsEquipados(equipo) {
  return new Set([
    ...(equipo?.armas ?? []),
    ...Object.values(equipo?.armaduras ?? {}),
    ...(equipo?.items ?? []),
  ]);
}

/**
 * Pinta el panel de equipamiento sobre un contenedor.
 *
 * @param {HTMLElement} contenedor
 * @param {object} opciones
 * @param {object} opciones.equipo `EquipamientoHeroe`
 * @param {Array<object>} opciones.elementos elementos conocidos del jugador
 * @param {(ranura: object, elemento: object) => void} opciones.alEquipar
 * @param {(ranura: object) => void} opciones.alDesequipar
 * @param {(raiz: ParentNode) => void} [opciones.alPintarRetratos]
 * @param {((pagina: number) => Promise<object>)|null} [opciones.pedirPagina] FI-R7 —
 *   como pedir otra pagina del inventario. Sin este puerto el selector solo
 *   puede ofrecer `elementos`, que es lo que hacia antes; con el, alcanza todo
 *   el inventario en tandas.
 */
export function pintarEquipamiento(
  contenedor,
  { equipo, elementos, alEquipar, alDesequipar, alPintarRetratos, pedirPagina = null },
) {
  if (!(contenedor instanceof HTMLElement)) {
    throw new TypeError('equipamiento: se esperaba un HTMLElement.');
  }
  vaciar(contenedor);

  const ranuras = ranurasDelHeroe(equipo, elementos);
  const puestos = idsEquipados(equipo);

  const porGrupo = {
    armas: { titulo: 'Armas', ranuras: [] },
    armaduras: { titulo: 'Armadura', ranuras: [] },
    items: { titulo: 'Ítems', ranuras: [] },
  };

  for (const ranura of ranuras) {
    const ocupada = Boolean(ranura.elemento);
    const candidatos = ocupada ? [] : candidatosPara(ranura, elementos, puestos);

    porGrupo[ranura.grupo].ranuras.push({
      etiqueta: ranura.etiqueta,
      objeto: ocupada
        ? {
            nombre: ranura.elemento.nombrePropio,
            icono: ICONO_DEL_TIPO[ranura.elemento.tipo] ?? 'escudo',
          }
        : null,
      // Una ranura vacia sin nada que meterle se marca como bloqueada CON su
      // motivo, en vez de dejar un boton que no hace nada al pulsarlo.
      // Una ranura vacia sin nada que meterle se marca como bloqueada CON su
      // motivo. FI-R7 — pero solo cuando se ha mirado el inventario entero:
      // con `pedirPagina` el selector recorre todas las paginas, asi que decir
      // aqui «no tienes ninguna pieza» seria afirmarlo habiendo leido dieciseis
      // elementos de cincuenta.
      bloqueo:
        !ocupada && candidatos.length === 0 && !pedirPagina
          ? `No tienes ninguna pieza para ${ranura.etiqueta.toLowerCase()}`
          : null,
      alElegir: () => {
        if (ocupada) {
          alDesequipar(ranura);
          return;
        }
        // FI-R7 — con `pedirPagina`, el selector recorre el inventario desde la
        // primera pagina en tandas de dieciseis. No se siembra con los
        // elementos de la pagina que se esta viendo: sembrar y luego recorrer
        // repetiria las mismas piezas en la lista.
        //
        // Sin el puerto se comporta como antes, con lo que haya en `elementos`.
        // Es el camino de las pruebas unitarias del panel, que no tienen
        // servicio detras.
        if (!pedirPagina) {
          elegirObjeto(ranura, candidatos, (elemento) => alEquipar(ranura, elemento));
          return;
        }

        // Las tandas van en cola, no en paralelo: cada una empieza donde acabo
        // la anterior. La cola se encadena de forma sincrona —nada se escribe
        // despues de un await— asi que dos pulsaciones seguidas no pueden pedir
        // la misma pagina ni saltarse una.
        let cola = Promise.resolve(0);
        const traerTanda = () => {
          const tanda = cola.then(async (desde) => {
            const { candidatos: nuevos, siguientePagina } = await juntarCandidatos(
              pedirPagina,
              ranura,
              puestos,
              desde,
            );
            return {
              desde: siguientePagina ?? desde,
              candidatos: nuevos,
              hayMas: siguientePagina !== null,
            };
          });
          cola = tanda.then(
            ({ desde }) => desde,
            // Si una tanda falla, la siguiente reintenta desde el principio:
            // mejor repetir que dejar un hueco sin mirar.
            () => 0,
          );
          return tanda;
        };
        elegirObjeto(ranura, [], (elemento) => alEquipar(ranura, elemento), {
          alCargar: traerTanda,
        });
      },
    });
  }

  for (const grupo of Object.values(porGrupo)) {
    contenedor.append(grupoDeRanuras(grupo.titulo, grupo.ranuras));
  }

  // El recuento sigue escrito: la forma de la pantalla lo dice, pero un lector
  // de pantalla no ve formas.
  contenedor.append(
    h('p', {
      clase: 'equipamiento__resumen t-meta',
      texto:
        `Armas ${(equipo?.armas ?? []).length}/${LIMITES.armas} · ` +
        `Armadura ${Object.keys(equipo?.armaduras ?? {}).length}/${LIMITES.armaduras} · ` +
        `Ítems ${(equipo?.items ?? []).length}/${LIMITES.items}`,
      atributos: { role: 'status', 'aria-live': 'polite' },
    }),
  );

  if (typeof alPintarRetratos === 'function') {
    alPintarRetratos(contenedor);
  }
}

/**
 * Cuantos candidatos se juntan antes de pintar. El mismo dieciseis que el resto
 * del inventario, para que la lista del dialogo se lea como la vitrina.
 */
export const CANDIDATOS_POR_TANDA = 16;

/**
 * Junta candidatos recorriendo paginas del inventario — FI-R7.
 *
 * ## El defecto que esto arregla
 *
 * El selector recibia `paginaMostrada.elementos`: los dieciseis elementos de la
 * pagina que el jugador tenia delante. Un objeto en la pagina 3 **no se podia
 * equipar**: no aparecia entre los candidatos y nada decia por que. Con un
 * inventario de cincuenta piezas, dos tercios del inventario estaban fuera del
 * alcance del panel de equipamiento, y la unica forma de llegar a ellos era
 * adivinar en que pagina estaban y navegar hasta alli antes de abrir la ranura.
 *
 * ## Por que se recorre y no se busca
 *
 * `GET /inventario/elementos/busqueda` existe y acepta el tipo como criterio,
 * pero es una busqueda de texto por subcadena: el criterio «ARMA» trae tambien
 * las ARMADURA. Con eso, una pagina de resultados puede quedarse en cero
 * candidatos tras filtrar mientras quedan mas en las siguientes, y el jugador
 * leeria «no tienes nada» siendo falso.
 *
 * ## Por que no se descarga el inventario entero
 *
 * Se piden paginas hasta juntar una tanda, y se para. Un inventario grande no
 * se baja completo para abrir una ranura; lo que quede se ofrece con «Ver mas».
 *
 * @param {(pagina: number) => Promise<{elementos?: Array, ultima?: boolean, totalPaginas?: number}>} pedirPagina
 * @param {object} ranura
 * @param {Set<string>} yaEquipados
 * @param {number} desde primera pagina del inventario que hay que mirar
 * @param {number} [tanda]
 * @returns {Promise<{candidatos: Array<object>, siguientePagina: number|null}>}
 */
export async function juntarCandidatos(
  pedirPagina,
  ranura,
  yaEquipados,
  desde = 0,
  tanda = CANDIDATOS_POR_TANDA,
) {
  const candidatos = [];
  let pagina = desde;
  // Tope duro por si el servicio devolviera paginas sin fin: mas vale ofrecer
  // menos que colgar el dialogo.
  const TOPE_DE_PAGINAS = 50;

  for (let vueltas = 0; vueltas < TOPE_DE_PAGINAS; vueltas += 1) {
    const traida = await pedirPagina(pagina);
    candidatos.push(...candidatosPara(ranura, traida?.elementos ?? [], yaEquipados));

    const total = Number(traida?.totalPaginas ?? 0);
    const esUltima = traida?.ultima === true || (total > 0 && pagina >= total - 1);
    if (esUltima) {
      return { candidatos, siguientePagina: null };
    }
    pagina += 1;
    if (candidatos.length >= tanda) {
      return { candidatos, siguientePagina: pagina };
    }
  }
  return { candidatos, siguientePagina: pagina };
}

/**
 * Dialogo para elegir que meter en una ranura vacia.
 *
 * Se usa el dialogo del kit y no una lista suelta porque atrapa el foco y se
 * cierra con Escape, que es lo que hace falta para poder elegir sin raton.
 *
 * @param {object} ranura
 * @param {Array<object>} candidatos
 * @param {(elemento: object) => void} alElegir
 * @param {{alCargar?: (() => Promise<{candidatos: Array<object>, hayMas: boolean}>)|null}} [opciones]
 *   FI-R7 — con `alCargar`, el dialogo trae sus candidatos del inventario en
 *   tandas en vez de recibirlos ya resueltos.
 */
function elegirObjeto(ranura, candidatos, alElegir, { alCargar = null } = {}) {
  const lista = h('ul', { clase: 'elegir-objeto' });
  const cuerpo = h('div', { clase: 'pila pila--compacta', hijos: [lista] });
  const nota = h('p', { clase: 't-meta', atributos: { role: 'status' } });

  function pintarOpciones(elementos) {
    for (const elemento of elementos) {
      const disponible = elemento.disponible !== false;
      const boton = h('button', {
        clase: clases('elegir-objeto__opcion', !disponible && 'elegir-objeto__opcion--bloqueada'),
        atributos: {
          type: 'button',
          disabled: !disponible,
          // HU-INV-010: bloqueado por una subasta en curso. Se dice, no se
          // esconde: el jugador tiene que poder entender por que no puede.
          title: disponible ? null : 'Está publicado en una subasta',
        },
        datos: { elegir: elemento.id },
        texto: disponible ? elemento.nombrePropio : `${elemento.nombrePropio} · en subasta`,
      });
      boton.addEventListener('click', () => {
        cerrar();
        alElegir(elemento);
      });
      lista.append(h('li', { hijos: [boton] }));
    }
  }

  pintarOpciones(candidatos);

  const mas = alCargar
    ? h('button', {
        clase: 'boton boton--secundario',
        atributos: { type: 'button' },
        datos: { accion: 'ver-mas-candidatos' },
        texto: 'Ver más del inventario',
      })
    : null;
  if (mas) {
    mas.addEventListener('click', () => cargarTanda());
    cuerpo.append(nota, mas);
  }

  async function cargarTanda() {
    // Se captura la referencia y se comprueba que siga en el documento en vez
    // de reasignar una variable capturada: `remove()` la desengancha, y eso ya
    // es el estado. Una variable escrita despues de un await es una carrera
    // esperando a pasar.
    const boton = mas;
    if (!boton || !boton.isConnected) {
      return;
    }
    boton.disabled = true;
    boton.textContent = 'Buscando…';
    nota.textContent = '';
    try {
      const { candidatos: nuevos, hayMas } = await alCargar();
      pintarOpciones(nuevos);
      if (hayMas) {
        boton.disabled = false;
        boton.textContent = 'Ver más del inventario';
      } else {
        boton.remove();
      }
      if (lista.children.length === 0) {
        // Aqui si se puede afirmar: se ha mirado hasta donde dice el servicio.
        nota.textContent = hayMas
          ? 'Nada de esta tanda entra en esta ranura. Sigue buscando.'
          : 'No tienes nada en el inventario que entre en esta ranura.';
      }
    } catch {
      boton.disabled = false;
      boton.textContent = 'Ver más del inventario';
      nota.textContent = 'No se pudo traer el inventario. Vuelve a intentarlo.';
    }
  }

  const { cerrar } = abrirDialogo({
    titulo: `Elegir para ${ranura.etiqueta}`,
    cuerpo,
  });

  // La primera tanda se pide al abrir: el dialogo no nace vacio esperando que
  // alguien pulse un boton para ensenar lo que ya podia ensenar.
  if (alCargar) {
    nota.textContent = 'Buscando en tu inventario…';
    cargarTanda();
  }
}
