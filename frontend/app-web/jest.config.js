/** Vista del navegador: pruebas unitarias sobre jsdom, sin framework. */
export default {
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/src'],
  transform: {},
  // Las de aceptacion las corre Playwright, no Jest.
  testPathIgnorePatterns: ['/node_modules/', '\.aceptacion\.spec\.js$'],
};
