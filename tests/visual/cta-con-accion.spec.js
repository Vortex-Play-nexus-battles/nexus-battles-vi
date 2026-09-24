/**
 * Ningun boton visible se queda sin nada que hacer — FI-R14.
 *
 * ## Por que hace falta un guardian y no una revision
 *
 * Un boton sin accion no falla, no avisa y no se ve en una captura: se ve
 * exactamente igual que uno que funciona. La lista de los que ya se
 * encontraron, cada uno descubierto por separado y despues de estar semanas en
 * la rama, dice todo lo que hay que saber:
 *
 * | CTA | Vista | Que hacia |
 * |---|---|---|
 * | «Entrar» de una sala | batallas | `console.log` con el id |
 * | «Confirmar heroe» | validacion-heroe | `console.info` y nada mas |
 * | «Buscar usuario» | gestion-usuarios | `sessionStorage.setItem` y un aviso |
 * | «Pagar» | tienda | habilitado, sin ningun manejador |
 * | el icono del asistente | todas | copiado en cada vista, sin comportamiento |
 *
 * Cinco defectos del mismo tipo, encontrados de cinco maneras distintas y
 * ninguna sistematica. Esto lo hace sistematico.
 *
 * ## Como lo comprueba
 *
 * En el navegador, no leyendo el codigo. Antes de que corra ningun script de
 * la pagina se envuelve `EventTarget.prototype.addEventListener` para anotar
 * que elementos reciben un manejador de `click` o de `submit`. Despues, por
 * cada boton o enlace **visible y habilitado**, se acepta una de estas:
 *
 *  - el elemento, o alguno de sus ancestros, tiene manejador de `click`
 *    (delegacion incluida: el listado engancha la rejilla, no cada tarjeta);
 *  - es `type="submit"` dentro de un `<form>` con manejador de `submit`, o con
 *    `action`;
 *  - es `type="reset"` dentro de un `<form>`: vaciar el formulario lo hace el
 *    navegador, sin que nadie enganche nada. La primera version de este
 *    guardian no lo contemplaba y acuso a «Limpiar» de productos y a «Limpiar
 *    filtros» de subastas, que funcionan los dos;
 *  - es un `<a>` con `href` que va a alguna parte;
 *  - esta `disabled` o `aria-disabled`, y entonces no se le pide nada.
 *
 * Lo que NO se acepta: un boton habilitado cuyo unico camino sea `console.*`.
 * Eso no lo puede ver este guardian —un manejador es un manejador— y lo cubren
 * las pruebas de conducta de cada vista, que afirman el efecto y no la
 * existencia del manejador. Aqui se corta el caso mas barato y mas frecuente:
 * el boton que nadie engancho.
 *
 * ## Lo que este guardian NO ve, dicho para que nadie lo cuente como cubierto
 *
 * El laboratorio corre **sin servicios**. Las vistas que esconden su contenido
 * detras de una comprobacion que necesita al backend no ensenan aqui sus
 * botones, y por tanto no se auditan. El caso comprobado: `gestion-usuarios`
 * decide el acceso contra `/api/v1/rbac/matrix`; sin respuesta falla cerrada y
 * pinta «Esta funcion no esta disponible temporalmente», asi que su formulario
 * de busqueda —uno de los CTA muertos de la lista de arriba— no llega a
 * pintarse. Desconectar su `submit` a proposito **no** pone este guardian en
 * rojo. Cubrirlo pide bancos poblados, que es otro bloque.
 *
 * Lo que si esta comprobado: desconectar el `submit` de «Crear sala» —una
 * vista que si se pinta entera sin servicios— lo pone en rojo con el nombre
 * del boton. El guardian funciona; su alcance es el de lo que el laboratorio
 * consigue pintar, y son 34 vistas.
 *
 * Vive en `tests/visual/` y no en `frontend/app-web/src/`: un PR que toque el
 * frontend no puede borrar de paso a quien lo vigila. Es la misma razon por la
 * que `integracion-viva.spec.js` esta aqui.
 */

import { test, expect } from '@playwright/test';

import { PREFIJO_WEB, VISTAS } from './vistas.js';
import { inyectarSesion } from './identidad.js';
import { conseguirPersonas, personasDe } from './personas.js';

/**
 * Envoltorio de `addEventListener` que marca el elemento con un atributo.
 *
 * Se usa un atributo del DOM y no un WeakSet en el contexto de la pagina
 * porque lo que viene despues corre en `page.evaluate`, en otro mundo: lo
 * unico que cruza es el DOM.
 */
