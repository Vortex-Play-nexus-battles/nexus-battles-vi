/**
 * HU-SAL-001 — Vista de creacion de sala.
 *
 * Se prueba el comportamiento que ve la persona: el estado de carga, el exito,
 * y sobre todo que un rechazo por campos marque el campo concreto en vez de
 * soltar un aviso general. El requisito exige indicar el motivo.
 */

// Con modulos ES, Jest NO inyecta `jest` como global: hay que importarlo.
// Misma linea que ya tiene inventario.test.js.
import { jest } from '@jest/globals';

import {
  montarCrearSala,
  leerFormulario,
  encuentroDesde,
  prefijarEncuentro,
  tonoPara,
  limitesDe,
  maximoDeMaquinas,
  ajustarPorModalidad,
  rutaDeLaSala,
  textoDeSalaCreada,
} from './crear-sala.js';
import { ErrorDeApi } from './cliente-salas.js';

const HTML = `
  <form id="f" novalidate>
    <div data-zona="aviso" hidden></div>
    <div data-zona="degradacion" data-seccion="Inventario" hidden></div>

    <div class="campo">
      <label class="campo__etiqueta" for="maximoParticipantes">Participantes</label>
      <input class="campo__control" id="maximoParticipantes" name="maximoParticipantes"
             type="number" min="2" max="6" value="4" />
      <p class="campo__pista" data-zona="pista-participantes">Entre 2 y 6 jugadores.</p>
    </div>

    <div class="campo">
      <label class="campo__etiqueta" for="recompensaCreditos">Recompensa</label>
      <input class="campo__control" id="recompensaCreditos" name="recompensaCreditos"
             type="number" value="0" />
    </div>

    <input type="radio" name="modalidad" value="UNO_CONTRA_UNO" />
    <input type="radio" name="modalidad" value="CONTRA_IA" />
    <input type="radio" name="modalidad" value="HASTA_SEIS" checked />

    <p data-zona="nota-contra-ia" hidden>Un rival controlado por la IA ocupa el segundo cupo.</p>
    <div data-zona="opciones-hasta-seis" hidden>
      <input name="heroesIA" type="number" min="0" max="5" value="0" />
      <select name="tamanoEquipo">
        <option value="" selected>Sin equipos</option>
        <option value="2">Equipos de 2</option>
        <option value="3">Equipos de 3</option>
      </select>
    </div>
    <input type="checkbox" name="privada" />
    <p data-zona="nota-torneo" hidden></p>

    <button type="submit">CREAR SALA</button>
  </form>
`;

function preparar() {
  document.body.innerHTML = HTML;
  return document.getElementById('f');
}

function elegir(formulario, modalidad) {
  formulario.querySelector(`[value="${modalidad}"]`).checked = true;
  formulario
    .querySelector(`[value="${modalidad}"]`)
    .dispatchEvent(new Event('change', { bubbles: true }));
}

