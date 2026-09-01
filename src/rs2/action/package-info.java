/**
 * Owns application-side menu-action routing and cohesive action-effect domains.
 * The package consumes revision-377 menu action identifiers from
 * {@code rs2.ui.menu}, centralizes revision-377 action wire formats in
 * {@link rs2.action.ActionPacketEncoder}, applies action effects through narrow
 * subsystem dependencies, and keeps the central client coordinator and raw
 * outgoing buffer out of the action-handler contracts.
 */
package rs2.action;
