const test = require('node:test');
const assert = require('node:assert/strict');
const { computeSettingsDialogLayout } = require('../app/src/main/assets/desktop/android-keyboard-layout.js');

test('pins the active settings dialog inside the visible area above the keyboard', () => {
  assert.deepEqual(
    computeSettingsDialogLayout({ layoutHeight: 780, visualHeight: 420, visualTop: 0 }),
    { top: 8, maxHeight: 404 },
  );
});

test('leaves dialog placement alone when the keyboard is not covering the page', () => {
  assert.equal(
    computeSettingsDialogLayout({ layoutHeight: 780, visualHeight: 780, visualTop: 0 }),
    null,
  );
});

test('accounts for a shifted visual viewport while keeping the dialog within it', () => {
  assert.deepEqual(
    computeSettingsDialogLayout({ layoutHeight: 780, visualHeight: 280, visualTop: 18 }),
    { top: 26, maxHeight: 264 },
  );
});
