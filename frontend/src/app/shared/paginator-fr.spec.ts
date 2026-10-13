import { PaginatorFr } from './paginator-fr';

describe('PaginatorFr', () => {
  const intl = new PaginatorFr();

  it('formate la plage affichée en français', () => {
    expect(intl.getRangeLabel(0, 10, 25)).toBe('1 – 10 sur 25');
    expect(intl.getRangeLabel(2, 10, 25)).toBe('21 – 25 sur 25');
    expect(intl.getRangeLabel(0, 10, 0)).toBe('0 sur 0');
  });
});
