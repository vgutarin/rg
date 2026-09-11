// The native TimePicker virtualizes one option per row. For hour-dividing steps, replace those
// options with virtualized hour rows whose buttons still commit through the native picker.

export {};

interface TimeOption {
  label: string;
  value: string;
}

interface TimeRow {
  slots: TimeOption[];
}

interface TimePickerElement extends HTMLElement {
  step: number;
  opened: boolean;
  renderer?: (root: HTMLElement, owner: unknown, model: { item: TimeRow }) => void;
  _dropdownItems?: TimeOption[];
  __effectiveI18n?: {
    parseTime(value: string): { hours: number } | undefined;
  };
  _scroller?: {
    renderer?: TimePickerElement['renderer'];
    style: CSSStyleDeclaration;
    setProperties(properties: Record<string, unknown>): void;
    requestContentUpdate(): void;
    scrollIntoView(index: number, alignToCenter?: boolean): void;
  };
  _focusedIndex: number;
  _inputElementValue: string;
  _comboBoxValue: string;
  _theme: string;
  inputElement: HTMLInputElement;
  _updateScroller: (opened: boolean, items: TimeOption[], focusedIndex: number, theme: string) => void;
  close(): void;
  __temporalHourRowsConfigured?: boolean;
  __temporalHourRowsEnabled?: boolean;
  __temporalHourRowsOriginalUpdateScroller?: TimePickerElement['_updateScroller'];
  __temporalKeyboardConfigured?: boolean;
  __temporalDropdownOnly?: boolean;
  __temporalKeyboardReady?: boolean;
}

interface DateTimePickerElement extends HTMLElement {
  __timePicker?: TimePickerElement;
}

const CONFIGURATION_ATTEMPTS = 10;

declare global {
  interface Window {
    rgConfigureTemporalTimePicker(
      dateTimePicker: DateTimePickerElement, stepSeconds: number, dropdownAvailable: boolean): void;
  }
}

function supportsHourRows(stepSeconds: number): boolean {
  return stepSeconds >= 15 * 60 && stepSeconds <= 60 * 60 && 60 * 60 % stepSeconds === 0;
}

export function groupByHour(timePicker: TimePickerElement, options: TimeOption[]): TimeRow[] {
  const rows = new Map<number, TimeOption[]>();
  options.forEach((option, index) => {
    const hour = timePicker.__effectiveI18n?.parseTime(option.value)?.hours
      ?? Math.floor(index / (60 * 60 / timePicker.step));
    const slots = rows.get(hour) ?? [];
    slots.push(option);
    rows.set(hour, slots);
  });
  return [...rows.values()].map((slots) => ({ slots }));
}

function renderHourRow(root: HTMLElement, timePicker: TimePickerElement, row: TimeRow): void {
  root.className = 'temporal-time-picker-hour';
  root.setAttribute('role', 'presentation');
  root.replaceChildren();

  const slots = document.createElement('div');
  slots.className = 'temporal-time-picker-slots';
  slots.style.setProperty('--temporal-time-picker-slots', String(row.slots.length));
  row.slots.forEach((option) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'temporal-time-picker-option';
    button.textContent = option.label;
    button.setAttribute('aria-label', option.label);
    button.classList.toggle('temporal-time-picker-option-selected', timePicker._comboBoxValue === option.value);
    button.addEventListener('click', (event) => {
      event.stopPropagation();
      selectTimeOption(timePicker, option);
    });
    slots.append(button);
  });
  root.append(slots);
}

export function selectTimeOption(timePicker: TimePickerElement, option: TimeOption): void {
  timePicker._focusedIndex = -1;
  timePicker._inputElementValue = option.label;
  timePicker._comboBoxValue = option.value;
  timePicker.close();
}

export function scrollToSelectedHour(timePicker: TimePickerElement, rows: TimeRow[]): void {
  const selectedRow = rows.findIndex((row) =>
    row.slots.some((option) => option.value === timePicker._comboBoxValue));
  if (selectedRow >= 0) {
    timePicker._scroller?.scrollIntoView(selectedRow, true);
  }
}

