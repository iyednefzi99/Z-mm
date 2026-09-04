import { describe, expect, it } from 'vitest';
import { deplacer } from './listes';

describe('deplacer', () => {
  it('échange deux éléments voisins', () => {
    expect(deplacer(['a', 'b', 'c'], 0, 1)).toEqual(['b', 'a', 'c']);
    expect(deplacer(['a', 'b', 'c'], 2, -1)).toEqual(['a', 'c', 'b']);
  });

  it('ne fait rien aux extrémités', () => {
    expect(deplacer(['a', 'b'], 0, -1)).toEqual(['a', 'b']);
    expect(deplacer(['a', 'b'], 1, 1)).toEqual(['a', 'b']);
  });

  it("ne modifie pas la liste d'origine, et preserve les identités", () => {
    const source = [{ id: 1 }, { id: 2 }];
    const deplacee = deplacer(source, 0, 1);
    expect(source.map((e) => e.id)).toEqual([1, 2]);
    expect(deplacee[0]).toBe(source[1]);
  });
});
