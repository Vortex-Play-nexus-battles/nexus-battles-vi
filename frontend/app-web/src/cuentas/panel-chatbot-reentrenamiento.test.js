/**
 * Pestaña de reentrenamiento del panel del asistente — HU-CHA-012
 * (RF-CHA-014).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import {
  DEBE_ESCALAR,
  editarCasoEnDialogo,
  montarReentrenamiento,
  nombreDeTema,
  resultadoDeEvaluacion,
} from './panel-chatbot-reentrenamiento.js';

const esperar = async (veces = 4) => {
  for (let i = 0; i < veces; i += 1) {
    await new Promise((resolver) => setTimeout(resolver, 0));
  }
};

const PRODUCCION = { id: 'v-1', numero: 1, estado: 'PRODUCCION' };
const RETIRADA = { id: 'v-0', numero: 0, estado: 'RETIRADA' };
const CANDIDATA = { id: 'v-2', numero: 2, estado: 'BORRADOR' };
const TEMAS = [
  { clave: 'clave-pujas', titulo: 'Cómo pujar' },
  { clave: 'clave-contrasena', titulo: 'Cambiar contraseña' },
];
const CASOS = [
  { id: 'c-1', pregunta: 'como pujo', temaClaveEsperada: 'clave-pujas', activo: true },
  { id: 'c-2', pregunta: 'como hackeo', temaClaveEsperada: null, activo: false },
];
const PEOR = {
  candidata: {
    numero: 2,
    casosEvaluados: 2,
    aciertos: 1,
    tasaAcierto: 0.5,
    fallos: [
      {
        casoId: 'c-1',
        pregunta: 'como pujo',
        temaClaveEsperada: 'clave-pujas',
        temaClaveObtenido: null,
      },
    ],
  },
  produccion: { numero: 1, casosEvaluados: 2, aciertos: 2, tasaAcierto: 1, fallos: [] },
};

function clienteFalso(sobrescribir = {}) {
  return {
    listarVersiones: jest.fn(async () => [CANDIDATA, PRODUCCION, RETIRADA]),
    listarCasos: jest.fn(async () => CASOS),
    listarTemas: jest.fn(async () => TEMAS),
    exportarVersion: jest.fn(async () => ({
      nombre: 'v1.json',
      contenido: new Blob([JSON.stringify({ temas: TEMAS })]),
    })),
    evaluarCandidata: jest.fn(async () => ({ ...PEOR, candidataApta: false })),
    desplegarCandidata: jest.fn(async () => ({ ...CANDIDATA, estado: 'PRODUCCION' })),
    revertir: jest.fn(async () => PRODUCCION),
    crearCaso: jest.fn(async () => CASOS[0]),
    editarCaso: jest.fn(async () => CASOS[0]),
    eliminarCaso: jest.fn(async () => null),
    ...sobrescribir,
  };
}

async function montar({ cliente = clienteFalso(), confirmar = true, editarCaso } = {}) {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const confirmarAccion = jest.fn(async () => confirmar);
  const pestana = montarReentrenamiento(raiz, {
    cliente,
    confirmarAccion,
    editarCaso: editarCaso ?? jest.fn(async () => false),
  });
  await pestana.cargar();
  return { raiz, cliente, confirmarAccion };
}

const nota = (raiz) => raiz.querySelector('.panel-chatbot__nota').textContent;

test('nombreDeTema usa el título, y «debe escalar» cuando no hay clave', () => {
  const titulos = new Map([['k', 'Tema K']]);
  expect(nombreDeTema('k', titulos)).toBe('Tema K');
  expect(nombreDeTema('otra', titulos)).toBe('otra');
  expect(nombreDeTema(null, titulos)).toBe(DEBE_ESCALAR);
});

test('resultadoDeEvaluacion muestra los dos puntajes, el veredicto y los fallos', () => {
  const titulos = new Map(TEMAS.map((t) => [t.clave, t.titulo]));
  const caja = resultadoDeEvaluacion({ ...PEOR, candidataApta: false }, titulos);

  const cifras = [...caja.querySelectorAll('.metrica__valor')].map((n) => n.textContent);
  expect(cifras).toEqual(['1 de 2', '2 de 2']);
  expect(caja.querySelector('.aviso--error')).not.toBeNull();
  const fila = caja.querySelector('[data-zona="fallos"] tbody tr');
  expect(fila.textContent).toContain('Cómo pujar');
  expect(fila.textContent).toContain('Escaló a soporte');
});

describe('con candidata', () => {
  test('Evaluar muestra la comparación sin desplegar', async () => {
    const vista = await montar();
    vista.raiz.querySelector('[data-accion="evaluar"]').click();
    await esperar();

    expect(vista.cliente.evaluarCandidata).toHaveBeenCalled();
    expect(vista.cliente.desplegarCandidata).not.toHaveBeenCalled();
    expect(vista.raiz.querySelector('[data-zona="resultado-evaluacion"]')).not.toBeNull();
  });

  test('Desplegar confirma, despliega y avisa', async () => {
    const vista = await montar();
    vista.raiz.querySelector('[data-accion="desplegar"]').click();
    await esperar(6);

    expect(vista.confirmarAccion).toHaveBeenCalled();
    expect(vista.cliente.desplegarCandidata).toHaveBeenCalled();
    expect(nota(vista.raiz)).toContain('está en producción');
  });

  // Paso 9 visto desde la interfaz: el 409 trae los resultados y se muestran.
  test('si la candidata rinde peor, no se despliega y se ven los casos que falla', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        desplegarCandidata: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'Candidata con peor desempeno', ...PEOR }, 409);
        }),
      }),
    });
    vista.raiz.querySelector('[data-accion="desplegar"]').click();
    await esperar(6);

    expect(nota(vista.raiz)).toContain('No se desplegó');
    expect(vista.raiz.querySelector('[data-zona="fallos"]').textContent).toContain('como pujo');
  });

  test('sin casos activos (409 sin resultados) lo explica', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        evaluarCandidata: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'Conflict' }, 409);
        }),
      }),
    });
    vista.raiz.querySelector('[data-accion="evaluar"]').click();
    await esperar();
    expect(nota(vista.raiz)).toContain('al menos un caso de evaluación activo');
  });
});

test('sin candidata no ofrece desplegar y usa los temas de producción', async () => {
  const vista = await montar({
    cliente: clienteFalso({ listarVersiones: jest.fn(async () => [PRODUCCION]) }),
  });

  expect(vista.raiz.querySelector('[data-accion="desplegar"]')).toBeNull();
  expect(vista.raiz.textContent).toContain('No hay una versión candidata');
  expect(vista.cliente.exportarVersion).toHaveBeenCalledWith('v-1');
  expect(vista.raiz.querySelector('[data-zona="tabla-casos"]').textContent).toContain('Cómo pujar');
});

describe('revertir', () => {
  test('confirma, revierte y dice qué versión volvió', async () => {
    const vista = await montar();
    vista.raiz.querySelector('[data-accion="revertir"]').click();
    await esperar(6);

    expect(vista.cliente.revertir).toHaveBeenCalled();
    expect(nota(vista.raiz)).toBe('Se restauró la versión 1.');
  });

  test('sin versiones retiradas el botón está deshabilitado', async () => {
    const vista = await montar({
      cliente: clienteFalso({ listarVersiones: jest.fn(async () => [PRODUCCION]) }),
    });
    expect(vista.raiz.querySelector('[data-accion="revertir"]').disabled).toBe(true);
  });
});

describe('casos de evaluación', () => {
  test('lista los casos con el título del tema esperado o «debe escalar»', async () => {
    const vista = await montar();
    const filas = vista.raiz.querySelectorAll('[data-zona="tabla-casos"] tbody tr');
    expect(filas[0].textContent).toContain('Cómo pujar');
    expect(filas[1].textContent).toContain(DEBE_ESCALAR);
    expect(filas[1].textContent).toContain('Inactivo');
  });

  test('Agregar caso usa los temas disponibles y crea el caso', async () => {
    const editarCaso = jest.fn(async () => true);
    const vista = await montar({ editarCaso });
    vista.raiz.querySelector('[data-accion="agregar-caso"]').click();
    await esperar();

    expect(editarCaso).toHaveBeenCalledWith(expect.objectContaining({ temas: TEMAS }));
    await editarCaso.mock.calls[0][0].guardar({ pregunta: 'x', temaClaveEsperada: null });
    expect(vista.cliente.crearCaso).toHaveBeenCalledWith({
      pregunta: 'x',
      temaClaveEsperada: null,
    });
  });

  test('Eliminar pide confirmación', async () => {
    const vista = await montar();
    vista.raiz.querySelector('[data-accion="eliminar-caso"]').click();
    await esperar(6);
    expect(vista.cliente.eliminarCaso).toHaveBeenCalledWith('c-1');
  });
});

describe('formulario de un caso', () => {
  test('envía la pregunta y el tema elegido; vacío = debe escalar', async () => {
    document.body.replaceChildren();
    const guardar = jest.fn(async () => undefined);
    const resultado = editarCasoEnDialogo({ titulo: 'Agregar caso', temas: TEMAS, guardar });
    const dialogo = document.querySelector('[role="dialog"]');

    dialogo.querySelector('[data-accion="guardar-caso"]').click();
    await esperar(1);
    expect(guardar).not.toHaveBeenCalled();

    dialogo.querySelector('textarea[name="pregunta"]').value = ' como pujo ';
    dialogo.querySelector('select[name="temaClaveEsperada"]').value = 'clave-pujas';
    dialogo.querySelector('[data-accion="guardar-caso"]').click();

    await expect(resultado).resolves.toBe(true);
    expect(guardar).toHaveBeenCalledWith({
      pregunta: 'como pujo',
      temaClaveEsperada: 'clave-pujas',
      activo: true,
    });
  });
});
