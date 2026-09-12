/**
 * HU-DIS-003, CA-02 y CP-02 — lo que ve el jugador cuando una seccion se cae.
 */

import {
  TIPO_SECCION_NO_DISPONIBLE,
  atenderRespuestaDegradada,
  esSeccionDegradada,
  limpiarSeccionDegradada,
  pintarSeccionDegradada,
  reintentarEnSegundos,
  seccionDe,
} from './aviso-degradacion.js';

/** Problem detail tal como lo construye ErroresDeDegradacion en el backend. */
function problemaDeInventario(extra = {}) {
  return {
    type: TIPO_SECCION_NO_DISPONIBLE,
    title: 'Inventario no disponible temporalmente',
    status: 503,
    detail:
      'La seccion de Inventario no esta disponible temporalmente. El resto del juego sigue funcionando.',
    instance: '/api/v1/salas/abc/inventario',
    seccion: 'Inventario',
    dependencia: 'inventario',
    reintentarEnSegundos: 30,
    ...extra,
  };
}

function contenedor(seccion = 'Inventario') {
  const div = document.createElement('div');
  div.dataset.seccion = seccion;
  document.body.append(div);
  return div;
}

beforeEach(() => {
  document.body.replaceChildren();
});

describe('reconocer la degradacion', () => {
  it('reconoce el problema por su type', () => {
    expect(esSeccionDegradada(problemaDeInventario())).toBe(true);
  });

  it('no decide por el texto, ni siquiera si el texto suena igual', () => {
    // MAPEO-ERRORES §2: title y detail pueden cambiar de redaccion sin aviso.
    // Programar sobre ellos es lo que ese documento prohibe expresamente.
    const otro = {
      type: 'https://nexusbattles.local/errores/creditos-insuficientes',
      title: 'Inventario no disponible temporalmente',
      status: 503,
    };

    expect(esSeccionDegradada(otro)).toBe(false);
  });

  it('un 503 sin type reconocido no se trata como degradacion', () => {
    expect(esSeccionDegradada({ status: 503, title: 'Algo fallo' })).toBe(false);
    expect(esSeccionDegradada(null)).toBe(false);
    expect(esSeccionDegradada('503')).toBe(false);
  });
});

describe('leer los datos del problema', () => {
  it('toma el nombre de la seccion del backend', () => {
    expect(seccionDe(problemaDeInventario())).toBe('Inventario');
  });

  it('si el backend no manda seccion, usa el respaldo en vez de decir "algo fallo"', () => {
    expect(seccionDe({ type: TIPO_SECCION_NO_DISPONIBLE }, 'Comentarios')).toBe('Comentarios');
    expect(seccionDe({ seccion: '   ' }, 'Comentarios')).toBe('Comentarios');
  });

  it('usa 30 segundos cuando el backend no dice cuanto esperar', () => {
    expect(reintentarEnSegundos(problemaDeInventario({ reintentarEnSegundos: 45 }))).toBe(45);
    expect(reintentarEnSegundos({})).toBe(30);
    expect(reintentarEnSegundos({ reintentarEnSegundos: -5 })).toBe(30);
  });
});

