// Recreates the screenshots of the vDeck user manual in ../img.
//
// The panel must run from a freshly extracted distribution package (initial password "admin",
// default profile) in a directory without valheim_server.exe, so Start shows its validation error.
// The panel refuses to start there unless its startup check is disabled, so start the package with
//   set JAVA_TOOL_OPTIONS=-Dvdeck.server-file-check=false
//   start-vDeck.bat
// The script changes the administrator password, edits the launch profile with sample values and
// toggles the advanced game settings, so never point it at a real installation.
//
// Usage:
//   npm install
//   set VDECK_NEW_PASSWORD=<password meeting the panel rules>
//   npm run zrzuty
//
// VDECK_URL defaults to http://127.0.0.1:8085. VDECK_BROWSER_CHANNEL selects the installed
// Chromium browser used by Playwright ("msedge" by default, "chrome" also works).
//
// Readability on an A4 page drives the shot types: one whole-screen overview per screen, and
// element shots of single form sections, so the text in the PDF stays legible at the page width.

import { chromium } from 'playwright';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const baseUrl = process.env.VDECK_URL ?? 'http://127.0.0.1:8085';
const channel = process.env.VDECK_BROWSER_CHANNEL ?? 'msedge';
const newPassword = process.env.VDECK_NEW_PASSWORD;
if ( !newPassword ) {
    throw new Error( 'Set VDECK_NEW_PASSWORD to the password that replaces the initial "admin".' );
}
const imageDirectory = path.resolve( path.dirname( fileURLToPath( import.meta.url ) ), '..', 'img' );

