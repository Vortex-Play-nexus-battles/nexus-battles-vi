/** Vista del navegador: pruebas unitarias sobre jsdom, sin framework. */
export default {
  testEnvironment: 'jsdom',
  roots: [
    '<rootDir>/src',
    // Los modulos de shared/ui-kit viven fuera de esta carpeta, asi que sin
    // esta linea Jest no los descubre y NINGUN componente compartido puede
    // tener pruebas. Se anade en HU-SAL-005, al probar `barra-vida.js`.
    // Es aditivo: no cambia como se descubren las pruebas de src/.
    '<rootDir>/../../shared/ui-kit/js',
  ],
  transform: {},
  // Las de aceptacion las corre Playwright, no Jest.
  testPathIgnorePatterns: ['/node_modules/', '\.aceptacion\.spec\.js$'],
};
