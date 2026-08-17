package com.sconcept.mirrordash.homeassistant

/**
 * Best-effort auto-login for Home Assistant's frontend. Unlike Jellyfin's plain-DOM login form,
 * HA's login lives inside nested Shadow DOM (`ha-authorize` > `ha-auth-flow` > `mwc-textfield`),
 * so a plain `document.querySelector` can't reach the actual `<input>` elements - this injects a
 * small recursive `deepQuery` helper that walks into every open shadow root it finds. Explicitly
 * flagged as the more fragile of the two auto-auth scripts (HA's frontend moves faster than
 * Jellyfin's login form) - if the selectors here ever stop matching, the normal login screen
 * still appears and just needs a manual sign-in that session, exactly like `autoAuth` being off.
 */
fun homeAssistantAutoAuthScript(username: String, password: String): String {
    val escapedUser = escapeForJs(username)
    val escapedPassword = escapeForJs(password)
    return """
        (function() {
            console.log('[mirrordash-auth] script started');
            function deepQuery(root, selector) {
                var found = root.querySelector(selector);
                if (found) return found;
                var all = root.querySelectorAll('*');
                for (var i = 0; i < all.length; i++) {
                    if (all[i].shadowRoot) {
                        var inner = deepQuery(all[i].shadowRoot, selector);
                        if (inner) return inner;
                    }
                }
                return null;
            }
            var attempts = 0;
            var maxAttempts = 40; // 40 * 200ms = 8s
            var timer = setInterval(function() {
                attempts++;
                var userField = deepQuery(document, 'input[name="username"]');
                var passField = deepQuery(document, 'input[name="password"]');
                if (userField && passField) {
                    console.log('[mirrordash-auth] fields found on attempt ' + attempts);
                    clearInterval(timer);
                    // Chrome/WebView's own autofill can silently revert a script-set value on a
                    // field it recognizes as autofillable right before submission - turning off
                    // autocomplete stops it from treating these fields as its own to manage.
                    userField.setAttribute('autocomplete', 'off');
                    passField.setAttribute('autocomplete', 'off');
                    var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    nativeSetter.call(userField, '$escapedUser');
                    nativeSetter.call(passField, '$escapedPassword');
                    userField.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                    userField.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                    passField.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                    passField.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                    console.log('[mirrordash-auth] fields filled, userField.value.length=' + userField.value.length + ' passField.value.length=' + passField.value.length);
                    // A short delay before submitting - dispatching Enter in the same tick as the
                    // synthetic input events can race HA's own reactive state picking up the new
                    // values, submitting a stale (often empty) password.
                    setTimeout(function() {
                        if (userField.value !== '$escapedUser') nativeSetter.call(userField, '$escapedUser');
                        if (passField.value !== '$escapedPassword') nativeSetter.call(passField, '$escapedPassword');
                        userField.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                        passField.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                        console.log('[mirrordash-auth] pre-submit check, userField.value.length=' + userField.value.length + ' passField.value.length=' + passField.value.length);
                        passField.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true, composed: true }));
                        console.log('[mirrordash-auth] submitted via Enter keydown');
                    }, 150);
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