const browser = await chromium.launch( { channel } );
try {
    const desktop = await browser.newContext( { viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1.5, locale: 'pl-PL' } );
    const page = await desktop.newPage();

    await page.goto( `${baseUrl}/login` );
    await captureElement( page, 'form', 'logowanie' );
    await logIn( page, 'admin' );

    // The password card is taller than the overview viewport, so it is shot whole in a taller one.
    await page.setViewportSize( { width: 1280, height: 1100 } );
    await page.waitForURL( '**/initial-password-change' );
    await captureElement( page, '.view-card', 'pierwsze-logowanie-zmiana-hasla' );
    await page.fill( '#new-password', newPassword );
    await page.fill( '#password-confirmation', newPassword );
    await submit( page );
    // A changed password ends the session, so the panel asks for the new one.
    await page.goto( `${baseUrl}/login` );
    await logIn( page, newPassword );
    await page.setViewportSize( { width: 1280, height: 800 } );

    await page.goto( `${baseUrl}/` );
    await mark( page, [
        { selector: '.island', label: '1' },
        { selector: '.island-status', label: '2' },
        { selector: '[data-side-menu-toggle]', label: '3' }
    ] );
    await capture( page, 'ekran-glowny' );
    await clearMarks( page );
    // Numbers follow the island buttons from left to right; the manual explains them in the same order.
    await mark( page, [
        ...[ 1, 2, 3, 4, 5, 6, 7, 8 ].map( number => ( { selector: '.island .action-button', nth: number - 1, label: String( number ), badgeOnly: true } ) ),
        { selector: '.island-status', label: '9', badgeOnly: true }
    ] );
    await captureAround( page, '.island-stack', 'wyspa-akcji', 24 );
    await clearMarks( page );

    await page.click( '[data-side-menu-toggle]' );
    await page.waitForSelector( '[data-side-menu-panel][data-state="open"]' );
    await page.waitForTimeout( 400 );
    await mark( page, [
        { selector: '.side-menu-entry[href$="/server-settings"]', label: '1' },
        { selector: '.side-menu-entry[href$="/game-settings"]', label: '2' },
        { selector: '.side-menu-entry[href$="/console"]', label: '3' }
    ] );
    await captureAround( page, '.side-menu-panel', 'menu-boczne', 16 );
    await clearMarks( page );

    await page.goto( `${baseUrl}/server-settings` );
    await capture( page, 'ekran-serwer' );
    // The navigation crop shows the two corner buttons alone, without the form text behind them.
    const hiddenContent = await page.addStyleTag( { content: '.page-content { visibility: hidden !important; }' } );
    await mark( page, [
        { selector: '[data-side-menu-toggle]', label: '1' },
        { selector: '.home-button', label: '2' }
    ] );
    await page.screenshot( { path: imagePath( 'przyciski-nawigacji', 'jpg' ), type: 'jpeg', quality: 85,
        clip: { x: 0, y: 640, width: 200, height: 160 } } );
    await clearMarks( page );
    await hiddenContent.evaluate( style => style.remove() );
    await page.fill( '#server-name', 'Serwer Wikingów' );
    await page.fill( '#world-name', 'Midgard' );
    await page.fill( '#server-password', 'Odyn2026' );
    await submit( page );
    await captureElement( page, '.settings-message', 'serwer-zapisano' );

    // Form sections are shot in a narrow, tall viewport: the form falls back to one column, so its text
    // stays large on the page, and hiding the floating island and menu buttons keeps them off the form.
    await page.setViewportSize( { width: 760, height: 1800 } );
    await hideFloatingControls( page );
    await captureSections( page, [ 'serwer-sekcja-serwer', 'serwer-sekcja-dostep', 'serwer-sekcja-swiat', 'serwer-sekcja-kopie' ] );
    await page.locator( '.form-submit' ).scrollIntoViewIfNeeded();
    await mark( page, [ { selector: '.form-submit', label: '' } ] );
    await captureAround( page, '.form-submit', 'serwer-przycisk-zapisz', 24 );
    await clearMarks( page );

    await page.goto( `${baseUrl}/game-settings` );
    await page.setViewportSize( { width: 1280, height: 800 } );
    await capture( page, 'ekran-ustawienia-gry' );
    await page.setViewportSize( { width: 760, height: 1800 } );
    await hideFloatingControls( page );
    await captureSections( page, [ 'gra-sekcja-zestaw' ] );
    await page.check( '[data-advanced-toggle]' );
    await page.waitForSelector( '#game-settings-advanced:not([hidden])' );
    await page.locator( '.settings-advanced-toggle' ).scrollIntoViewIfNeeded();
    await mark( page, [ { selector: '.settings-advanced-toggle .form-checkbox', label: '' } ] );
    await captureAround( page, '.settings-advanced-toggle', 'gra-przelacznik-zaawansowane', 12 );
    await clearMarks( page );
    await captureElement( page, '#game-settings-advanced', 'gra-sekcja-zaawansowane' );
    await page.uncheck( '[data-advanced-toggle]' );
    await page.setViewportSize( { width: 1280, height: 800 } );

    // The package has no valheim_server.exe, so no launch has a log and the console stays empty.
    await page.goto( `${baseUrl}/console` );
    await capture( page, 'ekran-konsola' );

    await page.setViewportSize( { width: 1280, height: 1100 } );
    await page.goto( `${baseUrl}/change-password` );
    await captureElement( page, '.view-card', 'zmiana-hasla' );
    await page.setViewportSize( { width: 1280, height: 800 } );

    await page.goto( `${baseUrl}/` );
    await page.click( 'form[action$="/server/start"] button' );
    await page.waitForSelector( '.form-alert' );
    await page.waitForLoadState( 'networkidle' );
    await mark( page, [
        { selector: 'form[action$="/server/start"] button', label: '1' },
        { selector: '.form-alert', label: '2' }
    ] );
    await page.screenshot( { path: imagePath( 'start-blad-walidacji', 'jpg' ), type: 'jpeg', quality: 85,
        clip: { x: 240, y: 0, width: 800, height: 500 } } );
    await desktop.close();

    const phone = await browser.newContext( { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: 'pl-PL' } );
    const phonePage = await phone.newPage();
    await phonePage.goto( `${baseUrl}/login` );
    await logIn( phonePage, newPassword );
    await phonePage.goto( `${baseUrl}/server-settings` );
    await capture( phonePage, 'telefon-ekran-serwer' );
    await phone.close();
} finally {
    await browser.close();
}

async function logIn( page, password ) {
    await page.fill( 'input[name="username"]', 'admin' );
    await page.fill( 'input[name="password"]', password );
    await submit( page );
}