const ESPIA = `
  (() => {
    const original = EventTarget.prototype.addEventListener;
    EventTarget.prototype.addEventListener = function (tipo, ...resto) {
      if ((tipo === 'click' || tipo === 'submit') && this instanceof Element) {
        const ya = this.getAttribute('data-espia-eventos') || '';
        if (!ya.includes(tipo)) {
          this.setAttribute('data-espia-eventos', ya ? ya + ' ' + tipo : tipo);
        }
      }
      return original.call(this, tipo, ...resto);
    };
  })();
`;

/** Lo que se considera «este boton lleva a algo». Corre dentro de la pagina. */
const AUDITAR = () => {
  const visible = (el) => {
    const caja = el.getBoundingClientRect();
    if (caja.width === 0 || caja.height === 0) {
      return false;
    }
    const estilo = getComputedStyle(el);
    return estilo.visibility !== 'hidden' && estilo.display !== 'none';
  };

  const tieneManejador = (el, tipo) => {
    let actual = el;
    while (actual) {
      const anotado = actual.getAttribute?.('data-espia-eventos') ?? '';
      if (anotado.includes(tipo)) {
        return true;
      }
      actual = actual.parentElement;
    }
    // El documento y la ventana tambien pueden delegar; si alguien engancha el
    // documento entero, cualquier boton esta cubierto y este guardian no tiene
    // nada que decir.
    return (
      (document.documentElement.getAttribute('data-espia-eventos') ?? '').includes(tipo) ||
      (document.body.getAttribute('data-espia-eventos') ?? '').includes(tipo)
    );
  };

  const huerfanos = [];
  for (const el of document.querySelectorAll('button, a, [role="button"], [role="tab"]')) {
    if (!visible(el)) {
      continue;
    }
    if (el.disabled || el.getAttribute('aria-disabled') === 'true') {
      continue;
    }

    const etiqueta = (el.getAttribute('aria-label') || el.textContent || '').trim().slice(0, 60);
    if (!etiqueta) {
      // Un control sin nombre accesible es un problema, pero es el de axe.
      continue;
    }

    if (tieneManejador(el, 'click')) {
      continue;
    }

    const formulario = el.closest('form');
    const esEnvio =
      el.tagName === 'BUTTON' && (el.type === 'submit' || !el.getAttribute('type')) && formulario;
    if (esEnvio && (tieneManejador(formulario, 'submit') || formulario.getAttribute('action'))) {
      continue;
    }
    // Un `type="reset"` dentro de un formulario tiene accion nativa: la del
    // navegador, que vacia los campos. No necesita manejador.
    if (el.tagName === 'BUTTON' && el.type === 'reset' && formulario) {
      continue;
    }

    if (el.tagName === 'A') {
      const destino = el.getAttribute('href') ?? '';
      if (destino && destino !== '#' && !destino.startsWith('javascript:')) {
        continue;
      }
    }

    huerfanos.push({
      etiqueta,
      etiquetaHtml: el.tagName.toLowerCase(),
      clase: el.getAttribute('class') ?? '',
      id: el.id || null,
    });
  }
  return huerfanos;
};

let sesiones = {};

test.beforeAll(async () => {
  ({ sesiones } = await conseguirPersonas(null));
});

for (const vista of VISTAS) {
  test(`${vista.id}: ningun boton visible se queda sin accion`, async ({ browser, baseURL }) => {
    const contexto = await browser.newContext({ viewport: { width: 1440, height: 900 }, baseURL });
    try {
      const { para } = personasDe(vista);
      await inyectarSesion(contexto, vista.acceso === 'publica' ? null : (sesiones[para] ?? null));

      const pagina = await contexto.newPage();
      // Antes de cualquier script de la pagina: si se instalara despues, los
      // manejadores que se enganchan al cargar no se verian y el guardian
      // acusaria a botones que si funcionan.
      await pagina.addInitScript(ESPIA);
      await pagina.goto(`/${PREFIJO_WEB}/${vista.ruta}`, { waitUntil: 'domcontentloaded' });
      // Lo mismo que espera la auditoria de accesibilidad: las vistas montan
      // su cabecera y sus datos de forma asincrona.
      await pagina.waitForTimeout(900);

      const huerfanos = await pagina.evaluate(AUDITAR);

      expect(
        huerfanos,
        `En ${vista.id} hay botones visibles y habilitados que no llevan a ninguna parte:\n` +
          huerfanos
            .map((h) => `  · «${h.etiqueta}» (<${h.etiquetaHtml}> ${h.id ? '#' + h.id : h.clase})`)
            .join('\n') +
          '\n\nCada uno tiene que hacer una de estas tres cosas: ejecutar una accion real, ' +
          'navegar de verdad, o estar deshabilitado diciendo por que.',
      ).toEqual([]);
    } finally {
      await contexto.close();
    }
  });
}
