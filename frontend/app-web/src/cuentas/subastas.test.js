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
  calcularSumaTopesAuto,
  verificarSobreCompromiso,
  calcularBalanceNetoCierre,
  generarConsejoTactico,
  calcularEstadoTopesConcurrencia,
  ControladorSubastas,
  SUBASTAS_INICIALES,
  HEROES_BASE,
  EVENTOS_CIERRE_DEFAULT
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

  describe('Navegación por Pestañas / Modos', () => {
    test('permite alternar entre Explorar, Mis Subastas y Cierre Múltiple mediante las pestañas', () => {
      // Estado inicial en lista/explorar
      expect(controlador.vista).toBe('lista');
      expect(contenedor.querySelector('.tab-btn[data-tab="explorar"]')).not.toBeNull();

      // Ir a Mis Subastas
      const tabMisSubastas = contenedor.querySelector('.tab-btn[data-tab="mis-subastas"]');
      expect(tabMisSubastas).not.toBeNull();
      tabMisSubastas.click();
      expect(controlador.vista).toBe('mis-subastas');
      expect(contenedor.querySelector('.vista-mis-subastas')).not.toBeNull();

      // Ir a Cierre Múltiple
      const tabCierre = contenedor.querySelector('.tab-btn[data-tab="cierre-multiple"]');
      expect(tabCierre).not.toBeNull();
      tabCierre.click();
      expect(controlador.vista).toBe('cierre-multiple');
      expect(contenedor.querySelector('.vista-cierre-multiple')).not.toBeNull();

      // Volver a Explorar
      const tabExplorar = contenedor.querySelector('.tab-btn[data-tab="explorar"]');
      expect(tabExplorar).not.toBeNull();
      tabExplorar.click();
      expect(controlador.vista).toBe('explorar');
      expect(contenedor.querySelector('.grid-subastas')).not.toBeNull();
    });
  });

  describe('Vista «Mis Subastas» - Finanzas y Concurrencia', () => {
    beforeEach(() => {
      controlador.abrirMisSubastas();
    });

    test('renderiza el panel de créditos con barra segmentada y leyenda', () => {
      expect(contenedor.querySelector('.panel-creditos-segmentada')).not.toBeNull();
      expect(contenedor.querySelector('.barra-segmentada-tramos')).not.toBeNull();
      expect(contenedor.querySelectorAll('.tramo-subasta').length).toBeGreaterThan(0);
      expect(contenedor.querySelector('.leyenda-tramos')).not.toBeNull();
      expect(contenedor.textContent).toContain('Tienes en total');
      expect(contenedor.textContent).toContain('Retenido en subastas');
      expect(contenedor.textContent).toContain('Libre para pujar');
    });

    test('renderiza medidores de topes de concurrencia y alerta reactiva al 80%', () => {
      expect(contenedor.querySelector('.grid-topes-concurrencia')).not.toBeNull();
      expect(contenedor.textContent).toContain('Subastas en las que participas');
      expect(contenedor.textContent).toContain('Pujas tuyas que van ganando');
      // Verificamos que las barras de progreso estén presentes
      const barrasProgreso = contenedor.querySelectorAll('.tope-barra-progreso');
      expect(barrasProgreso.length).toBe(2);
    });

    test('detecta y alerta el sobre-compromiso de pujas automáticas cuando superan el saldo total', () => {
      // Con las subastas iniciales, la suma de topes excede el saldo de 6200
      const alerta = contenedor.querySelector('.alerta-sobrecompromiso');
      expect(alerta).not.toBeNull();
      expect(alerta.textContent).toContain('Tus automáticas prometen más de lo que tienes');
      expect(alerta.textContent).toContain('Las últimas en responder fallarán');
    });

    test('ordena las subastas por proximidad de vencimiento y asigna bordes de estado', () => {
      const filas = contenedor.querySelectorAll('.fila-mi-subasta');
      expect(filas.length).toBe(SUBASTAS_INICIALES.length);

      // La primera debe ser la que tiene menor tiempo restante
      const tiempoPrimero = filas[0].querySelector('.reloj-fila');
      expect(tiempoPrimero).not.toBeNull();

      // Debe haber al menos una con borde verde (ganando) y una con borde rojo (superada)
      expect(contenedor.querySelector('.borde-ganando')).not.toBeNull();
      expect(contenedor.querySelector('.borde-superada')).not.toBeNull();
    });

    test('muestra botón "Ir ahora" con latido para subastas < 10 s y "Recuperarla" para superadas', () => {
      // Subasta con <= 10 s
      const btnIrAhora = contenedor.querySelector('.btn-ir-ahora');
      expect(btnIrAhora).not.toBeNull();
      expect(btnIrAhora.textContent).toContain('Ir ahora');
      expect(btnIrAhora.classList.contains('animacion-latido')).toBe(true);

      // Subasta superada
      const btnRecuperar = contenedor.querySelector('.btn-recuperar');
      expect(btnRecuperar).not.toBeNull();
      expect(btnRecuperar.textContent).toContain('Recuperarla');

      // Al pulsar "Recuperarla", debe abrir el detalle de esa subasta
      btnRecuperar.click();
      expect(controlador.vista).toBe('detalle');
      expect(contenedor.querySelector('.vista-detalle')).not.toBeNull();
    });
  });

  describe('Vista «Cierre Múltiple»', () => {
    beforeEach(() => {
      controlador.abrirCierreMultiple();
    });

    test('renderiza el balance financiero neto con cobrado, devuelto y libre', () => {
      expect(contenedor.querySelector('.panel-cierre-multiple')).not.toBeNull();
      expect(contenedor.querySelector('.cierre-titular').textContent).toContain('3 CERRARON');
      expect(contenedor.querySelector('.caja-neto .cifra-neto--cobrado')).not.toBeNull();
      expect(contenedor.querySelector('.caja-neto .cifra-neto--devuelto')).not.toBeNull();
      expect(contenedor.querySelector('.caja-neto--libre .cifra-neto--libre')).not.toBeNull();
    });

    test('renderiza filas para cada desenlace de cierre distinguiendo causas', () => {
      const filas = contenedor.querySelectorAll('.fila-evento-cierre');
      expect(filas.length).toBe(3);

      expect(contenedor.querySelector('.evento--adjudicada')).not.toBeNull();
      expect(contenedor.querySelector('.evento--superada-rival')).not.toBeNull();
      expect(contenedor.querySelector('.evento--superada-tope')).not.toBeNull();
      expect(contenedor.textContent).toContain('su automática respondió');
      expect(contenedor.textContent).toContain('paró en su tope');
    });

    test('renderiza consejo táctico personalizado con créditos exactos', () => {
      const cajaConsejo = contenedor.querySelector('.caja-consejo-tactico');
      expect(cajaConsejo).not.toBeNull();
      expect(cajaConsejo.textContent).toContain('se te escapó por 50 cr');
      expect(cajaConsejo.textContent).toContain('Tu tope estaba en 2.400 cr y cerró en 2.450 cr');
      expect(cajaConsejo.textContent).toContain('más de margen era tuyo');
    });

    test('permite navegar desde el panel de cierre a mis subastas o al listado', () => {
      const btnMisSubastas = contenedor.querySelector('#btn-cierre-a-mis-subastas');
      btnMisSubastas.click();
      expect(controlador.vista).toBe('mis-subastas');

      controlador.abrirCierreMultiple();
      const btnExplorar = contenedor.querySelector('#btn-cierre-a-explorar');
      btnExplorar.click();
      expect(controlador.vista).toBe('explorar');
    });
  });

  describe('Sistema de Avisos Cruzados en Vivo (Toast en esquina inferior derecha)', () => {
    test('renderiza y descarta un toast flotante de aviso cruzado cuando te superan en otra subasta', () => {
      expect(contenedor.querySelector('.toast-cruzado-flotante')).toBeNull();

      // Disparar aviso cruzado
      controlador.lanzarAvisoCruzado('grebas-centinela');
      const toast = contenedor.querySelector('.toast-cruzado-flotante');
      expect(toast).not.toBeNull();
      expect(toast.textContent).toContain('¡Te superaron en otra subasta!');
      expect(toast.textContent).toContain('Grebas del Centinela Caído');

      // Descartar
      const btnDescartar = toast.querySelector('.btn-toast-descartar');
      btnDescartar.click();
      expect(controlador.avisoCruzado).toBeNull();
      expect(contenedor.querySelector('.toast-cruzado-flotante')).toBeNull();
    });

    test('el botón "Ir" del aviso cruzado lleva al detalle de la subasta superada', () => {
      controlador.abrirExplorar();
      controlador.lanzarAvisoCruzado('hacha-obsidiana');

      const btnIr = contenedor.querySelector('.btn-toast-ir');
      expect(btnIr).not.toBeNull();
      btnIr.click();

      expect(controlador.vista).toBe('detalle');
      expect(controlador.subastaActivaId).toBe('hacha-obsidiana');
      expect(controlador.avisoCruzado).toBeNull();
      expect(contenedor.querySelector('.toast-cruzado-flotante')).toBeNull();
    });
  });
});

