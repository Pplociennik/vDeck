/**
 * Keeps the "Stan serwera" summary current: re-reads the page every five seconds and swaps only the value
 * lists, so the state, the uptime and the profile follow the server while the links - which hold the
 * keyboard focus - stay in place. No request starts while the page is hidden or another one is pending.
 */
let pending = false;

/**
 * Retrieves the summary and replaces every value list by the one with the same id. A failed read keeps
 * the last values.
 *
 * @returns {Promise<void>} completion of one bounded refresh
 */
async function refreshSummary() {
    const summary = document.querySelector('[data-server-state]');
    if (summary === null || document.hidden || pending) {
        return;
    }
    pending = true;
    try {
        const response = await fetch(summary.dataset.stateUrl, {
            credentials: 'same-origin',
            cache: 'no-store',
            redirect: 'error',
            signal: AbortSignal.timeout(5000)
        });
        if (!response.ok) {
            return;
        }
        const fresh = new DOMParser().parseFromString(await response.text(), 'text/html');
        for (const list of summary.querySelectorAll('[data-server-state-values]')) {
            const replacement = fresh.getElementById(list.id);
            if (replacement !== null) {
                list.replaceWith(replacement);
            }
        }
    } catch {
        // Keep the last values; losing connectivity cannot imply that the server stopped.
    } finally {
        pending = false;
    }
}

setInterval(refreshSummary, 5000);
document.addEventListener('visibilitychange', refreshSummary);
