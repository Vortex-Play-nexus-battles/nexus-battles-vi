/**
 * HU-REN-002 CA-03 y HU-REN-003 CP-01 — el panel de observabilidad.
 */

import {
  informeComoTexto,
  iniciarPanel,
  pintarCargando,
  pintarError,
  pintarInforme,
} from './panel-metricas.js';
import { ErrorDeMetricas, TIPO_PERCENTIL_NO_ACORDADO } from './cliente-metricas.js';

function contenedor() {
  const div = document.createElement('div');
  document.body.append(div);
  return div;
}

/** Informe tal como lo publica contracts/openapi/metricas-plataforma.yaml. */
function informeCompleto(extra = {}) {
  return {
    servicio: 'ms-subastas',
    muestras: 100,
    percentil: 'p95',
    objetivoMs: 500,
    percentilMs: 312,
    maximoMs: 1904,
    cumple: true,
    sinDatos: false,
    porTipo: [
      { tipo: 'lectura', muestras: 99, percentilMs: 10, maximoMs: 40 },
      { tipo: 'escritura', muestras: 1, percentilMs: 900, maximoMs: 900 },
    ],
    operacionesMasLentas: [
      { metodo: 'POST', ruta: '/api/v1/subastas/{subastaId}/pujas', muestras: 1, percentilMs: 900 },
      { metodo: 'GET', ruta: '/api/v1/subastas', muestras: 99, percentilMs: 10 },
    ],
    ...extra,
  };
}

beforeEach(() => {
  document.body.replaceChildren();
});

describe('estados de la vista', () => {
  it('muestra que esta cargando sin dejar la pantalla en blanco', () => {
    const caja = contenedor();

    pintarCargando(caja);

    expect(caja.querySelector('[data-estado="cargando"]')).not.toBeNull();
    expect(caja.querySelectorAll('.esqueleto').length).toBeGreaterThan(0);
    expect(caja.textContent).toContain('Cargando');
  });

  it('un fallo del servicio ofrece reintentar', async () => {
    // MAPEO-ERRORES §5.1: fallo de la vista entera -> Estado de vista + reintentar.
    const caja = contenedor();
    const reintentos = [];

    pintarError(caja, new Error('el servicio no respondio'), () => reintentos.push(1));
    caja.querySelector('[data-accion="reintentar"]').click();

    expect(caja.querySelector('[data-estado="error"]')).not.toBeNull();
    expect(reintentos).toHaveLength(1);
  });

  it('sin muestras no se pinta un verde con ceros', () => {
    // No medir no es lo mismo que cumplir: un servicio que nunca se
    // instrumento no puede parecer el mas rapido del tablero.
    const caja = contenedor();

    pintarInforme(caja, informeCompleto({ sinDatos: true, muestras: 0 }));

    expect(caja.querySelector('[data-estado="sin-datos"]')).not.toBeNull();
    expect(caja.textContent).toContain('No se puede afirmar que cumpla');
    expect(caja.querySelector('[data-tabla="por-tipo"]')).toBeNull();
  });
});

describe('falta la decision del Product Owner', () => {
  it('no se pinta como un fallo ni ofrece reintentar', () => {
    // Reintentar no arregla una decision de negocio pendiente. Mandar a
    // alguien a pulsar un boton inutil es peor que explicarle que falta.
    const caja = contenedor();
    const error = new ErrorDeMetricas(
      {
        type: TIPO_PERCENTIL_NO_ACORDADO,
        title: 'Percentil de evaluacion no acordado',
        criterio: 'HU-REN-001 CA-03',
        muestrasAcumuladas: 1284,
      },
      409,
    );

    pintarError(caja, error, () => {});

    expect(caja.querySelector('[data-estado="sin-percentil"]')).not.toBeNull();
    expect(caja.querySelector('[data-accion="reintentar"]')).toBeNull();
    expect(caja.textContent).toContain('1284 muestras acumuladas');
    expect(caja.textContent).toContain('HU-REN-001 CA-03');
  });

  it('se reconoce por el type y no por el texto', () => {
    const otro = new ErrorDeMetricas(
      {
        type: 'https://nexusbattles.local/errores/otra-cosa',
        title: 'Percentil de evaluacion no acordado',
      },
      409,
    );

    expect(otro.esPercentilNoAcordado()).toBe(false);
  });
});

