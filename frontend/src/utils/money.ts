export function formatMoney(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const amount = typeof value === 'number' ? value : Number(value);
  if (Number.isNaN(amount)) {
    return String(value);
  }
  return amount.toFixed(2);
}
