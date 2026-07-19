/**
 * Client d'API — implementation provisoire du SPRINT-00.
 *
 * ATTENTION : a partir du moment ou le backend publiera son contrat OpenAPI 3,
 * ce client devra etre GENERE depuis ce contrat et non plus ecrit a la main,
 * afin de garantir la parite des types client/serveur. Ce fichier n'existe que
 * le temps du walking skeleton.
 */

export interface Info {
  nom: string;
  version: string;
  accueil: string;
  langues: string[];
}

export const recupererInfo = async (langue: string, signal?: AbortSignal): Promise<Info> => {
  const reponse = await fetch('/api/info', {
    headers: { 'Accept-Language': langue },
    signal,
  });
  if (!reponse.ok) {
    throw new Error(`Réponse ${reponse.status} de /api/info`);
  }
  return (await reponse.json()) as Info;
};
