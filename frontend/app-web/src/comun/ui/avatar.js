/**
 * La foto de una cuenta, con la inicial del apodo como respaldo.
 *
 * Auditoría de DEV del 30-sep: la foto de perfil devolvía 404 y en medio de la
 * tarjeta se veía su texto alternativo. La causa estaba en el borde (ninguna
 * ruta llevaba `/avatares-subidos/` a ms-identidad) y en el despliegue (las
 * fotos vivían en el disco efímero del contenedor), y se corrige allí. Esto es
 * para que una foto que falte —se borró, la red falló— deje la inicial en su
 * sitio en vez de un hueco con texto.
 *
 * @module comun/ui/avatar
 */

import { h } from './dom.js';
import { inicialDe } from './comunidad/comentario.js';

/**
 * @param {object} [opciones]
 * @param {string|null} [opciones.url] la que devuelve ms-identidad en `avatar`
 * @param {string|null} [opciones.apodo]
 * @param {string} [opciones.clase] clase del kit; la misma para foto e inicial
 * @returns {HTMLElement}
 */
export function fotoDeCuenta({ url = null, apodo = null, clase = 'avatar-vista-previa' } = {}) {
  const nombre = `Avatar de ${apodo || 'la cuenta'}`;
  const conInicial = () =>
    h('span', {
      clase,
      texto: inicialDe(apodo),
      atributos: { role: 'img', 'aria-label': nombre },
      datos: { avatar: 'inicial' },
    });
  if (!url) {
    return conInicial();
  }
  const foto = h('img', {
    clase,
    atributos: { src: url, alt: nombre },
    datos: { avatar: 'foto' },
  });
  foto.addEventListener('error', () => foto.replaceWith(conInicial()), { once: true });
  return foto;
}
