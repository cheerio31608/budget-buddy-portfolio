// A retry of an unchanged form must use the same key after a lost response.
export function prepareTransactionAttempt(payload, previous, newKey = () => crypto.randomUUID()) {
    const fingerprint = JSON.stringify(payload);
    if (previous?.fingerprint === fingerprint) return previous;
    return { fingerprint, body: { ...payload, idempotencyKey: newKey() } };
}
