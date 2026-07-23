import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { LangueProvider } from './i18n/langue';
import './theme/tokens.css';
import './theme/base.css';

const racine = document.getElementById('root');
if (!racine) {
  throw new Error("L'element racine #root est introuvable");
}

createRoot(racine).render(
  <StrictMode>
    <LangueProvider>
      <App />
    </LangueProvider>
  </StrictMode>,
);

// PWA : enregistrement du service worker en production uniquement (en dev, Vite
// sert les modules et un SW gênerait le rechargement à chaud).
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js');
  });
}
