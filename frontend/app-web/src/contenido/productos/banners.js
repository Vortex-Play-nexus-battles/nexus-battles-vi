import { confirmarConMotivo } from '../../comun/ui/dialogo.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { textoDeError } from '../../comun/ui/texto-de-fallo.js';
import { consultar } from '../../plataforma/consola/cliente-consola.js';
import { directorioDeTitulares } from '../../plataforma/consola/titulares.js';
import {
  crearBanner,
  editarBanner,
  historialDeBanner,
  listarBanners,
  retirarBanner,
} from './cliente-banners.js';

/** SA-18: lo que se hizo con un banner, como lo dice quien lo administra. */
export const NOMBRE_DE_OPERACION = Object.freeze({
  ALTA_BANNER: 'Publicación',
  MODIFICACION_BANNER: 'Edición',
  RETIRO_BANNER: 'Retiro',
});

/**
 * Retirar se confirma con el diálogo del kit, no con `window.confirm()`
 * (UXC-7, `sin-dialogos-nativos.test.js`), y desde SA-18 con su motivo: es
 * una acción administrativa que queda en el historial del banner y en la
 * bitácora central (§7.2.5, §7.3.5). Retirar es lógico: el registro queda en
 * la lista.
 *
 * @param {{contenido: string}} banner
 * @returns {Promise<string|null>} el motivo, o null si se canceló
 */
function pedirMotivoDeRetiro(banner) {
  return confirmarConMotivo({
    titulo: '¿Retirar este banner?',
    mensaje: `«${banner.contenido}» deja de mostrarse en la página de inicio de los jugadores desde este momento. El registro se conserva en esta lista.`,
    etiquetaMotivo: 'Motivo del retiro',
    textoConfirmar: 'Retirar',
    peligro: true,
  });
}

function aLocal(instante) {
  const fecha = new Date(instante);
  fecha.setMinutes(fecha.getMinutes() - fecha.getTimezoneOffset());
  return fecha.toISOString().slice(0, 16);
}

function estadoDe(banner) {
  const ahora = Date.now();
  if (banner.retirado) {
    return 'Retirado';
  }
  if (new Date(banner.publicarDesde).getTime() > ahora) {
    return 'Programado';
  }
  if (new Date(banner.vigenteHasta).getTime() <= ahora) {
    return 'Finalizado';
  }
  return 'Vigente';
}

/**
 * El apodo de una cuenta; sin él, que es una cuenta (nunca su identificador).
 *
 * @param {string|null|undefined} uid
 * @param {{de: (uid: string) => ({apodo?: string}|null)}|null} titulares
 */
function apodoDe(uid, titulares) {
  return titulares?.de(uid)?.apodo ?? 'Cuenta sin apodo';
}

/**
 * Quién publicó el banner y quién lo tocó por última vez (SA-18). Los
 * anteriores no lo guardaban: para ellos no se dice nada, no se inventa.
 *
 * @param {{creadoPor?: string|null, modificadoPor?: string|null, creadoEn?: string,
 *   modificadoEn?: string}} banner
 * @param {{de: (uid: string) => ({apodo?: string}|null)}|null} titulares
 * @returns {string|null}
 */
export function autoriaDe(banner, titulares) {
  if (!banner.creadoPor && !banner.modificadoPor) {
    return null;
  }
  const partes = [];
  if (banner.creadoPor) {
    partes.push(`Publicado por ${apodoDe(banner.creadoPor, titulares)}`);
  }
  const editado =
    banner.modificadoPor &&
    (banner.modificadoPor !== banner.creadoPor || banner.modificadoEn !== banner.creadoEn);
  if (editado) {
    partes.push(
      `${banner.retirado ? 'Retirado' : 'Última edición'} por ${apodoDe(banner.modificadoPor, titulares)} el ${fechaHora(banner.modificadoEn)}`,
    );
  }
  return partes.join(' · ');
}

/**
 * El historial de un banner, de lo más reciente a lo más antiguo.
 *
 * @param {Array<{id: string, fecha: string, autor: string, tipo: string,
 *   valorAnterior?: string|null, valorNuevo?: string|null, motivo?: string|null}>} cambios
 * @param {{de: (uid: string) => ({apodo?: string}|null)}|null} titulares
 * @returns {HTMLElement}
 */
