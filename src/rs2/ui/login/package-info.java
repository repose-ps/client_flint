/**
 * Contains login-screen state and title-screen animation support.
 *
 * <p>
 * Login form state remains separate from the title-flame animator, which owns
 * its simulation buffers and worker thread rather than using the main client as
 * a secondary {@link java.lang.Runnable}.
 * </p>
 */
package rs2.ui.login;
