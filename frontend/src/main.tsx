import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './theme/tokens.css';
import './theme/base.css';

const racine = document.getElementById('root');
if (!racine) {
  throw new Error("L'element racine #root est introuvable");
}

createRoot(racine).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
