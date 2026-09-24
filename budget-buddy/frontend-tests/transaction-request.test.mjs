import test from "node:test";
import assert from "node:assert/strict";
import { prepareTransactionAttempt } from "../src/main/resources/static/transaction-request.mjs";

test("a retry after a lost response keeps the same key and payload", () => {
    const first = prepareTransactionAttempt({ amount: "1000" }, null, () => "key-1");
    const retry = prepareTransactionAttempt({ amount: "1000" }, first, () => "key-2");
    assert.strictEqual(retry, first);
    assert.equal(retry.body.idempotencyKey, "key-1");
});

test("an edited amount represents a new request", () => {
    const first = prepareTransactionAttempt({ amount: "1000" }, null, () => "key-1");
    const edited = prepareTransactionAttempt({ amount: "2000" }, first, () => "key-2");
    assert.equal(edited.body.idempotencyKey, "key-2");
    assert.equal(edited.body.amount, "2000");
});

test("after confirmed success an identical new transaction gets a new key", () => {
    const next = prepareTransactionAttempt({ amount: "1000" }, null, () => "new-key");
    assert.equal(next.body.idempotencyKey, "new-key");
});
