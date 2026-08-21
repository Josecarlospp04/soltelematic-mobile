package pe.soltelematic.mobile.di

import org.koin.dsl.module

/**
 * Contraparte no-op de src/debug/.../DebugModule.kt: vacío a propósito. SoltelematicApp.kt
 * (src/main) registra debugModule sin condicional; en release no hay ningún binding que
 * registrar porque SyntheticAssetSeeder ni siquiera está en el classpath de este variant.
 */
val debugModule = module {}
