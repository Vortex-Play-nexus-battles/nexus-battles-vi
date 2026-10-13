import { confirmar as confirmarConDialogo } from '../../comun/ui/dialogo.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { textoDeError } from '../../comun/ui/texto-de-fallo.js';
import { crearBanner, editarBanner, listarBanners, retirarBanner } from './cliente-banners.js';

/**
 * Retirar se confirma con el diálogo del kit, no con `window.confirm()`
 * (UXC-7, `sin-dialogos-nativos.test.js`): se lee con lector de pantalla y
 * devuelve el foco. Retirar es lógico: el registro queda en la lista.
 *
 * @param {{contenido: string}} banner
 * @returns {Promise<boolean>}
 */
function confirmarRetiro(banner) {
  return confirmarConDialogo({
    titulo: '¿Retirar este banner?',
    mensaje: `«${banner.contenido}» deja de mostrarse en la página de inicio de los jugadores desde este momento. El registro se conserva en esta lista.`,
    textoConfirmar: 'Retirar',
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
 * @param {HTMLElement} raiz
 * @param {{crearBanner: Function, editarBanner: Function, listarBanners: Function,
 *          retirarBanner: Function}} [api]
 * @param {{confirmar?: (banner: object) => boolean|Promise<boolean>}} [opciones]
 */
export function montarBanners(
  raiz,
  api = { crearBanner, editarBanner, listarBanners, retirarBanner },
  { confirmar = confirmarRetiro } = {},
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
  async function cargar() {
    mensaje.textContent = 'Cargando banners…';
    try {
      const banners = await api.listarBanners();
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
        const retirar = h('button', {
          clase: 'boton boton--peligro',
          texto: 'Retirar',
          atributos: { type: 'button', disabled: banner.retirado ? true : null },
        });
        retirar.addEventListener('click', async () => {
          if (!(await confirmar(banner))) {
            return;
          }
          try {
            await api.retirarBanner(banner.id);
            await cargar();
          } catch (error) {
            mensaje.textContent = textoDeError(error, 'No se pudo retirar el banner.');
          }
        });
        lista.append(
          h('article', {
            clase: 'tarjeta pila pila--ajustada',
            hijos: [
              h('strong', { texto: banner.contenido }),
              h('p', {
                clase: 't-meta',
                texto: `${estadoDe(banner)} · ${fechaHora(banner.publicarDesde)} – ${fechaHora(banner.vigenteHasta)}`,
              }),
              h('div', { clase: 'banners__acciones', hijos: [editar, retirar] }),
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