// The island's Start and Stop are submit buttons too, so only the page's own form button is pressed.
async function submit( page ) {
    const button = page.locator( '.form-submit, form[action$="/login"] button[type="submit"]' ).first();
    await Promise.all( [ page.waitForNavigation(), button.click() ] );
}

// Whole-screen shots are JPEG: the panel's photographic background would make PNG files several MB each.
async function capture( page, name ) {
    await page.waitForLoadState( 'networkidle' );
    await page.screenshot( { path: imagePath( name, 'jpg' ), type: 'jpeg', quality: 80 } );
}

async function captureElement( page, selector, name ) {
    await page.waitForLoadState( 'networkidle' );
    await page.locator( selector ).screenshot( { path: imagePath( name, 'jpg' ), type: 'jpeg', quality: 85 } );
}

// Shoots the visible form sections of the current screen in document order, one file per name.
async function captureSections( page, names ) {
    const sections = page.locator( 'fieldset.form-section:visible' );
    for ( const [ index, name ] of names.entries() ) {
        await sections.nth( index ).screenshot( { path: imagePath( name, 'jpg' ), type: 'jpeg', quality: 85 } );
    }
}

// Shoots the element's bounding box widened by padding CSS pixels, so marks around its edge stay visible.
async function captureAround( page, selector, name, padding ) {
    await page.waitForLoadState( 'networkidle' );
    const box = await page.locator( selector ).boundingBox();
    const viewport = page.viewportSize();
    const x = Math.max( 0, box.x - padding );
    const y = Math.max( 0, box.y - padding );
    const clip = {
        x, y,
        width: Math.min( viewport.width - x, box.width + 2 * padding ),
        height: Math.min( viewport.height - y, box.height + 2 * padding )
    };
    await page.screenshot( { path: imagePath( name, 'jpg' ), type: 'jpeg', quality: 85, clip } );
}

// Overlays manual annotations in the panel's accent colour: a frame around each target and, when the
// label is not empty, a numbered badge at its top-left corner. badgeOnly skips the frame for dense rows.
async function mark( page, marks ) {
    await page.evaluate( items => {
        for ( const item of items ) {
            const target = document.querySelectorAll( item.selector )[ item.nth ?? 0 ];
            if ( !target ) {
                throw new Error( `No element to mark: ${item.selector}` );
            }
            const rect = target.getBoundingClientRect();
            if ( !item.badgeOnly ) {
                const frame = document.createElement( 'div' );
                frame.dataset.manualMark = '';
                Object.assign( frame.style, {
                    position: 'fixed', left: `${rect.left - 5}px`, top: `${rect.top - 5}px`,
                    width: `${rect.width + 10}px`, height: `${rect.height + 10}px`,
                    border: '3px solid #e8b45a', borderRadius: '12px', boxShadow: '0 0 12px rgba(232, 180, 90, 0.7)',
                    zIndex: 2147483646, pointerEvents: 'none', boxSizing: 'border-box'
                } );
                document.body.append( frame );
            }
            if ( item.label ) {
                const badge = document.createElement( 'div' );
                badge.dataset.manualMark = '';
                badge.textContent = item.label;
                Object.assign( badge.style, {
                    position: 'fixed', left: `${rect.left - 14}px`, top: `${rect.top - 14}px`,
                    width: '30px', height: '30px', borderRadius: '50%', background: '#e8b45a', color: '#100e0c',
                    font: 'bold 17px/30px "Segoe UI", sans-serif', textAlign: 'center',
                    boxShadow: '0 0 0 2px #100e0c', zIndex: 2147483647, pointerEvents: 'none'
                } );
                document.body.append( badge );
            }
        }
    }, marks );
}

async function hideFloatingControls( page ) {
    await page.addStyleTag( { content: '.island-header, .side-menu, .home-button { visibility: hidden !important; }' } );
}

async function clearMarks( page ) {
    await page.evaluate( () => document.querySelectorAll( '[data-manual-mark]' ).forEach( element => element.remove() ) );
}

function imagePath( name, extension ) {
    return path.join( imageDirectory, `${name}.${extension}` );
}
