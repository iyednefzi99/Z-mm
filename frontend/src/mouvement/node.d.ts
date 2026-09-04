/**
 * Les deux seules API Node utilisées par `mouvement.test.tsx`, déclarées ici
 * plutôt qu'en installant `@types/node`.
 *
 * <p>Ce n'est pas de l'avarice de dépendance : `@types/node` verse les globales
 * Node dans <em>tous</em> les fichiers du front, y compris le code applicatif, où
 * un `Buffer` ou un `process.env` écrit par mégarde compilerait sans broncher puis
 * planterait dans le navigateur. Six lignes ici gardent la frontière visible.
 */
declare module 'node:fs' {
  export function readFileSync(chemin: string, encodage: 'utf8'): string;
}

declare const process: { cwd(): string };