export function listaDelHistorial(cambios, titulares) {
  if (!cambios.length) {
    return h('p', {
      clase: 't-meta',
      texto: 'Este banner no tiene cambios registrados todavía.',
    });
  }
  return h('ol', {
    clase: 'banners__cambios',
    hijos: cambios.map((cambio) =>
      h('li', {
        datos: { operacion: cambio.id, tipo: cambio.tipo },
        hijos: [
          h('strong', { texto: NOMBRE_DE_OPERACION[cambio.tipo] ?? 'Cambio' }),
          h('p', {
            clase: 't-meta',
            texto: `${fechaHora(cambio.fecha)} · ${apodoDe(cambio.autor, titulares)}`,
          }),
          cambio.tipo === 'MODIFICACION_BANNER' && cambio.valorAnterior
            ? h('p', { clase: 'banners__valor', texto: `Antes: ${cambio.valorAnterior}` })
            : null,
          cambio.tipo === 'MODIFICACION_BANNER' && cambio.valorNuevo
            ? h('p', { clase: 'banners__valor', texto: `Después: ${cambio.valorNuevo}` })
            : null,
          cambio.motivo ? h('p', { texto: `Motivo: ${cambio.motivo}` }) : null,
        ],
      }),
    ),
  });
}

/**
 * @param {HTMLElement} raiz
 * @param {{crearBanner: Function, editarBanner: Function, listarBanners: Function,
 *          retirarBanner: Function, historialDeBanner?: Function}} [api]
 * @param {{pedirMotivo?: (banner: object) => Promise<string|null>,
 *          titulares?: ReturnType<typeof directorioDeTitulares>|null}} [opciones]
 */
