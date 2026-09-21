/*
 * Behaviour of the "Ustawienia gry" screen: the "Konfiguracja zaawansowana" checkbox shows or hides the
 * advanced section and remembers the choice on the server at once, without saving the form. The
 * server renders the section already shown or hidden, so this module only reacts to the toggle.
 */

/**
 * Stores whether the advanced section is shown. The request carries the CSRF token rendered into the
 * form; a failure leaves the section as toggled, and the next visit shows the last stored state.
 *
 * @param {HTMLFormElement} form settings form holding the CSRF token field
 * @param {string} url endpoint remembering the choice
 * @param {boolean} shown whether the section is shown
 * @returns {Promise<void>} settles when the request has finished
 */
async function storeAdvancedShown(form, url, shown) {
    const body = new URLSearchParams({ shown: String(shown) });
    const csrf = form.querySelector('input[name="_csrf"]');

    if (csrf !== null) {
        body.append(csrf.name, csrf.value);
    }

    try {
        await fetch(url, { method: 'POST', body, credentials: 'same-origin' });
    } catch {
        // The choice is a convenience; a lost request only means the previous state returns next time.
    }
}

/**
 * Wires the advanced-section toggle of one settings form.
 *
 * @param {HTMLFormElement} form settings form marked with data-game-settings
 * @returns {void}
 */
function initGameSettings(form) {
    const toggle = form.querySelector('[data-advanced-toggle]');
    const section = form.querySelector('[data-advanced-section]');

    if (toggle === null || section === null) {
        return;
    }

    toggle.addEventListener('change', () => {
        section.hidden = !toggle.checked;
        storeAdvancedShown(form, toggle.dataset.advancedUrl, toggle.checked);
    });
}

document.querySelectorAll('[data-game-settings]').forEach(initGameSettings);
