(() => {
  const KEYBOARD_OCCLUSION_THRESHOLD = 120;

  function computeSettingsDialogLayout({ layoutHeight, visualHeight, visualTop = 0 } = {}) {
    if (!Number.isFinite(layoutHeight) || !Number.isFinite(visualHeight) || !Number.isFinite(visualTop)) return null;
    if (layoutHeight - visualHeight < KEYBOARD_OCCLUSION_THRESHOLD) return null;
    return {
      top: Math.max(0, Math.ceil(visualTop)) + 8,
      maxHeight: Math.max(100, Math.floor(visualHeight - 16)),
    };
  }

  const api = Object.freeze({ computeSettingsDialogLayout });
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.DayflowAndroidKeyboardLayout = api;
})();
