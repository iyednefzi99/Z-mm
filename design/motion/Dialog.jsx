import { useEffect, useRef, useState } from "react";

// Aligner avec --modal-close-dur dans modal.css
const CLOSE_MS = 150;

export default function Dialog({ open, onClose, children }) {
  // `mounted` garde le nœud dans l'arbre pendant l'animation de fermeture.
  const [mounted, setMounted] = useState(open);
  const [closing, setClosing] = useState(false);
  const timer = useRef(null);

  useEffect(() => {
    if (open) {
      clearTimeout(timer.current);
      setClosing(false);
      setMounted(true);
    } else if (mounted) {
      setClosing(true);
      timer.current = setTimeout(() => {
        setClosing(false);
        setMounted(false);
      }, CLOSE_MS);
    }
    return () => clearTimeout(timer.current);
  }, [open, mounted]);

  // Fermeture avec Échap.
  useEffect(() => {
    if (!open) return;
    const onKey = (e) => e.key === "Escape" && onClose?.();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!mounted) return null;

  return (
    <div className="t-modal-backdrop" onClick={onClose}>
      <div
        className={`t-modal${open && !closing ? " is-open" : ""}${closing ? " is-closing" : ""}`}
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
}
