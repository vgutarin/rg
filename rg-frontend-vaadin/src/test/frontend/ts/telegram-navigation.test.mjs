import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const appShell = await readFile(new URL('../../../main/frontend/index.html', import.meta.url), 'utf8');
let scenario = 0;

function history() {
  let backCalls = 0;
  const pushStateCalls = [];
  return {
    state: { vaadin: true },
    back() { backCalls++; },
    backCalls: () => backCalls,
    pushState(state, unused, url) { pushStateCalls.push({ state, unused, url }); },
    pushStateCalls: () => pushStateCalls,
  };
}

function telegramBackButton() {
  const handlers = [];
  const button = {
    showCalls: 0,
    hideCalls: 0,
    show() { this.showCalls++; },
    hide() { this.hideCalls++; },
    onClick(handler) { handlers.push(handler); },
  };
  return { button, handlers };
}

async function navigationBridge(withTelegram = true) {
  globalThis.window = new EventTarget();
  window.location = { pathname: '/', href: 'https://rg.example/' };
  const browserHistory = history();
  window.history = browserHistory;

  const telegramSdkScript = new EventTarget();
  globalThis.document = {
    getElementById(id) {
      return id === 'telegram-web-app-sdk' ? telegramSdkScript : null;
    },
  };

  const telegram = telegramBackButton();
  function enableTelegram() {
    window.Telegram = { WebApp: { BackButton: telegram.button } };
  }

  if (withTelegram) {
    enableTelegram();
  }

  const bridge = await import(`../../../main/frontend/ts/telegram-navigation.ts?scenario=${scenario++}`);
  return { ...bridge, browserHistory, ...telegram, enableTelegram, telegramSdkScript };
}

function completeVaadinNavigation(pathname) {
  window.location = { pathname, href: `https://rg.example${pathname}` };
  window.dispatchEvent(new Event('vaadin-navigated'));
}

test('does nothing when the Telegram WebApp SDK is unavailable', async () => {
  const { browserHistory, startTelegramNavigation, updateTelegramBackButton } = await navigationBridge(false);

  assert.doesNotThrow(updateTelegramBackButton);
  assert.doesNotThrow(() => startTelegramNavigation('/'));
  assert.equal(browserHistory.backCalls(), 0);
});

test('does not register or show the native button before authentication starts navigation', async () => {
  const { button, handlers } = await navigationBridge();

  completeVaadinNavigation('/login');
  window.dispatchEvent(new Event('popstate'));

  assert.equal(handlers.length, 0);
  assert.equal(button.showCalls, 0);
  assert.equal(button.hideCalls, 0);
});

test('initializes after the Telegram SDK script loads once authenticated navigation starts', async () => {
  const { browserHistory, button, enableTelegram, telegramSdkScript, startTelegramNavigation } = await navigationBridge(false);

  startTelegramNavigation('/');
  assert.equal(button.hideCalls, 0);

  enableTelegram();
  telegramSdkScript.dispatchEvent(new Event('load'));

  assert.equal(browserHistory.backCalls(), 0);
  assert.equal(button.hideCalls, 1);
});

test('loads the Telegram SDK before Vaadin injects its entry bundle', () => {
  const sdkTag = '<script id="telegram-web-app-sdk" src="https://telegram.org/js/telegram-web-app.js?63"></script>';

  assert.ok(appShell.indexOf(sdkTag) >= 0);
  assert.ok(appShell.indexOf(sdkTag) < appShell.indexOf('<!-- index.ts is included here automatically'));
  assert.doesNotMatch(sdkTag, /\s(?:async|defer)(?:=|\s|>)/);
});

test('starting on authenticated Home initializes the transition count to zero', async () => {
  const { browserHistory, button, handlers } = await navigationBridge();

  window.location = { pathname: '/', search: '?source=telegram', href: 'https://rg.example/?source=telegram' };
  window.rgStartTelegramNavigation('/');

  assert.equal(button.hideCalls, 1);
  assert.equal(button.showCalls, 0);
  assert.equal(handlers.length, 1);
  assert.equal(browserHistory.backCalls(), 0);
  assert.deepEqual(browserHistory.pushStateCalls(), [{
    state: { vaadin: true },
    unused: '',
    url: 'https://rg.example/?source=telegram',
  }]);
});

