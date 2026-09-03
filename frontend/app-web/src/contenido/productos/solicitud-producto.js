/** HU-PRD-001 - Traducción y validación del formulario al contrato OpenAPI. */

export const TIPOS_PRODUCTO = ['HEROE', 'HABILIDAD', 'ARMA', 'ARMADURA', 'ITEM', 'EPICA'];
export const PROTOTIPOS = [
  'Guerrero Tanque',
  'Guerrero Armas',
  'Mago Fuego',
  'Mago Hielo',
  'Pícaro Veneno',
  'Pícaro Machete',
  'Chamán',
  'Médico',
];
export const PARTES_ARMADURA = ['CASCO', 'PECHO', 'GUANTES', 'BRAZALETES', 'PANTALON', 'ZAPATOS'];

function control(formulario, nombre) {
  const encontrados = [...formulario.querySelectorAll(`[name="${nombre}"]`)];
  const encontrado = encontrados.find((candidato) => !candidato.disabled) || encontrados[0];
  if (!encontrado) {
    throw new Error(`Falta el campo ${nombre} en el formulario.`);
  }
  return encontrado;
}

function texto(formulario, nombre, etiqueta = nombre) {
  const valor = String(control(formulario, nombre).value ?? '').trim();
  if (!valor) {
    throw new Error(`${etiqueta} es obligatorio.`);
  }
  return valor;
}

function numero(formulario, nombre, etiqueta, { entero = false, minimo, maximo, mayorQue } = {}) {
  const valorTexto = String(control(formulario, nombre).value ?? '').trim();
  const valor = Number(valorTexto);
  if (valorTexto === '' || !Number.isFinite(valor)) {
    throw new Error(`${etiqueta} debe ser un número válido.`);
  }
  if (entero && !Number.isInteger(valor)) {
    throw new Error(`${etiqueta} debe ser un número entero.`);
  }
  if (minimo !== undefined && valor < minimo) {
    throw new Error(`${etiqueta} no puede ser menor que ${minimo}.`);
  }
  if (maximo !== undefined && valor > maximo) {
    throw new Error(`${etiqueta} no puede ser mayor que ${maximo}.`);
  }
  if (mayorQue !== undefined && valor <= mayorQue) {
    throw new Error(`${etiqueta} debe ser mayor que ${mayorQue}.`);
  }
  return valor;
}

function uuid(formulario, nombre, etiqueta) {
  const valor = texto(formulario, nombre, etiqueta);
  const patron = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (!patron.test(valor)) {
    throw new Error(`${etiqueta} debe ser un UUID válido.`);
  }
  return valor;
}

function tiraje(formulario) {
  const valor = numero(formulario, 'tiraje', 'El tiraje', { entero: true });
  if (valor !== -1 && valor < 1) {
    throw new Error('El tiraje debe ser -1 (ilimitado) o un entero mayor que cero.');
  }
  return valor;
}

function tasaDeCaida(formulario) {
  return numero(formulario, 'tasaDeCaida', 'La tasa de caída', { minimo: 0, maximo: 100 });
}

/** Convierte el formulario en una solicitud sin campos ajenos al tipo elegido. */
export function construirSolicitudProducto(formulario) {
  const tipo = texto(formulario, 'tipo', 'El tipo');
  if (!TIPOS_PRODUCTO.includes(tipo)) {
    throw new Error('El tipo de producto no está permitido.');
  }

  const premium = Boolean(control(formulario, 'premium').checked);
  const solicitud = {
    nombre: texto(formulario, 'nombre', 'El nombre'),
    imagen: texto(formulario, 'imagen', 'La imagen'),
    descripcion: texto(formulario, 'descripcion', 'La descripción'),
    tipo,
    tiraje: tiraje(formulario),
    premium,
  };

  if (premium) {
    solicitud.precioMonedaReal = numero(
      formulario,
      'precioMonedaReal',
      'El precio en moneda real',
      { minimo: 0 },
    );
  } else {
    solicitud.precioCreditos = numero(formulario, 'precioCreditos', 'El precio en créditos', {
      entero: true,
      minimo: 0,
    });
  }

  switch (tipo) {
    case 'HEROE': {
      const prototipo = texto(formulario, 'prototipo', 'El prototipo');
      if (!PROTOTIPOS.includes(prototipo)) {
        throw new Error('El prototipo seleccionado no está permitido.');
      }
      solicitud.prototipo = prototipo;
      break;
    }
    case 'HABILIDAD':
      solicitud.heroe = uuid(formulario, 'heroe', 'El héroe');
      solicitud.costoPoder = numero(formulario, 'costoPoder', 'El costo de poder', {
        entero: true,
        minimo: 1,
      });
      solicitud.multiplicadorNivel = numero(
        formulario,
        'multiplicadorNivel',
        'El multiplicador de nivel',
        { mayorQue: 0 },
      );
      solicitud.turnosCarga = numero(formulario, 'turnosCarga', 'Los turnos de carga', {
        entero: true,
        minimo: 0,
      });
      break;
    case 'ARMA':
      solicitud.poderDeAtaque = numero(formulario, 'poderDeAtaque', 'El poder de ataque', {
        entero: true,
        minimo: 1,
      });
      solicitud.tasaDeCaida = tasaDeCaida(formulario);
      break;
    case 'ARMADURA': {
      solicitud.defensa = numero(formulario, 'defensa', 'La defensa', {
        entero: true,
        minimo: 1,
      });
      const parte = texto(formulario, 'parte', 'La parte de armadura');
      if (!PARTES_ARMADURA.includes(parte)) {
        throw new Error('La parte de armadura seleccionada no está permitida.');
      }
      solicitud.parte = parte;
      solicitud.tasaDeCaida = tasaDeCaida(formulario);
      break;
    }
    case 'ITEM':
      solicitud.efecto = texto(formulario, 'efecto', 'El efecto');
      solicitud.tasaDeCaida = tasaDeCaida(formulario);
      break;
    case 'EPICA':
      solicitud.heroe = uuid(formulario, 'heroe', 'El héroe');
      solicitud.turnosRecarga = numero(formulario, 'turnosRecarga', 'Los turnos de recarga', {
        entero: true,
        minimo: 0,
      });
      solicitud.efectoGeneral = texto(formulario, 'efectoGeneral', 'El efecto general');
      solicitud.efectoPotenciado = texto(formulario, 'efectoPotenciado', 'El efecto potenciado');
      break;
  }

  return solicitud;
}
