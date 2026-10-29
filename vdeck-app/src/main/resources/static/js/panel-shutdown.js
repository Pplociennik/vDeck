/*
 * Behaviour of the panel shutdown screen: sends the closing request in the background and then reveals the
 * closing message already on the page. The panel stops serving right after the request, so a new page could
 * no longer load its stylesheet and textures. Without this module the form posts normally and the server
 * answers with the same screen in its closing state.
 */

/**
 * Initialises the shutdown screen: wires its confirmation form.
 *
 * @param {HTMLElement} root element carrying data-panel-shutdown
 * @returns {void}
 * @throws {Error} when the screen markup lacks the form or one of its two states
 */
function initPanelShutdown(root) {
    const form = root.querySelector('[data-panel-shutdown-form]');
    const confirmation = root.querySelector('[data-panel-shutdown-confirmation]');
    const closing = root.querySelector('[data-panel-shutdown-closing]');

    if (form === null || confirmation === null || closing === null) {
        throw new Error('Panel shutdown markup is incomplete');
    }

    /**
     * Replaces the confirmation with the closing message and moves focus to it, because the focused button
     * disappears together with the confirmation.
     *
     * @returns {void}
     */
    const showClosing = () => {
        confirmation.hidden = true;
        closing.hidden = false;
        closing.focus();
    };

    /**
     * Sends the closing request with the form's CSRF token. A request redirected elsewhere - to the login
     * page after the session expired - is followed, and a failed one falls back to a normal form submission,
     * so the browser shows the server's own answer.
     *
     * @async
     * @param {SubmitEvent} event submit event of the confirmation form
     * @returns {Promise<void>}
     */
    const handleSubmit = async (event) => {
        event.preventDefault();

        let response;
        try {
            response = await fetch(form.action, {method: 'POST', body: new FormData(form)});
        } catch {
            form.submit();
            return;
        }

        if (response.redirected) {
            window.location.assign(response.url);
            return;
        }
        if (!response.ok) {
            form.submit();
            return;
        }

        showClosing();
    };

    form.addEventListener('submit', handleSubmit);
}

document.querySelectorAll('[data-panel-shutdown]').forEach(initPanelShutdown);
