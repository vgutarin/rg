package vg.rg.frontend.vaadin.component.dialog;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;

/**
 * A dialog together with the buttons that drive it.
 *
 * <p>Exists because Vaadin puts footer components in a virtual slot that cannot be walked from the
 * dialog — {@code dialog.getFooter().getChildren()} throws — so a flow that needs its buttons driven or
 * asserted has to hand them back rather than being reachable through the component tree.
 *
 * <p>Shared by every screen with a confirmation, so the three that have one cannot drift into three
 * slightly different shapes.
 */
public record Prompt(Dialog dialog, Button confirm, Button cancel) { }
