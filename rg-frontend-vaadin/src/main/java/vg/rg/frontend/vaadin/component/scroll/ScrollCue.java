package vg.rg.frontend.vaadin.component.scroll;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * A vertically scrolling list that shows a fading chevron cue at whichever edge has more content out of
 * view: an up chevron while there is content scrolled above the viewport, a down chevron while there is
 * content below. Both cues disappear at their respective ends, so the presence of a chevron is a reliable
 * "there is more this way" signal.
 *
 * <p><strong>Extracted so the screens that use it cannot drift apart.</strong> The event-participants
 * dialog defined this shape first — two tabs, each a scrollable roster — and duplicated the DOM, the CSS
 * and the client-side toggling once inside itself already; a third scrolling list would copy it a third
 * time, and a fix to the cue logic would then have to be found and repeated everywhere. Following the same
 * reasoning as {@code DisclosureList}, the styles are named for the <em>pattern</em> ({@code scroll-cue-*})
 * rather than for any domain, so no screen owns them.
 *
 * <p>The cues are toggled entirely on the client from the viewport's own scroll position and size, so they
 * stay correct through scrolling, resizing and content changes without a server round trip. The
 * {@code --at-top} / {@code --at-bottom} classes CSS keys off are the only contract between this class and
 * the stylesheet.
 *
 * <p>This element <em>is</em> the cue wrapper (the positioning context for the chevrons); the actual scroll
 * viewport is an inner element. {@link #add}, {@link #removeAll} and the other content methods delegate to
 * that viewport, so a caller treats a {@code ScrollCue} as the list itself.
 */
public class ScrollCue extends Div {

    /**
     * The CSS vocabulary this component owns.
     *
     * <p>Constants rather than string literals because these names are a <strong>contract</strong> spanning
     * this class and the stylesheet (and any test that walks the DOM): a typo in either place fails
     * silently — a rule that never matches, a cue that never toggles. The two {@code --at-*} state classes
     * are applied by the client script below rather than from Java, and are declared here so the stylesheet
     * has one place to read their spelling from.
     */
    public static final String WRAPPER = "scroll-cue";

    /** The scroll viewport, an inner element so its {@code scrollTop} is independent of the wrapper. */
    public static final String VIEWPORT = "scroll-cue__viewport";

    /** A cue chevron. Overlaid at an edge and non-interactive. */
    public static final String CUE = "scroll-cue__cue";

    public static final String CUE_UP = "scroll-cue__cue--up";
    public static final String CUE_DOWN = "scroll-cue__cue--down";

    /** Set on the wrapper by the client while the viewport is scrolled fully to the top; hides the up cue. */
    public static final String AT_TOP = "scroll-cue--at-top";

    /** Set on the wrapper while the viewport is scrolled fully to the bottom; hides the down cue. */
    public static final String AT_BOTTOM = "scroll-cue--at-bottom";

    private final Div viewport = new Div();

    public ScrollCue() {
        addClassName(WRAPPER);
        viewport.addClassName(VIEWPORT);

        var up = new Div(VaadinIcon.CHEVRON_UP.create());
        up.addClassNames(CUE, CUE_UP);
        up.getElement().setAttribute("aria-hidden", "true");
        var down = new Div(VaadinIcon.CHEVRON_DOWN.create());
        down.addClassNames(CUE, CUE_DOWN);
        down.getElement().setAttribute("aria-hidden", "true");

        add(up, viewport, down);
        wireScrollCues();
    }

    /** Appends content to the scroll viewport. */
    public void addContent(Component... components) {
        viewport.add(components);
    }

    /** Empties the scroll viewport. */
    public void clearContent() {
        viewport.removeAll();
    }

    /**
     * The scroll viewport itself, for a caller that needs the element directly — e.g. to run its own
     * scroll-position script against it. Prefer {@link #addContent} / {@link #clearContent} for content.
     */
    public Div viewport() {
        return viewport;
    }

    /**
     * Shifts the viewport's scroll by {@code rows} times the height of its first child, in the given
     * direction (negative up, positive down). Rows are uniform height, so after a single-step reorder that
     * moved an item by one position, shifting by one row keeps that item — and the control just tapped — at
     * the same screen position. A no-op when the viewport is empty. Runs on the client.
     */
    public void scrollByRows(int rows) {
        viewport.getElement().executeJs("""
                const list = this;
                const first = list.firstElementChild;
                if (first) {
                    list.scrollTop += $0 * first.offsetHeight;
                }
                """, rows);
    }

    /**
     * Wires the client-side cue toggling: on scroll and on size changes (a ResizeObserver on the viewport),
     * and once immediately, sets {@code --at-top} / {@code --at-bottom} on the wrapper from the viewport's
     * scroll position, so CSS can hide the corresponding cue.
     */
    private void wireScrollCues() {
        viewport.getElement().executeJs("""
                const list = this;
                const wrapper = list.parentElement;
                const update = () => {
                    const atTop = list.scrollTop <= 1;
                    const atBottom = list.scrollTop + list.clientHeight >= list.scrollHeight - 1;
                    wrapper.classList.toggle($0, atTop);
                    wrapper.classList.toggle($1, atBottom);
                };
                list.addEventListener('scroll', update, { passive: true });
                if (window.ResizeObserver) {
                    new ResizeObserver(update).observe(list);
                }
                requestAnimationFrame(update);
                """, AT_TOP, AT_BOTTOM);
    }
}