describe('HU-SUB-004 - Pruebas Unitarias de Cálculos Nuevos', () => {
  test('calcularSumaTopesAuto suma correctamente los topes de pujas automáticas', () => {
    const subastas = [
      { autoLimite: 2000 },
      { autoLimite: 1500 },
      { autoLimite: 0 }
    ];
    expect(calcularSumaTopesAuto(subastas)).toBe(3500);
  });

  test('verificarSobreCompromiso detecta sobre-compromiso correctamente', () => {
    const subastasExcedidas = [
      { autoLimite: 4000 },
      { autoLimite: 3000 }
    ];
    const res1 = verificarSobreCompromiso(6000, subastasExcedidas);
    expect(res1.sobreCompromiso).toBe(true);
    expect(res1.sumaTopes).toBe(7000);
    expect(res1.faltante).toBe(1000);

    const subastasOk = [
      { autoLimite: 2000 },
      { autoLimite: 1500 }
    ];
    const res2 = verificarSobreCompromiso(6000, subastasOk);
    expect(res2.sobreCompromiso).toBe(false);
    expect(res2.faltante).toBe(0);
  });

  test('calcularBalanceNetoCierre totaliza cobros, devoluciones y saldo libre', () => {
    const eventos = [
      { montoCobrado: 1350, montoDevuelto: 0 },
      { montoCobrado: 0, montoDevuelto: 880 },
      { montoCobrado: 0, montoDevuelto: 2400 }
    ];
    const balance = calcularBalanceNetoCierre(eventos, 6200);
    expect(balance.cobrado).toBe(1350);
    expect(balance.devuelto).toBe(3280);
    expect(balance.neto).toBe(1930);
    expect(balance.saldoLibre).toBe(4850);
  });

  test('generarConsejoTactico calcula la diferencia y el margen necesario', () => {
    const evento = {
      nombre: 'Amuleto de Brasa Eterna',
      montoFinal: 2450,
      topePropio: 2400
    };
    const consejo = generarConsejoTactico(evento, 4130);
    expect(consejo.diferencia).toBe(50);
    expect(consejo.margenRecomendado).toBe(100);
    expect(consejo.titulo).toContain('se te escapó por 50 cr');
    expect(consejo.cuerpo).toContain('tenías 4.130 cr libres');
  });

  test('calcularEstadoTopesConcurrencia genera alertas al superar el 80%', () => {
    // 8 de 10 subastas = 80%
    const subastas8 = Array.from({ length: 8 }, (_, i) => ({ id: `s-${i}`, ganando: false }));
    const estado1 = calcularEstadoTopesConcurrencia(subastas8, { maxSubastasSimultaneas: 10, maxPujasActivas: 50 });
    expect(estado1.subastas.alerta).toBe(true);
    expect(estado1.subastas.topeAlcanzado).toBe(false);
    expect(estado1.subastas.pista).toContain('Aviso de tope (80%)');

    // 10 de 10 subastas = 100%
    const subastas10 = Array.from({ length: 10 }, (_, i) => ({ id: `s-${i}`, ganando: false }));
    const estado2 = calcularEstadoTopesConcurrencia(subastas10, { maxSubastasSimultaneas: 10, maxPujasActivas: 50 });
    expect(estado2.subastas.topeAlcanzado).toBe(true);
    expect(estado2.subastas.pista).toContain('Has llegado al tope');
  });
});
