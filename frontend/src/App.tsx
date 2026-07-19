import { useEffect, useState } from 'react';
import { recupererInfo, type Info } from './api/client';
import { LANGUES, direction, messages, type Langue } from './i18n/messages';
import './App.css';

/**
 * Ecran unique du walking skeleton (SPRINT-00).
 *
 * Il prouve trois choses : le client React atteint l'API, l'internationalisation
 * traverse toute la chaine (en-tete Accept-Language -> messages du backend), et
 * le passage en arabe bascule reellement le document en RTL.
 *
 * Les trois ecrans structurants (calendrier, rapport de visite, carte) sont des
 * maquettes du SPRINT-00 et seront implementes a partir du SPRINT-01.
 */
export default function App() {
  const [langue, setLangue] = useState<Langue>('fr');
  const [info, setInfo] = useState<Info | null>(null);
  const [erreur, setErreur] = useState(false);
  const [chargement, setChargement] = useState(true);

  const t = messages[langue];

  useEffect(() => {
    const racine = document.documentElement;
    racine.lang = langue;
    racine.dir = direction(langue);
  }, [langue]);

  useEffect(() => {
    const controleur = new AbortController();
    setChargement(true);
    setErreur(false);

    recupererInfo(langue, controleur.signal)
      .then((donnees) => setInfo(donnees))
      .catch((cause: unknown) => {
        if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
          setErreur(true);
        }
      })
      .finally(() => setChargement(false));

    return () => controleur.abort();
  }, [langue]);

  return (
    <main className="z-page">
      <header className="z-entete">
        <h1 className="z-titre">{t.titre}</h1>
        <p className="z-sous-titre">{t.sousTitre}</p>
      </header>

      <section className="z-carte" aria-labelledby="etat-api">
        <h2 className="z-carte__titre" id="etat-api">
          {t.etatApi}
        </h2>

        <p className="z-etat" role="status" aria-live="polite">
          {chargement ? (
            <span className="z-pastille z-pastille--attente" />
          ) : (
            <span
              className={`z-pastille ${erreur ? 'z-pastille--erreur' : 'z-pastille--ok'}`}
            />
          )}
          <span>{chargement ? t.chargement : erreur ? t.apiInjoignable : t.apiJoignable}</span>
        </p>

        {info && !erreur && (
          <dl className="z-details">
            <dt>{info.nom}</dt>
            <dd>
              <code>{info.version}</code>
            </dd>
            <dt>{t.langue}</dt>
            <dd>{info.accueil}</dd>
          </dl>
        )}
      </section>

      <nav className="z-langues" aria-label={t.langue}>
        {LANGUES.map((code) => (
          <button
            key={code}
            type="button"
            className="z-langue"
            aria-current={code === langue}
            onClick={() => setLangue(code)}
          >
            {code.toUpperCase()}
          </button>
        ))}
      </nav>
    </main>
  );
}