describe('pintar el aviso', () => {
  it('dice QUE funcion esta limitada, no solo que algo fallo', () => {
    const caja = contenedor();

    pintarSeccionDegradada(caja, problemaDeInventario());

    expect(caja.textContent).toContain('Inventario no disponible temporalmente');
  });

  it('deja claro que el resto del juego sigue', () => {
    // El criterio es que la caida de un microservicio no tumbe la aplicacion.
    // Si el aviso no lo dice, el jugador asume que se cayo todo.
    const caja = contenedor();

    pintarSeccionDegradada(caja, problemaDeInventario());

    expect(caja.textContent).toContain('El resto del juego sigue funcionando');
  });

  it('nunca muestra type ni instance al jugador', () => {
    // MAPEO-ERRORES §9: son URIs internas.
    const caja = contenedor();

    pintarSeccionDegradada(caja, problemaDeInventario());

    expect(caja.textContent).not.toContain('nexusbattles.local');
    expect(caja.textContent).not.toContain('/api/v1/');
    expect(caja.textContent).not.toContain('503');
  });

  it('no muestra el nombre interno de la dependencia', () => {
    const caja = contenedor();

    pintarSeccionDegradada(caja, problemaDeInventario({ dependencia: 'ms-inventario-v2' }));

    expect(caja.textContent).not.toContain('ms-inventario-v2');
  });

  it('siempre ofrece una salida: el boton de reintentar', () => {
    // MAPEO-ERRORES §9: no dejar un error sin salida.
    const caja = contenedor();
    const reintentos = [];

    pintarSeccionDegradada(caja, problemaDeInventario(), {
      alReintentar: () => reintentos.push(1),
    });
    caja.querySelector('.seccion-degradada__reintentar').click();

    expect(reintentos).toHaveLength(1);
  });

  it('se anuncia sin interrumpir, porque el resto de la pantalla sigue usable', () => {
    // role="status" y no "alert": una degradacion no es una emergencia.
    const caja = contenedor();

    const aviso = pintarSeccionDegradada(caja, problemaDeInventario());

    expect(aviso.getAttribute('role')).toBe('status');
    expect(aviso.getAttribute('aria-live')).toBe('polite');
  });

  it('no apila avisos: el nuevo reemplaza al anterior', () => {
    const caja = contenedor();

    pintarSeccionDegradada(caja, problemaDeInventario());
    pintarSeccionDegradada(caja, problemaDeInventario());

    expect(caja.querySelectorAll('.seccion-degradada')).toHaveLength(1);
  });

  it('al volver la seccion, el aviso desaparece', () => {
    const caja = contenedor();
    pintarSeccionDegradada(caja, problemaDeInventario());

    limpiarSeccionDegradada(caja);

    expect(caja.children).toHaveLength(0);
    expect(caja.hidden).toBe(true);
  });

  it('sin contenedor falla con un mensaje claro en vez de en silencio', () => {
    expect(() => pintarSeccionDegradada(null, problemaDeInventario())).toThrow(/contenedor/);
  });
});

describe('atender la respuesta HTTP', () => {
  /** Respuesta minima con lo que usa el modulo. */
  function respuesta(ok, cuerpo) {
    return {
      ok,
      clone() {
        return {
          json: async () => {
            if (cuerpo === undefined) {
              throw new Error('no es JSON');
            }
            return cuerpo;
          },
        };
      },
    };
  }

  it('pinta el aviso cuando la respuesta es una seccion degradada', async () => {
    const caja = contenedor();

    const atendida = await atenderRespuestaDegradada(
      respuesta(false, problemaDeInventario()),
      caja,
    );

    expect(atendida).toBe(true);
    expect(caja.textContent).toContain('Inventario no disponible temporalmente');
  });

  it('no toca nada si la respuesta fue correcta', async () => {
    const caja = contenedor();

    expect(await atenderRespuestaDegradada(respuesta(true, {}), caja)).toBe(false);
    expect(caja.children).toHaveLength(0);
  });

  it('deja pasar los demas errores para que se pinten donde les toca', async () => {
    // MAPEO-ERRORES §5: cada error va a un sitio distinto segun de donde vino.
    const caja = contenedor();
    const creditos = {
      type: 'https://nexusbattles.local/errores/creditos-insuficientes',
      status: 422,
    };

    expect(await atenderRespuestaDegradada(respuesta(false, creditos), caja)).toBe(false);
    expect(caja.children).toHaveLength(0);
  });

  it('un 503 sin cuerpo JSON no se adivina como degradacion', async () => {
    const caja = contenedor();

    expect(await atenderRespuestaDegradada(respuesta(false, undefined), caja)).toBe(false);
    expect(caja.children).toHaveLength(0);
  });
});
