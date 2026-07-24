import { useEffect, useState, type ReactElement } from 'react';
import QRCode from 'qrcode';
import { recoltes, ruches, tracerLot } from '../api/client';
import type { Recolte, RecolteCorps, Ruche, Trace } from '../api/types';
import { gabarit } from '../i18n/console';
import { useT } from '../i18n/langue';
import { useRessource } from '../hooks';
import {
  Bouton,
  ChampDate,
  ChampNombre,
  ChampSelect,
  ChampTexte,
  Colonne,
  Modale,
  Option,
  Table,
} from '../ui/composants';
import { CorpsSection } from './CorpsSection';

/** Image QR d'un payload de traçabilité (US-033). */
function QrImage({ payload }: { payload: string }): ReactElement {
  const [url, setUrl] = useState('');
  useEffect(() => {
    void QRCode.toDataURL(payload, { margin: 1, width: 220 }).then(setUrl).catch(() => setUrl(''));
  }, [payload]);
  return url ? (
    <img src={url} alt={payload} width={220} height={220} style={{ display: 'block', margin: '0 auto' }} />
  ) : (
    <span>{payload}</span>
  );
}

/** Récoltes, numéro de lot et QR de traçabilité (US-033). */
export function RecoltesVue(): ReactElement {
  const t = useT();
  const etat = useRessource<Recolte, RecolteCorps>(recoltes);
  const [optRuches, setOptRuches] = useState<Option[]>([]);
  const [ouvert, setOuvert] = useState(false);
  const [rucheId, setRucheId] = useState('');
  const [dateRecolte, setDateRecolte] = useState('');
  const [quantite, setQuantite] = useState('');
  const [typeMiel, setTypeMiel] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [qr, setQr] = useState<Recolte | null>(null);
  const [trace, setTrace] = useState<Trace | null>(null);

  const colonnes: Colonne<Recolte>[] = [
    { entete: t.recolte.date, rendu: (r) => r.dateRecolte },
    { entete: t.recolte.ruche, rendu: (r) => r.rucheModele },
    { entete: t.recolte.quantite, rendu: (r) => String(r.quantiteKg) },
    { entete: t.recolte.lot, rendu: (r) => r.lot },
    {
      entete: t.recolte.qr,
      rendu: (r) => (
        <button type="button" className="z-lien" onClick={() => { setTrace(null); setQr(r); void tracerLot(r.lot).then(setTrace).catch(() => setTrace(null)); }}>
          {t.recolte.qr}
        </button>
      ),
    },
  ];

  useEffect(() => {
    void ruches.lister().then((l: Ruche[]) => setOptRuches(l.map((r) => ({ valeur: String(r.id), libelle: r.modele })))).catch(() => setOptRuches([]));
  }, [etat.elements]);

  const ouvrir = () => {
    setRucheId('');
    setDateRecolte('');
    setQuantite('');
    setTypeMiel('');
    setErreur(null);
    setOuvert(true);
  };

  const enregistrer = async () => {
    if (rucheId === '' || quantite === '') return;
    try {
      await etat.creer({
        rucheId: Number(rucheId),
        dateRecolte,
        quantiteKg: Number(quantite),
        typeMiel: typeMiel.trim() === '' ? null : typeMiel,
        note: null,
      });
      setOuvert(false);
    } catch (cause) {
      setErreur(cause instanceof Error ? cause.message : t.etats.erreur);
    }
  };

  const supprimer = (r: Recolte) => {
    if (window.confirm(gabarit(t.etats.confirmerSuppression, { nom: r.lot }))) {
      void etat.supprimer(r.id);
    }
  };

  return (
    <CorpsSection titre={t.onglets.recoltes} etat={etat} onNouveau={ouvrir}>
      {etat.elements.length > 0 && (
        <Table colonnes={colonnes} elements={etat.elements} onModifier={() => undefined} onSupprimer={supprimer} />
      )}

      {ouvert && (
        <Modale titre={t.onglets.recoltes} onFermer={() => setOuvert(false)}>
          <form className="z-form" onSubmit={(e) => { e.preventDefault(); void enregistrer(); }}>
            <div className="z-form__grille">
              <ChampSelect libelle={t.recolte.ruche} valeur={rucheId} options={optRuches} onChange={setRucheId} requis />
              <ChampDate libelle={t.recolte.date} valeur={dateRecolte} onChange={setDateRecolte} requis />
            </div>
            <div className="z-form__grille">
              <ChampNombre libelle={t.recolte.quantite} valeur={quantite} onChange={setQuantite} requis />
              <ChampTexte libelle={t.recolte.typeMiel} valeur={typeMiel} onChange={setTypeMiel} />
            </div>
            {erreur && <p className="z-form__erreur">{erreur}</p>}
            <div className="z-form__actions">
              <Bouton variante="fantome" onClick={() => setOuvert(false)}>{t.actions.annuler}</Bouton>
              <Bouton variante="primaire" type="submit">{t.actions.enregistrer}</Bouton>
            </div>
          </form>
        </Modale>
      )}

      {qr && (
        <Modale titre={`${t.recolte.tracabilite} — ${qr.lot}`} onFermer={() => setQr(null)}>
          <div className="z-form">
            <QrImage payload={qr.qrPayload} />
            {trace && (
              <p className="z-info">
                <strong>{t.recolte.origine} :</strong> {trace.rucheModele} · {t.recolte.site} :{' '}
                {trace.siteNom} · {t.recolte.ferme} : {trace.fermeNom} · {trace.quantiteKg} kg
                {trace.typeMiel ? ` · ${trace.typeMiel}` : ''}
              </p>
            )}
          </div>
        </Modale>
      )}
    </CorpsSection>
  );
}