/** Deja que se resuelvan las promesas encadenadas del manejador de submit. */
const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('leerFormulario', () => {
  test('arma el cuerpo del contrato con los tipos correctos', () => {
    const formulario = preparar();

    expect(leerFormulario(formulario)).toEqual({
      maximoParticipantes: 4,
      modalidad: 'HASTA_SEIS',
      recompensaCreditos: 0,
      incluirHeroeIA: false,
      heroesIA: 0,
      privada: false,
      tamanoEquipo: null,
    });
  });

  test('no manda tamanoEquipo ni maquinas en una modalidad que no las admite', () => {
    const formulario = preparar();
    formulario.querySelector('[name="heroesIA"]').value = '3';
    formulario.querySelector('[name="tamanoEquipo"]').value = '2';
    formulario.querySelector('[value="UNO_CONTRA_UNO"]').checked = true;

    const cuerpo = leerFormulario(formulario);
    expect(cuerpo.tamanoEquipo).toBeNull();
    expect(cuerpo.heroesIA).toBe(0);
    expect(cuerpo.incluirHeroeIA).toBe(false);
  });

  test('contra la IA la máquina va siempre, aunque el campo diga cero', () => {
    const formulario = preparar();
    formulario.querySelector('[value="CONTRA_IA"]').checked = true;

    const cuerpo = leerFormulario(formulario);
    expect(cuerpo.heroesIA).toBe(1);
    expect(cuerpo.incluirHeroeIA).toBe(true);
  });

  test('hasta seis manda las maquinas y el tamano de equipo elegidos', () => {
    const formulario = preparar();
    formulario.querySelector('[name="heroesIA"]').value = '2';
    formulario.querySelector('[name="tamanoEquipo"]').value = '3';

    const cuerpo = leerFormulario(formulario);
    expect(cuerpo.heroesIA).toBe(2);
    expect(cuerpo.incluirHeroeIA).toBe(true);
    expect(cuerpo.tamanoEquipo).toBe(3);
  });

  test('la casilla antigua de RF-JUE-001 sigue valiendo por una máquina', () => {
    document.body.innerHTML = `
      <form id="f">
        <input name="maximoParticipantes" value="4" />
        <input type="radio" name="modalidad" value="HASTA_SEIS" checked />
        <input type="checkbox" name="incluirHeroeIA" checked />
      </form>`;

    expect(leerFormulario(document.getElementById('f')).heroesIA).toBe(1);
  });
});

describe('encuentro de torneo (HU-TOR-004 CA-04)', () => {
  test('encuentroDesde lee ?torneo=&encuentro= y rechaza lo que no es un encuentro 1..14', () => {
    expect(encuentroDesde('?torneo=t-1&encuentro=3')).toEqual({
      torneoId: 't-1',
      numeroEncuentro: 3,
    });
    expect(encuentroDesde('')).toBeNull();
    expect(encuentroDesde('?torneo=t-1')).toBeNull();
    expect(encuentroDesde('?torneo=t-1&encuentro=15')).toBeNull();
    expect(encuentroDesde('?torneo=t-1&encuentro=abc')).toBeNull();
    expect(encuentroDesde('?encuentro=2')).toBeNull();
  });

  test('sin encuentro el cuerpo no lleva torneo: el cuerpo 1.3.0 sigue igual', () => {
    const formulario = preparar();
    expect(leerFormulario(formulario)).not.toHaveProperty('torneo');
  });

  test('prefijar deja el vinculo en campos ocultos, muestra la nota y sugiere 4 en equipos de 2', () => {
    const formulario = preparar();
    elegir(formulario, 'UNO_CONTRA_UNO');

    prefijarEncuentro(formulario, { torneoId: 't-1', numeroEncuentro: 3 });

    const cuerpo = leerFormulario(formulario);
    expect(cuerpo.torneo).toEqual({ torneoId: 't-1', numeroEncuentro: 3 });
    expect(cuerpo.modalidad).toBe('HASTA_SEIS');
    expect(cuerpo.maximoParticipantes).toBe(4);
    expect(cuerpo.tamanoEquipo).toBe(2);
    const nota = formulario.querySelector('[data-zona="nota-torneo"]');
    expect(nota.hidden).toBe(false);
    expect(nota.textContent).toContain('encuentro 3');
    expect(formulario.querySelectorAll('input[name="torneoId"]')).toHaveLength(1);

    // Volver a prefijar no duplica los campos ocultos.
    prefijarEncuentro(formulario, { torneoId: 't-1', numeroEncuentro: 3 });
    expect(formulario.querySelectorAll('input[name="torneoId"]')).toHaveLength(1);
  });

  test('montar con encuentro manda torneo al servicio y lo conserva tras crear (reset)', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest
      .fn()
      .mockResolvedValue({ id: 'a1', maximoParticipantes: 4, recompensaCreditos: 0 });
    montarCrearSala(formulario, {
      crearSalaImpl,
      encuentro: { torneoId: 't-1', numeroEncuentro: 1 },
    });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    expect(crearSalaImpl).toHaveBeenCalledWith(
      expect.objectContaining({ torneo: { torneoId: 't-1', numeroEncuentro: 1 } }),
    );
    expect(leerFormulario(formulario).torneo).toEqual({ torneoId: 't-1', numeroEncuentro: 1 });
  });
});

