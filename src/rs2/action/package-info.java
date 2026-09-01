/**
 * Owns application-side menu-action routing and cohesive action-effect domains.
 * The package consumes revision-377 menu action identifiers from
 * {@code rs2.ui.menu}, writes action-specific protocol effects through narrow
 * subsystem dependencies, and keeps the central client coordinator out of the
 * action-handler contracts.
 */
package rs2.action;
