package vg.rg.frontend.vaadin.component.disclosure;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * A single-open accordion list: each entry is a tappable header row with a collapsible detail panel
 * beneath it, and opening one closes whichever was open.
 *
 * <p><strong>Extracted so the screens that use it cannot drift apart.</strong> The locations screen
 * defined this shape first and the participants screen needs the identical one; duplicating either the
 * DOM structure or the CSS would guarantee that a fix to one silently misses the other. The styles it
 * relies on are named for the <em>pattern</em> ({@code disclosure-*}) rather than for either domain, so
 * neither screen owns them and a third can adopt them without renaming anything.
 *
 * <p>The caller supplies the header text and fills the panel; this class owns the DOM shape, the
 * accessibility attributes on the header, and the open/closed state. What goes in a panel is entirely
 * the caller's business — which is why {@link #addItem} hands one back rather than taking content.
 */
public class DisclosureList extends Div {

    /**
     * The CSS vocabulary this component owns.
     *
     * <p>Constants rather than string literals because these names are a <strong>contract</strong>: the
     * stylesheet, this class, both screens that fill a panel, and both of their tests all have to spell
     * them identically, and a typo in any of them fails silently — a rule that never matches, or an
     * assertion that finds nothing and passes. Two of the names below are not even applied here
     * ({@link #ROW_ACTIONS}, {@link #DETAIL_DESCRIPTION}, {@link #DETAIL_META}): they are conventions
     * for panel <em>content</em>, which callers apply, and they are declared here precisely because a
     * caller must not have to guess the spelling.
     *
     * <p>Classes used inside a single view stay literals there — the point is not that every class
     * becomes a constant, but that a name crossing a file boundary has one definition.
     */
    public static final String LIST = "disclosure-list";

    /** One entry: the header row plus its collapsible panel. Carries the zebra striping and divider. */
    public static final String ITEM = "disclosure-item";

    /** Present on an entry whose panel is open. Drives both the panel and the chevron rotation. */
    public static final String ITEM_OPEN = "disclosure-item--open";

    /** The tappable header row. */
    public static final String ROW = "disclosure-row";

    public static final String ROW_TEXT = "disclosure-row__text";
    public static final String ROW_NAME = "disclosure-row__name";
    public static final String ROW_META = "disclosure-row__meta";

    /** An informational glyph in the header, present only when it applies. See {@link #addItem}. */
    public static final String ROW_MARKER = "disclosure-row__marker";

    /** The disclosure chevron. */
    public static final String ROW_ICON = "disclosure-row__icon";

    public static final String ROW_PANEL = "disclosure-row__panel";
    public static final String ROW_PANEL_INNER = "disclosure-row__panel-inner";

    /** Applied by a caller to a row of management actions inside a panel: side by side, equal width. */
    public static final String ROW_ACTIONS = "disclosure-row__actions";

    /** Applied by a caller to a panel's leading free-text paragraph. */
    public static final String DETAIL_DESCRIPTION = "disclosure-detail__description";

    /** Applied by a caller to a container of {@link #detailRow} entries. */
    public static final String DETAIL_META = "disclosure-detail__meta";

    public static final String DETAIL_ROW = "disclosure-detail__row";
    public static final String DETAIL_HEADING = "disclosure-detail__heading";
    public static final String DETAIL_ICON = "disclosure-detail__icon";
    public static final String DETAIL_LABEL = "disclosure-detail__label";
    public static final String DETAIL_VALUE = "disclosure-detail__value";
    public static final String DETAIL_VALUE_MONO = "disclosure-detail__value--mono";

    /** The entry whose panel is currently open, or {@code null}. Cleared by {@link #reset()}. */
    private Div openItem;

    /**
     * The <em>key</em> of the open entry, which deliberately <strong>survives {@link #reset()}</strong>.
     *
     * <p>This is what keeps an entry open across a re-render. A screen that saves an edit re-queries and
     * rebuilds the whole list, so the expanded entry is a different {@code Div} afterwards — without a
     * key to recognise it by, the panel the user was reading would silently collapse and they would have
     * to find and reopen the row to see whether their edit took.
     *
     * <p>An entry whose key no longer appears — deleted, or filtered out — simply does not reopen, which
     * is the right outcome and needs no special case.
     */
    private Object openKey;

    public DisclosureList() {
        addClassName(LIST);
    }

