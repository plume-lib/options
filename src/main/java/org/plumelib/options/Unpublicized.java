// The five files
//   Option.java
//   OptionGroup.java
//   Options.java
//   Unpublicized.java
//   OptionsDoclet.java
// together comprise the implementation of command-line processing.

package org.plumelib.options;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates a field that can be set by a command-line option, but that command-line option is
 * unpublicized: it is not included in the usage message. Should only be written on a field that
 * also has a {@code @Option} annotation.
 *
 * <p>If the {@code showUnpublicized} argument to the {@link Options#usage(boolean, String[])}
 * method is true, the usage message shows all options, even unpublicized ones.
 *
 * @see org.plumelib.options.Option
 * @see org.plumelib.options.Options
 * @see org.plumelib.options.OptionGroup
 * @see org.plumelib.options.OptionsDoclet
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Unpublicized {}