describe('lecturas y escrituras separadas', () => {
  it('pinta una fila por tipo con su percentil y su maximo', () => {
    const caja = contenedor();

    pintarInforme(caja, informeCompleto());

    const filas = caja.querySelectorAll('[data-tabla="por-tipo"] tbody tr');
    expect(filas).toHaveLength(2);
    expect(filas[0].dataset.tipo).toBe('lectura');
    expect(filas[0].textContent).toContain('10 ms');
    expect(filas[1].dataset.tipo).toBe('escritura');
    expect(filas[1].textContent).toContain('900 ms');
  });

  it('la escritura lenta se ve aunque el percentil global salga bien', () => {
    // Este es el punto de HU-REN-002: sin separar, la puja de 900 ms queda
    // enterrada bajo 99 listados de 10 ms y nadie la ve.
    const caja = contenedor();

    pintarInforme(caja, informeCompleto());

    expect(caja.querySelector('[data-campo="resumen"]').textContent).toContain('312 ms');
    expect(caja.querySelector('[data-tipo="escritura"]').textContent).toContain('900 ms');
  });

  it('con solo lecturas no se inventa una fila de escrituras', () => {
    const caja = contenedor();

    pintarInforme(
      caja,
      informeCompleto({
        porTipo: [{ tipo: 'lectura', muestras: 40, percentilMs: 12, maximoMs: 30 }],
      }),
    );

    expect(caja.querySelectorAll('[data-tabla="por-tipo"] tbody tr')).toHaveLength(1);
    expect(caja.querySelector('[data-tipo="escritura"]')).toBeNull();
  });

  it('con solo escrituras tampoco', () => {
    const caja = contenedor();

    pintarInforme(
      caja,
      informeCompleto({
        porTipo: [{ tipo: 'escritura', muestras: 3, percentilMs: 90, maximoMs: 120 }],
      }),
    );

    expect(caja.querySelectorAll('[data-tabla="por-tipo"] tbody tr')).toHaveLength(1);
    expect(caja.querySelector('[data-tipo="lectura"]')).toBeNull();
  });

  it('sin desglose no se pinta la tabla vacia', () => {
    const caja = contenedor();

    pintarInforme(caja, informeCompleto({ porTipo: [] }));

    expect(caja.querySelector('[data-tabla="por-tipo"]')).toBeNull();
  });
});

describe('el objetivo se presenta como referencia, no como umbral de esta HU', () => {
  it('dice de donde sale el numero', () => {
    // HU-REN-002 no define ningun umbral propio. Presentar los 500 ms como
    // "su" limite seria inventarle un requisito.
    const caja = contenedor();

    pintarInforme(caja, informeCompleto());

    const referencia = caja.querySelector('[data-campo="referencia-objetivo"]').textContent;
    expect(referencia).toContain('RNF-REN-001');
    expect(referencia).toContain('no define un umbral propio');
  });

  it('el veredicto habla del objetivo configurado', () => {
    const caja = contenedor();

    pintarInforme(caja, informeCompleto({ cumple: false }));

    const veredicto = caja.querySelector('[data-campo="veredicto"]');
    expect(veredicto.className).toContain('aviso--error');
    expect(veredicto.textContent).toContain('objetivo configurado');
  });
});

describe('operaciones mas lentas', () => {
  it('lista metodo y ruta de cada una', () => {
    const caja = contenedor();

    pintarInforme(caja, informeCompleto());

    const filas = caja.querySelectorAll('[data-tabla="operaciones"] tbody tr');
    expect(filas).toHaveLength(2);
    expect(filas[0].textContent).toContain('POST /api/v1/subastas/{subastaId}/pujas');
  });
});

describe('exportacion para el acta', () => {
  it('exporta exactamente lo que se esta viendo', () => {
    const texto = informeComoTexto(informeCompleto());

    expect(texto).toContain('ms-subastas');
    expect(texto).toContain('p95: 312 ms');
    expect(texto).toContain('Resultado: CUMPLE');
    expect(texto).toContain('lectura: 10 ms');
    expect(texto).toContain('escritura: 900 ms');
    expect(texto).toContain('RNF-REN-001');
  });

  it('sin datos, el texto tampoco se lee como un verde', () => {
    const texto = informeComoTexto(informeCompleto({ sinDatos: true, muestras: 0 }));

    expect(texto).toContain('SIN DATOS');
    expect(texto).not.toContain('Resultado: CUMPLE');
  });
});

describe('arranque del panel', () => {
  it('pide el informe y lo pinta', async () => {
    const caja = contenedor();

    await iniciarPanel(caja, { obtener: async () => informeCompleto() });

    expect(caja.querySelector('[data-estado="con-datos"]')).not.toBeNull();
    expect(caja.dataset.ultimoInforme).toContain('escritura: 900 ms');
  });

  it('si el servicio falla, deja la pantalla con salida', async () => {
    const caja = contenedor();

    await iniciarPanel(caja, {
      obtener: async () => {
        throw new Error('conexion rechazada');
      },
    });

    expect(caja.querySelector('[data-accion="reintentar"]')).not.toBeNull();
  });

  it('sin contenedor falla con un mensaje claro', async () => {
    await expect(iniciarPanel(null, { obtener: async () => informeCompleto() })).rejects.toThrow(
      /contenedor/,
    );
  });
});
