import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { LangueProvider } from './i18n/langue';
import { ThemeProvider } from './theme/theme';
import { DialoguesProvider } from './ui/dialogues';
import { ToastsProvider } from './ui/toasts';
import { enregistrerServiceWorker } from './pwa';
import './theme/tokens.css';
import './theme/base.css';

const racine = document.getElementById('root');
if (!racine) {
  throw new Error("L'element racine #root est introuvable");
}

createRoot(racine).render(
  <StrictMode>
    {/* L'ordre compte : les toasts et les dialogues lisent la langue, le sélecteur
        de thème aussi. Tous descendent donc de `LangueProvider`. */}
    <LangueProvider>
      <ThemeProvider>
        <ToastsProvider>
          <DialoguesProvider>
            <App />
          </DialoguesProvider>
        </ToastsProvider>
      </ThemeProvider>
    </LangueProvider>
  </StrictMode>,
);

// PWA : le service worker et son précache sont GÉNÉRÉS au build (Workbox), ce
// qui est la seule façon de connaître les noms de bundles hashés — donc la seule
// façon que l'application démarre réellement hors ligne.
window.addEventListener('load', () => {
  void enregistrerServiceWorker();
});
