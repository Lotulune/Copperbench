# Stage15 legacy Linux preferences migration

PR65 review identified a real upgrade-path omission: pre-XDG files in `~/.copperbench` were no longer searched before preferences initialization. The unresolved conversation correctly blocked main integration after CI passed.

`LegacyPreferencesMigration` now imports one preferred legacy format (`userpreferences`, otherwise `preferences`) before the existing converter/loader runs. An existing active file in either format wins; old files remain unchanged. Explicit absolute COPPERBENCH_HOME/MCREATOR_HOME roots are isolated and Windows retains its original directory behavior. A private temporary copy is moved without replacement so failed copies cannot become active partial preferences. Import errors stop initialization with a clear diagnostic instead of silently saving defaults over the migration target.

Four migration regressions plus four directory-resolution and two desktop-path integration checks pass locally. A dedicated installed-JAR verifier exercises actual PreferencesManager initialization, old-value preservation and new XDG writes for both legacy formats.

Run37 remains accepted only for its own frozen source and is superseded for release by this product change. Its evidence remains unchanged. Release authorization is reset to pending-validation until the replacement candidate and installed migration checks pass. No tag, merge, production promotion or public release has occurred.