test('shows the button on the first authenticated navigation, not the login-to-Home redirect', async () => {
  const { browserHistory, button, handlers } = await navigationBridge();

  completeVaadinNavigation('/login');
  window.rgStartTelegramNavigation('/');
  assert.equal(button.showCalls, 0);
  assert.equal(button.hideCalls, 1);
  // Still at the landing baseline: nothing to go back to.
  handlers[0]();
  assert.equal(browserHistory.backCalls(), 0);
  assert.equal(browserHistory.pushStateCalls().length, 0);

  // The browser URL catches up to the server-confirmed baseline; the safety boundary is pushed.
  completeVaadinNavigation('/');
  assert.equal(browserHistory.pushStateCalls().length, 1);
  assert.equal(button.showCalls, 0);
  assert.equal(button.hideCalls, 2);

  // The first real navigation away from the landing shows the button.
  completeVaadinNavigation('/workspaces/events');

  assert.equal(button.showCalls, 1);
  assert.equal(button.hideCalls, 2);
});

test('does not reset known history when MainView starts navigation again', async () => {
  const { browserHistory, handlers } = await navigationBridge();

  window.rgStartTelegramNavigation('/');
  completeVaadinNavigation('/workspaces/events');
  completeVaadinNavigation('/workspaces/participants');
  window.rgStartTelegramNavigation('/workspaces/participants');
  handlers[0]();

  assert.equal(browserHistory.backCalls(), 1);
  assert.equal(browserHistory.pushStateCalls().length, 1);
});

test('steps back with the browser from the very first navigation', async () => {
  const { browserHistory, button, handlers } = await navigationBridge();

  window.rgStartTelegramNavigation('/');
  completeVaadinNavigation('/workspaces/events');

  // A single navigation away from the landing is enough to enable browser back.
  assert.equal(button.showCalls, 1);
  handlers[0]();
  assert.equal(browserHistory.backCalls(), 1);
});

test('retraces the exact observed path, revisits included, and hides only at the landing', async () => {
  const { browserHistory, button, handlers } = await navigationBridge();

  // Landing baseline: hidden, nothing to retrace.
  window.rgStartTelegramNavigation('/');
  assert.equal(button.showCalls, 0);
  assert.equal(button.hideCalls, 1);

  // Locations -> Home -> Locations: three forward transitions, all recorded as real history.
  completeVaadinNavigation('/workspaces/locations');
  completeVaadinNavigation('/');
  completeVaadinNavigation('/workspaces/locations');
  assert.equal(button.showCalls, 3);

  // First back: Locations -> Home (the earlier Home revisit).
  handlers[0]();
  assert.equal(browserHistory.backCalls(), 1);
  window.location = { pathname: '/', href: 'https://rg.example/' };
  window.dispatchEvent(new Event('popstate'));
  assert.equal(button.showCalls, 4);

  // Second back: Home -> Locations.
  handlers[0]();
  assert.equal(browserHistory.backCalls(), 2);
  window.location = { pathname: '/workspaces/locations', href: 'https://rg.example/workspaces/locations' };
  window.dispatchEvent(new Event('popstate'));
  assert.equal(button.showCalls, 5);

  // Third back: Locations -> landing. Now at the baseline, so the button hides.
  handlers[0]();
  assert.equal(browserHistory.backCalls(), 3);
  window.location = { pathname: '/', href: 'https://rg.example/' };
  window.dispatchEvent(new Event('popstate'));
  assert.equal(button.hideCalls, 2);

  // At the landing baseline back does nothing further.
  handlers[0]();
  assert.equal(browserHistory.backCalls(), 3);
});

test('keeps a known BackButton path after visiting Home through a link', async () => {
  const { browserHistory, button, handlers } = await navigationBridge();

  window.rgStartTelegramNavigation('/');
  completeVaadinNavigation('/workspaces/locations');
  completeVaadinNavigation('/');

  // Visible from the first navigation and stays visible through the Home revisit.
  assert.equal(button.hideCalls, 1);
  assert.equal(button.showCalls, 2);

  completeVaadinNavigation('/workspaces/participants');
  handlers[0]();

  assert.equal(browserHistory.backCalls(), 1);

  window.location = { pathname: '/', href: 'https://rg.example/' };
  window.dispatchEvent(new Event('popstate'));
  handlers[0]();

  assert.equal(browserHistory.backCalls(), 2);
});
