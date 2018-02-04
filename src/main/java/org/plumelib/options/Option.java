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

/*>>>
import org.checkerframework.framework.qual.IgnoreInWholeProgramInference;
*/

/**
 * Indicates that the annotated field is set via a command-line option.
 *
 * <p>The command-line options are processed by the {@link org.plumelib.options.Options} class. For
 * example usage, see the documentation for {@link org.plumelib.options.Options}.
 *
 * @see org.plumelib.options.Options
 * @see org.plumelib.options.OptionGroup
 * @see org.plumelib.options.Unpublicized
 * @see org.plumelib.options.OptionsDoclet
 */
/*@IgnoreInWholeProgramInference*/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {
  /**
   * A string that describes the option. The format is <span style="white-space: nowrap;">"{@code
   * [-c] [<type>] description}"</span>:
   *
   * <ul>
   *   <li>"-c" is an optional single-character short name for the option.
   *   <li>"{@code <type>}" is an optional description of the option type, to be displayed instead
   *       of its Java type (e.g., "{@code <filename>}" if the variable's type is String). The
   *       less-than and greater-than symbols are required.
   *   <li>"description" is a short (one-line) description of the option suitable for a usage
   *       message. By convention, it starts with a capital letter but does not end with a period.
   *       The field's Javadoc comment should contain a longer description appropriate for a manual
   *       or manpage. The Javadoc comment often repeats something similar to the {@code @Option}
   *       description.
   * </ul>
   *
   * @return a string that describes this option
   * @see Option
   */
  String value();

  /**
   * Aliases for this option, which a user can use instead of the option's standard name. For
   * example, <span style="white-space: nowrap;">"--optimize"</span> and <span style="white-space:
   * nowrap;">"--optimise"</span> might be two aliases that may be used interchangeably. For
   * information about the use of this field, see the section on "Option aliases" in {@link
   * org.plumelib.options.Options}. If there is only a single, one-character alias, it can be put at
   * the beginning of the value field without the need for an aliases field.
   *
   * @return aliases for this option
   */
  String[] aliases() default {};

  /**
   * If true, {@link org.plumelib.options.OptionsDoclet} does not report the field's default value.
   *
   * @return whether not to hide default values
   */
  boolean noDocDefault() default false;
}
