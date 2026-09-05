# ADR-013 — Où tourne l'IA : sur l'appareil, ou pas du tout

- **Date** : 2026-09-05
- **Statut** : ✅ **Accepté** — mis en œuvre au SPRINT-30 (lot G du plan de couverture)
- **Décideurs** : architecte, développeur front
- **Tranche** : la décision **D4** de
  [`docs/PLAN-COUVERTURE-ECARTS.md`](../../../docs/PLAN-COUVERTURE-ECARTS.md)
- **Dépend de** : la décision du SPRINT-24 « la note vocale ne quitte pas
  l'appareil », qu'elle généralise

---

## Contexte

Le plan de couverture posait la question ainsi :

> « Traitement local, sans aucun trafic sortant » et « assistant IA » se
> contredisent tant qu'on n'a pas dit **où** le modèle s'exécute.

Le §8 du document d'écart reproche deux choses distinctes, et il faut les
séparer avant de décider :

1. **« Traitement local, sans aucun trafic sortant »** — il n'existe aucune
   bascule explicite. Zümm appelle `api.open-meteo.com`, charge des tuiles depuis
   `tile.openstreetmap.org`, et délègue la détection d'anomalie à un
   microservice. Les trois ont un repli, mais rien ne permet de dire « cette
   exploitation ne parle à personne ». HiveSense en fait un argument de vente.
2. **« IA embarquée sur l'appareil »** — l'inférence est côté serveur.

Et le §4 en demande une troisième : la **saisie vocale**, « le seul geste qui
fonctionne avec des gants », que trois concurrents transcrivent sans réseau.

Trois voies étaient ouvertes : transcription dans le navigateur (Whisper WASM,
une quarantaine de mégaoctets à télécharger), transcription sur le serveur
d'exploitation, ou service tiers.

---

## Décision

**Rien de ce qui est personnel ne quitte l'appareil, et rien n'est envoyé à un
tiers.** Trois conséquences, dans cet ordre.

### 1. La transcription se fait sur l'appareil, ou pas du tout

La saisie vocale utilise l'API `SpeechRecognition` du navigateur, et
**seulement** quand celui-ci peut la traiter localement. Là où il ne le peut
pas — Chrome de bureau route l'audio vers un service de reconnaissance —,
l'interface **refuse** et l'écrit, plutôt que d'envoyer la voix de l'apiculteur
à un fournisseur sans le lui dire.

C'est la généralisation d'une décision déjà prise : au SPRINT-24, la note vocale
avait été laissée locale parce qu'encoder de l'audio en base64 dans un champ
texte aurait fabriqué un stockage de fichiers clandestin. Le raisonnement vaut
aussi pour le transport.

**Whisper WASM est écarté**, et ce n'est pas une paresse. Quarante mégaoctets à
télécharger sur le téléphone qui monte au rucher contredisent la raison d'être
de la PWA. La porte reste ouverte : la transcription est isolée derrière une
seule fonction, et lui donner un second moteur ne touchera pas les écrans.

**Un service tiers est écarté sans condition.** Il fermerait la ligne §4 et
laisserait les deux lignes §8 rouges pour toujours.

### 2. L'assistance ne passe par aucun modèle de langue

Le « briefing quotidien » du §7 est calculé à partir de quatre registres qui
existent : alertes ouvertes, tâches échues, carences qui se terminent dans la
semaine, colonies non ouvertes depuis trois semaines. Chaque ligne **cite ce qui
la fonde** et se vérifie d'un clic.

Un modèle qui rédigerait « votre colonie 12 semble affaiblie » produirait une
phrase plus agréable et moins vérifiable ; le jour où elle serait fausse,
personne ne saurait dire d'où elle vient. Et il faudrait lui envoyer l'historique
de l'exploitation, ce que le point 1 vient d'interdire. **Les deux moitiés de la
décision doivent tenir ensemble**, sans quoi la première n'est qu'un slogan.

C'est le même arbitrage qu'au SPRINT-22 : les règles sont du code, pas une table
paramétrable, parce qu'un moteur d'expression demanderait à être écrit, testé et
sécurisé pour un besoin que personne n'a exprimé.

### 3. Le mode local est une bascule, pas une promesse en prose

`PolitiqueReseau` (`zumm.reseau.sortant`) coupe **tous** les appels sortants du
serveur : météo et microservice IA. La détection d'anomalie retombe alors sur
l'EWMA locale, qui existait déjà pour l'indisponibilité — la bascule emprunte un
chemin écrit, elle n'en crée pas un second.

Côté navigateur, le mode local coupe les tuiles de carte et exécute la même EWMA
sur les mesures déjà chargées. `/api/info` publie l'état du serveur pour que
l'écran puisse l'afficher : un exploitant qui a coupé le réseau veut le voir
écrit, et celui qui ne l'a pas coupé ne doit pas croire l'avoir fait.

---

## Ce que la décision ne couvre pas, et qu'il faut dire

- **Le trafic du navigateur n'est pas celui du serveur.** `PolitiqueReseau` ne
  peut rien contre une tuile de carte : ce trafic part du poste de
  l'utilisateur. Les deux moitiés se coupent séparément, et l'interface le dit.
  Promettre ici plus qu'on ne tient serait pire que ne rien promettre.
- **La reconnaissance vocale n'existe pas dans tous les navigateurs.** Firefox
  n'implémente pas `SpeechRecognition`. L'écran le signale et laisse le clavier :
  une fonctionnalité absente qui se dit vaut mieux qu'un bouton qui ne réagit pas.
- **La qualité de transcription n'est pas la nôtre.** Elle dépend du moteur du
  système. C'est le prix de ne rien envoyer, et il est explicite : le texte
  transcrit atterrit dans un champ **modifiable**, jamais enregistré directement.
  Le §4 le disait déjà — « la relecture avant validation est le vrai sujet ».

---

## Conséquences

- ✅ Les cinq lignes du lot G se ferment sans dépendance externe, et la ligne
  « notes vocales sans transcription » du §4 passe de 🟡 à ✅.
- ✅ Le mode local devient vérifiable : une classe à relire, pas six appels
  dispersés.
- ⚠️ Zümm n'aura pas de conversation en langage naturel tant que cet ADR tient.
  C'est un renoncement réel, et assumé : le produit préfère une liste qui cite
  ses sources à une phrase qu'on ne peut pas vérifier.
- La porte reste ouverte à un second moteur de transcription local (Whisper WASM,
  ou l'API de reconnaissance sur l'appareil quand elle sera partout) : le point
  d'entrée est unique.
