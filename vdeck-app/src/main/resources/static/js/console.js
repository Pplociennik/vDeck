/**
 * Follows the game server log of the current launch: every second it asks for the lines written since
 * the last answer and appends them to the console. The log is only ever inserted as text, never as markup.
 * No request starts while the page is hidden, and requests never overlap.
 */
const POLL_INTERVAL_MS = 1000;

/** Lines kept in the console; older ones are dropped so a long session does not slow the page down. */
const MAX_LINES = 5000;

/** Distance from the bottom, in pixels, within which the console still counts as following the log. */
const FOLLOW_THRESHOLD_PX = 24;

const output = document.querySelector('[data-console-output]');

let source = null;
let offset = null;
let pending = false;

/**
 * Retrieves the next part of the log and shows it. A failed read keeps what is shown and retries on the
 * next tick.
 *
 * @returns {Promise<void>} completion of one bounded poll
 */
async function poll() {
    if (output === null || document.hidden || pending) {
        return;
    }
    pending = true;
    try {
        const url = new URL(output.dataset.logUrl, window.location.href);
        if (offset !== null) {
            url.searchParams.set('offset', offset);
        }
        const response = await fetch(url, {
            credentials: 'same-origin',
            cache: 'no-store',
            redirect: 'error',
            signal: AbortSignal.timeout(5000)
        });
        if (!response.ok) {
            return;
        }
        show(await response.json());
    } catch {
        // Keep the last shown log; a lost connection says nothing about the server.
    } finally {
        pending = false;
    }
}

/**
 * Applies one answer: starts over when the log was replaced or the position reset, then appends the new
 * lines and keeps following the end if the administrator was already there.
 *
 * @param {{source: ?string, offset: number, text: string, reset: boolean}} chunk answer of the server
 */
function show(chunk) {
    const following = output.scrollHeight - output.scrollTop - output.clientHeight <= FOLLOW_THRESHOLD_PX;
    if (chunk.reset || chunk.source !== source) {
        output.textContent = '';
    }
    source = chunk.source;
    offset = chunk.offset;
    if (chunk.text.length > 0) {
        output.append(document.createTextNode(chunk.text));
        trim();
    }
    if (following) {
        output.scrollTop = output.scrollHeight;
    }
}

/**
 * Drops the oldest lines once the console holds more than MAX_LINES.
 */
function trim() {
    const lines = output.textContent.split('\n');
    if (lines.length > MAX_LINES + 1) {
        output.textContent = lines.slice(lines.length - MAX_LINES - 1).join('\n');
    }
}

poll();
setInterval(poll, POLL_INTERVAL_MS);
document.addEventListener('visibilitychange', poll);
