// Google Maps browser connector for the "Add location" flow (User Story 2).
//
// Responsibilities:
//  - load the Google Maps JavaScript API (referrer-restricted key from server config, finite timeout);
//  - present the map picker so the user selects a place (Place ID + coordinates) via search or a POI
//    tap, or a point (coordinates only) by tapping/dragging the map;
//  - if Google Maps is unavailable, signal onMapsUnavailable so the server lets the user add a location
//    without coordinates (there is no Telegram/geolocation fallback).
//
// The server side is reached through `element.$server` (Vaadin @ClientCallable): onCoordinatesAcquired
// and onMapsUnavailable. Google globals are declared loosely to avoid extra type deps; Vite strips
// types without type-checking.

declare const google: any;

const MAPS_LOAD_TIMEOUT_MS = 8000;
const SCRIPT_ID = 'rg-google-maps-js';

// Map-centering only (NOT coordinate acquisition): centre priority is Telegram LocationManager (works on
// iOS, where the W3C Geolocation API is unreliable) -> browser geolocation -> the Kyiv default.
const KYIV = { lat: 50.4501, lng: 30.5234 };
const TG_INIT_TIMEOUT_MS = 8000;
// Generous: getLocation() shows Telegram's native permission prompt the first time, so the read must
// give the user time to answer. This is only a safety net against a stuck callback, not the common path.
const TG_GET_TIMEOUT_MS = 30000;

interface FlowServer {
  onCoordinatesAcquired(
    latitude: number, longitude: number, placeId: string | null, name: string | null): Promise<unknown>;
  onMapsUnavailable(): Promise<unknown>;
}

interface FlowElement extends HTMLElement {
  $server?: FlowServer;
}

function server(element: FlowElement): FlowServer | undefined {
  return element.$server;
}

function loadMapsScript(apiKey: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    if ((window as any).google && (window as any).google.maps) {
      resolve();
      return;
    }
    if (!apiKey) {
      reject(new Error('missing-google-maps-key'));
      return;
    }
    const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener('load', () => resolve());
      existing.addEventListener('error', () => reject(new Error('google-maps-load-error')));
      return;
    }
    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.async = true;
    script.src =
      'https://maps.googleapis.com/maps/api/js?libraries=places&key=' + encodeURIComponent(apiKey);
    script.addEventListener('load', () => resolve());
    script.addEventListener('error', () => reject(new Error('google-maps-load-error')));
    document.head.appendChild(script);
  });
}

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new Error('timeout')), ms);
    promise.then(
      (value) => {
        window.clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        window.clearTimeout(timer);
        reject(error);
      },
    );
  });
}

function telegramManager(): { webApp: any; manager: any } | null {
  const webApp = (window as any).Telegram && (window as any).Telegram.WebApp;
  const manager = webApp && webApp.LocationManager;
  if (!manager || typeof manager.getLocation !== 'function') {
    return null;
  }
  return { webApp, manager };
}

/** Resolves once the manager is initialised (via the locationManagerUpdated event) or times out. */
function ensureInitiated(webApp: any, manager: any): Promise<void> {
  if (manager.isInited) {
    return Promise.resolve();
  }
  return new Promise<void>((resolve) => {
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      try { webApp.offEvent('locationManagerUpdated', finish); } catch (_error) { /* ignore */ }
      resolve();
    };
    try { webApp.onEvent('locationManagerUpdated', finish); } catch (_error) { /* ignore */ }
    // Proceed on the locationManagerUpdated EVENT (reliable), not init()'s own callback (which can
    // never fire) — so init gets an empty callback and we resolve via the event or the timeout.
    try { manager.init(() => {}); } catch (_error) { finish(); }
    window.setTimeout(finish, TG_INIT_TIMEOUT_MS);
  });
}

function getLocationOnce(manager: any): Promise<{ latitude: number; longitude: number } | null> {
  return new Promise((resolve) => {
    try {
      manager.getLocation((data: any) => {
        if (data && typeof data.latitude === 'number' && typeof data.longitude === 'number') {
          resolve({ latitude: data.latitude, longitude: data.longitude });
        } else {
          resolve(null);
        }
      });
    } catch (_error) {
      resolve(null);
    }
  });
}

/** Bounded Telegram getLocation: resolves null if the client doesn't answer within TG_GET_TIMEOUT_MS. */
function getTelegramLocation(manager: any): Promise<{ latitude: number; longitude: number } | null> {
  return Promise.race([
    getLocationOnce(manager),
    new Promise<{ latitude: number; longitude: number } | null>(
      (resolve) => window.setTimeout(() => resolve(null), TG_GET_TIMEOUT_MS)),
  ]);
}

