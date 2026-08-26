// Reglas de JavaScript de la Empresa A.
//
// Sin framework y sin compilador, ESLint es la unica red que atrapa los errores
// tipicos antes de que lleguen al navegador. Las reglas de abajo no son de
// estilo -de eso se encarga Prettier-: son las que evitan fallos reales.

import js from '@eslint/js';
import globals from 'globals';

export default [
  js.configs.recommended,
  {
    files: ['src/**/*.js'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.browser,
      },
    },
    rules: {
      // Comparaciones: == convierte tipos en silencio y produce errores dificiles de ver.
      eqeqeq: ['error', 'always'],

      // Declaraciones
      'no-var': 'error',
      'prefer-const': 'error',
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],

      // Errores que el navegador no avisa
      'no-undef': 'error',
      'no-implicit-globals': 'error',
      'no-shadow': 'error',
      'require-atomic-updates': 'error',

      // Legibilidad entre tres equipos
      curly: ['error', 'all'],
      'no-nested-ternary': 'error',
      'prefer-template': 'error',

      // La consola sirve para depurar, no para dejarla en produccion.
      'no-console': ['warn', { allow: ['warn', 'error'] }],

      // Seguridad basica
      'no-eval': 'error',
      'no-implied-eval': 'error',
      'no-script-url': 'error',
    },
  },
];
