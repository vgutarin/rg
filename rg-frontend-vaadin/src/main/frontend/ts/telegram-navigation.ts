interface TelegramBackButton {
  show(): void;
  hide(): void;
  onClick(callback: () => void): void;
}

declare global {
  interface Window {
    Telegram?: {
      WebApp?: {
        BackButton?: TelegramBackButton;
      };
    };
    rgUpdateTelegramBackButton?: () => void;
    rgStartTelegramNavigation?: (currentRoute: string) => void;
  }
}

let registeredBackButton: TelegramBackButton | undefined;
let waitingForSdkLoad = false;
const TELEGRAM_SDK_ELEMENT_ID = 'telegram-web-app-sdk';
let currentLocation: string | undefined;
let knownInAppTransitions = 0;
let browserBackPending = false;
let navigationStarted = false;
let initialButtonUpdatePending = false;

function telegramBackButton(): TelegramBackButton | undefined {
  return window.Telegram?.WebApp?.BackButton;
}

function navigateBack(): void {
  // The button is hidden at the landing baseline, so this only fires with at least one known
  // transition to retrace. Browser back retraces the real history exactly, revisits included.
  if (knownInAppTransitions > 0) {
    browserBackPending = true;
    window.history.back();
  }
}

function observeNavigation(): void {
  const nextLocation = window.location.pathname;

  if (currentLocation !== undefined && currentLocation !== nextLocation) {
    if (browserBackPending) {
      knownInAppTransitions = Math.max(0, knownInAppTransitions - 1);
      browserBackPending = false;
    } else {
      knownInAppTransitions++;
    }
  }

  currentLocation = nextLocation;
}

/** Keeps Telegram's native back affordance aligned with the current Vaadin route. */
export function updateTelegramBackButton(): void {
  if (!navigationStarted) {
    return;
  }

  const backButton = telegramBackButton();
  if (!backButton) {
    return;
  }

  if (registeredBackButton !== backButton) {
    backButton.onClick(navigateBack);
    registeredBackButton = backButton;
  }

  if (initialButtonUpdatePending) {
    if (currentLocation === window.location.pathname) {
      initialButtonUpdatePending = false;
      window.history.pushState(window.history.state, '', window.location.href);
    }
    backButton.hide();
    return;
  }

  observeNavigation();

  if (knownInAppTransitions > 0) {
    backButton.show();
  } else {
    backButton.hide();
  }
}

export function initializeTelegramBackButton(): void {
  if (!navigationStarted) {
    return;
  }

  if (telegramBackButton()) {
    updateTelegramBackButton();
    return;
  }

  registerSdkLoadListener();
}

/** Starts route observation at the completed authenticated shell navigation. */
export function startTelegramNavigation(currentRoute: string): void {
  if (navigationStarted) {
    return;
  }

  navigationStarted = true;
  currentLocation = currentRoute ? `/${currentRoute.replace(/^\/+/, '')}` : '/';
  knownInAppTransitions = 0;
  browserBackPending = false;
  initialButtonUpdatePending = true;
  initializeTelegramBackButton();
}

window.rgUpdateTelegramBackButton = updateTelegramBackButton;
window.rgStartTelegramNavigation = startTelegramNavigation;
window.addEventListener('vaadin-navigated', updateTelegramBackButton);
window.addEventListener('popstate', updateTelegramBackButton);

function registerSdkLoadListener(): void {
  if (waitingForSdkLoad) {
    return;
  }

  const script = document.getElementById(TELEGRAM_SDK_ELEMENT_ID);
  if (!script) {
    return;
  }

  waitingForSdkLoad = true;
  script.addEventListener('load', () => {
    waitingForSdkLoad = false;
    updateTelegramBackButton();
  }, { once: true });
}

if (!telegramBackButton()) {
  registerSdkLoadListener();
}
