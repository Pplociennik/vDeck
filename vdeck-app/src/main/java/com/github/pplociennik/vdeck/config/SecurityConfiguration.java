package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.feature.authentication.domain.PanelRole;
import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import com.github.pplociennik.vdeck.security.PasswordChangeAuthenticationSuccessHandler;
import com.github.pplociennik.vdeck.security.PasswordChangeRequiredFilter;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Wires form login, persistent panel-user authentication and mandatory first-login password replacement.
 * The initial-password screen is reserved for accounts that still carry
 * {@link PanelUserService#PASSWORD_CHANGE_REQUIRED_AUTHORITY}, because it replaces a password without
 * asking for the current one; every other panel address, the ordinary password-change screen included,
 * requires the administrator role.
 */
@Configuration( proxyBeanMethods = false )
class SecurityConfiguration {

    /**
     * Creates the adaptive delegating encoder used for both initial and replacement passwords.
     *
     * @return password encoder using Spring Security's current storage recommendations
     */
    @Bean
    @Nonnull
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Creates the servlet security chain protecting every panel request.
     *
     * @param http
     *         Spring Security servlet configuration builder
     * @param passwordChangeRequiredFilter
     *         filter preventing use of initial credentials outside their replacement flow
     * @param authenticationSuccessHandler
     *         handler routing initial-credential logins to password replacement
     * @return built servlet security filter chain
     * @throws Exception
     *         if Spring Security cannot build the filter chain
     */
    @Bean
    @Nonnull
    SecurityFilterChain securityFilterChain(
            @Nonnull HttpSecurity http,
            @Nonnull PasswordChangeRequiredFilter passwordChangeRequiredFilter,
            @Nonnull PasswordChangeAuthenticationSuccessHandler authenticationSuccessHandler ) throws Exception {
        http.authorizeHttpRequests( authorization -> authorization
                        .requestMatchers( "/initial-password-change" )
                        .hasAuthority( PanelUserService.PASSWORD_CHANGE_REQUIRED_AUTHORITY )
                        .anyRequest().hasRole( PanelRole.ADMIN.name() ) )
                .formLogin( form -> form.successHandler( authenticationSuccessHandler ) )
                .logout( Customizer.withDefaults() )
                .sessionManagement( session -> session
                        .sessionFixation( fixation -> fixation.changeSessionId() )
                        .maximumSessions( 1 )
                        .expiredUrl( "/login?expired" )
                        .maxSessionsPreventsLogin( false ) )
                .addFilterAfter( passwordChangeRequiredFilter, UsernamePasswordAuthenticationFilter.class );
        return http.build();
    }

    /**
     * Creates the filter enforcing the first-login restriction on every authenticated request.
     *
     * @return first-login restriction filter
     */
    @Bean
    @Nonnull
    PasswordChangeRequiredFilter passwordChangeRequiredFilter() {
        return new PasswordChangeRequiredFilter();
    }

    /**
     * Creates the login-success handler preserving saved requests after the initial password is replaced.
     *
     * @return authentication success handler
     */
    @Bean
    @Nonnull
    PasswordChangeAuthenticationSuccessHandler authenticationSuccessHandler() {
        return new PasswordChangeAuthenticationSuccessHandler();
    }

    /**
     * Publishes session destruction events required by concurrent-session control.
     *
     * @return servlet session event publisher
     */
    @Bean
    @Nonnull
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
