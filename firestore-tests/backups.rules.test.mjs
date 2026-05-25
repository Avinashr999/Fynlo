// Security-rules unit test for the Firestore `backups` rules.
//
// Verifies the change that lets Reset All Data wipe a user's own backups:
//   - the owner CAN delete their own backup doc and nested snapshot docs
//   - a different user CANNOT delete the owner's backup (security preserved)
//   - nested backup snapshots remain immutable (update denied)
//
// Run from the repo root (needs JDK 21+ for the emulator):
//   firebase emulators:exec --only firestore --project demo-fynlo \
//     "node firestore-tests/backups.rules.test.mjs"
// or from this directory: npm test

import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, deleteDoc, updateDoc } from "firebase/firestore";
import { readFileSync } from "node:fs";

const rules = readFileSync(new URL("../firestore.rules", import.meta.url), "utf8");

const testEnv = await initializeTestEnvironment({
  projectId: "demo-fynlo",
  firestore: { rules },
});

const owner = testEnv.authenticatedContext("u1").firestore();
const other = testEnv.authenticatedContext("u2").firestore();

// Seed a backup doc + a nested snapshot doc with rules disabled (setup only).
async function seed() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users/u1/backups/2026-01-01"), {
      date: "2026-01-01", netWorth: 100, createdAt: 1,
    });
    await setDoc(doc(db, "users/u1/backups/2026-01-01/accounts/a1"), { name: "Cash" });
  });
}

let failures = 0;
async function check(name, promise) {
  try {
    await promise;
    console.log("PASS:", name);
  } catch (e) {
    failures++;
    console.error("FAIL:", name, "-", e.message);
  }
}

await seed();
await check(
  "owner can delete a nested backup snapshot doc",
  assertSucceeds(deleteDoc(doc(owner, "users/u1/backups/2026-01-01/accounts/a1")))
);
await check(
  "owner can delete their own backup doc",
  assertSucceeds(deleteDoc(doc(owner, "users/u1/backups/2026-01-01")))
);

await seed();
await check(
  "a different user CANNOT delete the owner's backup",
  assertFails(deleteDoc(doc(other, "users/u1/backups/2026-01-01")))
);
await check(
  "nested backup snapshot stays immutable (update denied)",
  assertFails(updateDoc(doc(owner, "users/u1/backups/2026-01-01/accounts/a1"), { name: "X" }))
);

await testEnv.cleanup();

if (failures > 0) {
  console.error(`\n${failures} rule check(s) FAILED`);
  process.exit(1);
}
console.log("\nAll backup rule checks passed.");