describe('limites por modalidad (RF-JUE-004)', () => {
  test('cada modalidad tiene su aforo y su cupo de maquinas, como el contrato', () => {
    expect(limitesDe('UNO_CONTRA_UNO')).toEqual({
      participantes: { min: 2, max: 2 },
      heroesIA: null,
      equipos: false,
    });
    expect(limitesDe('CONTRA_IA').heroesIA).toEqual({ min: 1, max: 1 });
    expect(limitesDe('HASTA_SEIS')).toEqual({
      participantes: { min: 2, max: 6 },
      heroesIA: { min: 0, max: 5 },
      equipos: true,
    });
  });

  test('las maquinas nunca ocupan el cupo del anfitrion', () => {
    expect(maximoDeMaquinas('HASTA_SEIS', 6)).toBe(5);
    expect(maximoDeMaquinas('HASTA_SEIS', 3)).toBe(2);
    expect(maximoDeMaquinas('CONTRA_IA', 2)).toBe(1);
    expect(maximoDeMaquinas('UNO_CONTRA_UNO', 2)).toBe(0);
  });

  test('al elegir 1 contra 1 el aforo se fija en 2 y se esconden las opciones de hasta seis', () => {
    const formulario = preparar();
    montarCrearSala(formulario, { crearSalaImpl: jest.fn() });
    const participantes = formulario.querySelector('[name="maximoParticipantes"]');
    expect(formulario.querySelector('[data-zona="opciones-hasta-seis"]').hidden).toBe(false);

    elegir(formulario, 'UNO_CONTRA_UNO');

    expect(participantes.value).toBe('2');
    expect(participantes.max).toBe('2');
    expect(participantes.readOnly).toBe(true);
    expect(formulario.querySelector('[data-zona="pista-participantes"]').textContent).toBe(
      'Exactamente 2 jugadores.',
    );
    expect(formulario.querySelector('[data-zona="opciones-hasta-seis"]').hidden).toBe(true);
    expect(formulario.querySelector('[data-zona="nota-contra-ia"]').hidden).toBe(true);
  });

  test('contra la IA muestra la nota de que la máquina ocupa el segundo cupo', () => {
    const formulario = preparar();
    montarCrearSala(formulario, { crearSalaImpl: jest.fn() });

    elegir(formulario, 'CONTRA_IA');

    expect(formulario.querySelector('[data-zona="nota-contra-ia"]').hidden).toBe(false);
    expect(formulario.querySelector('[name="maximoParticipantes"]').value).toBe('2');
  });

  test('en hasta seis el tope de maquinas sigue al aforo y recorta lo que sobra', () => {
    const formulario = preparar();
    montarCrearSala(formulario, { crearSalaImpl: jest.fn() });
    const participantes = formulario.querySelector('[name="maximoParticipantes"]');
    const maquinas = formulario.querySelector('[name="heroesIA"]');
    maquinas.value = '5';

    participantes.value = '3';
    participantes.dispatchEvent(new Event('change', { bubbles: true }));

    expect(maquinas.max).toBe('2');
    expect(maquinas.value).toBe('2');
    expect(ajustarPorModalidad(formulario)).toEqual({
      modalidad: 'HASTA_SEIS',
      participantes: { min: 2, max: 6 },
    });
  });

  test('volver a hasta seis reabre el aforo sin perder el valor valido', () => {
    const formulario = preparar();
    montarCrearSala(formulario, { crearSalaImpl: jest.fn() });
    elegir(formulario, 'UNO_CONTRA_UNO');

    elegir(formulario, 'HASTA_SEIS');

    const participantes = formulario.querySelector('[name="maximoParticipantes"]');
    expect(participantes.readOnly).toBe(false);
    expect(participantes.max).toBe('6');
    expect(participantes.value).toBe('2');
  });
});