export function montarBanners(
  raiz,
  api = { crearBanner, editarBanner, listarBanners, retirarBanner, historialDeBanner },
  {
    pedirMotivo = pedirMotivoDeRetiro,
    titulares = directorioDeTitulares({ consultarApi: consultar }),
  } = {},
) {
  let editando = null;
  const mensaje = h('p', { atributos: { role: 'status', 'aria-live': 'polite' } });
  const contenido = h('textarea', {
    atributos: { name: 'contenido', required: true, maxlength: 500, rows: 4 },
  });
  const desde = h('input', {
    atributos: { name: 'publicarDesde', type: 'datetime-local', required: true },
  });
  const hasta = h('input', {
    atributos: { name: 'vigenteHasta', type: 'datetime-local', required: true },
  });
  const botonGuardar = h('button', {
    clase: 'boton boton--acento',
    texto: 'Crear banner',
    atributos: { type: 'submit' },
  });
  const cancelar = h('button', {
    clase: 'boton boton--contorno',
    texto: 'Cancelar edición',
    atributos: { type: 'button', hidden: true },
  });
  const formulario = h('form', {
    clase: 'producto-seccion pila',
    hijos: [
      h('h2', { texto: 'Programar anuncio' }),
      h('label', { hijos: ['Contenido ', contenido] }),
      h('div', {
        clase: 'banners__fechas',
        hijos: [
          h('label', { hijos: ['Publicar desde ', desde] }),
          h('label', { hijos: ['Vigente hasta ', hasta] }),
        ],
      }),
      h('div', { clase: 'banners__acciones', hijos: [botonGuardar, cancelar] }),
      mensaje,
    ],
  });
  const lista = h('div', { clase: 'banners__lista' });

  function limpiar() {
    editando = null;
    formulario.reset();
    botonGuardar.textContent = 'Crear banner';
    cancelar.hidden = true;
  }

  /** El panel del historial de un banner, que se carga al abrirlo. */
  function panelDelHistorial(banner) {
    const panel = h('div', {
      clase: 'banners__historial',
      atributos: { id: `historial-${banner.id}`, hidden: true },
    });
    async function cargarCambios() {
      panel.replaceChildren(h('p', { clase: 't-meta', texto: 'Consultando el historial…' }));
      try {
        const cambios = (await api.historialDeBanner?.(banner.id)) ?? [];
        await titulares?.nombrar(cambios.map((cambio) => cambio.autor).filter(Boolean));
        panel.replaceChildren(listaDelHistorial(cambios, titulares));
        panel.dataset.cargado = 'si';
      } catch (error) {
        const reintentar = h('button', {
          clase: 'boton boton--contorno boton--pequeno',
          texto: 'Reintentar',
          atributos: { type: 'button' },
        });
        reintentar.addEventListener('click', () => void cargarCambios());
        panel.replaceChildren(
          h('p', { texto: textoDeError(error, 'No se pudo cargar el historial del banner.') }),
          reintentar,
        );
      }
    }
    return { panel, cargar: cargarCambios };
  }

  async function cargar() {
    mensaje.textContent = 'Cargando banners…';
    try {
      const banners = await api.listarBanners();
      await titulares?.nombrar(
        banners.flatMap((banner) => [banner.creadoPor, banner.modificadoPor]).filter(Boolean),
      );
      vaciar(lista);
      if (!banners.length) {
        lista.append(h('p', { texto: 'Todavía no hay banners programados.' }));
      }
      for (const banner of banners) {
        const editar = h('button', {
          clase: 'boton boton--contorno',
          texto: 'Editar',
          atributos: { type: 'button' },
        });
        editar.addEventListener('click', () => {
          editando = banner.id;
          contenido.value = banner.contenido;
          desde.value = aLocal(banner.publicarDesde);
          hasta.value = aLocal(banner.vigenteHasta);
          botonGuardar.textContent = 'Guardar cambios';
          cancelar.hidden = false;
          contenido.focus();
        });
        const { panel, cargar: cargarHistorial } = panelDelHistorial(banner);
        const verHistorial = h('button', {
          clase: 'boton boton--contorno',
          texto: 'Historial',
          atributos: {
            type: 'button',
            'aria-expanded': 'false',
            'aria-controls': panel.id,
          },
        });
        verHistorial.addEventListener('click', () => {
          const abrir = panel.hidden;
          panel.hidden = !abrir;
          verHistorial.setAttribute('aria-expanded', String(abrir));
          if (abrir && panel.dataset.cargado !== 'si') {
            void cargarHistorial();
          }
        });
        const retirar = h('button', {
          clase: 'boton boton--peligro',
          texto: 'Retirar',
          atributos: { type: 'button', disabled: banner.retirado ? true : null },
        });
        retirar.addEventListener('click', async () => {
          const motivo = await pedirMotivo(banner);
          if (motivo === null || motivo === undefined) {
            return;
          }
          try {
            await api.retirarBanner(banner.id, { motivo });
            await cargar();
          } catch (error) {
            mensaje.textContent = textoDeError(error, 'No se pudo retirar el banner.');
          }
        });
        const autoria = autoriaDe(banner, titulares);
        lista.append(
          h('article', {
            clase: 'tarjeta pila pila--ajustada',
            datos: { banner: banner.id },
            hijos: [
              h('strong', { texto: banner.contenido }),
              h('p', {
                clase: 't-meta',
                texto: `${estadoDe(banner)} · ${fechaHora(banner.publicarDesde)} – ${fechaHora(banner.vigenteHasta)}`,
              }),
              autoria ? h('p', { clase: 't-meta banners__autoria', texto: autoria }) : null,
              h('div', { clase: 'banners__acciones', hijos: [editar, verHistorial, retirar] }),
              panel,
            ],
          }),
        );
      }
      mensaje.textContent = '';
    } catch (error) {
      mensaje.textContent = textoDeError(error, 'No se pudieron cargar los banners.');
    }
  }
  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    mensaje.textContent = 'Guardando…';
    const solicitud = {
      contenido: contenido.value.trim(),
      publicarDesde: new Date(desde.value).toISOString(),
      vigenteHasta: new Date(hasta.value).toISOString(),
    };
    try {
      if (editando) {
        await api.editarBanner(editando, solicitud);
      } else {
        await api.crearBanner(solicitud);
      }
      limpiar();
      mensaje.textContent = 'Banner guardado.';
      await cargar();
    } catch (error) {
      mensaje.textContent = textoDeError(error, 'No se pudo guardar el banner.');
    }
  });
  cancelar.addEventListener('click', limpiar);
  raiz.append(
    h('header', {
      clase: 'productos-cabecera',
      hijos: [
        h('div', {
          hijos: [
            h('p', { clase: 'productos-cabecera__marca', texto: 'NEXUS BATTLES VI' }),
            h('h1', { texto: 'Banners y anuncios' }),
            h('p', { texto: 'Programa, edita y retira mensajes visibles para los jugadores.' }),
          ],
        }),
        h('a', {
          clase: 'productos-cabecera__enlace',
          texto: 'Crear producto',
          atributos: { href: './productos.html' },
        }),
      ],
    }),
    formulario,
    h('section', {
      clase: 'producto-seccion pila',
      hijos: [h('h2', { texto: 'Banners programados' }), lista],
    }),
  );
  cargar();
  return { recargar: cargar };
}
