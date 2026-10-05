import { consultarAlertasCatalogo } from './cliente-alertas-catalogo.js';
import { abrirDialogo } from '../../comun/ui/dialogo.js';
import { h } from '../../comun/ui/dom.js';
import { fechaHora } from '../../comun/ui/formato.js';

function textoSeguro(valor) {
  return typeof valor === 'string' ? valor : '';
}

function fechaIso(valor) {
  const fecha = new Date(valor);
  return Number.isNaN(fecha.getTime()) ? '' : fecha.toISOString();
}

export function crearVistaAlertasCatalogo(alertas) {
  const lista = h('ul', {
    clase: 'alertas-catalogo__lista pila pila--compacta',
    hijos: alertas.map((alerta) => {
      const fecha = fechaIso(alerta.implementadaEn);
      return h('li', {
        clase: 'tarjeta pila pila--ajustada',
        hijos: [
          h('h3', {
            texto: textoSeguro(alerta.productoNombre) || 'Producto del catálogo',
          }),
          h('p', { texto: textoSeguro(alerta.descripcion) }),
          h('time', {
            clase: 't-meta',
            texto: fecha ? `Implementado el ${fechaHora(fecha)}` : 'Fecha no disponible',
            atributos: { datetime: fecha || null },
          }),
        ],
      });
    }),
  });
  return h('div', {
    clase: 'pila pila--compacta',
    hijos: [
      h('p', { clase: 't-meta', texto: 'Estas novedades se aplicaron desde tu último ingreso.' }),
      lista,
    ],
  });
}

export async function mostrarAlertasCatalogoAlIniciarSesion({
  consultar = consultarAlertasCatalogo,
} = {}) {
  try {
    const alertas = await consultar();
    if (alertas.length === 0) {
      return false;
    }
    return await new Promise((resolver) => {
      const continuar = h('button', {
        clase: 'boton boton--primario',
        texto: 'Continuar',
        atributos: { type: 'button' },
        datos: { accion: 'continuar' },
      });
      const { elemento, cerrar } = abrirDialogo({
        titulo: 'Cambios recientes del catálogo',
        cuerpo: crearVistaAlertasCatalogo(alertas),
        acciones: [continuar],
        cerrableFuera: false,
        alCerrar: () => resolver(true),
      });
      elemento.classList.add('dialogo--ancho');
      continuar.addEventListener('click', cerrar, { once: true });
    });
  } catch {
    return false;
  }
}
