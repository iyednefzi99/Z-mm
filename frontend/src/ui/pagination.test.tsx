import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { LangueProvider } from '../i18n/langue';
import { Pagination } from './composants';

/** Tests de la barre de pagination (US-052, SPRINT-11). */

const monter = (props: Partial<Parameters<typeof Pagination>[0]> = {}) => {
  const onPage = vi.fn();
  render(
    <LangueProvider>
      <Pagination page={0} taille={25} total={250} onPage={onPage} {...props} />
    </LangueProvider>,
  );
  return onPage;
};

describe('barre de pagination', () => {
  it('ne s’affiche pas quand tout tient sur une page', () => {
    monter({ total: 12 });

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('ne s’affiche pas non plus quand le total égale exactement la taille de page', () => {
    monter({ total: 25 });

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('annonce la page courante, le nombre de pages et le total', () => {
    monter({ page: 3, total: 250 });

    expect(screen.getByRole('status')).toHaveTextContent('Page 4 sur 10 — 250 éléments');
  });

  it('désactive « Précédent » sur la première page', () => {
    monter({ page: 0 });

    expect(screen.getByRole('button', { name: 'Précédent' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Suivant' })).toBeEnabled();
  });

  it('désactive « Suivant » sur la dernière page', () => {
    monter({ page: 9, total: 250 });

    expect(screen.getByRole('button', { name: 'Suivant' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Précédent' })).toBeEnabled();
  });

  it('demande la page suivante et la page précédente', async () => {
    const onPage = monter({ page: 2 });

    await userEvent.click(screen.getByRole('button', { name: 'Suivant' }));
    expect(onPage).toHaveBeenCalledWith(3);

    await userEvent.click(screen.getByRole('button', { name: 'Précédent' }));
    expect(onPage).toHaveBeenCalledWith(1);
  });

  it('compte une dernière page partielle', () => {
    // 51 éléments par pages de 25 : trois pages, la dernière n'en portant qu'un.
    monter({ page: 0, total: 51 });

    expect(screen.getByRole('status')).toHaveTextContent('Page 1 sur 3');
  });
});