/** Browser (W3C) geolocation as a promise; resolves null on any error / when unsupported. */
function browserGeolocation(): Promise<{ lat: number; lng: number } | null> {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve(null);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ lat: position.coords.latitude, lng: position.coords.longitude }),
      () => resolve(null),
      // maximumAge: 0 forces a FRESH high-accuracy GPS fix (no cached/coarse position).
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 0 });
  });
}

/**
 * Resolves the map-centering location. Browser geolocation FIRST — it uses GPS and forces a fresh,
 * high-accuracy fix (maximumAge: 0), the accurate source on Android webviews and browsers. Telegram
 * LocationManager is only the fallback (iOS, where navigator is unreliable); it has no "refresh" and can
 * return a coarse/last-known fix, so it is used only when the browser yields nothing. Telegram path:
 * ready() -> init() (proceed on the locationManagerUpdated EVENT) -> getLocation(); if not granted,
 * openSettings() and re-centre via `centring.recenter` when the user grants (non-blocking).
 */
async function resolveUserCentre(
  centring: { recenter: (lat: number, lng: number) => void },
): Promise<{ lat: number; lng: number } | null> {
  // 1. Browser geolocation — accurate GPS, forced fresh.
  const browser = await browserGeolocation();
  if (browser) {
    return browser;
  }
  // 2. Fallback: Telegram LocationManager (covers iOS; may be coarse).
  const tg = telegramManager();
  if (!tg) {
    return null;
  }
  const { webApp, manager } = tg;
  try {
    if (typeof webApp.ready === 'function') webApp.ready();
    await ensureInitiated(webApp, manager);
    if (manager.isLocationAvailable) {
      const current = await getTelegramLocation(manager);
      if (current) {
        return { lat: current.latitude, lng: current.longitude };
      }
      if (!manager.isAccessGranted && typeof manager.openSettings === 'function') {
        try { manager.openSettings(); } catch (_error) { /* ignore */ }
        const onUpdate = async () => {
          if (!manager.isAccessGranted) {
            return;
          }
          try { webApp.offEvent('locationManagerUpdated', onUpdate); } catch (_error) { /* ignore */ }
          const granted = await getTelegramLocation(manager);
          if (granted) {
            centring.recenter(granted.latitude, granted.longitude);
          }
        };
        try { webApp.onEvent('locationManagerUpdated', onUpdate); } catch (_error) { /* ignore */ }
      }
    }
  } catch (_error) {
    // ignore; stay on the Kyiv default
  }
  return null;
}

// Resolved once per browser session (no persistence across sessions). The first "Add location" open
// runs the full centre-resolution chain — which may prompt for geolocation permission — and caches the
// result; every later open in the same session reuses it, so the permission prompt is not shown again.
// A resolved `null` (denied/unavailable) is cached too, so we don't re-prompt on each open. `centring`
// is only wired to the first open's map (for a late Telegram access-grant); later opens reuse the
// already-resolved centre and don't restart the chain.
let sessionCentre: { lat: number; lng: number } | null | undefined;
let sessionCentrePromise: Promise<{ lat: number; lng: number } | null> | null = null;

function resolveUserCentreCached(
  centring: { recenter: (lat: number, lng: number) => void },
): Promise<{ lat: number; lng: number } | null> {
  if (sessionCentre !== undefined) {
    return Promise.resolve(sessionCentre);
  }
  if (!sessionCentrePromise) {
    sessionCentrePromise = resolveUserCentre(centring)
      .catch(() => null)
      .then((centre) => {
        sessionCentre = centre;
        return centre;
      });
  }
  return sessionCentrePromise;
}

