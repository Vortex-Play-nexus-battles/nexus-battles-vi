/**
 * Subastas - Pruebas unitarias y de integración DOM (HU-SUB-004)
 */

import { jest } from '@jest/globals';
import {
  calcularSaldoLibre,
  calcularSaldoRetenido,
  calcularMinimoPuja,
  validarPuja,
  validarLimiteAuto,
  calcularComparacionHeroe,
  formatearCreditos,
  formatearTiempo,
  ControladorSubastas,
  SUBASTAS_INICIALES,
  HEROES_BASE
} from './subastas.js';

describe('HU-SUB-004 - Reglas de Negocio de Subastas y Pujas', () => {
  describe('Cálculos y formateo', () => {
    test('formatea cifras con separador de miles es-CO', () => {
      expect(formatearCreditos(1350)).toBe('1.350');
      expect(formatearCreditos(0)).toBe('0');
    });

    test('formatea tiempo en minutos y segundos', () => {
      expect(formatearTiempo(38)).toBe('0:38');
      expect(formatearTiempo(184)).toBe('3:04');
      expect(formatearTiempo(3665)).toBe('1 h 1 m');
      expect(formatearTiempo(0)).toBe('Cerrada');
    });

    test('calcula saldo retenido y saldo libre correctamente', () => {
      const subastas = [
        { id: '1', retenido: 1000 },
        { id: '2', retenido: 500 },
        { id: '3', retenido: 0 }
      ];
      expect(calcularSaldoRetenido(subastas)).toBe(1500);
      expect(calcularSaldoLibre(6000, subastas)).toBe(4500);
    });

    test('calcula el incremento mínimo correctamente', () => {
      expect(calcularMinimoPuja(1000, 50)).toBe(1050);
      expect(calcularMinimoPuja(0, 50)).toBe(50);
    });
  });

  describe('Validación de pujas', () => {
    const subastaEjemplo = {
      id: 'sub-1',
      oferta: 1000,
      retenido: 1000,
      segundosRestantes: 60
    };

    test('acepta una puja que cumple oferta + incremento y saldo suficiente', () => {
      const res = validarPuja(1050, subastaEjemplo, 2000, 50, 0);
      expect(res.valida).toBe(true);
    });

    test('rechaza una puja inferior a la oferta actual + incremento mínimo', () => {
      const res = validarPuja(1020, subastaEjemplo, 2000, 50, 0);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('al menos 1.050 cr');
    });

    test('rechaza una puja si el saldo disponible no alcanza', () => {
      // Saldo libre 0 + retenido 1000 = 1000 disponibles. Intentar pujar 1100 debe fallar
      const res = validarPuja(1100, subastaEjemplo, 0, 50, 0);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('Saldo insuficiente');
    });

    test('rechaza una puja si la subasta está cerrada', () => {
      const subCerrada = { ...subastaEjemplo, segundosRestantes: 0 };
      const res = validarPuja(1100, subCerrada, 5000, 50, 0);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('cerrada');
    });

    test('rechaza una puja si el intervalo de espera de 5 s no ha culminado', () => {
      const res = validarPuja(1100, subastaEjemplo, 5000, 50, 4);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('esperar 4 s');
    });
  });

  describe('Validación de puja automática', () => {
    const subasta = { id: 'sub-1', oferta: 800, retenido: 0 };

    test('acepta un límite superior a la oferta mínima y dentro del saldo', () => {
      const res = validarLimiteAuto(1200, subasta, 2000, 50);
      expect(res.valida).toBe(true);
    });

    test('rechaza si el tope es inferior a la oferta + incremento', () => {
      const res = validarLimiteAuto(820, subasta, 2000, 50);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('al menos 850 cr');
    });

    test('rechaza si el tope excede el saldo libre', () => {
      const res = validarLimiteAuto(3000, subasta, 1000, 50);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('Saldo insuficiente');
    });
  });

  describe('Comparación de héroes y alerta de nivel (RN-INV-004)', () => {
    const item = { nivel: 24, aporte: { poder: 10, vida: 50, defensa: 5 } };

    test('alerta cuando el héroe no tiene el nivel suficiente', () => {
      const heroeBajo = { nivel: 20, stats: { poder: 100, vida: 500, defensa: 30 } };
      const comp = calcularComparacionHeroe(heroeBajo, item);
      expect(comp.nivelInsuficiente).toBe(true);
      expect(comp.deltaNivel).toBe(4);
    });

    test('aprueba cuando el héroe cumple o supera el nivel', () => {
      const heroeAlto = { nivel: 26, stats: { poder: 100, vida: 500, defensa: 30 } };
      const comp = calcularComparacionHeroe(heroeAlto, item);
      expect(comp.nivelInsuficiente).toBe(false);
      expect(comp.deltaNivel).toBe(-2);
    });

    test('calcula correctamente los deltas de estadísticas', () => {
      const heroe = { nivel: 25, stats: { poder: 100, vida: 500, defensa: 30 } };
      const comp = calcularComparacionHeroe(heroe, item);
      expect(comp.comparaciones).toEqual([
        { stat: 'Poder', actual: 100, nuevo: 110, delta: 10 },
        { stat: 'Vida', actual: 500, nuevo: 550, delta: 50 },
        { stat: 'Defensa', actual: 30, nuevo: 35, delta: 5 }
      ]);
    });
  });
});

