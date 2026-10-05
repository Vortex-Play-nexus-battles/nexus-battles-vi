/**
 * R18 - Un jugador nuevo entra al Nexo con una cuenta util. B1 - y solo
 * despues de demostrar que el correo es suyo.
 *
 * ## Que afirma
 *
 * Una cuenta creada desde cero, sin que nadie la prepare a mano ni toque una
 * base de datos, termina en un estado real y jugable: correo verificado con el
 * codigo que llego al buzon, creditos, heroe, inventario, tienda con catalogo,
 * y la contrasena se puede recuperar. Y al volver a entrar, todo sigue ahi.
 *
 * ## Por que existe
 *
 * Cada afirmacion corresponde a algo que estaba roto y que nadie veia:
 *
 *   - los correos salian a un buzon de pruebas que no reenvia a nadie;
 *   - una sola solicitud de "olvide mi contrasena" dejaba DOS correos;
 *   - las cuentas anteriores al alta automatica nunca recibieron creditos ni
 *     heroe, y seguian asi para siempre;
 *   - B1: se registraba un correo que no existia y se seguia usando la cuenta
 *     (feedback del profesor). Desde identidad 2.0.0 la cuenta nace pendiente,
 *     el login la rechaza con 403 `cuenta-no-verificada` y el alta no empieza
 *     hasta que se confirma el codigo.
 *
 * Ninguno lo detecto una prueba unitaria, porque ninguno esta en una unidad:
 * estan en el camino completo. Por eso esta prueba recorre el camino.
 *
 * Es `serial` a proposito: cada paso usa la cuenta que creo el anterior, que
 * es lo que hace un jugador. Corre contra el entorno desplegado y lee los
 * correos del buzon de pruebas de ese entorno (`ayudantes/correo.js`:
 * MAILPIT_URL, o /mailpit en el mismo host). Sin buzon no hay forma de activar
 * la cuenta, asi que su falta se dice y se falla, no se salta.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  codigoDelCorreo,
  correosPara,
  correosVistos,
  esperarCodigo,
  leerCorreo,
} from './ayudantes/correo.js';
import {
  confirmarCodigo,
  cuerpoDelToken,
  iniciarSesion,
  respetandoElLimite,
} from './ayudantes/cuentas.js';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const CLAVE = 'Contrasena-R18-2026';
const CLAVE_NUEVA = 'Contrasena-R18-Nueva-2026';
const ACEPTA = 'application/problem+json, application/json, text/plain';

/** Cuanto se espera a que el alta del jugador termine sus pasos remotos. */
const ESPERA_ALTA_MS = 45000;

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

/** Los correos de recuperacion de una direccion (por su enlace o su asunto). */
async function correosDeRecuperacion(direccion) {
  const resultado = [];
  for (const resumen of await correosPara(direccion, { base: AWS })) {
    const hallado = codigoDelCorreo(await leerCorreo(resumen.ID, { base: AWS }));
    if (hallado?.tipo === 'recuperacion') {
      resultado.push({ ...hallado, id: resumen.ID });
    }
  }
  return resultado;
}

