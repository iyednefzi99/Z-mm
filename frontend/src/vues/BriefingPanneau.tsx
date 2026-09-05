import { useEffect, useState, type ReactElement } from 'react';
import { chargerBriefing } from '../api/client';
import type { Briefing, LigneBriefing } from '../api/types';
import { useFormats, useT } from '../i18n/langue';

/**
 * Le point du jour (SPRINT-30, lot G).
 *
 * <p>Ferme la ligne « assistant / mentor IA, briefing quotidien » du §7. Il est
 * posé en tête du tableau de bord parce que c'est l'écran qu'on ouvre le matin :
 * lui donner un onglet à lui aurait fait un vingt-quatrième écran qu'il faut
 * penser à visiter.
 *
 * <p><strong>Aucun modèle de langue ne rédige ces lignes</strong>, et l'écran le
 * dit ([ADR-013]). Chaque ligne porte son `detail` — un compte, une date, un nom
 * de ruche — parce que c'est ce qui la rend vérifiable. Une phrase agréable
 * qu'on ne peut pas remonter à sa source vaut moins qu'une liste sèche.
 *
 * <p>Un échec de chargement ne montre rien : le briefing est un confort, et une
 * bannière d'erreur en tête du tableau de bord ferait croire à une panne du
 * produit.
 */
export function BriefingPanneau(): ReactElement | null {
  const t = useT();
  const f = useFormats();
  const [briefing, setBriefing] = useState<Briefing | null>(null);

  useEffect(() => {
    void chargerBriefing().then(setBriefing).catch(() => setBriefing(null));
  }, []);

  if (briefing === null) {
    return null;
  }

  const urgences = [1, 2, 3].filter((u) => briefing.lignes.some((l) => l.urgence === u));

  return (
    <section className="z-briefing">
      <header className="z-briefing__entete">
        <h2 className="z-briefing__titre">{t.briefing.titre}</h2>
        <span className="z-briefing__date">{f.date(briefing.genereLe)}</span>
      </header>

      {briefing.lignes.length === 0 ? (
        <p className="z-info">{t.briefing.vide}</p>
      ) : (
        urgences.map((urgence) => (
          <div key={urgence} className="z-briefing__groupe">
            <p className="z-briefing__urgence">
              {urgence === 1
                ? t.briefing.urgence1
                : urgence === 2
                  ? t.briefing.urgence2
                  : t.briefing.urgence3}
            </p>
            <ul className="z-briefing__lignes">
              {briefing.lignes
                .filter((ligne) => ligne.urgence === urgence)
                .map((ligne) => (
                  <LigneBriefingVue
                    key={`${ligne.categorie}-${ligne.titre}`}
                    ligne={ligne}
                    categorie={t.briefing.categories[ligne.categorie]}
                  />
                ))}
            </ul>
          </div>
        ))
      )}

      <p className="z-briefing__aide">{t.briefing.aide}</p>
    </section>
  );
}

function LigneBriefingVue({
  ligne,
  categorie,
}: {
  ligne: LigneBriefing;
  categorie: string;
}): ReactElement {
  return (
    <li className={`z-briefing__ligne z-briefing__ligne--${ligne.categorie}`}>
      <span className="z-briefing__categorie">{categorie}</span>
      <span>
        <strong>{ligne.titre}</strong>
        <br />
        {/* Ce qui fonde la ligne, et sans quoi elle ne serait qu'une opinion. */}
        <small>{ligne.detail}</small>
      </span>
    </li>
  );
}
