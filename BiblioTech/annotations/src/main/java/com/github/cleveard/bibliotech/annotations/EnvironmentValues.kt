package com.github.cleveard.bibliotech.annotations

/**
 * Annotation to add values from the environment to a compilation
 * @param vars The names of the environment variable names to add
 * When this annotation is added to a class named ClassName it creates
 * a class named ClassName_Environment that acts as a map of
 * the environment variable names to the variables' values.
 * Requesting a variable that is not set in the environment is an error.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class EnvironmentValues(vararg val vars: String)
