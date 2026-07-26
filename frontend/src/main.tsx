import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { LangueProvider } from './i18n/langue';
import { DialoguesProvider } from './ui/dialogues';
import { enregistrerServiceWorker } from './pwa';
import './theme/tokens.css';
import './theme/base.css';

const racine = document.getElementById('root');
if (!racine) {
  throw new Error("L'element racine #root est introuvable");
}

createRoot(racine).render(
  <StrictMode>
    <LangueProvider>
      <DialoguesProvider>
        <App />
      </DialoguesProvider>
    </LangueProvider>
  </StrictMode>,
);

// PWA : le service worker et son précache sont GÉNÉRÉS au build (Workbox), ce
// qui est la seule façon de connaître les noms de bundles hashés — donc la seule
// façon que l'application démarre réellement hors ligne.
window.addEventListener('load', () => {
  void enregistrerServiceWorker();
});
