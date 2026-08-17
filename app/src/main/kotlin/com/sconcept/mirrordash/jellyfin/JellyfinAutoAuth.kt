package com.sconcept.mirrordash.jellyfin

/**
 * Best-effort auto-login for jellyfin-web's login page - `#txtManualName`/`#txtManualPassword`
 * have been that form's field ids for years, so this is a reasonably durable target. Polls for
 * up to ~8s (the SPA route may not have rendered the login form yet when this fires) rather than
 * running once, then fills both fields and submits the enclosing form via `requestSubmit()`
 * instead of hunting for a specific button class - the field ids are far more stable across
 * jellyfin-web releases than its button markup. A no-op (never throws, never loops forever) if
 * the fields never appear, e.g. because the session is already authenticated.
 */
fun jellyfinAutoAuthScript(username: String, password: String): String {
    val escapedUser = escapeForJs(username)
    val escapedPassword = escapeForJs(password)
    return """
        (function() {
            console.log('[mirrordash-auth] script started');
            var attempts = 0;
            var maxAttempts = 40; // 40 * 200ms = 8s
            var timer = setInterval(function() {
                attempts++;
                var userField = document.getElementById('txtManualName');
                var passField = document.getElementById('txtManualPassword');
                if (userField && passField) {
                    console.log('[mirrordash-auth] fields found on attempt ' + attempts);
                    clearInterval(timer);
                    try {
                        // Chrome/WebView's own autofill can silently revert a script-set value on
                        // a field it recognizes as autofillable (typically the username field)
                        // right before submission - turning off autocomplete on both fields stops
                        // it from treating them as its own to manage.
                        userField.setAttribute('autocomplete', 'off');
                        passField.setAttribute('autocomplete', 'off');
                        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                        nativeSetter.call(userField, '$escapedUser');
                        nativeSetter.call(passField, '$escapedPassword');
                        userField.dispatchEvent(new Event('input', { bubbles: true }));
                        passField.dispatchEvent(new Event('input', { bubbles: true }));
                        userField.dispatchEvent(new Event('change', { bubbles: true }));
                        passField.dispatchEvent(new Event('change', { bubbles: true }));
                        console.log('[mirrordash-auth] fields filled, userField.value.length=' + userField.value.length + ' passField.value.length=' + passField.value.length);
                        // A short delay before submitting - firing requestSubmit() in the same
                        // tick as the synthetic input events can race the page's own JS framework
                        // picking up the new values, submitting a stale (often empty) password.
                        setTimeout(function() {
                            // Re-assert both values immediately before submitting - defends
                            // against the browser's autofill subsystem (or the page's own
                            // framework) reverting a field between the initial fill and now.
                            if (userField.value !== '$escapedUser') nativeSetter.call(userField, '$escapedUser');
                            if (passField.value !== '$escapedPassword') nativeSetter.call(passField, '$escapedPassword');
                            userField.dispatchEvent(new Event('input', { bubbles: true }));
                            passField.dispatchEvent(new Event('input', { bubbles: true }));
                            console.log('[mirrordash-auth] pre-submit check, userField.value.length=' + userField.value.length + ' passField.value.length=' + passField.value.length);
                            var form = passField.closest('form');
                            if (form && form.requestSubmit) {
                                console.log('[mirrordash-auth] submitting via requestSubmit');
                                form.requestSubmit();
                            } else if (form) {
                                console.log('[mirrordash-auth] submitting via dispatchEvent');
                                form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                            } else {
                                console.log('[mirrordash-auth] no enclosing form found');
                            }
                        }, 150);
                    } catch (e) {
                        console.log('[mirrordash-auth] error: ' + e.message);
                    }
                } else if (attempts >= maxAttempts) {
                    console.log('[mirrordash-auth] timed out, userField=' + !!userField + ' passField=' + !!passField);
                    clearInterval(timer);
                }
            }, 200);
        })();
    """.trimIndent()
}

private fun escapeForJs(value: String): String =
    value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
