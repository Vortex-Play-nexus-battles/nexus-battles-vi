/**
 * Las entradas de la bitácora dichas para quien opera — §7.3.5 (SA-5): nombres
 * en vez de identificadores, el cambio en una línea y el momento de la acción.
 */

import {
  LARGO_EN_LA_TABLA,
  cambioDe,
  celdaDeSobre,
  momentoDe,
  motivoLegible,
  valorLegible,
  quienDe,
  recortado,
  sobreDe,
  uidsDe,
} from './entradas-de-auditoria.js';

const LYRA = '7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55';
const MODERADORA = '5b2e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55';
const titulares = {
  de: (uid) =>
    ({ [LYRA]: { apodo: 'lyra_92' }, [MODERADORA]: { apodo: 'moderadora_qa' } })[uid] ?? null,
};

describe('quién actuó', () => {
  test('un uid conocido es su apodo; uno sin apodo lo dice; un apodo (ms-identidad) queda tal cual', () => {
    expect(quienDe(MODERADORA, titulares)).toEqual({
      texto: 'moderadora_qa',
      identificado: true,
      uid: MODERADORA,
    });
    expect(quienDe('11111111-1111-4111-8111-111111111111', titulares)).toMatchObject({
      texto: 'Cuenta sin apodo',
      identificado: false,
    });
    expect(quienDe('superadmin_qa', titulares)).toEqual({
      texto: 'superadmin_qa',
      identificado: true,
      uid: null,
    });
  });

  test('sin dato, «desconocido» o «ANONYMOUS»: sin identificar', () => {
    for (const valor of [null, '', 'desconocido', 'ANONYMOUS']) {
      expect(quienDe(valor, titulares)).toMatchObject({
        texto: 'Sin identificar',
        identificado: false,
      });
    }
  });
});

describe('sobre qué', () => {
  test('una cuenta por su uid o por su clave numérica', () => {
    expect(sobreDe(LYRA, titulares)).toEqual({
      texto: 'lyra_92',
      clase: 'Cuenta',
      uid: LYRA,
      referencia: LYRA,
    });
    expect(sobreDe('42', titulares)).toMatchObject({ texto: 'Cuenta n.º 42', clase: 'Cuenta' });
  });

  test('un comentario, un parámetro con su nombre, un término y el propio registro', () => {
    expect(sobreDe('comentario:c-42', titulares)).toEqual({
      texto: 'Comentario',
      clase: null,
      uid: null,
      referencia: 'c-42',
    });
    expect(sobreDe('parametro:sanciones.suspension.maxima-dias', titulares)).toMatchObject({
      texto: 'Suspensión máxima',
      clase: 'Parámetro',
      referencia: 'sanciones.suspension.maxima-dias',
    });
    expect(sobreDe('parametro:clave.nueva', titulares).texto).toBe('clave.nueva');
    expect(sobreDe('lista-negra:Spider-Man', titulares)).toMatchObject({
      texto: '«Spider-Man»',
      clase: 'Lista negra',
    });
    expect(sobreDe('registro-de-auditoria', titulares)).toMatchObject({
      texto: 'Registro de auditoría',
    });
  });

  test('SA-18: lo del catálogo, con el nombre que tenía; sin nombre, qué era', () => {
    expect(sobreDe('producto:p-1', titulares, 'Espada solar')).toEqual({
      texto: '«Espada solar»',
      clase: 'Producto',
      uid: null,
      referencia: 'p-1',
    });
    expect(sobreDe('banner:b-1', titulares, '  Temporada de héroes ')).toMatchObject({
      texto: '«Temporada de héroes»',
      clase: 'Banner',
    });
    expect(sobreDe('recurso:r-1', titulares, null)).toMatchObject({
      texto: 'Recurso gráfico',
      clase: 'Recurso gráfico',
      referencia: 'r-1',
    });
    expect(sobreDe('producto:p-2', titulares)).toMatchObject({ texto: 'Producto del catálogo' });
    expect(sobreDe('banner:b-2', titulares, '')).toMatchObject({ texto: 'Banner de inicio' });
  });

  test('un permiso (intento de acceso no autorizado) se dice con su nombre; lo demás, tal cual', () => {
    expect(sobreDe('GESTIONAR_CUENTAS', titulares)).toMatchObject({
      texto: 'Gestionar cuentas de otros usuarios',
      clase: 'Permiso',
    });
    expect(sobreDe('algo-raro', titulares)).toMatchObject({ texto: 'algo-raro', clase: null });
    expect(sobreDe('', titulares).texto).toBeNull();
  });

  test('la celda: el nombre y debajo su clase; una cuenta enlaza a su ficha si se sabe cómo', () => {
    const celda = celdaDeSobre(sobreDe(LYRA, titulares), {
      enlaceDeCuenta: (uid) => `/ficha?usuario=${uid}`,
    });
    expect(celda.querySelector('a').getAttribute('href')).toBe(`/ficha?usuario=${LYRA}`);
    expect(celda.textContent).toBe('lyra_92Cuenta');
    const comentario = celdaDeSobre(sobreDe('comentario:c-1', titulares));
    expect(comentario.querySelector('a')).toBeNull();
    expect(comentario.textContent).toBe('Comentario');
    expect(celdaDeSobre(sobreDe('', titulares))).toBeNull();
  });
});

