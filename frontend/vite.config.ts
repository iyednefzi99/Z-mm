/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  // US-049 : Vitest partage la configuration de Vite (alias, plugins) plutot que
  // d'en maintenir une seconde. jsdom fournit le DOM ; le fichier d'amorcage
  // ajoute les assertions de jest-dom et nettoie localStorage entre les tests.
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/tests/amorcage.ts',
    css: false,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/tests/**', 'src/**/*.d.ts', 'src/main.tsx'],
    },
  },
  server: {
    port: 5173,
    // En developpement, l'API est appelee via le meme origine que le client :
    // pas de CORS a ouvrir. En production, c'est le proxy inverse qui route.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
