/**
 * Refreshes only the action island; it preserves settings forms and prevents overlapping requests.
 * Every new fragment carries fresh CSRF tokens. No request starts while the page is hidden.
 */
let pending = false;

/**
 * Retrieves and installs a fresh authenticated island fragment. A failed read preserves the last status.
 *
 * @returns {Promise<void>} completion of one bounded refresh
 */
async function refreshIsland() {
    const island = document.querySelector('[data-server-island]');
    if (island === null || document.hidden || pending) {
        return;
    }
    pending = true;
    try {
        const response = await fetch(island.dataset.statusUrl, {
            credentials: 'same-origin',
            cache: 'no-store',
            redirect: 'error',
            signal: AbortSignal.timeout(5000)
        });
        if (!response.ok) {
            return;
        }
        const documentFragment = new DOMParser().parseFromString(await response.text(), 'text/html');
        const replacement = documentFragment.querySelector('[data-server-island]');
        if (replacement !== null && !island.contains(document.activeElement)) {
            island.replaceWith(replacement);
        }
    } catch {
        // Preserve the last known state; losing connectivity cannot imply that the server stopped.
    } finally {
        pending = false;
    }
}

setInterval(refreshIsland, 2000);
document.addEventListener('visibilitychange', refreshIsland);
