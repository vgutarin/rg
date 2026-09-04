package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The accordion's own contract, tested once rather than inferred from its two callers.
 *
 * <p>{@link WorkspaceLocationsViewTest} and {@link WorkspaceParticipantsViewTest} assert that each
 * screen <em>uses</em> this component correctly — a location row carries meta, a participant row carries
 * a phone marker. What they cannot show is the parts of the contract neither screen happens to exercise,
 * and those are exactly the parts that fail silently: the header's accessibility attributes (remove them
 * and every other test still passes while the accordion stops being reachable by keyboard), the
 * open-state bookkeeping across a {@link DisclosureList#reset()}, and {@link
 * DisclosureList#detailRow}'s shape.
 */
class DisclosureListTest {

    @Test
    void addItem_buildsAHeaderAndACollapsedPanel_andReturnsThePanelBody() {
        var list = new DisclosureList();

        var body = list.addItem("Depot", "Depot", null);

        var item = onlyItem(list);
        assertThat(item.hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
        assertThat(names(item)).containsExactly("Depot");
        // The returned body is the panel's inner container, so whatever a caller adds lands inside the
        // collapsible region rather than beside it.
        assertThat(body.hasClassName(DisclosureList.ROW_PANEL_INNER)).isTrue();
        assertThat(descendants(item)).contains(body);
        assertThat(withClass(item, DisclosureList.ROW_PANEL)).hasSize(1);
    }

    /**
     * The header is a {@code Div} rather than a {@code Button}, because the region it controls is a
     * sibling rather than a child. These two attributes are the whole of what keeps it operable and
     * announced as a control — and nothing else in the suite would notice their removal.
     */
    @Test
    void header_isOperableAsAControl() {
        var list = new DisclosureList();
        list.addItem("Depot", "Depot", null);

        var header = header(onlyItem(list));

        assertThat(header.getElement().getAttribute("role")).isEqualTo("button");
        assertThat(header.getElement().getAttribute("tabindex")).isEqualTo("0");
    }

    @Test
    void meta_isRenderedWhenGivenAndOmittedWhenNot() {
        var list = new DisclosureList();
        list.addItem("With", "With", "120 m");
        list.addItem("Without", "Without", null);
        list.addItem("Blank", "Blank", "   ");

        var items = items(list);
        assertThat(texts(items.get(0), DisclosureList.ROW_META)).containsExactly("120 m");
        // A blank meta must produce no element at all, not an empty one: an empty span still occupies
        // its line and pushes the row's height around.
        assertThat(texts(items.get(1), DisclosureList.ROW_META)).isEmpty();
        assertThat(texts(items.get(2), DisclosureList.ROW_META)).isEmpty();
    }

    @Test
    void markers_areRenderedInOrderBeforeTheChevron_andNullsAreSkipped() {
        var list = new DisclosureList();

        list.addItem("Ivan", "Ivan", null, VaadinIcon.PHONE.create(), null, VaadinIcon.STAR.create());

        var header = header(onlyItem(list));
        var classes = header.getChildren()
                .map(child -> child.getElement().getClassList())
                .toList();
        assertThat(withClass(header, DisclosureList.ROW_MARKER)).hasSize(2);
        // The chevron is last, so the markers sit between the text and it.
        assertThat(classes.getLast()).contains(DisclosureList.ROW_ICON);
    }

    /**
     * A marker's <em>presence</em> is its message, so unlike the chevron it must reach assistive
     * technology. Hiding it would silently make the signal sighted-only — which is why the two are
     * asserted together here rather than separately.
     */
    @Test
    void theChevronIsHiddenFromAssistiveTechnology_butAMarkerIsNot() {
        var list = new DisclosureList();
        list.addItem("Ivan", "Ivan", null, VaadinIcon.PHONE.create());

        var header = header(onlyItem(list));

        assertThat(withClass(header, DisclosureList.ROW_ICON))
                .allSatisfy(icon ->
                        assertThat(icon.getElement().getAttribute("aria-hidden")).isEqualTo("true"));
        assertThat(withClass(header, DisclosureList.ROW_MARKER))
                .allSatisfy(marker ->
                        assertThat(marker.getElement().getAttribute("aria-hidden")).isNull());
    }

    @Test
    void clickingAHeader_opensAndClosesItsOwnPanel() {
        var list = new DisclosureList();
        list.addItem("Depot", "Depot", null);
        var item = onlyItem(list);

        click(header(item));
        assertThat(item.hasClassName(DisclosureList.ITEM_OPEN)).isTrue();

        click(header(item));
        assertThat(item.hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
    }

    @Test
    void openingOneEntry_closesWhicheverWasOpen() {
        var list = new DisclosureList();
        list.addItem("First", "First", null);
        list.addItem("Second", "Second", null);
        list.addItem("Third", "Third", null);
        var items = items(list);

        click(header(items.get(0)));
        click(header(items.get(2)));

        assertThat(items.get(0).hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
        assertThat(items.get(1).hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
        assertThat(items.get(2).hasClassName(DisclosureList.ITEM_OPEN)).isTrue();
    }

    /**
     * {@code reset()} must forget the open <em>element</em> while keeping its key.
     *
     * <p>Holding the detached {@code Div} would leak, and the next toggle would spend its "close the
     * previous one" step on a row that is no longer rendered. Keeping the key is what lets the rebuild
     * reopen the same entry — see below.
     */
    @Test
    void reset_detachesTheOpenEntryButRemembersWhichItWas() {
        var list = new DisclosureList();
        list.addItem("before", "Before", null);
        var stale = onlyItem(list);
        click(header(stale));

        list.reset();

        assertThat(stale.getParent()).isEmpty();
        assertThat(list.openKey()).isEqualTo("before");
    }

    /**
     * The behaviour this whole key mechanism exists for.
     *
     * <p>A screen that saves an edit re-queries and rebuilds its list, so the expanded entry is a
     * different {@code Div} afterwards. Without the key the panel the user was reading would collapse,
     * and they would have to find and reopen the row to see whether their edit took — which is exactly
     * the complaint that prompted this.
     */
    @Test
    void rebuilding_reopensTheEntryThatWasOpen_withItsNewContent() {
        var list = new DisclosureList();
        list.addItem("ivan", "Ivan", null);
        click(header(onlyItem(list)));

        // The rebuild a save triggers: same entry, new title.
        list.reset();
        list.addItem("ivan", "Ivan Petrenko", null);

        var reopened = onlyItem(list);
        assertThat(reopened.hasClassName(DisclosureList.ITEM_OPEN)).isTrue();
        assertThat(names(reopened)).containsExactly("Ivan Petrenko");
    }

    /** Its position may change — a renamed entry re-sorts — so the key, not the index, is what matches. */
    @Test
    void rebuilding_reopensByKeyRatherThanByPosition() {
        var list = new DisclosureList();
        list.addItem("ivan", "Ivan", null);
        list.addItem("olena", "Olena", null);
        click(header(items(list).get(1)));

        // Rebuilt with the previously-second entry now first.
        list.reset();
        list.addItem("olena", "Olena", null);
        list.addItem("ivan", "Ivan", null);

        assertThat(items(list).get(0).hasClassName(DisclosureList.ITEM_OPEN)).isTrue();
        assertThat(items(list).get(1).hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
    }

    /** A deleted or filtered-out entry simply does not come back, which needs no special case. */
    @Test
    void rebuilding_withoutThatEntry_opensNothing() {
        var list = new DisclosureList();
        list.addItem("ivan", "Ivan", null);
        click(header(onlyItem(list)));

        list.reset();
        list.addItem("olena", "Olena", null);

        assertThat(onlyItem(list).hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
    }

    /**
     * Closing an entry has to be durable. If the key survived a deliberate collapse, the next rebuild
     * would reopen what the user just closed.
     */
    @Test
    void closingAnEntry_isNotUndoneByARebuild() {
        var list = new DisclosureList();
        list.addItem("ivan", "Ivan", null);
        var item = onlyItem(list);
        click(header(item));
        click(header(item));
        assertThat(list.openKey()).isNull();

        list.reset();
        list.addItem("ivan", "Ivan", null);

        assertThat(onlyItem(list).hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
    }

    @Test
    void collapse_forgetsTheOpenEntryEntirely() {
        var list = new DisclosureList();
        list.addItem("ivan", "Ivan", null);
        var item = onlyItem(list);
        click(header(item));

        list.collapse();

        assertThat(item.hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
        assertThat(list.openKey()).isNull();
        list.reset();
        list.addItem("ivan", "Ivan", null);
        assertThat(onlyItem(list).hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
    }

    @Test
    void detailRow_putsTheIconAndLabelOnOneLineAndTheValueBelow() {
        var row = DisclosureList.detailRow(
                VaadinIcon.MAP_MARKER, "Coordinates", "50.0, 30.0", false);

        assertThat(row.hasClassName(DisclosureList.DETAIL_ROW)).isTrue();
        var heading = withClass(row, DisclosureList.DETAIL_HEADING);
        assertThat(heading).hasSize(1);
        assertThat(texts(row, DisclosureList.DETAIL_LABEL)).containsExactly("Coordinates");
        assertThat(texts(row, DisclosureList.DETAIL_VALUE)).containsExactly("50.0, 30.0");
        // The icon belongs to the heading line, not to the row.
        assertThat(withClass((Div) heading.getFirst(), DisclosureList.DETAIL_ICON)).hasSize(1);
        assertThat(texts(row, DisclosureList.DETAIL_VALUE_MONO)).isEmpty();
    }

    @Test
    void detailRow_monospaceVariant_addsTheModifierAlongsideTheBaseClass() {
        var row = DisclosureList.detailRow(VaadinIcon.INFO_CIRCLE, "Place id", "ChIJ_abc", true);

        // A modifier, so both classes have to be present -- the base one carries the layout.
        assertThat(texts(row, DisclosureList.DETAIL_VALUE)).containsExactly("ChIJ_abc");
        assertThat(texts(row, DisclosureList.DETAIL_VALUE_MONO)).containsExactly("ChIJ_abc");
    }

    /**
     * Every class name this component declares must exist in the stylesheet.
     *
     * <p>The failure this guards is silent in both directions: a constant misspelled here matches no
     * rule and the element simply renders unstyled, and a rule renamed in the stylesheet leaves the
     * constant pointing at nothing. Neither breaks a build or any other test — the screen just quietly
     * loses its styling, which is precisely the class of bug that reaches production.
     *
     * <p>The match is on a whole selector token deliberately. A substring check looks like it works and
     * does not: {@code .disclosure-row__actions} contains {@code .disclosure-row__action}, so exactly
     * the likeliest typo — a dropped trailing character — would have passed.
     */
    @Test
    void everyDeclaredClassNameExistsInTheStylesheet() throws IOException {
        var stylesheet = Files.readString(repositoryRoot().resolve(
                "rg-frontend-vaadin/src/main/resources/META-INF/resources/styles.css"));

        var declared = Arrays.stream(DisclosureList.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers())
                        && field.getType() == String.class)
                .map(field -> {
                    try {
                        return (String) field.get(null);
                    } catch (IllegalAccessException exception) {
                        throw new IllegalStateException("Cannot read " + field.getName(), exception);
                    }
                })
                .toList();

        assertThat(declared).isNotEmpty();
        assertThat(declared).allSatisfy(className ->
                assertThat(stylesheet)
                        .as("stylesheet rule for .%s", className)
                        // A whole selector token, not a substring. Plain `contains` would accept
                        // ".disclosure-row__action" because ".disclosure-row__actions" contains it, so
                        // every truncated misspelling would pass -- which it did, until this was fixed.
                        .containsPattern("\\." + java.util.regex.Pattern.quote(className)
                                + "(?![A-Za-z0-9_-])"));
    }

    // --- traversal ----------------------------------------------------------------------------------

    private static List<Div> items(DisclosureList list) {
        return list.getChildren()
                .filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName(DisclosureList.ITEM))
                .toList();
    }

    private static Div onlyItem(DisclosureList list) {
        var found = items(list);
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private static Div header(Div item) {
        return item.getChildren()
                .filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName(DisclosureList.ROW))
                .findFirst().orElseThrow();
    }

    private static List<String> names(Component root) {
        return texts(root, DisclosureList.ROW_NAME);
    }

    private static List<String> texts(Component root, String className) {
        return descendants(root).stream()
                .filter(Span.class::isInstance).map(Span.class::cast)
                .filter(span -> span.hasClassName(className))
                .map(Span::getText)
                .toList();
    }

    private static List<Component> withClass(Component root, String className) {
        return descendants(root).stream()
                // Component implements HasStyle in Vaadin 25, so no instanceof is needed -- and an
                // unconditional pattern is a compile error on Java 21.
                .filter(component -> component.hasClassName(className))
                .toList();
    }

    private static void click(Component element) {
        ComponentUtil.fireEvent(element, new ClickEvent<>(element));
    }

    private static List<Component> descendants(Component component) {
        return component.getChildren()
                .flatMap(child -> Stream.concat(Stream.of(child), descendants(child).stream()))
                .toList();
    }

    private static Path repositoryRoot() {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return candidate;
    }
}
