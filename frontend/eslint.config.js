/**
 * Configuration ESLint « plat » du front Zümm (US-049, SPRINT-10).
 *
 * Le front n'avait ni test ni lint : la configuration part donc du recommande
 * TypeScript, sans regles de style — Prettier s'en charge, et
 * `eslint-config-prettier` desactive celles qui entreraient en conflit.
 *
 * Le typage strict (`strictTypeChecked`) n'est volontairement PAS active : sur une
 * base existante il produirait des centaines de signalements d'un coup, ce qui
 * reviendrait a garder un lint rouge en permanence — donc ignore. Le durcissement
 * viendra fichier par fichier.
 */
import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import reactHooks from 'eslint-plugin-react-hooks';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'node_modules'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended, prettier],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser, ...globals.es2021 },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'jsx-a11y': jsxA11y,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // US-054 : garde-fou d'accessibilité. Le piège de focus et les libellés de
      // dialogue ont été corrigés à la main ; la règle empêche la régression.
      ...jsxA11y.flatConfigs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // Le prefixe `_` marque un parametre volontairement inutilise.
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
  {
    files: ['**/*.test.{ts,tsx}', 'src/tests/**'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
  },
);
