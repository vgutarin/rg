import assert from 'node:assert/strict';
import test from 'node:test';

globalThis.window = {};
globalThis.requestAnimationFrame = (callback) => callback();

const { configureKeyboardBehavior, groupByHour, scrollToSelectedHour, selectTimeOption } =
  await import('../../../../main/frontend/ts/datetime/temporal-time-picker.ts');

test('groups localized 15-minute choices into one row per hour', () => {
  const options = [
    '12:00 AM', '12:15 AM', '12:30 AM', '12:45 AM',
    '1:00 AM', '1:15 AM', '1:30 AM', '1:45 AM',
  ].map((label) => ({ label, value: label }));
  const picker = {
    step: 15 * 60,
    __effectiveI18n: {
      parseTime(value) {
        const [clock, period] = value.split(' ');
        const [rawHour] = clock.split(':').map(Number);
        return { hours: period === 'PM' && rawHour !== 12 ? rawHour + 12 : rawHour % 12 };
      },
    },
  };

  assert.deepEqual(groupByHour(picker, options).map((row) => row.slots.map(({ label }) => label)), [
    ['12:00 AM', '12:15 AM', '12:30 AM', '12:45 AM'],
    ['1:00 AM', '1:15 AM', '1:30 AM', '1:45 AM'],
  ]);
});

test('selecting a slot updates and closes the native picker', () => {
  let closeCount = 0;
  const picker = {
    _focusedIndex: 7,
    _inputElementValue: '',
    _comboBoxValue: '',
    close() { closeCount++; },
  };

  selectTimeOption(picker, { label: '2:30 PM', value: '2:30 PM' });

  assert.equal(picker._focusedIndex, -1);
  assert.equal(picker._inputElementValue, '2:30 PM');
  assert.equal(picker._comboBoxValue, '2:30 PM');
  assert.equal(closeCount, 1);
});

test('keeps the opening press keyboard-free and enables typing only after the dropdown has opened', () => {
  let focusCount = 0;
  let blurCount = 0;
  const input = new EventTarget();
  input.readOnly = false;
  input.focus = () => { focusCount++; };
  input.blur = () => { blurCount++; };
  const picker = new EventTarget();
  picker.opened = false;
  picker.inputElement = input;

  configureKeyboardBehavior(picker, true);
  const openingPress = new Event('pointerdown', { cancelable: true });
  input.dispatchEvent(openingPress);
  assert.equal(openingPress.defaultPrevented, true);
  assert.equal(blurCount, 1);
  picker.opened = true;
  const opened = new Event('opened-changed');
  Object.defineProperty(opened, 'detail', { value: { value: true } });
  picker.dispatchEvent(opened);
  input.dispatchEvent(new Event('click'));
  assert.equal(input.readOnly, true);
  assert.equal(focusCount, 0);

  const editingPress = new Event('pointerdown', { cancelable: true });
  input.dispatchEvent(editingPress);
  assert.equal(editingPress.defaultPrevented, false);
  assert.equal(input.readOnly, false);
  assert.equal(focusCount, 1);

  picker.opened = false;
  const closed = new Event('opened-changed');
  Object.defineProperty(closed, 'detail', { value: { value: false } });
  picker.dispatchEvent(closed);
  assert.equal(input.readOnly, true);
});

test('scrolls the open dropdown to the current time hour', () => {
  const scrolledTo = [];
  const picker = {
    _comboBoxValue: '2:30 PM',
    _scroller: { scrollIntoView(index, centered) { scrolledTo.push([index, centered]); } },
  };
  const rows = [
    { slots: [{ value: '1:00 PM' }, { value: '1:15 PM' }] },
    { slots: [{ value: '2:00 PM' }, { value: '2:30 PM' }] },
  ];

  scrollToSelectedHour(picker, rows);

  assert.deepEqual(scrolledTo, [[1, true]]);
});
