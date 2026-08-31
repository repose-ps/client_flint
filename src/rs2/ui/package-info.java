/**
 * Contains revision-377 interface lifecycle, interaction state, widget runtime
 * processing, and widget rendering.
 *
 * <p>
 * {@link rs2.ui.InterfaceController} owns interface selection, hover/tooltip,
 * scrollbar, report-abuse, and content-action state. {@link rs2.ui.WidgetRuntime}
 * evaluates cache-defined CS1 scripts, {@link rs2.ui.WidgetContentController}
 * owns application-defined dynamic widget content, and {@link rs2.ui.WidgetRenderer}
 * recursively renders widget trees and scrollbars. Character appearance editing
 * and the narrow widget-script view of client state also live in this package.
 * </p>
 */
package rs2.ui;