describe('tonoPara', () => {
  test('un fallo del sistema es error; lo corregible es advertencia', () => {
    expect(tonoPara(500)).toBe('error');
    expect(tonoPara(503)).toBe('error');
    expect(tonoPara(422)).toBe('advertencia');
    expect(tonoPara(400)).toBe('advertencia');
    expect(tonoPara(401)).toBe('advertencia');
    expect(tonoPara(404)).toBe('info');
  });
});

describe('montarCrearSala', () => {
  test('al crear la sala muestra un aviso de exito y limpia el formulario', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest
      .fn()
      .mockResolvedValue({ id: 'a1', maximoParticipantes: 4, recompensaCreditos: 0 });
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const aviso = document.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--exito');
    expect(aviso.textContent).toContain('4 participantes');
    expect(document.querySelector('[data-zona="aviso"]').hidden).toBe(false);
  });

  test('R18 · al crear la sala ofrece entrar a ella, y entrar lleva a su sala', async () => {
    const formulario = preparar();
    const irALaSala = jest.fn();
    const crearSalaImpl = jest
      .fn()
      .mockResolvedValue({ id: 'sala 7', maximoParticipantes: 2, recompensaCreditos: 0 });
    montarCrearSala(formulario, { crearSalaImpl, irALaSala });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const entrar = document.querySelector('.aviso [data-accion="entrar-a-la-sala"]');
    expect(entrar).not.toBeNull();
    expect(entrar.textContent).toBe('Entrar a la sala');
    expect(irALaSala).not.toHaveBeenCalled();

    entrar.click();
    expect(irALaSala).toHaveBeenCalledWith('./sala-batalla.html?sala=sala%207');
  });

  test('R18 · contra la IA no manda a esperar a nadie: el rival ya esta dentro', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockResolvedValue({
      id: 'ia1',
      modalidad: 'CONTRA_IA',
      maximoParticipantes: 2,
      ocupacion: 2,
      recompensaCreditos: 0,
    });
    montarCrearSala(formulario, { crearSalaImpl, irALaSala: jest.fn() });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const aviso = document.querySelector('.aviso');
    expect(aviso.textContent).toContain('Tu rival ya está en la sala');
    expect(aviso.textContent).not.toContain('esperando jugadores');
  });

  test('R18 · tras crear, el formulario vuelve entero a su modalidad por omision', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest
      .fn()
      .mockResolvedValue({ id: 'x', maximoParticipantes: 2, recompensaCreditos: 0 });
    montarCrearSala(formulario, { crearSalaImpl, irALaSala: jest.fn() });
    elegir(formulario, 'CONTRA_IA');
    expect(formulario.querySelector('[data-zona="nota-contra-ia"]').hidden).toBe(false);

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    // El HTML de la prueba trae «hasta seis» y 4 como valores iniciales: el
    // reset vuelve a ellos, y la nota y la pista tienen que acompanarlos.
    expect(formulario.querySelector('[value="HASTA_SEIS"]').checked).toBe(true);
    expect(formulario.querySelector('[name="maximoParticipantes"]').value).toBe('4');
    expect(formulario.querySelector('[data-zona="nota-contra-ia"]').hidden).toBe(true);
    expect(formulario.querySelector('[data-zona="pista-participantes"]').textContent).toMatch(
      /^Entre \d y \d jugadores\.$/,
    );
  });

  test('mientras espera, el botón se bloquea y lo dice', async () => {
    const formulario = preparar();
    let resolver;
    const crearSalaImpl = jest.fn(() => new Promise((r) => (resolver = r)));
    montarCrearSala(formulario, { crearSalaImpl });
    const boton = formulario.querySelector('[type="submit"]');

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    expect(boton.disabled).toBe(true);
    expect(boton.getAttribute('aria-busy')).toBe('true');
    expect(boton.textContent).toMatch(/creando/i);

    resolver({ id: 'a1', maximoParticipantes: 4, recompensaCreditos: 0 });
    await asentar();

    expect(boton.disabled).toBe(false);
    expect(boton.getAttribute('aria-busy')).toBe('false');
  });

  test('al terminar restaura el literal de la vista, no uno impuesto por el modulo', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest
      .fn()
      .mockResolvedValue({ id: 'a1', maximoParticipantes: 4, recompensaCreditos: 0 });
    montarCrearSala(formulario, { crearSalaImpl });
    const boton = formulario.querySelector('[type="submit"]');

    formulario.dispatchEvent(new Event('submit'));
    await asentar();
    expect(boton.textContent).toBe('CREAR SALA');

    // Y una segunda vez: el texto guardado no se contamina con «Creando…».
    formulario.dispatchEvent(new Event('submit'));
    await asentar();
    expect(boton.textContent).toBe('CREAR SALA');
  });

  test('un rechazo por campos marca el campo, no suelta un aviso general', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/parametros-invalidos',
          title: 'Revisa los datos de la sala',
          status: 400,
          detail: 'Hay 1 campo que corregir.',
          errores: [
            {
              campo: 'maximoParticipantes',
              mensaje: 'Esta modalidad admite entre 2 y 6 jugadores.',
            },
          ],
        },
        400,
      ),
    );
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const control = formulario.querySelector('[name="maximoParticipantes"]');
    expect(control.closest('.campo').classList.contains('campo--invalido')).toBe(true);
    expect(control.getAttribute('aria-invalid')).toBe('true');
    expect(formulario.querySelector('.campo__error').textContent).toContain('2 y 6');
    expect(document.querySelector('.aviso')).toBeNull();
    expect(document.activeElement).toBe(control);
  });

  test('los errores de campo anteriores se limpian antes del siguiente intento', async () => {
    const formulario = preparar();
    const conError = new ErrorDeApi(
      { status: 400, errores: [{ campo: 'maximoParticipantes', mensaje: 'Fuera de rango.' }] },
      400,
    );
    const crearSalaImpl = jest
      .fn()
      .mockRejectedValueOnce(conError)
      .mockResolvedValueOnce({ id: 'a1', maximoParticipantes: 4, recompensaCreditos: 0 });
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();
    expect(formulario.querySelectorAll('.campo__error')).toHaveLength(1);

    formulario.dispatchEvent(new Event('submit'));
    await asentar();
    expect(formulario.querySelectorAll('.campo__error')).toHaveLength(0);
    expect(formulario.querySelectorAll('.campo--invalido')).toHaveLength(0);
  });

  test('los créditos sin integrar salen como aviso de error, con su motivo', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/creditos-sin-integrar',
          title: 'Las apuestas todavía no están disponibles',
          status: 503,
          detail: 'Por ahora solo se pueden crear salas sin recompensa.',
        },
        503,
      ),
    );
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const aviso = document.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--error');
    expect(aviso.textContent).toContain('apuestas todavía no están disponibles');
    expect(aviso.textContent).toContain('sin recompensa');
    expect(aviso.getAttribute('role')).toBe('alert');
  });

  test('los créditos insuficientes salen como advertencia diciendo cuanto falta', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/creditos-insuficientes',
          title: 'Creditos insuficientes',
          status: 422,
          detail: 'Tienes 240 créditos y necesitas 400 para crear esta sala.',
        },
        422,
      ),
    );
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const aviso = document.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--advertencia');
    expect(aviso.textContent).toContain('240');
    expect(aviso.textContent).toContain('400');
  });

  test('una caida de red no deja a la persona sin mensaje', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const aviso = document.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--error');
    expect(aviso.textContent).toMatch(/conexión/i);
    expect(formulario.querySelector('[type="submit"]').disabled).toBe(false);
  });
});