/** Keeps the opening press from focusing the input, then enables typing after the overlay has settled. */
export function configureKeyboardBehavior(timePicker: TimePickerElement, dropdownAvailable: boolean): void {
  timePicker.__temporalDropdownOnly = dropdownAvailable;
  if (!timePicker.__temporalKeyboardConfigured) {
    timePicker.__temporalKeyboardConfigured = true;
    timePicker.inputElement.addEventListener('pointerdown', (event) => {
      if (!timePicker.__temporalDropdownOnly) {
        return;
      }
      if (timePicker.__temporalKeyboardReady && timePicker.opened) {
        timePicker.inputElement.readOnly = false;
        timePicker.inputElement.focus({ preventScroll: true });
        return;
      }
      // Do not let the press focus an already-focused input: on mobile that preserves or opens
      // the virtual keyboard before TimePicker's host click has opened the dropdown.
      event.preventDefault();
      timePicker.inputElement.blur();
    });
    timePicker.addEventListener('opened-changed', (event) => {
      if ((event as CustomEvent<{ value: boolean }>).detail.value) {
        timePicker.__temporalKeyboardReady = false;
        requestAnimationFrame(() => {
          if (timePicker.opened && timePicker.__temporalDropdownOnly) {
            timePicker.__temporalKeyboardReady = true;
          }
        });
      } else {
        timePicker.__temporalKeyboardReady = false;
        if (timePicker.__temporalDropdownOnly) {
          timePicker.inputElement.readOnly = true;
        }
      }
    });
  }
  timePicker.__temporalKeyboardReady = false;
  timePicker.inputElement.readOnly = dropdownAvailable;
}

function configureTimePicker(
  timePicker: TimePickerElement, stepSeconds: number, dropdownAvailable: boolean, attempt = 0): void {
  if (!timePicker._scroller) {
    if (attempt < CONFIGURATION_ATTEMPTS) {
      requestAnimationFrame(() => configureTimePicker(timePicker, stepSeconds, dropdownAvailable, attempt + 1));
    }
    return;
  }

  if (!timePicker.__temporalHourRowsConfigured) {
    timePicker.__temporalHourRowsConfigured = true;
    timePicker.__temporalHourRowsOriginalUpdateScroller = timePicker._updateScroller.bind(timePicker);
    timePicker._updateScroller = (opened, options, focusedIndex, theme) => {
      const original = timePicker.__temporalHourRowsOriginalUpdateScroller!;
      if (!timePicker.__temporalHourRowsEnabled) {
        original(opened, options, focusedIndex, theme);
        return;
      }
      if (opened) {
        timePicker._scroller!.style.maxHeight =
          getComputedStyle(timePicker).getPropertyValue('--vaadin-time-picker-overlay-max-height') || '65vh';
      }
      const rows = groupByHour(timePicker, options);
      const focusedRow = rows.findIndex((row) => row.slots.includes(options[focusedIndex]));
      timePicker._scroller!.setProperties({
        items: opened ? rows : [],
        opened,
        focusedIndex: focusedRow,
        theme,
      });
      if (opened) {
        requestAnimationFrame(() => {
          if (timePicker.opened) {
            scrollToSelectedHour(timePicker, rows);
          }
        });
      }
    };
  }

  const hourRowsEnabled = supportsHourRows(stepSeconds);
  timePicker.__temporalHourRowsEnabled = hourRowsEnabled;
  timePicker.renderer = hourRowsEnabled
    ? (root, _owner, model) => renderHourRow(root, timePicker, model.item)
    : undefined;
  timePicker._scroller.renderer = timePicker.renderer;
  configureKeyboardBehavior(timePicker, dropdownAvailable);
  timePicker._updateScroller(timePicker.opened, timePicker._dropdownItems ?? [], timePicker._focusedIndex, timePicker._theme);
}

function configureDateTimePicker(
  dateTimePicker: DateTimePickerElement, stepSeconds: number, dropdownAvailable: boolean, attempt = 0): void {
  const timePicker = dateTimePicker.__timePicker;
  if (!timePicker) {
    if (attempt < CONFIGURATION_ATTEMPTS) {
      requestAnimationFrame(() => configureDateTimePicker(dateTimePicker, stepSeconds, dropdownAvailable, attempt + 1));
    }
    return;
  }
  configureTimePicker(timePicker, stepSeconds, dropdownAvailable);
}

window.rgConfigureTemporalTimePicker = (dateTimePicker, stepSeconds, dropdownAvailable) => {
  configureDateTimePicker(dateTimePicker, stepSeconds, dropdownAvailable);
};
window.dispatchEvent(new Event('rg-temporal-time-picker-ready'));
