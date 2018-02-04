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
 * Indicates which {@code @Option}-annotated fields are part of an option group &mdash; a related
 * set of user-visible features. Option groups are documented in {@link Options}.
 *
 * <p>Write {@code OptionGroup} on the first field in every group. Every {@code @Option}-annotated
 * field must be part of a group if any of them are.
 *
 * <p>{@code @OptionGroup} must appear after a field's Javadoc comment, if any. A Javadoc comment
 * between {@code @OptionGroup} and the field is ignored. For example, you must write
 *
 * <pre>
 *  /** comment for first option{@literal *}/
 * {@literal @}OptionGroup("the group")
 * {@literal @}Option("the first option")
 *  public static int first_option = blah;</pre>
 *
 * instead of
 *
 * <pre>
 * {@literal @}OptionGroup("the group")
 *
 *  /** comment for first option{@literal *}/
 * {@literal @}Option("the first option")
 *  public static int first_option = blah;</pre>
 *
 * @see org.plumelib.options.Options
 * @see org.plumelib.options.Option
 * @see org.plumelib.options.Unpublicized
 * @see org.plumelib.options.OptionsDoclet
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OptionGroup {
  /**
   * Name of this option group. Must be unique across all objects that are passed to the {@link
   * Options} constructor.
   *
   * @return the name of this option group
   */
  String value();
  /**
   * Whether this option group is unpublicized.
   *
   * @return true if this option group is unpublicized, false if it is publicized
   */
  boolean unpublicized() default false;
}