// HU-DIS-003 · CA-02: cuando la seccion depende de un servicio caido, el
// jugador ve QUE funcion esta limitada, que el resto sigue, y puede reintentar.
describe('montarCrearSala · sección degradada (HU-DIS-003)', () => {
  const inventarioCaido = () =>
    new ErrorDeApi(
      {
        type: 'https://nexusbattles.local/errores/seccion-no-disponible',
        title: 'Inventario no disponible temporalmente',
        status: 503,
        detail:
          'La sección de Inventario no esta disponible temporalmente. El resto del juego sigue funcionando.',
        seccion: 'Inventario',
        reintentarEnSegundos: 7,
        dependencia: 'inventario',
      },
      503,
    );

  test('pinta Sección degradada con la funcion limitada, no un Aviso de error', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(inventarioCaido());
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    const degradada = formulario.querySelector('.seccion-degradada');
    expect(degradada).not.toBeNull();
    expect(degradada.getAttribute('role')).toBe('status');
    expect(degradada.textContent).toContain('Inventario no disponible temporalmente');
    expect(degradada.textContent).toContain('El resto del juego sigue funcionando');
    expect(degradada.textContent).toContain('7 segundos');
    expect(document.querySelector('.aviso')).toBeNull();
    // MAPEO-ERRORES §3: ni type ni dependencia se muestran.
    expect(degradada.textContent).not.toContain('nexusbattles.local');
    expect(degradada.textContent).not.toContain('inventario');
    expect(formulario.querySelector('[type="submit"]').disabled).toBe(false);
  });

  test('Reintentar vuelve a enviar el formulario y, si el servicio volvio, la sala se crea', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest
      .fn()
      .mockRejectedValueOnce(inventarioCaido())
      .mockResolvedValueOnce({ id: 's-1', maximoParticipantes: 4, recompensaCreditos: 0 });
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();
    formulario.querySelector('.seccion-degradada__reintentar').click();
    await asentar();

    expect(crearSalaImpl).toHaveBeenCalledTimes(2);
    expect(formulario.querySelector('.seccion-degradada')).toBeNull();
    expect(document.querySelector('.aviso--exito').textContent).toContain('Sala creada');
  });

  test('un 503 con otro type sigue siendo un Aviso de error: se decide por type, no por status', async () => {
    const formulario = preparar();
    const crearSalaImpl = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/creditos-no-disponibles',
          title: 'El libro de créditos no esta disponible ahora mismo',
          status: 503,
          detail: 'No se pudieron comprometer los créditos.',
        },
        503,
      ),
    );
    montarCrearSala(formulario, { crearSalaImpl });

    formulario.dispatchEvent(new Event('submit'));
    await asentar();

    expect(formulario.querySelector('.seccion-degradada')).toBeNull();
    expect(document.querySelector('.aviso--error')).not.toBeNull();
  });
});

describe('R18 - rutaDeLaSala y textoDeSalaCreada', () => {
  test('la ruta lleva a la sala de espera de esa sala, con el id escapado', () => {
    expect(rutaDeLaSala('abc')).toBe('./sala-batalla.html?sala=abc');
    expect(rutaDeLaSala('a/b?c')).toBe('./sala-batalla.html?sala=a%2Fb%3Fc');
  });

  test('una sala que ya esta completa no espera a nadie, con o sin modalidad', () => {
    expect(
      textoDeSalaCreada({ maximoParticipantes: 2, ocupacion: 2, recompensaCreditos: 10 }),
    ).toBe('Tu rival ya está en la sala, 10 créditos en juego. Entra y arranca el combate.');
    expect(textoDeSalaCreada({ maximoParticipantes: 4, ocupacion: 1 })).toContain(
      'esperando jugadores: 4 participantes',
    );
  });
});