    /**
     * Appends an entry.
     *
     * @param title   the entry's heading
     * @param meta    a secondary line under the title, or {@code null} for none
     * @param markers glyphs shown before the disclosure chevron. Their <em>presence</em> is the signal,
     *                so a marker is added only when it applies rather than being greyed out — which
     *                means the caller MUST give each one an accessible name, since for a
     *                screen-reader user there is nothing else to notice. Unlike the chevron, these are
     *                deliberately not hidden from assistive technology.
     * @param key     identifies the entry across re-renders, compared with {@code equals}. An entry
     *                whose key was open before the last {@link #reset()} is <strong>reopened</strong>,
     *                so a screen that rebuilds its list after a write keeps the panel the user was
     *                reading open — showing the updated data. Callers pass the domain identifier.
     * @return the panel body, for the caller to fill with detail content
     */
    public Div addItem(Object key, String title, String meta, Component... markers) {
        var item = new Div();
        item.addClassName(ITEM);

        var header = new Div();
        header.addClassName(ROW);
        // A Div, not a Button, because the panel it controls is a sibling rather than a child; these
        // attributes are what keep it operable by keyboard and announced as a control.
        header.getElement().setAttribute("role", "button");
        header.getElement().setAttribute("tabindex", "0");

        var text = new Div();
        text.addClassName(ROW_TEXT);
        var titleSpan = new Span(title);
        titleSpan.addClassName(ROW_NAME);
        text.add(titleSpan);
        if (meta != null && !meta.isBlank()) {
            var metaSpan = new Span(meta);
            metaSpan.addClassName(ROW_META);
            text.add(metaSpan);
        }
        header.add(text);

        for (var marker : markers) {
            if (marker != null) {
                marker.addClassName(ROW_MARKER);
                header.add(marker);
            }
        }

        // The chevron points right when collapsed and rotates to point down when open (CSS-driven).
        var chevron = VaadinIcon.ANGLE_RIGHT.create();
        chevron.addClassName(ROW_ICON);
        chevron.getElement().setAttribute("aria-hidden", "true");
        header.add(chevron);
        header.addClickListener(event -> toggle(item, key));

        var panel = new Div();
        panel.addClassName(ROW_PANEL);
        var body = new Div();
        body.addClassName(ROW_PANEL_INNER);
        panel.add(body);

        item.add(header, panel);
        add(item);
        // Reopen if this is the entry that was open before the list was rebuilt.
        if (key != null && key.equals(openKey)) {
            item.addClassName(ITEM_OPEN);
            openItem = item;
        }
        return body;
    }

    /** The key of the open entry, or {@code null}. For tests, and for a caller that wants to scroll. */
    public Object openKey() {
        return openKey;
    }

    /**
     * Empties the list, keeping <em>which</em> entry was open so that rebuilding reopens it.
     *
     * <p>The detached {@code Div} is forgotten — holding it would leak and would make the next toggle
     * spend its "close the previous one" step on an element that is no longer rendered — but the key is
     * not. Use {@link #collapse()} to forget the open entry as well.
     */
    public void reset() {
        removeAll();
        openItem = null;
    }

    /** Forgets the open entry, so a subsequent rebuild starts with everything collapsed. */
    public void collapse() {
        if (openItem != null) {
            openItem.removeClassName(ITEM_OPEN);
        }
        openItem = null;
        openKey = null;
    }

    private void toggle(Div item, Object key) {
        if (item.hasClassName(ITEM_OPEN)) {
            item.removeClassName(ITEM_OPEN);
            openItem = null;
            // Cleared too, or a deliberate collapse would be undone by the next rebuild.
            openKey = null;
            return;
        }
        if (openItem != null) {
            openItem.removeClassName(ITEM_OPEN);
        }
        item.addClassName(ITEM_OPEN);
        openItem = item;
        openKey = key;
    }

    /**
     * One detail entry for a panel: the icon and its label share the first line, the value sits on the
     * line below. {@code monospace} suits opaque identifiers.
     *
     * <p>Static and shared for the same reason as the rest of this class — two screens rendering
     * "labelled value with a leading glyph" differently would be an accident, not a decision.
     */
    public static Div detailRow(VaadinIcon icon, String label, String value, boolean monospace) {
        var row = new Div();
        row.addClassName(DETAIL_ROW);

        var heading = new Div();
        heading.addClassName(DETAIL_HEADING);
        var glyph = icon.create();
        glyph.addClassName(DETAIL_ICON);
        glyph.getElement().setAttribute("aria-hidden", "true");
        var labelSpan = new Span(label);
        labelSpan.addClassName(DETAIL_LABEL);
        heading.add(glyph, labelSpan);

        var valueSpan = new Span(value);
        valueSpan.addClassName(DETAIL_VALUE);
        if (monospace) {
            valueSpan.addClassName(DETAIL_VALUE_MONO);
        }

        row.add(heading, valueSpan);
        return row;
    }
}
