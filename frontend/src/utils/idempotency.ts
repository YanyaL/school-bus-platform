export function createIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export class IdempotencySession {
  private key: string | null = null;

  begin(): string {
    if (!this.key) {
      this.key = createIdempotencyKey();
    }
    return this.key;
  }

  reset(): void {
    this.key = null;
  }

  current(): string | null {
    return this.key;
  }
}
