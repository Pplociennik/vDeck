package com.github.pplociennik.vdeck.feature.home;

import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the panel home page, the address a signed-in administrator lands on when no other request was
 * saved. The page currently carries only the panel background and the action island; content is added
 * together with the features it will present.
 */
@Controller
class HomeController {

    private static final String HOME_VIEW = "home/index";

    /**
     * Displays the panel home page.
     *
     * @return home page template name
     */
    @GetMapping( "/" )
    @Nonnull
    String showHome() {
        return HOME_VIEW;
    }
}