// Tests the discard-confirmation behavior around the tasting-note form.
//
// Bugs being verified (see chat):
//   1. Cancel button SHOULD trigger discard confirmation when form is dirty
//   2. Save SHOULD NOT trigger discard confirmation
//   3. Cancelling the discard confirmation SHOULD preserve form state
//
// Run with: node dev/test_note_discard.js
// Requires: backend on :8080, shadow-cljs watcher running.

const { makePageWithAuth, gotoApp } = require('./test_helpers');
const { execSync } = require('child_process');
const path = require('path');

const WINE_ID = 24; // Weingut Keller Kirchspiel Riesling GG — pick any existing wine
const TEST_TAG = '[test-note-discard]'; // Prefix added to every note this suite creates
const PROJECT_ROOT = path.resolve(__dirname, '..');

function logPass(label) { console.log(`PASS  ${label}`); }
function logFail(label, detail) { console.log(`FAIL  ${label}${detail ? '  →  ' + detail : ''}`); }

async function openFreshNoteForm(page) {
  await gotoApp(page, `/wine/${WINE_ID}`);
  await page.waitForSelector('text=Tasting Notes');
  // The "add tasting note" trigger is a small icon-only button next to the header.
  // Use the tooltip's title attribute to find it reliably.
  await page.locator('[aria-label="Add tasting note"], button:near(:text("Tasting Notes"))').first().click().catch(async () => {
    // Fallback: click the + (Add) icon button under the Tasting Notes section
    await page.locator('button:has(svg[data-testid="AddIcon"])').first().click();
  });
  await page.getByRole('heading', { name: 'Add Tasting Note' }).waitFor({ timeout: 3000 });
}

// Make the form "dirty" in the reagent state sense by setting the Rating field,
// then also type into the notes textarea so we can later verify it's preserved.
// Tasting Date is required for non-external notes — fill it so submit isn't
// blocked by HTML5 validation in the save-path test. Note text is prefixed with
// TEST_TAG so the teardown step can clean up anything that gets persisted.
async function dirtyTheForm(page, noteText = 'My tasting test note', rating = '92') {
  const taggedNote = `${TEST_TAG} ${noteText}`;
  const dateInput = page.locator('label:has-text("Tasting Date") >> xpath=following::input[1]');
  await dateInput.fill('2025-06-01');
  const ratingInput = page.locator('label:has-text("Rating") >> xpath=following::input[1]');
  await ratingInput.fill(rating);
  const notesArea = page.locator('textarea[placeholder*="tasting notes"]').first();
  await notesArea.fill(taggedNote);
  // Blur to commit values into reagent state
  await notesArea.press('Tab');
}

async function isFormOpen(page) {
  return await page.getByRole('heading', { name: 'Add Tasting Note' }).isVisible().catch(() => false);
}

async function readNoteValue(page) {
  return await page.locator('textarea[placeholder*="tasting notes"]').first().inputValue();
}

async function readRatingValue(page) {
  return await page.locator('label:has-text("Rating") >> xpath=following::input[1]').inputValue();
}

// ── Test 1: Cancel button triggers discard confirmation ─────────────────────
async function testCancelTriggersDiscard() {
  console.log('\n=== Test 1: Cancel button shows discard prompt ===');
  const { browser, page } = await makePageWithAuth();
  try {
    await openFreshNoteForm(page);
    await dirtyTheForm(page);

    let confirmShown = false;
    page.once('dialog', async dialog => {
      confirmShown = true;
      console.log(`  dialog appeared: "${dialog.message()}"`);
      await dialog.dismiss(); // click Cancel on the confirm
    });

    await page.locator('button:has-text("Cancel")').click();
    await page.waitForTimeout(500);

    if (!confirmShown) { logFail('Test 1', 'Cancel did not trigger confirm dialog'); return; }
    const stillOpen = await isFormOpen(page);
    if (!stillOpen) { logFail('Test 1', 'Form closed even though discard was cancelled'); return; }

    const rating = await readRatingValue(page);
    const note = await readNoteValue(page);
    if (rating !== '92') { logFail('Test 1', `Rating lost: got "${rating}"`); return; }
    const expectedNote = `${TEST_TAG} My tasting test note`;
    if (note !== expectedNote) { logFail('Test 1', `Note text lost: got "${note}"`); return; }
    logPass('Test 1: Cancel prompts; dismissing keeps form + data intact');
  } finally {
    await browser.close();
  }
}

// ── Test 2: Save does NOT trigger discard confirmation ──────────────────────
async function testSaveDoesNotPrompt() {
  console.log('\n=== Test 2: Save does not show discard prompt ===');
  const { browser, page } = await makePageWithAuth();
  try {
    await openFreshNoteForm(page);
    await dirtyTheForm(page, 'Save path test note', '88');

    let confirmShown = false;
    page.on('dialog', async dialog => {
      confirmShown = true;
      console.log(`  UNEXPECTED dialog: "${dialog.message()}"`);
      await dialog.dismiss();
    });

    await page.locator('button:has-text("Add Note")').click();
    await page.waitForTimeout(1500); // let API + navigation settle

    if (confirmShown) { logFail('Test 2', 'Save unexpectedly triggered discard prompt'); return; }
    const stillOpen = await isFormOpen(page);
    if (stillOpen) { logFail('Test 2', 'Form still open after Save'); return; }
    logPass('Test 2: Save closes form without any discard prompt');
  } finally {
    await browser.close();
  }
}

// ── Test 3: Confirm Cancel (OK = discard) closes the form ───────────────────
async function testCancelConfirmDiscards() {
  console.log('\n=== Test 3: Confirming discard closes form ===');
  const { browser, page } = await makePageWithAuth();
  try {
    await openFreshNoteForm(page);
    await dirtyTheForm(page, 'Discard test', '90');

    page.once('dialog', async dialog => {
      console.log(`  dialog appeared: "${dialog.message()}"`);
      await dialog.accept(); // click OK = discard
    });

    await page.locator('button:has-text("Cancel")').click();
    await page.waitForTimeout(800);

    const stillOpen = await isFormOpen(page);
    if (stillOpen) { logFail('Test 3', 'Form still open after confirming discard'); return; }
    logPass('Test 3: Confirming discard closes the form');
  } finally {
    await browser.close();
  }
}

// Delete any tasting notes this suite (or a prior crashed run) persisted.
// Robust to re-runs: keyed on TEST_TAG prefix, not on per-test IDs.
function cleanupTestNotes() {
  console.log('\n=== Teardown: deleting test-tagged notes ===');
  const expr = `(do (require '[next.jdbc :as jdbc]) (require '[wine-cellar.db.connection :as conn]) (jdbc/execute-one! conn/ds [\\"DELETE FROM tasting_notes WHERE notes LIKE ?\\" \\"${TEST_TAG}%\\"]))`;
  try {
    const out = execSync(`bb scripts/repl_client.clj "${expr}"`, { cwd: PROJECT_ROOT }).toString().trim();
    console.log(`  ${out}`);
  } catch (e) {
    console.log(`  cleanup failed: ${e.message}`);
  }
}

(async () => {
  try {
    await testCancelTriggersDiscard();
    await testSaveDoesNotPrompt();
    await testCancelConfirmDiscards();
  } finally {
    cleanupTestNotes();
  }
})().catch(err => { console.error(err); process.exit(1); });