describe('el cambio y los textos largos', () => {
  test('antes → después; solo después; solo antes; nada', () => {
    expect(cambioDe({ valorAnterior: 'ACTIVO', valorNuevo: 'SUSPENDIDO hasta mañana' })).toBe(
      'Activa → SUSPENDIDO hasta mañana',
    );
    expect(cambioDe({ valorAnterior: null, valorNuevo: 'Advertencia' })).toBe('Advertencia');
    expect(cambioDe({ valorAnterior: '«troll» · activo', valorNuevo: null })).toBe(
      'Antes: «troll» · activo',
    );
    expect(cambioDe({})).toBeNull();
  });

  test('recortado: espacios juntos y una elipsis al final del largo', () => {
    expect(recortado('  dos   espacios ')).toBe('dos espacios');
    const largo = recortado('x'.repeat(200));
    expect(largo).toHaveLength(LARGO_EN_LA_TABLA);
    expect(largo.endsWith('…')).toBe(true);
    expect(recortado('   ')).toBeNull();
  });
});

describe('los códigos en palabras', () => {
  test('los motivos de un intento de acceso de ms-identidad', () => {
    expect(motivoLegible('CREDENTIAL_MISSING')).toBe('Sin credencial');
    expect(motivoLegible('JWT_INVALID_OR_EXPIRED: JWT expired at 2026-10-14')).toBe(
      'Sesión no válida o caducada',
    );
    expect(motivoLegible('Insultos en el chat')).toBe('Insultos en el chat');
    expect(motivoLegible('  ')).toBeNull();
  });

  test('roles, estados y el 403; una frase queda tal cual', () => {
    expect(valorLegible('403_FORBIDDEN')).toBe('Acceso denegado');
    expect(valorLegible('ANONYMOUS')).toBe('Sin sesión');
    expect(valorLegible('MODERADOR')).toBe('Moderador');
    expect(valorLegible('SUSPENDIDO')).toBe('Suspendida');
    expect(valorLegible('SUSPENDIDO hasta 2026-10-20')).toBe('SUSPENDIDO hasta 2026-10-20');
    expect(cambioDe({ valorAnterior: 'JUGADOR', valorNuevo: '403_FORBIDDEN' })).toBe(
      'Jugador → Acceso denegado',
    );
  });
});

describe('momento y cuentas a nombrar', () => {
  test('el momento es cuando ocurrió; sin él, cuando se registró', () => {
    expect(
      momentoDe({ ocurridoEn: '2026-10-14T14:59:30Z', fechaHora: '2026-10-14T15:00:00Z' }),
    ).toBe('2026-10-14T14:59:30Z');
    expect(momentoDe({ fechaHora: '2026-10-14T15:00:00Z' })).toBe('2026-10-14T15:00:00Z');
    expect(momentoDe(null)).toBeNull();
  });

  test('los uid de quién y sobre quién, sin repetir ni lo que no es un uid', () => {
    expect(
      uidsDe([
        { administrador: MODERADORA, afectado: LYRA },
        { administrador: 'superadmin_qa', afectado: LYRA.toUpperCase() },
        { administrador: MODERADORA, afectado: 'comentario:c-1' },
      ]),
    ).toEqual([MODERADORA, LYRA]);
  });
});