test.describe('R18/B1 - activacion del jugador nuevo', () => {
  test.describe.configure({ mode: 'serial' });

  const marca = Date.now();
  const apodo = `r18_${marca}`;
  const email = `r18.${marca}@nexus.test`;

  let api;
  let sesion;
  let uid;
  let codigoDeRecuperacion;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: AWS, ignoreHTTPSErrors: true });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  // ------------------------------------------------------------------ alta

  test('el registro crea la cuenta PENDIENTE de verificar, con rol de jugador', async () => {
    // Por el límite del borde (respetandoElLimite): con el host rápido, el
    // runner llega a él.
    const registro = await respetandoElLimite(() =>
      api.post('/api/v1/auth/registro', {
        headers: { Accept: 'application/problem+json, application/json' },
        multipart: { nombres: 'Jugador', apellidos: 'Nuevo', email, password: CLAVE, apodo },
      }),
    );

    expect(registro.status(), await registro.text()).toBe(201);
    const cuenta = await registro.json();
    // Identidad 2.0.0: la cuenta de autorregistro no esta activa hasta que se
    // demuestra que el buzon es de quien se registro.
    expect(cuenta.estado).toBe('PENDIENTE_VERIFICACION');
    expect(cuenta.rol.nombre).toBe('JUGADOR');
    expect(cuenta.publicId).toBeTruthy();
    // La contrasena nunca sale en la respuesta.
    expect(JSON.stringify(cuenta)).not.toContain(CLAVE);
  });

  test('hasta confirmar el correo el login la rechaza, y solo con la contrasena buena lo dice', async () => {
    const buena = await iniciarSesion(api, email, CLAVE);
    expect(buena.estado, buena.texto).toBe(403);
    expect(buena.tipo).toBe('cuenta-no-verificada');

    // Con una contrasena equivocada sigue siendo el 401 generico: el estado de
    // la cuenta no se revela a quien no conoce la contrasena.
    const mala = await iniciarSesion(api, email, 'No-Es-Esta-2026');
    expect(mala.estado).toBe(401);
    expect(mala.tipo).not.toBe('cuenta-no-verificada');
  });

  test('el codigo llega al buzon y activa la cuenta', async () => {
    const correo = await esperarCodigo(email, { tipo: 'verificacion', base: AWS });
    if (correo.enlace) {
      // correo 1.4.0: el enlace lleva a /verificar con el codigo y el correo
      // en el fragmento, que el navegador no manda a ningun servidor.
      const enlace = new URL(correo.enlace);
      expect(enlace.pathname).toBe('/verificar');
      expect(enlace.search).toBe('');
      expect(correo.correo).toBe(email);
    }

    const canje = await confirmarCodigo(api, email, correo.codigo);
    expect(canje.estado, canje.texto).toBe(200);
    expect(canje.cuerpo.estado).toBe('ACTIVO');

    // Un codigo de un solo uso que se puede reutilizar no es de un solo uso.
    const otraVez = await confirmarCodigo(api, email, correo.codigo);
    expect(otraVez.estado).toBe(400);
    expect(otraVez.tipo).toBe('codigo-invalido');
  });

  test('el login funciona y el token trae el identificador publico', async () => {
    const login = await iniciarSesion(api, email, CLAVE);

    expect(login.estado, login.texto).toBe(200);
    sesion = login.cuerpo;
    const claims = cuerpoDelToken(sesion.token);
    uid = claims.uid;
    expect(uid, 'sin uid en el token, ningun servicio sabe de quien habla').toBeTruthy();
    expect(claims.rol).toBe('JUGADOR');
  });

  test('al verificar sale tambien el correo de bienvenida', async () => {
    // El del codigo y el de bienvenida (identidad 2.0.0: «se envia el correo
    // de bienvenida» al confirmar). El de bienvenida no trae codigo.
    await expect
      .poll(async () => (await correosPara(email, { base: AWS })).length, {
        timeout: 30000,
        message: 'el correo de bienvenida no salio al verificar',
      })
      .toBeGreaterThanOrEqual(2);
  });

  // ------------------------------------------------- la cuenta queda lista

  test('el alta del jugador termina sin que nadie la prepare a mano', async () => {
    await expect
      .poll(
        async () => {
          const estado = await api.get('/api/v1/auth/onboarding', {
            headers: conToken(sesion.token),
          });
          return estado.ok() ? (await estado.json()).estado : 'SIN_RESPUESTA';
        },
        { timeout: ESPERA_ALTA_MS, message: 'el alta del jugador no llego a COMPLETO' },
      )
      .toBe('COMPLETO');

    const estado = await (
      await api.get('/api/v1/auth/onboarding', { headers: conToken(sesion.token) })
    ).json();
    expect(estado.listo).toBe(true);
    expect(estado.pasos.map((p) => p.estado)).not.toContain('PENDIENTE');
  });

  test('empieza con creditos, y con UN solo movimiento inicial', async () => {
    const saldo = await api.get(`/api/v1/creditos/${uid}/saldo`, {
      headers: conToken(sesion.token),
    });
    expect(saldo.status(), await saldo.text()).toBe(200);
    const { saldoDisponible } = await saldo.json();
    // El importe lo decide un parametro, no esta prueba: se afirma que se
    // puede apostar algo, no cuanto.
    expect(Number(saldoDisponible)).toBeGreaterThan(0);

    const movimientos = await api.get(`/api/v1/creditos/${uid}/movimientos`, {
      headers: conToken(sesion.token),
    });
    expect(movimientos.status()).toBe(200);
    const pagina = await movimientos.json();
    // Exactamente uno: si el alta se reintenta, el bono NO se duplica --
    // ms-finanzas lo descarta por su refId.
    expect(pagina.content).toHaveLength(1);
    expect(pagina.content[0].concepto).toContain('bono-registro');
    expect(pagina.content[0].referenciaId).toContain(uid);
    expect(Number(pagina.content[0].monto)).toBe(Number(saldoDisponible));
  });

  test('empieza con un heroe de verdad en el inventario', async () => {
    const inventario = await api.get('/api/v1/inventario/elementos', {
      headers: conToken(sesion.token),
    });

    expect(inventario.status(), await inventario.text()).toBe(200);
    const { elementos } = await inventario.json();
    const heroes = elementos.filter((e) => e.tipo === 'HEROE');
    expect(heroes.length, 'sin heroe no se puede entrar a una batalla').toBeGreaterThan(0);
    expect(heroes[0].nombrePropio).toBeTruthy();
    expect(heroes[0].productoId, 'el heroe sale del catalogo, no inventado').toBeTruthy();
  });

  test('reintentar el alta no duplica nada', async () => {
    const reintento = await api.post('/api/v1/auth/onboarding/reintentos', {
      headers: conToken(sesion.token),
    });
    expect([200, 202, 409]).toContain(reintento.status());

    const movimientos = await (
      await api.get(`/api/v1/creditos/${uid}/movimientos`, { headers: conToken(sesion.token) })
    ).json();
    expect(movimientos.content, 'el bono no puede pagarse dos veces').toHaveLength(1);
  });
  // ------------------------------------------------------ hay donde gastar

  test('la tienda tiene catalogo, o dice por que no', async () => {
    const productos = await api.get('/api/v1/productos', { headers: conToken(sesion.token) });

    if ([502, 503].includes(productos.status())) {
      test.info().annotations.push({
        type: 'servicio degradado',
        description: `el catalogo no responde (HTTP ${productos.status()})`,
      });
      test.skip(true, 'el servicio del catalogo no esta atendiendo en este entorno');
    }
    expect(productos.status()).toBe(200);
    const pagina = await productos.json();
    const filas = pagina.content ?? pagina;
    expect(filas.length, 'una tienda vacia no deja empezar a nadie').toBeGreaterThan(0);
    // Precio de verdad: un 0 por defecto tecnico es peor que no tener tienda.
    expect(filas.some((p) => Number(p.precioCreditos) > 0)).toBe(true);
  });

  test('la vitrina de subastas responde o se degrada, pero no revienta', async () => {
    const subastas = await api.get('/api/v1/subastas', { headers: conToken(sesion.token) });

    // 200 con lista (aunque este vacia) o una caida declarada. Lo que no vale
    // es un 500: eso seria un fallo del servicio, no una ausencia de datos.
    expect([200, 502, 503]).toContain(subastas.status());
    if (subastas.status() === 200) {
      const cuerpo = await subastas.json();
      expect(cuerpo.contenido ?? cuerpo.content ?? cuerpo).toBeDefined();
    }
  });

  // ----------------------------------------------- todo sigue ahi al volver

  test('salir y volver a entrar conserva creditos, heroe y alta', async () => {
    const salida = await api.post('/api/v1/auth/logout', { headers: conToken(sesion.token) });
    expect([200, 204]).toContain(salida.status());

    const vuelta = await iniciarSesion(api, email, CLAVE);
    expect(vuelta.estado).toBe(200);
    const nueva = vuelta.cuerpo;

    const saldo = await (
      await api.get(`/api/v1/creditos/${uid}/saldo`, { headers: conToken(nueva.token) })
    ).json();
    expect(Number(saldo.saldoDisponible)).toBeGreaterThan(0);

    const inventario = await (
      await api.get('/api/v1/inventario/elementos', { headers: conToken(nueva.token) })
    ).json();
    expect(inventario.elementos.filter((e) => e.tipo === 'HEROE').length).toBeGreaterThan(0);

    const alta = await (
      await api.get('/api/v1/auth/onboarding', { headers: conToken(nueva.token) })
    ).json();
    expect(alta.estado).toBe('COMPLETO');
    sesion = nueva;
  });

  // ------------------------------------------------ recuperar la contrasena

  test('pedir el restablecimiento no revela si la cuenta existe', async () => {
    // Un 429 del borde no llega a ms-identidad: repetirlo no manda dos correos.
    const existe = await respetandoElLimite(() =>
      api.post('/api/v1/auth/restablecer/solicitar', {
        headers: { Accept: ACEPTA },
        data: { email },
      }),
    );
    const noExiste = await respetandoElLimite(() =>
      api.post('/api/v1/auth/restablecer/solicitar', {
        headers: { Accept: ACEPTA },
        data: { email: `no.existe.${marca}@nexus.test` },
      }),
    );

    expect(existe.status()).toBe(200);
    expect(noExiste.status()).toBe(200);
    // Misma respuesta palabra por palabra: si difirieran, cualquiera podria
    // averiguar que correos estan registrados.
    expect(await noExiste.text()).toBe(await existe.text());
  });

  test('el codigo llega UNA vez, no dos', async () => {
    await expect
      .poll(async () => (await correosDeRecuperacion(email)).length, {
        timeout: 30000,
        message: 'el correo de recuperacion no salio',
      })
      .toBeGreaterThan(0);

    // Una solicitud, un correo. Hasta R18.3 salian dos identicos: la entrega
    // ocurria dentro de la peticion y el cliente reintentaba al agotar su
    // espera de dos segundos.
    const correos = await correosDeRecuperacion(email);
    expect(correos, 'una solicitud deja un solo correo').toHaveLength(1);
    codigoDeRecuperacion = correos[0].codigo;
    if (correos[0].enlace) {
      expect(new URL(correos[0].enlace).pathname).toBe('/restablecer');
    }
  });

  test('el codigo da acceso a las preguntas de la cuenta: sin configurar, ninguna', async () => {
    const r = await respetandoElLimite(() =>
      api.post('/api/v1/auth/restablecer/preguntas', {
        headers: { Accept: ACEPTA },
        data: { email, codigo: codigoDeRecuperacion },
      }),
    );
    expect(r.status(), await r.text()).toBe(200);
    expect(await r.json()).toEqual({ configuradas: false, preguntas: [] });
  });

  test('el codigo cambia la contrasena, y solo sirve una vez', async () => {
    const cambio = await respetandoElLimite(() =>
      api.post('/api/v1/auth/restablecer/confirmar', {
        headers: { Accept: ACEPTA },
        data: { email, codigo: codigoDeRecuperacion, nuevaPassword: CLAVE_NUEVA },
      }),
    );
    expect(cambio.status(), await cambio.text()).toBe(200);

    const conLaNueva = await iniciarSesion(api, email, CLAVE_NUEVA);
    expect(conLaNueva.estado).toBe(200);

    const conLaVieja = await iniciarSesion(api, email, CLAVE);
    expect(conLaVieja.estado, 'la contrasena anterior deja de valer').toBe(401);

    // Un codigo de un solo uso que se puede reutilizar no es de un solo uso.
    const reintento = await respetandoElLimite(() =>
      api.post('/api/v1/auth/restablecer/confirmar', {
        headers: { Accept: ACEPTA },
        data: { email, codigo: codigoDeRecuperacion, nuevaPassword: 'Otra-Contrasena-R18-2026' },
      }),
    );
    expect(reintento.status()).toBe(400);
  });

  test('despues de cambiar la contrasena la cuenta sigue siendo la misma', async () => {
    const login = await iniciarSesion(api, email, CLAVE_NUEVA);
    expect(cuerpoDelToken(login.cuerpo.token).uid).toBe(uid);

    const saldo = await (
      await api.get(`/api/v1/creditos/${uid}/saldo`, { headers: conToken(login.cuerpo.token) })
    ).json();
    expect(Number(saldo.saldoDisponible)).toBeGreaterThan(0);
    // Nada de lo anterior abrio un buzon distinto: los correos siguen siendo
    // de esta direccion.
    expect((await correosVistos(email, { base: AWS })).size).toBeGreaterThanOrEqual(3);
  });
});