async function openPicker(
  element: FlowElement,
  mapId: string,
  confirmLabel: string,
  promptLabel: string,
  closeLabel: string,
  centrePromise: Promise<{ lat: number; lng: number } | null>,
  centring: { recenter: (lat: number, lng: number) => void },
): Promise<void> {
  // Three ways to choose the point under the fixed centre pin:
  //  1. type an address / place in the search box -> named place, with Place ID;
  //  2. tap one of the labelled places (POIs) the map shows (metro, stadium, restaurant) -> named
  //     place, with Place ID;
  //  3. tap an empty spot or drag the map under the pin -> a custom point, coordinates only (no Place
  //     ID). The status line shows the place name (1, 2) or the coordinates (3) before confirming.
  const [{ PlaceAutocompleteElement, Place }, { Map }] = await Promise.all([
    google.maps.importLibrary('places'),
    google.maps.importLibrary('maps'),
  ]);

  const overlay = document.createElement('div');
  overlay.className = 'rg-maps-picker-overlay';
  overlay.style.cssText =
    'position:fixed;inset:0;background:rgba(0,0,0,0.5);z-index:1000;display:flex;' +
    'align-items:stretch;justify-content:center;padding:0.5rem;';

  const panel = document.createElement('div');
  // max-height + overflow so a short screen (e.g. landscape phone) can scroll rather than clip.
  panel.style.cssText =
    'background:#fff;width:100%;max-width:40rem;max-height:100%;overflow:auto;display:flex;' +
    'flex-direction:column;gap:0.5rem;padding:0.5rem;border-radius:0.5rem;';

  const search = new PlaceAutocompleteElement();
  search.style.width = '100%';

  // Shows the selected place name (POI/search) or the centre coordinates (custom point) or the prompt.
  const status = document.createElement('div');
  status.style.cssText = 'font-size:0.95rem;padding:0.25rem 0.5rem;min-height:1.25em;color:#202124;';
  status.textContent = promptLabel;

  // Map plus a fixed centre pin: the pin marks the chosen point; the map moves under it.
  const mapWrap = document.createElement('div');
  // Responsive floor: cap the map at 40vh on short screens so the form/controls stay reachable.
  mapWrap.style.cssText = 'position:relative;flex:1 1 auto;min-height:min(320px, 40vh);width:100%;';
  const mapDiv = document.createElement('div');
  mapDiv.style.cssText = 'position:absolute;inset:0;border-radius:0.25rem;';
  const pin = document.createElement('div');
  pin.style.cssText =
    'position:absolute;left:50%;top:50%;transform:translate(-50%,-100%);pointer-events:none;z-index:1;';
  pin.innerHTML =
    '<svg width="30" height="42" viewBox="0 0 24 34" xmlns="http://www.w3.org/2000/svg">' +
    '<path d="M12 0C6 0 1.5 4.5 1.5 10.5c0 7.5 9 22 10.5 23.5 1.5-1.5 10.5-16 10.5-23.5C22.5 4.5 18 0 12 0z" ' +
    'fill="#ea4335" stroke="#b31412" stroke-width="1"/><circle cx="12" cy="10.5" r="4" fill="#fff"/></svg>';
  mapWrap.appendChild(mapDiv);
  mapWrap.appendChild(pin);

  const confirm = document.createElement('button');
  confirm.type = 'button';
  confirm.textContent = confirmLabel;
  confirm.disabled = true;
  confirm.style.cssText = 'padding:0.75rem;font-size:1rem;';

  panel.appendChild(search);
  panel.appendChild(status);
  panel.appendChild(mapWrap);
  panel.appendChild(confirm);
  overlay.appendChild(panel);
  document.body.appendChild(overlay);

  const close = () => overlay.remove();

  // Explicit close (✕, top-right): dismiss the picker WITHOUT choosing a point — no server callback
  // fires, so no inline add form is shown. Same effect as tapping the backdrop.
  const closeButton = document.createElement('button');
  closeButton.type = 'button';
  closeButton.textContent = '✕';
  closeButton.setAttribute('aria-label', closeLabel);
  closeButton.style.cssText =
    'align-self:flex-end;font-size:1.25rem;line-height:1;padding:0.25rem 0.6rem;' +
    'background:transparent;border:none;cursor:pointer;color:#5f6368;';
  closeButton.addEventListener('click', () => close());
  panel.prepend(closeButton);

  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) {
      close();
    }
  });

  // A Vector Map ID enables the modern vector (WebGL) map; without one the map renders as raster.
  // clickableIcons:true keeps the map's POI labels tappable so the user can pick a place directly.
  const mapOptions: any = {
    center: KYIV,
    zoom: 12,
    clickableIcons: true,
    disableDefaultUI: true,
    zoomControl: true,
    gestureHandling: 'greedy',
  };
  if (mapId) {
    mapOptions.mapId = mapId;
  }
  const map = new Map(mapDiv, mapOptions);

  // The point is always whatever is under the centre pin (map.getCenter()); selectedPlaceId is set only
  // while that centre corresponds to a chosen POI / searched place, and cleared for a custom point.
  let selectedPlaceId: string | null = null;
  let selectedName: string | null = null;
  let positioned = false;

  const showCentreCoords = () => {
    const centre = map.getCenter();
    if (centre) {
      status.textContent = centre.lat().toFixed(6) + ', ' + centre.lng().toFixed(6);
    }
  };

  // Panning by hand makes the centre a custom point: any selected Place ID no longer applies.
  map.addListener('dragstart', () => {
    positioned = true;
    selectedPlaceId = null;
    selectedName = null;
  });
  map.addListener('idle', () => {
    if (!positioned) {
      return; // ignore the initial settle on the default world view
    }
    confirm.disabled = false;
    if (!selectedPlaceId) {
      showCentreCoords();
    }
  });

  map.addListener('click', async (event: any) => {
    positioned = true;
    if (event && event.placeId) {
      // A labelled place (POI) was tapped: capture its Place ID and name; Google shows its info window.
      try {
        const place = new Place({ id: event.placeId });
        await place.fetchFields({ fields: ['displayName', 'location'] });
        const location = place.location;
        if (!location) {
          return;
        }
        selectedPlaceId = event.placeId;
        selectedName = place.displayName || null;
        status.textContent =
          place.displayName || (location.lat().toFixed(6) + ', ' + location.lng().toFixed(6));
        confirm.disabled = false;
        map.panTo(location);
      } catch (_error) {
        // ignore; leave the previous selection unchanged
      }
    } else if (event && event.latLng) {
      // An empty spot was tapped: a custom point, coordinates only (idle refreshes the coordinates).
      selectedPlaceId = null;
      selectedName = null;
      confirm.disabled = false;
      map.panTo(event.latLng);
    }
  });

  // Type-and-select a place from the search box.
  search.addEventListener('gmp-select', async (event: any) => {
    const prediction = event && event.placePrediction;
    if (!prediction) {
      return;
    }
    positioned = true;
    const place = prediction.toPlace();
    const placeId = prediction.placeId || place.id || null;
    await place.fetchFields({ fields: ['displayName', 'location'] });
    const location = place.location;
    if (!location) {
      return;
    }
    selectedPlaceId = placeId;
    selectedName = place.displayName || null;
    status.textContent =
      place.displayName || (location.lat().toFixed(6) + ', ' + location.lng().toFixed(6));
    confirm.disabled = false;
    map.setCenter({ lat: location.lat(), lng: location.lng() });
    map.setZoom(16);
  });

  // Confirm sends whatever is under the centre pin, plus the Place ID and name of the selected place
  // (both null for a custom map point).
  confirm.addEventListener('click', () => {
    const centre = map.getCenter();
    if (!centre) {
      return;
    }
    close();
    server(element)?.onCoordinatesAcquired(centre.lat(), centre.lng(), selectedPlaceId, selectedName);
  });

  // Best-effort centering (until the user interacts): Telegram LocationManager (works on iOS) first,
  // then browser geolocation, otherwise the Kyiv default the map already opened at. Runs in the
  // background so the map is usable immediately.
  const centreOn = (lat: number, lng: number) => {
    if (positioned) {
      return;
    }
    positioned = true;
    map.setCenter({ lat, lng });
    map.setZoom(15);
  };
  // Let a late Telegram access-grant (user returning from openSettings) re-centre the map.
  centring.recenter = centreOn;
  void centrePromise.then((centre) => {
    if (centre) {
      centreOn(centre.lat, centre.lng);
    }
  });
}

async function init(
  element: FlowElement,
  apiKey: string,
  mapId: string,
  confirmLabel: string,
  promptLabel: string,
  closeLabel: string,
): Promise<void> {
  // Resolve the centre chain in parallel with the Maps script load (Telegram location if granted;
  // otherwise it requests access via openSettings and re-centres later through `centring.recenter`;
  // then browser geolocation, else Kyiv). Started here, within the click gesture, so openSettings() is
  // allowed to prompt. Non-blocking and bounded; used only to centre the map. Resolved once per browser
  // session and cached, so re-opening the picker does not prompt for geolocation permission again.
  const centring = { recenter: (_lat: number, _lng: number) => { /* set once the map exists */ } };
  const centrePromise = resolveUserCentreCached(centring);
  try {
    await withTimeout(loadMapsScript(apiKey), MAPS_LOAD_TIMEOUT_MS);
    await openPicker(element, mapId, confirmLabel, promptLabel, closeLabel, centrePromise, centring);
  } catch (_error) {
    // No coordinate fallback: when Google Maps is unavailable, let the user add a location without
    // coordinates. The server opens the add form with null coordinates.
    server(element)?.onMapsUnavailable();
  }
}

(window as any).rgInitGoogleMapsConnector = init;

export { init };
