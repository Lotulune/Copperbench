{
  "schemaVersion": 1,
  "id": "${settings.getModID()}",
  "version": "${settings.getCleanVersion()}",
  "name": "${JavaConventions.escapeStringForJava(settings.getModName())}",
  "license": "${JavaConventions.escapeStringForJava(settings.getLicense())}",
  "environment": "*",
  "entrypoints": { "main": ["${package}.${JavaModName}"] },
  "depends": { "fabricloader": ">=0.19.5", "minecraft": "26.3", "java": ">=25", "fabric-api": "0.162.0+26.3" }
}
