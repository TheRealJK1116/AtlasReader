// Atlas Reader — root build file.
// Single :app module today. Package boundaries are organised so the module can be
// split into :core:engine, :core:database, :core:importer, :domain, :data, :ui
// without moving code (see docs/architecture.md).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
