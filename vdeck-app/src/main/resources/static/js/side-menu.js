/*
 * Behaviour of the side menu rendered from the common/side-menu fragment: opening and closing the
 * panel from its round toggle, and expanding its branches as an accordion. The animations belong to
 * the stylesheet; this module only switches the state attributes the styles react to.
 */

/**
 * Tells whether the given branch entry currently shows its children.
 *
 * @param {HTMLButtonElement} branch branch entry carrying aria-expanded
 * @returns {boolean} true when the branch is expanded
 */
function isBranchExpanded(branch) {
    return branch.getAttribute('aria-expanded') === 'true';
}

/**
 * Finds the children wrapper controlled by a branch entry.
 *
 * @param {HTMLButtonElement} branch branch entry whose aria-controls names the wrapper
 * @returns {HTMLElement} wrapper of the branch's nested list
 * @throws {Error} when the fragment markup does not contain the controlled wrapper
 */
function findChildren(branch) {
    const children = document.getElementById(branch.getAttribute('aria-controls'));

    if (children === null) {
        throw new Error('Side menu branch controls a missing element');
    }

    return children;
}

/**
 * Expands or collapses one branch. A collapsed branch is inert, so its entries leave the tab order
 * and the accessibility tree while they are hidden.
 *
 * @param {HTMLButtonElement} branch branch entry to update
 * @param {boolean} expanded requested state
 * @returns {void}
 */
function setBranchExpanded(branch, expanded) {
    branch.setAttribute('aria-expanded', String(expanded));
    findChildren(branch).inert = !expanded;
}

/**
 * Initialises one side menu: wires its toggle, its branches and the document-level interactions that
 * close it.
 *
 * @param {HTMLElement} root element carrying data-side-menu
 * @returns {void}
 * @throws {Error} when the fragment markup lacks the toggle or the panel
 */
function initSideMenu(root) {
    const toggle = root.querySelector('[data-side-menu-toggle]');
    const panel = root.querySelector('[data-side-menu-panel]');

    if (toggle === null || panel === null) {
        throw new Error('Side menu markup is incomplete');
    }

    const branches = Array.from(panel.querySelectorAll('[data-side-menu-branch]'));

    /**
     * Tells whether the panel is currently open.
     *
     * @returns {boolean} true when the panel is open
     */
    const isOpen = () => panel.dataset.state === 'open';

    /**
     * Collapses a branch together with every branch nested below it, so reopening it shows only its
     * direct children.
     *
     * @param {HTMLButtonElement} branch branch entry to collapse
     * @returns {void}
     */
    const collapseBranch = (branch) => {
        const children = findChildren(branch);

        for (const nested of branches) {
            if (children.contains(nested)) {
                setBranchExpanded(nested, false);
            }
        }
        setBranchExpanded(branch, false);
    };

    /**
     * Opens the panel and moves focus to its first entry.
     *
     * @returns {void}
     */
    const openMenu = () => {
        panel.inert = false;
        panel.dataset.state = 'open';
        toggle.setAttribute('aria-expanded', 'true');

        const firstEntry = panel.querySelector('button');

        if (firstEntry !== null) {
            firstEntry.focus();
        }
    };

    /**
     * Closes the panel and collapses every branch, so the menu reopens in its initial state. Focus
     * returns to the toggle only when it was inside the panel; a click elsewhere keeps its own target.
     *
     * @returns {void}
     */
    const closeMenu = () => {
        const focusWasInside = panel.contains(document.activeElement);

        branches.filter(isBranchExpanded).forEach(collapseBranch);
        panel.dataset.state = 'closed';
        panel.inert = true;
        toggle.setAttribute('aria-expanded', 'false');

        if (focusWasInside) {
            toggle.focus();
        }
    };

    /**
     * Expands a collapsed branch and collapses its expanded siblings, or collapses an expanded one.
     *
     * @param {HTMLButtonElement} branch branch entry that was activated
     * @returns {void}
     */
    const toggleBranch = (branch) => {
        if (isBranchExpanded(branch)) {
            collapseBranch(branch);
            return;
        }

        const list = branch.closest('ul');

        for (const sibling of branches) {
            if (sibling !== branch && sibling.closest('ul') === list && isBranchExpanded(sibling)) {
                collapseBranch(sibling);
            }
        }
        setBranchExpanded(branch, true);
    };

    /**
     * Opens a closed menu and closes an open one.
     *
     * @returns {void}
     */
    const handleToggleClick = () => {
        if (isOpen()) {
            closeMenu();
            return;
        }

        openMenu();
    };

    /**
     * Closes the open menu when Escape is pressed.
     *
     * @param {KeyboardEvent} event keyboard event raised anywhere in the document
     * @returns {void}
     */
    const handleKeydown = (event) => {
        if (event.key !== 'Escape' || !isOpen()) {
            return;
        }

        closeMenu();
    };

    /**
     * Closes the open menu when the user presses anywhere outside the panel and its toggle.
     *
     * @param {PointerEvent} event pointer event raised anywhere in the document
     * @returns {void}
     */
    const handlePointerDown = (event) => {
        if (!isOpen() || root.contains(event.target)) {
            return;
        }

        closeMenu();
    };

    /**
     * Expands or collapses the branch entry that was clicked.
     *
     * @param {MouseEvent} event click event whose current target is a branch entry
     * @returns {void}
     */
    const handleBranchClick = (event) => {
        toggleBranch(event.currentTarget);
    };

    toggle.addEventListener('click', handleToggleClick);
    for (const branch of branches) {
        branch.addEventListener('click', handleBranchClick);
    }
    document.addEventListener('keydown', handleKeydown);
    document.addEventListener('pointerdown', handlePointerDown);
}

document.querySelectorAll('[data-side-menu]').forEach(initSideMenu);