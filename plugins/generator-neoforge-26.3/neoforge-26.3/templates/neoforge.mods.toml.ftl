modLoader="javafml"
loaderVersion="[4,)"
license="${JavaConventions.escapeStringForJava(settings.getLicense())}"

[[mods]]
modId="${modid}"
version="${settings.getCleanVersion()}"
displayName="${JavaConventions.escapeStringForJava(settings.getModName())}"

[[dependencies.${modid}]]
modId="neoforge"
type="required"
versionRange="[26.3.0.76]"
ordering="NONE"
side="BOTH"

[[dependencies.${modid}]]
modId="minecraft"
type="required"
versionRange="[26.3]"
ordering="NONE"
side="BOTH"
