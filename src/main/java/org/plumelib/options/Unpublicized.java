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
 * Used in conjunction with the {@code @Option} annotation. Indicates that by default, the option
 * should not be included in the usage message.
 *
 * <p>If the showUnpublicized argument to the {@link Options#usage(boolean, String[])} method method
 * is true, the usage method shows all options, even unpublicized ones.
 *
 * @see org.plumelib.options.Option
 * @see org.plumelib.options.Options
 * @see org.plumelib.options.OptionGroup
 * @see org.plumelib.options.OptionsDoclet
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Unpublicized {}
