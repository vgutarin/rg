package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.exception.workspace.DuplicateParticipantPhoneException;
import vg.rg.exception.workspace.ParticipantLimitReachedException;
import vg.rg.frontend.vaadin.component.disclosure.DisclosureList;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.auth.NoAccessView;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.model.workspace.WorkspaceParticipantModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceParticipantService;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors {@link WorkspaceLocationsViewTest}: the same two tabs, the same collapsed rows with a
 * disclosure chevron, the same single-open accordion, the same management actions inside the panel.
 * Where a test here has a counterpart there, they assert the same class names — deliberately, since the
 * two screens share {@link DisclosureList} and a divergence would mean one of them had been restyled
 * alone.
 *
 * <p>What is specific to this screen is that <strong>a phone number cannot reach the roster</strong>.
 * Three separate things have to hold, and each has its own test: a row shows a glyph and no digits, the
 * edit form does not pre-fill the number it is editing, and plaintext appears only after a distinct
 * action that makes its own authorized call.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceParticipantsViewTest {

    private static final UniqueId WORKSPACE = new UniqueId(6001L);
    private static final UniqueId PARTICIPANT = new UniqueId(6002L);
    private static final String PHONE = "+380501112233";

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock WorkspaceParticipantService participantService;
    @Mock WorkspaceSelectionService selectionService;
    @Mock BeforeEnterEvent event;

    /**
     * A dialog auto-adds itself to the current UI when opened, so these flows need one. Held in a field
     * on purpose: Vaadin keeps the current UI behind a weak reference, so one with no other referent can
     * be collected mid-test and {@code dialog.open()} then fails at random.
     */
    private UI ui;

    /**
     * Local permissions the scoped check should refuse, consulted by the stub in {@link #entered}.
     *
     * <p>A set rather than per-test {@code when(...)} calls, because a broad matcher stubbed later wins
     * in Mockito: a specific denial written before {@code entered()} would be silently replaced by the
     * blanket "everything is permitted" stub inside it, and the test would pass while asserting nothing.
     */
    private final java.util.Set<String> deniedLocalPermissions = new java.util.HashSet<>();

    @BeforeEach
    void resetIdentifiers() {
        IDS.clear();
    }

    @BeforeEach
    void attachUi() {
        ui = new UI();
        UI.setCurrent(ui);
    }

    @AfterEach
    void detachUi() {
        UI.setCurrent(null);
        ui = null;
    }

    // --- the screen shape, shared with locations ----------------------------------------------------

    @Test
    void beforeEnter_rendersTwoTabsAndATitle() {
        var view = entered();

        var tabs = descendants(view).stream()
                .filter(TabSheet.class::isInstance).map(TabSheet.class::cast)
                .findFirst().orElseThrow();

        assertThat(tabContents(tabs)).hasSize(2);
        assertThat(descendants(view).stream().anyMatch(H1.class::isInstance)).isTrue();
    }

    @Test
    void beforeEnter_readsAreScopedToTheActiveWorkspace() {
        var view = entered(withPhone("Ivan the plumber"));

        verify(participantService).browse(eq(WORKSPACE), any(), any(Pageable.class));
        assertThat(view.renderedLabels()).contains("Ivan the plumber");
    }

    @Test
    void filter_passesTheLabelToTheSameServiceCall() {
        var view = entered();
        when(participantService.browse(eq(WORKSPACE), eq("iva"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withPhone("Found"))));

        searchField(view).setValue("iva");

        // One method for both paths, so the filtered and unfiltered lists cannot disagree about order.
        verify(participantService).browse(eq(WORKSPACE), eq("iva"), any(Pageable.class));
        assertThat(view.renderedLabels()).containsExactly("Found");
    }

    @Test
    void participantRow_rendersCollapsed_withADisclosureChevron() {
        var view = entered(withPhone("Ivan"));

        var item = onlyItem(view);
        assertThat(item.hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
        assertThat(chevronIcons(item)).hasSize(1);
    }

    @Test
    void openingAnotherRow_collapsesThePreviouslyOpenOne() {
        var view = entered(withPhone("First"), withPhone("Second"));
        var items = items(view);
        assertThat(items).hasSize(2);

        click(header(items.get(0)));
        click(header(items.get(1)));

        assertThat(items.get(0).hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
        assertThat(items.get(1).hasClassName(DisclosureList.ITEM_OPEN)).isTrue();
    }

    @Test
    void emptyRoster_andNoSearchResults_haveDistinctEmptyStates() {
        var view = entered();
        assertThat(paragraphs(view)).contains("participants.empty");

        when(participantService.browse(eq(WORKSPACE), eq("nobody"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        searchField(view).setValue("nobody");

        assertThat(paragraphs(view)).contains("participants.search.no-results");
    }

    // --- paging -------------------------------------------------------------------------------------

    @Test
    void whenEverythingFits_thereIsNoCountLineAndNoLoadMore() {
        // A count and a button that cannot do anything are noise, and the count would imply there is
        // more to see.
        var view = entered(withoutPhone("Only one"));

        assertThat(buttonTexts(view)).doesNotContain("participants.load-more");
        assertThat(paragraphs(view)).noneMatch(text -> text.startsWith("participants.showing"));
    }

    /**
     * An alphabetical list that silently stops part-way looks like a list of everyone, so a participant
     * whose name sorts late would appear not to exist. The count is what prevents that reading.
     */
    @Test
    void whenThereIsMore_theCountAndLoadMoreAppear() {
        var view = enteredWith(roster(WorkspaceParticipantsView.BROWSE_PAGE_SIZE + 5));

        assertThat(view.renderedLabels()).hasSize(WorkspaceParticipantsView.BROWSE_PAGE_SIZE);
        assertThat(buttonTexts(view)).contains("participants.load-more");
        verify(localization).getTranslation(eq("participants.showing"), any(),
                eq(WorkspaceParticipantsView.BROWSE_PAGE_SIZE),
                eq((long) WorkspaceParticipantsView.BROWSE_PAGE_SIZE + 5));
    }

    /**
     * Sized at exactly one more than a page, so a single click completes the roster <em>whatever the
     * page size is</em>. Writing it as "page + 5" made it pass only while the page size was large
     * relative to that, which is not a property of the code under test.
     */
    @Test
    void loadMore_appendsTheNextPageRatherThanReplacingTheFirst() {
        var view = enteredWith(roster(WorkspaceParticipantsView.BROWSE_PAGE_SIZE + 1));
        var firstPage = view.renderedLabels();
        assertThat(firstPage).hasSize(WorkspaceParticipantsView.BROWSE_PAGE_SIZE);

        click(loadMoreButton(view));

        // Appended, not replaced: the first page's entries are still there, still in front.
        assertThat(view.renderedLabels())
                .hasSize(WorkspaceParticipantsView.BROWSE_PAGE_SIZE + 1)
                .startsWith(firstPage.toArray(String[]::new));
        // Everything is now shown, so the affordance retires itself.
        assertThat(buttonTexts(view)).doesNotContain("participants.load-more");
    }

    @Test
    void changingTheFilter_startsOverFromTheFirstPage() {
        var view = enteredWith(roster(WorkspaceParticipantsView.BROWSE_PAGE_SIZE + 1));
        click(loadMoreButton(view));
        assertThat(view.renderedLabels()).hasSize(WorkspaceParticipantsView.BROWSE_PAGE_SIZE + 1);

        searchField(view).setValue("participant");

        // A new filter is a new result set, so the pages loaded against the old one mean nothing.
        assertThat(view.renderedLabels()).hasSize(WorkspaceParticipantsView.BROWSE_PAGE_SIZE);
    }

    // --- the phone is presence-only in a row --------------------------------------------------------

    @Test
    void aRowWithANumber_showsAGlyphAndNoDigits() {
        var view = entered(withPhone("Ivan"));

        var markers = markers(onlyItem(view));
        assertThat(markers).hasSize(1);
        // The glyph's presence is the whole message, so it must carry an accessible name -- there is
        // nothing else for a screen reader to notice.
        assertThat(markers.getFirst().getElement().getAttribute("aria-label"))
                .isEqualTo("participant.phone.present");
        assertThat(allText(view)).noneMatch(text -> text.contains(PHONE) || text.contains("2233"));
    }

    @Test
    void aRowWithoutANumber_showsNoGlyphAtAll() {
        // Presence is the signal, so absence must be silence rather than a greyed-out icon or a
        // "no phone" caption -- both of which state something about a person for no benefit.
        var view = entered(withoutPhone("Label only"));

        assertThat(markers(onlyItem(view))).isEmpty();
        assertThat(allText(view)).noneMatch(text -> text.contains("participant.phone"));
    }

    @Test
    void noRowExposesAMaskedSuffix() {
        var view = entered(withPhone("Ivan"), withoutPhone("Olena"));

        // The masked-suffix wording is gone from the screen entirely, not merely unused: a suffix is
        // still information about a person shown to whoever can see the display.
        assertThat(allText(view)).noneMatch(text -> text.contains("masked") || text.contains("••••"));
    }

    // --- disclosure -------------------------------------------------------------------------

    @Test
    void reveal_livesInsideTheExpandedPanel_notInTheRow() {
        var view = entered(withPhone("Ivan"));
        var item = onlyItem(view);

        assertThat(buttonTexts(header(item))).doesNotContain("participants.reveal");
        assertThat(buttonTexts(item)).contains("participants.reveal");
    }

    @Test
    void reveal_makesItsOwnCallAndShowsThePlaintextInADialog() {
        var view = entered(withPhone("Ivan"));
        when(participantService.revealContact(PARTICIPANT))
                .thenReturn(ParticipantDescriptor.of("Ivan", PHONE));

        var prompt = view.revealContact(withPhone("Ivan"));

        verify(participantService).revealContact(PARTICIPANT);
        assertThat(texts(prompt.dialog(), Span.class)).contains(PHONE);
    }

    /** Rendering the roster must not fetch anybody's number; only the reveal action does. */
    @Test
    void rendering_neverRevealsAnything() {
        entered(withPhone("Ivan"), withPhone("Olena"));

        verify(participantService, never()).revealContact(any());
    }

    @Test
    void withoutTheRevealPermission_theActionIsNotOffered() {
        deniedLocalPermissions.add(LocalPermissions.WorkspaceParticipant.REVEAL_CONTACT);
        var view = entered(withPhone("Ivan"));

        assertThat(buttonTexts(onlyItem(view))).doesNotContain("participants.reveal");
    }

    @Test
    void aParticipantWithNoNumber_isNotOfferedAReveal() {
        var view = entered(withoutPhone("Label only"));

        assertThat(buttonTexts(onlyItem(view))).doesNotContain("participants.reveal");
    }

    @Test
    void reveal_whenDenied_showsAMessageAndNoNumber() {
        var view = entered(withPhone("Ivan"));
        when(participantService.revealContact(PARTICIPANT))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        when(localization.i18n(any(Exception.class))).thenReturn("exception.unknown");

        var prompt = view.revealContact(withPhone("Ivan"));

        assertThat(texts(prompt.dialog(), Span.class)).noneMatch(text -> text.contains(PHONE));
    }

    // --- editing ------------------------------------------------------------------------------------

    /**
     * The edit form pre-fills the number for a caller who may see it anyway.
     *
     * <p>Saving replaces the whole descriptor, so without the current value in the field an edit of the
     * label alone would silently wipe the number.
     */
    @Test
    void editDialog_withRevealPermission_preFillsTheCurrentNumber() {
        var view = entered(withPhone("Ivan"));
        when(participantService.revealContact(PARTICIPANT))
                .thenReturn(ParticipantDescriptor.of("Ivan", PHONE));

        var prompt = view.openEditDialog(withPhone("Ivan"));

        assertThat(values(prompt.dialog())).contains("Ivan", PHONE);
    }

    /**
     * Without that permission the field stays empty and nothing is fetched — so editing cannot become a
     * way around the reveal permission.
     */
    @Test
    void editDialog_withoutRevealPermission_leavesTheNumberEmptyAndFetchesNothing() {
        deniedLocalPermissions.add(LocalPermissions.WorkspaceParticipant.REVEAL_CONTACT);
        var view = entered(withPhone("Ivan"));

        var prompt = view.openEditDialog(withPhone("Ivan"));

        assertThat(values(prompt.dialog())).contains("Ivan").doesNotContain(PHONE);
        verify(participantService, never()).revealContact(any());
    }

    /** A participant with no number needs no lookup either. */
    @Test
    void editDialog_forAParticipantWithNoNumber_fetchesNothing() {
        var view = entered(withoutPhone("Label only"));

        view.openEditDialog(withoutPhone("Label only"));

        verify(participantService, never()).revealContact(any());
    }

    /** A failed lookup must not block editing the label. */
    @Test
    void editDialog_whenTheNumberCannotBeFetched_stillOpensForEditing() {
        var view = entered(withPhone("Ivan"));
        when(participantService.revealContact(PARTICIPANT))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));

        var prompt = view.openEditDialog(withPhone("Ivan"));

        assertThat(prompt.dialog().isOpened()).isTrue();
        assertThat(values(prompt.dialog())).contains("Ivan").doesNotContain(PHONE);
    }

    @Test
    void editDialog_savesTheTypedContactData() {
        var view = entered(withPhone("Ivan"));
        var prompt = view.openEditDialog(withPhone("Ivan"));
        setValues(prompt.dialog(), "Ivan Petrenko", "+380501114455");

        click(prompt.confirm());

        var descriptor = ArgumentCaptor.forClass(ParticipantDescriptor.class);
        verify(participantService).update(eq(PARTICIPANT), anyInt(), descriptor.capture());
        assertThat(descriptor.getValue().label()).isEqualTo("Ivan Petrenko");
        assertThat(descriptor.getValue().phone()).isEqualTo("+380501114455");
        assertThat(prompt.dialog().isOpened()).isFalse();
    }

    /**
     * The end-to-end version of what {@code DisclosureListTest} covers on the component: saving an edit
     * rebuilds the list, and the row the user was reading has to still be expanded afterwards — showing
     * the new value — rather than silently collapsing and making them find and reopen it.
     */
    @Test
    void afterAnEdit_theEditedRowStaysOpenAndShowsTheNewLabel() {
        var view = entered(withPhone("Ivan"));
        var row = onlyItem(view);
        click(header(row));
        assertThat(row.hasClassName(DisclosureList.ITEM_OPEN)).isTrue();

        // The saved edit, and the re-query it triggers. The identifier is deliberately the *same* one:
        // a rename does not change identity, and reopening is keyed on identity rather than on the
        // label — which is what lets a renamed row re-sort and still stay open.
        when(participantService.browse(eq(WORKSPACE), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(WorkspaceParticipantModel.builder()
                        .uniqueId(idFor("Ivan"))
                        .label("Ivan Petrenko")
                        .phoneRecorded(true)
                        .version(4)
                        .build())));
        var prompt = view.openEditDialog(withPhone("Ivan"));
        setValues(prompt.dialog(), "Ivan Petrenko", "");
        click(prompt.confirm());

        var rebuilt = onlyItem(view);
        assertThat(view.renderedLabels()).containsExactly("Ivan Petrenko");
        assertThat(rebuilt.hasClassName(DisclosureList.ITEM_OPEN)).isTrue();
    }

    /** A removed row cannot stay open, and nothing else should take its place. */
    @Test
    void afterARemoval_nothingIsLeftOpen() {
        var view = entered(withPhone("Ivan"), withoutPhone("Olena"));
        click(header(items(view).get(0)));

        when(participantService.browse(eq(WORKSPACE), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withoutPhone("Olena"))));
        var prompt = view.confirmRemove(withPhone("Ivan"));
        click(prompt.confirm());

        assertThat(items(view)).allSatisfy(item ->
                assertThat(item.hasClassName(DisclosureList.ITEM_OPEN)).isFalse());
    }

    @Test
    void editDialog_staysOpenOnAStaleSave() {
        var view = entered(withPhone("Ivan"));
        when(participantService.update(any(), anyInt(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, PARTICIPANT));
        var prompt = view.openEditDialog(withPhone("Ivan"));
        setValues(prompt.dialog(), "Ivan", "");

        click(prompt.confirm());

        // The typed text has to survive, so the user retries rather than retypes.
        assertThat(prompt.dialog().isOpened()).isTrue();
        assertThat(values(prompt.dialog())).contains("Ivan");
    }

    @Test
    void withoutTheUpdateOrDeletePermission_thePanelOffersNeither() {
        deniedLocalPermissions.add(LocalPermissions.WorkspaceParticipant.UPDATE);
        deniedLocalPermissions.add(LocalPermissions.WorkspaceParticipant.DELETE);
        var view = entered(withPhone("Ivan"));

        assertThat(buttonTexts(onlyItem(view)))
                .doesNotContain("participants.edit", "participants.remove");
    }

    // --- removal ------------------------------------------------------------------------------------

    @Test
    void removal_isConfirmedFirst() {
        var view = entered(withPhone("Ivan"));

        view.confirmRemove(withPhone("Ivan"));

        verify(participantService, never()).delete(any());
    }

    @Test
    void removal_doubleTap_deletesOnce() {
        var view = entered(withPhone("Ivan"));
        var prompt = view.confirmRemove(withPhone("Ivan"));

        click(prompt.confirm());
        click(prompt.confirm());

        verify(participantService, times(1)).delete(PARTICIPANT);
    }

    @Test
    void removal_cancel_deletesNothing() {
        var view = entered(withPhone("Ivan"));
        var prompt = view.confirmRemove(withPhone("Ivan"));

        click(prompt.cancel());

        verify(participantService, never()).delete(any());
        assertThat(prompt.dialog().isOpened()).isFalse();
    }

    // --- the add tab --------------------------------------------------------------------------------

    @Test
    void register_passesTheActiveWorkspaceAndTheTypedFields() {
        var view = entered();

        setValues(view, "Ivan the plumber", "+380 50 111 2233");
        click(registerButton(view));

        var descriptor = ArgumentCaptor.forClass(ParticipantDescriptor.class);
        verify(participantService).register(eq(WORKSPACE), descriptor.capture());
        assertThat(descriptor.getValue().label()).isEqualTo("Ivan the plumber");
        // Passed through as typed: normalization is the business layer's job, not the form's.
        assertThat(descriptor.getValue().phone()).isEqualTo("+380 50 111 2233");
    }

    @Test
    void withoutTheCreatePermission_theAddTabOffersNoForm() {
        deniedLocalPermissions.add(LocalPermissions.WorkspaceParticipant.CREATE);
        var view = entered();

        assertThat(paragraphs(view)).contains("participants.add.no-permission");
        assertThat(buttonTexts(view)).doesNotContain("participants.register");
        // The browse filter is still a text field, so assert on the form's own fields by label.
        assertThat(descendants(view).stream()
                .filter(TextField.class::isInstance).map(TextField.class::cast)
                .map(TextField::getLabel))
                .doesNotContain("participant.field.label", "participant.field.phone");
    }

    @Test
    void register_atTheLimit_reportsItAndKeepsTheTypedText() {
        var view = entered();
        when(participantService.register(any(), any())).thenThrow(new ParticipantLimitReachedException(2));

        setValues(view, "Third", null);
        click(registerButton(view));

        assertThat(values(view)).contains("Third");
    }

    @Test
    void register_duplicateNumber_reportsItAndKeepsTheTypedText() {
        var view = entered();
        when(participantService.register(any(), any()))
                .thenThrow(new DuplicateParticipantPhoneException());

        setValues(view, "Ivan again", PHONE);
        click(registerButton(view));

        assertThat(values(view)).contains("Ivan again");
    }

    @Test
    void register_invalidField_reportsTheBusinessLayersMessageKey() {
        var view = entered();
        when(participantService.register(any(), any()))
                .thenThrow(new IllegalArgumentException("workspace.participant.error.phone-invalid"));

        setValues(view, "Ivan", "nonsense");
        click(registerButton(view));

        verify(localization).i18n("workspace.participant.error.phone-invalid");
    }

    // --- the gate -----------------------------------------------------------------------------------

    @Test
    void withoutTheWorkspacePermission_theViewReroutesAndRendersNothing() {
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(false);

        var view = new WorkspaceParticipantsView(
                localization, authorityChecker, participantService, selectionService);
        view.beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
        verify(participantService, never()).browse(any(), any(), any());
        assertThat(view.renderedLabels()).isEmpty();
    }

    /** A maximum-length label must render rather than be truncated or throw. */
    @Test
    void aMaximumLengthLabel_renders() {
        var maximal = "x".repeat(128);
        var view = entered(withoutPhone(maximal));

        assertThat(view.renderedLabels()).contains(maximal);
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private WorkspaceParticipantsView entered(WorkspaceParticipantModel... roster) {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getTranslation(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(true);
        // The owner holds no participant capability of their own; the scoped check admits them because
        // owning the workspace grants complete authority over its contents -- except whatever a test
        // has put in deniedLocalPermissions.
        lenient().when(authorityChecker.hasAuthority(any(UniqueId.class), anyString()))
                .thenAnswer(invocation ->
                        !deniedLocalPermissions.contains(invocation.getArgument(1, String.class)));
        when(selectionService.activeWorkspace())
                .thenReturn(WorkspaceModel.builder().uniqueId(WORKSPACE).defaultWorkspace(true).build());
        // One stub for both paths, answering from the requested page size so the Load-more flow can be
        // driven: the service slices, so the stub must too, or "shown vs total" would be meaningless.
        when(participantService.browse(eq(WORKSPACE), any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable requested = invocation.getArgument(2, Pageable.class);
                    var all = List.of(roster);
                    var to = Math.min((int) requested.getOffset() + requested.getPageSize(), all.size());
                    return new PageImpl<>(all.subList(0, to), requested, all.size());
                });

        var view = new WorkspaceParticipantsView(
                localization, authorityChecker, participantService, selectionService);
        view.beforeEnter(event);
        return view;
    }

    /**
     * {@code PARTICIPANT} for the first label a test uses, a distinct identifier for every other.
     *
     * <p>Identifiers have to differ now that they key the accordion's open entry: two fixtures sharing
     * one would be indistinguishable to it, and a test asserting that <em>this</em> row stayed open
     * would pass because a different row opened. The first label keeps {@code PARTICIPANT}, so the
     * single-participant tests and their {@code verify(...revealContact(PARTICIPANT))} still work.
     */
    private static final java.util.Map<String, UniqueId> IDS = new java.util.LinkedHashMap<>();

    private static UniqueId idFor(String label) {
        return IDS.computeIfAbsent(label, key -> IDS.isEmpty()
                ? PARTICIPANT
                : new UniqueId(PARTICIPANT.getLongValue() + IDS.size()));
    }

    private static WorkspaceParticipantModel withPhone(String label) {
        return WorkspaceParticipantModel.builder()
                .uniqueId(idFor(label)).label(label).phoneRecorded(true).version(3)
                .build();
    }

    /** {@code n} participants whose labels sort in the order generated, so paging is checkable. */
    private static WorkspaceParticipantModel[] roster(int n) {
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(index -> WorkspaceParticipantModel.builder()
                        .uniqueId(new UniqueId(7000L + index))
                        .label(String.format("participant %03d", index))
                        .phoneRecorded(false)
                        .version(1)
                        .build())
                .toArray(WorkspaceParticipantModel[]::new);
    }

    private WorkspaceParticipantsView enteredWith(WorkspaceParticipantModel[] roster) {
        return entered(roster);
    }

    private Button loadMoreButton(Component view) {
        return descendants(view).stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "participants.load-more".equals(button.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("no load-more button"));
    }

    private static WorkspaceParticipantModel withoutPhone(String label) {
        return WorkspaceParticipantModel.builder()
                .uniqueId(idFor(label)).label(label).phoneRecorded(false).version(3)
                .build();
    }

    // --- traversal, as in WorkspaceLocationsViewTest ------------------------------------------------

    private List<Div> items(Component view) {
        return descendants(view).stream()
                .filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName(DisclosureList.ITEM))
                .toList();
    }

    private Div onlyItem(Component view) {
        var found = items(view);
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private Div header(Div item) {
        return item.getChildren()
                .filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName(DisclosureList.ROW))
                .findFirst().orElseThrow();
    }

    private List<String> chevronIcons(Div item) {
        return descendants(item).stream()
                .filter(component -> component.hasClassName(DisclosureList.ROW_ICON))
                .map(component -> component.getElement().getAttribute("icon"))
                .toList();
    }

    private List<Component> markers(Div item) {
        return descendants(item).stream()
                .filter(component -> component.hasClassName(DisclosureList.ROW_MARKER))
                .toList();
    }

    private TextField searchField(Component view) {
        return descendants(view).stream()
                .filter(TextField.class::isInstance).map(TextField.class::cast)
                .findFirst().orElseThrow();
    }

    private Button registerButton(Component view) {
        return descendants(view).stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "participants.register".equals(button.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("no register button"));
    }

    /** Fills the label and phone fields of whichever form or dialog is passed. */
    private void setValues(Component root, String label, String phone) {
        var fields = descendants(root).stream()
                .filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> "participant.field.label".equals(field.getLabel())
                        || "participant.field.phone".equals(field.getLabel()))
                .toList();
        fields.get(0).setValue(label == null ? "" : label);
        fields.get(1).setValue(phone == null ? "" : phone);
    }

    private List<String> buttonTexts(Component root) {
        return descendants(root).stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .map(Button::getText).toList();
    }

    private List<String> paragraphs(Component root) {
        return descendants(root).stream()
                .filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .map(Paragraph::getText).toList();
    }

    /** Every rendered string on the screen, for the "no digits anywhere" assertions. */
    private List<String> allText(Component root) {
        return descendants(root).stream()
                .map(component -> component instanceof Button button
                        ? button.getText() : component.getElement().getText())
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private static <T extends Component> List<String> texts(Component root, Class<T> type) {
        return descendants(root).stream()
                .filter(type::isInstance)
                .map(component -> component instanceof Button button ? button.getText()
                        : component.getElement().getText())
                .toList();
    }

    private static List<String> values(Component root) {
        return descendants(root).stream()
                .filter(HasValue.class::isInstance)
                .map(component -> String.valueOf(((HasValue<?, ?>) component).getValue()))
                .toList();
    }

    private static void click(Component element) {
        ComponentUtil.fireEvent(element, new ClickEvent<>(element));
    }

    private static List<Component> descendants(Component component) {
        var children = new ArrayList<>(component.getChildren().toList());
        // TabSheet content is not attached without a UI, so getChildren() omits it; pull each tab's
        // content component explicitly so the traversal reaches the tab contents.
        if (component instanceof TabSheet tabSheet) {
            children.addAll(tabContents(tabSheet));
        }
        return children.stream()
                .flatMap(child -> Stream.concat(Stream.of(child), descendants(child).stream()))
                .toList();
    }

    private static List<Component> tabContents(TabSheet tabSheet) {
        var contents = new ArrayList<Component>();
        for (int index = 0; index < 100; index++) {
            Tab tab;
            try {
                tab = tabSheet.getTabAt(index);
            } catch (RuntimeException outOfRange) {
                break;
            }
            if (tab == null) {
                break;
            }
            var content = tabSheet.getComponent(tab);
            if (content != null) {
                contents.add(content);
            }
        }
        return contents;
    }
}