describe('ControladorSubastas - Interacción y Flujo DOM', () => {
  let contenedor;
  let controlador;

  beforeEach(() => {
    contenedor = document.createElement('div');
    document.body.appendChild(contenedor);
    controlador = new ControladorSubastas({
      contenedor,
      subastas: SUBASTAS_INICIALES,
      heroes: HEROES_BASE
    });
    controlador.render();
  });

  afterEach(() => {
    controlador.destruir();
    contenedor.remove();
  });

  test('renderiza el listado inicial de subastas con sus tarjetas', () => {
    const tarjetas = contenedor.querySelectorAll('.tarjeta-subasta');
    expect(tarjetas.length).toBe(SUBASTAS_INICIALES.length);
    expect(contenedor.textContent).toContain('Subastas y Pujas');
  });

  test('soporta los 4 estados obligatorios de RNF-USA-003', () => {
    // Estado Carga
    controlador.estadoDatos = 'carga';
    controlador.render();
    expect(contenedor.querySelector('.estado-carga')).not.toBeNull();

    // Estado Error
    controlador.estadoDatos = 'error';
    controlador.mensajeError = 'Fallo de conexión';
    controlador.render();
    expect(contenedor.querySelector('.estado-error')).not.toBeNull();
    expect(contenedor.textContent).toContain('Fallo de conexión');

    // Estado Vacío
    controlador.estadoDatos = 'vacio';
    controlador.render();
    expect(contenedor.querySelector('.estado-vacio')).not.toBeNull();

    // Estado Éxito
    controlador.estadoDatos = 'exito';
    controlador.render();
    expect(contenedor.querySelector('.grid-subastas')).not.toBeNull();
  });

  test('navega de lista a detalle al pulsar una subasta y permite volver', () => {
    expect(controlador.vista).toBe('lista');

    // Abrir hacha
    controlador.abrirDetalle('hacha-obsidiana');
    expect(controlador.vista).toBe('detalle');
    expect(contenedor.querySelector('.vista-detalle')).not.toBeNull();
    expect(contenedor.textContent).toContain('Hacha de Obsidiana Fracturada');

    // Volver
    const btnVolver = contenedor.querySelector('#btn-volver');
    btnVolver.click();
    expect(controlador.vista).toBe('lista');
    expect(contenedor.querySelector('.grid-subastas')).not.toBeNull();
  });

  test('permite cambiar el héroe para comparar estadísticas en el detalle', () => {
    controlador.abrirDetalle('hacha-obsidiana'); // Pide nivel 24
    expect(controlador.heroeId).toBe('kaelen'); // Kaelen es nivel 26 -> compatible
    expect(contenedor.textContent).toContain('Compatible');

    // Cambiar a Lyra (nivel 21)
    controlador.seleccionarHeroe('lyra');
    expect(controlador.heroeId).toBe('lyra');
    expect(contenedor.textContent).toContain('Nivel insuficiente');
  });

  test('realizar una puja válida actualiza la oferta vigente y el historial', () => {
    controlador.abrirDetalle('hacha-obsidiana');
    const ofertaPrevia = controlador.getSubastaActiva().oferta; // 1350
    const nuevaOferta = ofertaPrevia + 100; // 1450

    const exito = controlador.pujar(nuevaOferta);
    expect(exito).toBe(true);

    const subActualizada = controlador.getSubastaActiva();
    expect(subActualizada.oferta).toBe(nuevaOferta);
    expect(subActualizada.ganando).toBe(true);
    expect(subActualizada.historial[0].monto).toBe(nuevaOferta);
    expect(subActualizada.historial[0].esTu).toBe(true);
  });

  test('permite configurar y desactivar una puja automática', () => {
    controlador.abrirDetalle('hacha-obsidiana');
    const sub = controlador.getSubastaActiva();

    controlador.configurarAutoPuja(2000);
    expect(sub.autoLimite).toBe(2000);

    controlador.desactivarAutoPuja();
    expect(sub.autoLimite).toBe(0);
  });

  test('flujo de compra inmediata exige confirmación y cierra la subasta', () => {
    controlador.abrirDetalle('hacha-obsidiana');
    const sub = controlador.getSubastaActiva();

    // Solicitar compra abre modal
    controlador.solicitarCompraInmediata();
    expect(controlador.confirmandoCompra).toBe(true);
    expect(contenedor.querySelector('#modal-compra-inmediata')).not.toBeNull();

    // Confirmar compra
    controlador.confirmarCompraInmediata();
    expect(controlador.confirmandoCompra).toBe(false);
    expect(controlador.resultadoCierre).toBe('comprada');
    expect(sub.segundosRestantes).toBe(0);
    expect(contenedor.textContent).toContain('¡Has comprado este objeto de inmediato!');
  });
});
