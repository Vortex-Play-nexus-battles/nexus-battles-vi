import { construirSolicitudProducto } from './solicitud-producto.js';

function agregar(formulario, nombre, valor, tipo = 'text') {
  const control = document.createElement('input');
  control.name = nombre;
  control.type = tipo;
  if (tipo === 'checkbox') {
    control.checked = Boolean(valor);
  } else {
    control.value = String(valor);
  }
  formulario.appendChild(control);
}

function formularioBase(tipo = 'ARMA', premium = false) {
  const formulario = document.createElement('form');
  agregar(formulario, 'nombre', 'Espada solar');
  agregar(formulario, 'imagen', '/imagenes/espada.webp');
  agregar(formulario, 'descripcion', 'Arma creada para pruebas');
  agregar(formulario, 'tipo', tipo);
  agregar(formulario, 'tiraje', 100, 'number');
  agregar(formulario, 'premium', premium, 'checkbox');
  agregar(formulario, 'precioCreditos', 500, 'number');
  agregar(formulario, 'precioMonedaReal', 9.99, 'number');
  return formulario;
}

test('construye un arma no premium sin precio en moneda real', () => {
  const formulario = formularioBase('ARMA');
  agregar(formulario, 'poderDeAtaque', 25, 'number');
  agregar(formulario, 'tasaDeCaida', 10, 'number');

  expect(construirSolicitudProducto(formulario)).toEqual({
    nombre: 'Espada solar',
    imagen: '/imagenes/espada.webp',
    descripcion: 'Arma creada para pruebas',
    tipo: 'ARMA',
    tiraje: 100,
    premium: false,
    precioCreditos: 500,
    poderDeAtaque: 25,
    tasaDeCaida: 10,
  });
});

test('un producto premium contiene únicamente precio en moneda real', () => {
  const formulario = formularioBase('ITEM', true);
  agregar(formulario, 'efecto', 'Recupera salud');
  agregar(formulario, 'tasaDeCaida', 5, 'number');
  const solicitud = construirSolicitudProducto(formulario);

  expect(solicitud.precioMonedaReal).toBe(9.99);
  expect(solicitud).not.toHaveProperty('precioCreditos');
});

test('acepta tiraje ilimitado', () => {
  const formulario = formularioBase('HEROE');
  formulario.elements.namedItem('tiraje').value = '-1';
  agregar(formulario, 'prototipo', 'Mago Fuego');
  expect(construirSolicitudProducto(formulario).tiraje).toBe(-1);
});

test.each([0, -2, 1.5])('rechaza el tiraje inválido %s', (tiraje) => {
  const formulario = formularioBase('HEROE');
  formulario.elements.namedItem('tiraje').value = String(tiraje);
  agregar(formulario, 'prototipo', 'Mago Fuego');
  expect(() => construirSolicitudProducto(formulario)).toThrow(/tiraje/i);
});

test('valida los campos propios de habilidad', () => {
  const formulario = formularioBase('HABILIDAD');
  agregar(formulario, 'heroe', '550e8400-e29b-41d4-a716-446655440000');
  agregar(formulario, 'costoPoder', 8, 'number');
  agregar(formulario, 'multiplicadorNivel', 1.5, 'number');
  agregar(formulario, 'turnosCarga', 2, 'number');

  expect(construirSolicitudProducto(formulario)).toMatchObject({
    tipo: 'HABILIDAD',
    costoPoder: 8,
    multiplicadorNivel: 1.5,
    turnosCarga: 2,
  });
});

test('valida defensa, parte y caída de armadura', () => {
  const formulario = formularioBase('ARMADURA');
  agregar(formulario, 'defensa', 30, 'number');
  agregar(formulario, 'parte', 'PECHO');
  agregar(formulario, 'tasaDeCaida', 15, 'number');
  expect(construirSolicitudProducto(formulario)).toMatchObject({
    defensa: 30,
    parte: 'PECHO',
    tasaDeCaida: 15,
  });
});

test('valida los campos propios de épica', () => {
  const formulario = formularioBase('EPICA');
  agregar(formulario, 'heroe', '550e8400-e29b-41d4-a716-446655440000');
  agregar(formulario, 'turnosRecarga', 3, 'number');
  agregar(formulario, 'efectoGeneral', 'Daño general');
  agregar(formulario, 'efectoPotenciado', 'Daño potenciado');
  expect(construirSolicitudProducto(formulario)).toMatchObject({
    turnosRecarga: 3,
    efectoGeneral: 'Daño general',
    efectoPotenciado: 'Daño potenciado',
  });
});

test('rechaza una tasa de caída superior a cien', () => {
  const formulario = formularioBase('ITEM');
  agregar(formulario, 'efecto', 'Recupera salud');
  agregar(formulario, 'tasaDeCaida', 101, 'number');
  expect(() => construirSolicitudProducto(formulario)).toThrow(/tasa de caída/i);
});
