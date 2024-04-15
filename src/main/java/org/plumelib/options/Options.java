// The five files
//   Option.java
//   OptionGroup.java
//   Options.java
//   Unpublicized.java
//   OptionsDoclet.java
// together comprise the implementation of command-line processing.

package org.plumelib.options;

import io.github.classgraph.ClassGraph;
import java.io.File;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import org.checkerframework.checker.formatter.qual.FormatMethod;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.signedness.qual.Signed;
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.qual.SideEffectFree;

/**
 * The Options class:
 *
 * <ul>
 *   <li>parses command-line options and sets fields in your program accordingly,
 *   <li>creates usage messages (such as printed by a <span style="white-space: nowrap;">{@code
 *       --help}</span> option), and
 *   <li>(at build time) creates documentation suitable for a manual or manpage.
 * </ul>
 *
 * <p>Thus, the programmer is freed from writing duplicative, boilerplate code. The user
 * documentation is automatically generated and never gets out of sync with the rest of the program.
 *
 * <p>The programmer does not have to write any code, only declare and document variables. For each
 * field that you want to set from a command-line argument, you write Javadoc and an {@code @Option}
 * annotation. Then, at run time, the field is automatically set from a command-line option of the
 * same name. Also, at build time, usage messages and printed documentation are generated
 * automatically.
 *
 * <p>Suppose your program should support the command-line arguments <span style="white-space:
 * nowrap;">{@code --outfile}</span>, <span style="white-space: nowrap;">{@code -o}</span>
 * (shorthand for <span style="white-space: nowrap;">{@code --outfile}</span>), <span
 * style="white-space: nowrap;">{@code --ignore-case}</span>, <span style="white-space:
 * nowrap;">{@code -i}</span>, (shorthand for <span style="white-space: nowrap;">{@code
 * --ignore-case}</span>), and <span style="white-space: nowrap;">{@code --temperature}</span>. This
 * code does so:
 *
 * <pre>
 * import org.plumelib.options.*;
 *
 * public class MyProgram {
 *
 *   &#64;Option("-o &lt;filename&gt; the output file ")
 *   public static File outfile = new File("/tmp/foobar");
 *
 *   &#64;Option("-i ignore case")
 *   public static boolean ignoreCase; // or, name the variable ignore_case
 *
 *   &#64;Option("set the initial temperature")
 *   public static double temperature = 75.0;
 *
 *   public static void main(String[] args) {
 *
 *     // variant 1
 *     MyProgram myInstance = new MyProgram();
 *     Options options = new Options("MyProgram [options] infile outfile",
 *                                   myInstance, MyUtilityClass.class);
 *     String[] remainingArgs = options.parse(true, args);
 *
 *     // variant 2
 *     Options options = new Options("MyProgram [options] infile outfile",
 *                                   MyProgram.class, MyUtilityClass.class);
 *     String[] remainingArgs = options.parse(true, args);
 *
 *     ...
 *   }
 * }
 * </pre>
 *
 * <p>In the first variant code above, the call to {@link #parse(boolean, String[])} sets fields in
 * object {@code myInstance} and sets static fields in class {@code MyUtilityClass}. (In the second
 * variant, it sets static fields in both classes.) It returns the original command line, with all
 * options removed. If a command-line argument is incorrect, it prints a usage message and
 * terminates the program. The program can also explicitly create or print a usage message; see
 * {@link #usage(String...)} and {@link #printUsage()}.
 *
 * <p>For examples of generated HTML documentation, see the documentation for <a
 * href="https://types.cs.washington.edu/plume-lib/api/plume/Lookup.html#command-line-options">Lookup</a>,
 * <a href="https://randoop.github.io/randoop/manual/#command-line-options">Randoop</a>, and <a
 * href="https://types.cs.washington.edu/javari/javarifier/#command-line-opts">Javarifier</a>.
 *
 * <p><b>@Option indicates a command-line option</b>
 *
 * <p>The {@code @}{@link Option} annotation on a field specifies brief user documentation and,
 * optionally, a one-character short name that a user may supply on the command line. The long name
 * is taken from the name of the variable. When the name contains a capital letter, the user must
 * use a hyphen or underscore to separate words. When the name contains an underscore, the user may
 * substitute a hyphen on the command line instead.
 *
 * <p>For example, both the <span style="white-space: nowrap;">{@code --multi-word-variable}</span>
 * and <span style="white-space: nowrap;">{@code --multi_word_variable}</span> command-line options
 * would set a variable named {@code multiWordVariable} or {@code multi_word_variable}. (It is an
 * error to define two variables {@code multiWordVariable} and {@code multi_word_variable}, and to
 * annotate both of them with {@code @Option}.)
 *
 * <p>A user of your program supplies command-line options in the form <span style="white-space:
 * nowrap;">"--name=value"</span> or <span style="white-space: nowrap;">"-name value"</span>. The
 * value (after the "=" or " ") is mandatory for all options except booleans. Booleans are set to
 * true if no value is specified.
 * <!-- Booleans support <span style="white-space:
 * nowrap;">"--no-<em>optionname</em>"</span> which is equivalent to <span style="white-space:
 * nowrap;">"--optionname=false"</span>. -->
 *
 * <p>A user may provide an option multiple times on the command line. If the field is a list, each
 * entry is added to the list. If the field is not a list, then only the last occurrence is used
 * (subsequent occurrences override the previous value).
 *
 * <p>All arguments that start with <span style="white-space: nowrap;">"-"</span> are processed as
 * options. By default, the entire command line is scanned for options. To terminate option
 * processing at the first non-option argument, see {@link #setParseAfterArg(boolean)}. Also, the
 * special option <span style="white-space: nowrap;">"--"</span> always terminates option
 * processing; <span style="white-space: nowrap;">"--"</span> is discarded, but no subsequent parts
 * of the command line are scanned for options.
 *
 * <p><b>Unpublicized options</b>
 *
 * <p>The {@code @}{@link Unpublicized} annotation causes an option not to be displayed in the usage
 * message. This can be useful for options that are preliminary, experimental, or for internal
 * purposes only. The {@code @}{@link Unpublicized} annotation must be specified in addition to the
 * {@code @}{@link Option} annotation.
 *
 * <p>The usage message can optionally include unpublicized options; see {@link
 * #usage(boolean,String...)}.
 *
 * <p><b>Option groups</b>
 *
 * <p>In a usage message or manual, it is useful to group related options and give the group a name.
 * For examples of this, see the documentation for <a
 * href="https://types.cs.washington.edu/plume-lib/api/plume/Lookup.html#command-line-options">Lookup</a>,
 * <a href="https://randoop.github.io/randoop/manual/#command-line-options">Randoop</a>, and <a
 * href="https://types.cs.washington.edu/javari/javarifier/#command-line-opts">Javarifier</a>.
 *
 * <p>If you wish to use option groups, then every option must be in some group. Declare related
 * fields adjacent to one another in your {@code .java} file. Write {@code @}{@link OptionGroup} on
 * the first field in each group.
 *
 * <p>The group name (the first argument of an {@code @OptionGroup} annotation) must be unique among
 * all classes and objects passed to the {@link #Options(String, Object...)} constructor.
 *
 * <p>If an option group itself is unpublicized:
 *
 * <ul>
 *   <li>The default usage message omits the group and all options belonging to it.
 *   <li>An unpublicized option group (that has any publicized options) is included in documentation
 *       for a manual.
 * </ul>
 *
 * <p>If an option group is not unpublicized but contains only unpublicized options, it will not be
 * included in the default usage message.
 *
 * <p><b>Option aliases</b>
 *
 * <p>The {@code @}{@link Option} annotation has an optional parameter {@code aliases}, which
 * accepts an array of strings. Each string in the array is an alias for the option being defined
 * and can be used in place of an option's long name or short name.
 *
 * <p>One example is that a program might support <span style="white-space:
 * nowrap;">"--optimize"</span> and <span style="white-space: nowrap;">"--optimise"</span> which are
 * interchangeable. Another example is that a program might support <span style="white-space:
 * nowrap;">"--help"</span> and <span style="white-space: nowrap;">"-help"</span> with the same
 * meaning:
 *
 * <pre>
 * // The user may supply --help, -h, or -help, which all mean the same thing and set this variable.
 * &#64;Option(value="-h Print a help message", aliases={"-help"})
 * public static boolean help;</pre>
 *
 * <p>Aliases should start with a single dash or double dash. If there is only a single,
 * one-character alias, it can be put at the beginning of the value field or in the aliases field.
 * It is the programmer's responsibility to ensure that no alias is the same as other options or
 * aliases.
 *
 * <p><b>Generating documentation for a manual or manpage</b>
 *
 * <p>It is helpful to include a summary of all command-line options in amanual, manpage, or the
 * class Javadoc for a class that has a main method. The {@link org.plumelib.options.OptionsDoclet}
 * class generates HTML documentation.
 *
 * <p><b>Supported field types</b>
 *
 * <p>A field with an {@code @}{@link Option} annotation may be of the following types:
 *
 * <ul>
 *   <li>Primitive types: boolean, byte, char, short, int, long, float, double.
 *   <li>Primitive type wrappers: Boolean, Byte, Char, Short, Integer, Long, Float, Double. Use of a
 *       wrapper type allows the argument to have no default value.
 *   <li>Reference types that have a constructor with a single string parameter.
 *   <li>java.util.regex.Pattern.
 *   <li>enums.
 *   <li>Lists of any of the above reference types.
 * </ul>
 *
 * <p><b>Customization</b>
 *
 * <p>Option processing can be customized in a number of ways.
 *
 * <ul>
 *   <li>If {@link #setUseSingleDash(boolean)} is true, then the long names take the form <span
 *       style="white-space: nowrap;">"-longname"</span> instead of <span style="white-space:
 *       nowrap;">"--longname"</span>. It defaults to false.
 *   <li>If {@link #setParseAfterArg(boolean)} is true, then options are searched for throughout a
 *       command line, to its end. If it is false, then processing stops at the first non-option
 *       argument. It defaults to true.
 *   <li>If {@link #spaceSeparatedLists} is true, then when an argument contains spaces, it is
 *       treated as multiple elements to be added to a list. It defaults to false.
 *   <li>The programmer may set {@link #usageSynopsis} to masquerade as another program.
 *   <li>If {@link #useDashes} is false, then usage messages advertise long options with underscores
 *       (as in {@code --my_option_name}) instead of dashes (as in {@code --my-option-name}). The
 *       user can always specify either one on the command line; {@link #useDashes} just affects
 *       usage messages. It defaults to false.
 * </ul>
 *
 * <p><b>Limitations</b>
 *
 * <ul>
 *   <li>Short options are only supported as separate entries (e.g., <span style="white-space:
 *       nowrap;">"-a -b"</span>) and not as a single group (e.g., <span style="white-space:
 *       nowrap;">"-ab"</span>).
 *   <li>If you have a boolean option named "long", you must use <span style="white-space:
 *       nowrap;">"--long=false"</span> to turn it off; <span style="white-space:
 *       nowrap;">"--no-long"</span> is not yet supported.
 * </ul>
 *
 * @see org.plumelib.options.Option
 * @see org.plumelib.options.OptionGroup
 * @see org.plumelib.options.Unpublicized
 * @see org.plumelib.options.OptionsDoclet
 */
public class Options {

  // User-settable fields

  /**
   * When true, long options take the form <span style="white-space: nowrap;">{@code
   * -longOption}</span> with a single dash, rather than the default <span style="white-space:
   * nowrap;">{@code --longOption}</span> with two dashes.
   */
  public boolean useSingleDash = false;

  /**
   * Whether to parse options after a non-option command-line argument. If false, option processing
   * stops at the first non-option command-line argument. If true, options specified even at the end
   * of the command line are processed.
   *
   * @see #setParseAfterArg(boolean)
   */
  private boolean parseAfterArg = true;

  /**
   * Whether to treat arguments to lists as space-separated. Defaults to false.
   *
   * <p>When true, an argument to an option of list type is split, on whitespace, into multiple
   * arguments each of which is added to the list. When false, each argument to an option of list
   * type is treated as a single element, no matter what characters it contains.
   *
   * <p>For example, when this is true, a command line containing <span style="white-space:
   * nowrap;">{@code --my-option="foo bar"}</span> is equivalent to <span style="white-space:
   * nowrap;">{@code --my-option="foo" --my-option="bar"}</span>. Both of them have the effect of
   * adding two elements, "foo" and "bar", to the list {@code myOption} (or {@code my_option}).
   */
  public static boolean spaceSeparatedLists = false;

  /**
   * Synopsis of usage. Example: "prog [options] arg1 arg2 ..."
   *
   * <p>This field is public so that clients can reset it. Setting it enables one program to
   * masquerade as another program, based on parsed options.
   */
  public @Nullable String usageSynopsis = null;

  /**
   * In usage messages, use dashes (hyphens) to split words in option names. This only applies to
   * fields whose name contains an underscore. On the command line, a user may use either the
   * underscores or dashes in the option name; this only controls which one is advertised in usage
   * messages.
   */
  public boolean useDashes = true;

  // Private fields

  /** First specified class. Void stands for "not yet initialized". */
  private Class<?> mainClass = Void.TYPE;

  /** List of all of the defined options. */
  private final List<OptionInfo> options = new ArrayList<>();

  /** Map from short or long option names (with leading dashes) to option information. */
  private final Map<String, OptionInfo> nameToOption = new LinkedHashMap<>();

  /** Map from option group name to option group information. */
  private final Map<String, OptionGroupInfo> groupNameToOptionGroup = new LinkedHashMap<>();

  /**
   * If true, then the user is using {@code @OptionGroup} annotations correctly (as per the
   * requirement specified above). If false, then {@code @OptionGroup} annotations have not been
   * specified on any {@code @Option}-annotated fields. When {@code @OptionGroup} annotations are
   * used incorrectly, an Error is thrown by the Options constructor.
   *
   * @see OptionGroup
   */
  private boolean hasGroups;

  /** String describing "[+]" (copied from Mercurial). */
  private static final String LIST_HELP = "[+] means option can be specified multiple times";

  /** If true, print diagnostic messages. */
  private boolean debugEnabled = false;

  /**
   * Enable or disable debug logging.
   *
   * @param enabled whether to enable or disable logging
   */
  public void enableDebugLogging(boolean enabled) {
    debugEnabled = enabled;
  }

  /** All of the argument options as a single string. Used for debugging. */
  private String optionsString = "";

  /** The system-dependent line separator. */
  private static String lineSeparator = System.lineSeparator();

  /** Information about an option. */
  class OptionInfo {

    /** What variable the option sets. */
    Field field;

    //    /** Option annotation on the field. */
    //    Option option;

    /** Object containing the field. Null if the field is static. */
    @UnknownInitialization @Nullable Object obj;

    /** Short (one-character) argument name. */
    @Nullable String shortName;

    /**
     * Long argument name. Uses '-' or '_' to separate words, depending on the value of {@link
     * useDashes}.
     */
    String longName;

    /** Aliases for this option. */
    String[] aliases;

    /** Argument description: the first line. */
    String description;

    /** Full Javadoc description. */
    @Nullable String jdoc;

    /**
     * Maps names of enum constants to their corresponding Javadoc. This is used by OptionsDoclet to
     * generate documentation for enum-type options. Null if the baseType is not an Enum.
     */
    @MonotonicNonNull Map<String, String> enumJdoc;

    /**
     * Name of the argument type. Defaults to the type of the field, but user can override this in
     * the option string.
     */
    String typeName;

    /** Class type of this field. If the field is a list, the basetype of the list. */
    Class<?> baseType;

    /** Default value of the option as a string. */
    @Nullable String defaultStr = null;

    /**
     * If true, the default value string for this option will be excluded from OptionsDoclet
     * documentation.
     */
    boolean noDocDefault = false;

    /** If the option is a list, this references that list. */
    @MonotonicNonNull List<Object> list = null;

    /** Constructor that takes one String for the type. */
    @Nullable Constructor<?> constructor = null;

    /**
     * Factory that takes a string (some classes don't have a string constructor) and always returns
     * non-null.
     */
    @Nullable Method factory = null;

    /** The second argument to the factory; non-null if needed. */
    @Nullable Object factoryArg2 = null;

    /**
     * If true, this OptionInfo is not output when printing documentation.
     *
     * @see #printUsage()
     */
    boolean unpublicized;

    /**
     * Create a new OptionInfo. The short name, type name, and description are taken from the option
     * parameter. The long name is the name of the field. The default value is the current value of
     * the field.
     *
     * @param field the field to set
     * @param option the option
     * @param obj the object whose field will be set; if obj is null, the field must be static
     * @param unpublicized whether the option is unpublicized
     */
    @SuppressWarnings({
      "nullness:argument", // field is static when object is null
      "interning:argument" // interning is not relevant to the call
    })
    OptionInfo(
        Field field,
        Option option,
        @UnknownInitialization @Nullable Object obj,
        boolean unpublicized) {
      this.field = field;
      //      this.option = option;
      this.obj = obj;
      this.baseType = field.getType();
      this.unpublicized = unpublicized;
      this.aliases = option.aliases();
      this.noDocDefault = option.noDocDefault();

      if (obj == null && !Modifier.isStatic(field.getModifiers())) {
        throw new Error("obj is null for non-static field " + field);
      }

      longName = fieldNameToOptionName(field.getName());
      if (useDashes) {
        longName = longName.replace('_', '-');
      }

      if (!Modifier.isPublic(field.getModifiers())) {
        throw new Error("option field is not public: " + field);
      }

      // Get the default value (if any)
      Object defaultObj = fieldGet(field, obj);
      if (defaultObj != null) {
        defaultStr = defaultObj.toString();
      }

      if (field.getType().isArray()) {
        throw new Error("@Option may not annotate a variable of array type: " + field);
      }

      // Handle lists.  When a list argument is specified multiple times,
      // each argument value is appended to the list.
      Type genType = field.getGenericType();
      if (genType instanceof ParameterizedType) {
        ParameterizedType pt = (ParameterizedType) genType;
        Type rawType = pt.getRawType();
        if (!rawType.equals(List.class)) {
          throw new Error(
              "@Option supports List<...> but no other parameterized type;"
                  + " it does not support type "
                  + pt
                  + " for field "
                  + field);
        }
        if (defaultObj == null) {
          List<Object> newList = new ArrayList<>();
          try {
            field.set(obj, newList);
          } catch (Exception e) {
            throw new Error("Unexpected error setting default for " + field, e);
          }
          defaultObj = newList;
        }
        if (((List<?>) defaultObj).isEmpty()) {
          defaultStr = null;
        }
        @SuppressWarnings("unchecked")
        List<Object> defaultObjAsList = (List<Object>) defaultObj;
        this.list = defaultObjAsList;
        // System.out.printf ("list default = %s%n", list);
        Type[] listTypeArgs = pt.getActualTypeArguments();
        this.baseType = (Class<?>) (listTypeArgs.length == 0 ? Object.class : listTypeArgs[0]);

        // System.out.printf ("Param type for %s = %s%n", field, pt);
        // System.out.printf ("raw type = %s, type = %s%n", pt.getRawType(),
        //                   pt.getActualTypeArguments()[0]);
      }

      // Get the short name, type name, and description from the annotation
      ParseResult pr;
      try {
        pr = parseOption(option.value());
      } catch (Throwable e) {
        throw new Error(
            "Error while processing @Option(\"" + option.value() + "\") on '" + field + "'", e);
      }
      shortName = pr.shortName;
      if (pr.typeName != null) {
        typeName = pr.typeName;
      } else {
        typeName = typeShortName(baseType);
      }
      description = pr.description;

      // Get a constructor for non-primitive base types
      if (!baseType.isPrimitive() && !baseType.isEnum()) {
        try {
          if (baseType == Path.class) {
            factory = Paths.class.getMethod("get", String.class, String[].class);
            factoryArg2 = new String[0];
          } else if (baseType == Pattern.class) {
            factory = Pattern.class.getMethod("compile", String.class);
          } else { // look for a string constructor
            constructor = baseType.getConstructor(String.class);
          }
        } catch (Exception e) {
          throw new Error(
              "@Option does not support type "
                  + baseType
                  + " for field "
                  + field
                  + " because it does not have a string constructor",
              e);
        }
      }
    }

    /**
     * Return whether or not this option has a required argument.
     *
     * @return whether or not this option has a required argument
     */
    public boolean argumentRequired() {
      Class<?> type = field.getType();
      return (type != Boolean.TYPE) && (type != Boolean.class);
    }

    /**
     * Returns a short synopsis of the option in the form <span style="white-space: nowrap;">{@code
     * -s --long=<type>}</span>.
     *
     * @return a synopsis of how the option can be provided on the command line
     */
    public String synopsis() {
      String prefix = useSingleDash ? "-" : "--";
      String name = prefix + longName;
      if (shortName != null) {
        name = String.format("-%s %s", shortName, name);
      }
      name += String.format("=<%s>", typeName);
      if (list != null) {
        name += " [+]";
      }
      return name;
    }

    /**
     * Return a one-line description of the option.
     *
     * @return a one-line description of the option
     */
    @Override
    @SideEffectFree
    public String toString(@GuardSatisfied OptionInfo this) {
      String prefix = useSingleDash ? "-" : "--";
      String shortNameStr = "";
      if (shortName != null) {
        shortNameStr = "-" + shortName + " ";
      }
      return String.format("%s%s%s field %s", shortNameStr, prefix, longName, field);
    }

    /**
     * Returns the class that declares this option.
     *
     * @return the class that declares this option
     */
    public Class<?> getDeclaringClass() {
      return field.getDeclaringClass();
    }
  }

  /** Information about an option group. */
  static class OptionGroupInfo {

    /** The name of this option group. */
    String name;

    /**
     * If true, this group of options will not be printed in usage output by default. However, the
     * usage information for this option group can be printed by specifying the group explicitly in
     * the call to {@link #printUsage}.
     */
    boolean unpublicized;

    /** List of options that belong to this group. */
    List<OptionInfo> optionList;

    /**
     * Create a new option group.
     *
     * @param name the name of this option group
     * @param unpublicized if true, this option group is unpublicized
     */
    OptionGroupInfo(String name, boolean unpublicized) {
      optionList = new ArrayList<OptionInfo>();
      this.name = name;
      this.unpublicized = unpublicized;
    }

    /**
     * Create a copy of an option group.
     *
     * @param optionGroup the option group to copy
     */
    OptionGroupInfo(OptionGroup optionGroup) {
      optionList = new ArrayList<OptionInfo>();
      this.name = optionGroup.value();
      this.unpublicized = optionGroup.unpublicized();
    }

    /**
     * If false, this group of options does not contain any publicized options, so it will not be
     * included in the default usage message.
     *
     * @return true if this group of options contains at least one publicized option
     */
    boolean anyPublicized() {
      for (OptionInfo oi : optionList) {
        if (!oi.unpublicized) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Prepare for option processing. Creates an object that will set fields in all the given
   * arguments. An argument to this method may be a Class, in which case its static fields are set.
   * The names of all the options (that is, the fields annotated with &#064;{@link Option}) must be
   * unique across all the arguments.
   *
   * @param args the classes whose options to process
   */
  public Options(@UnknownInitialization Object... args) {
    this("", args);
  }

  /**
   * Prepare for option processing. Creates an object that will set fields in all the given
   * arguments. An argument to this method may be a Class, in which case it must be fully initalized
   * and its static fields are set. The names of all the options (that is, the fields annotated with
   * &#064;{@link Option}) must be unique across all the arguments.
   *
   * @param usageSynopsis a synopsis of how to call your program
   * @param args the classes whose options to process
   */
  public Options(String usageSynopsis, @UnknownInitialization Object... args) {

    if (args.length == 0) {
      throw new Error("Must pass at least one object to Options constructor");
    }

    this.usageSynopsis = usageSynopsis;

    this.hasGroups = false;

    // true once the first @Option annotation is observed, false until then.
    boolean seenFirstOpt = false;

    // Loop through each specified object or class
    for (Object obj : args) {
      boolean isClass = obj instanceof Class<?>;
      // null or a key in groupNameToOptionGroup (that is, an option group name)
      @SuppressWarnings("keyfor")
      @KeyFor("groupNameToOptionGroup") String currentGroup = null;

      @SuppressWarnings({
        "nullness" // if isClass is true, obj is a non-null initialized Class
      })
      @Initialized @NonNull Class<?> clazz = (isClass ? (@Initialized @NonNull Class<?>) obj : obj.getClass());
      if (mainClass == Void.TYPE) {
        mainClass = clazz;
      }
      Field[] fields = clazz.getDeclaredFields();

      for (Field f : fields) {
        try {
          // Possible exception because "obj" is not yet initialized; catch it and proceed
          @SuppressWarnings("nullness:cast.unsafe")
          Object objNonraw = (@Initialized Object) obj;
          if (debugEnabled) {
            System.err.printf("Considering field %s of object %s%n", f, objNonraw);
          }
        } catch (Throwable t) {
          if (debugEnabled) {
            System.err.printf("Considering field %s of object of type %s%n", f, obj.getClass());
          }
        }
        try {
          if (debugEnabled) {
            System.err.printf(
                "  with annotations %s%n", Arrays.toString(f.getDeclaredAnnotations()));
          }
        } catch (java.lang.ArrayStoreException e) {
          if (e.getMessage() != null
              && Objects.equals(
                  e.getMessage(), "sun.reflect.annotation.TypeNotPresentExceptionProxy")) {
            if (debugEnabled) {
              System.err.printf("  with TypeNotPresentExceptionProxy while getting annotations%n");
            }
          } else {
            throw e;
          }
        }
        Option option = safeGetAnnotation(f, Option.class);
        if (option == null) {
          continue;
        }

        boolean unpublicized = safeGetAnnotation(f, Unpublicized.class) != null;

        if (isClass && !Modifier.isStatic(f.getModifiers())) {
          throw new Error("non-static option " + f + " in class " + obj);
        }

        @Initialized OptionInfo oi = new OptionInfo(f, option, isClass ? null : obj, unpublicized);
        options.add(oi);

        // The @OptionGroup annotation on this field, or null
        OptionGroup optionGroup = safeGetAnnotation(f, OptionGroup.class);

        if (!seenFirstOpt) {
          seenFirstOpt = true;
          // This is the first @Option annotation encountered so we can decide
          // now if the user intends to use option groups.
          if (optionGroup != null) {
            hasGroups = true;
          } else {
            continue;
          }
        }

        if (!hasGroups) {
          if (optionGroup != null) {
            // The user included an @OptionGroup annotation in their code
            // without including an @OptionGroup annotation on the first
            // @Option-annotated field, hence violating the requirement.

            // NOTE: changing this error string requires changes to TestPlume
            throw new Error(
                "missing @OptionGroup annotation on the first "
                    + "@Option-annotated field of class "
                    + mainClass);
          } else {
            continue;
          }
        }
        // hasGroups is true at this point.

        if (optionGroup != null) {
          String name = optionGroup.value();
          if (groupNameToOptionGroup.containsKey(name)) {
            throw new Error("option group " + name + " declared twice");
          }
          OptionGroupInfo gi = new OptionGroupInfo(optionGroup);
          groupNameToOptionGroup.put(name, gi);
          currentGroup = name;
        }
        // The variable currentGroup is set to null at the start of every iteration through 'args'.
        // This is so we can check that the first @Option-annotated field of every class/object in
        // 'args' has an @OptionGroup annotation when hasGroups is true, as required.
        if (currentGroup == null) {
          // NOTE: changing this error string requires changes to TestPlume
          throw new Error("missing @OptionGroup annotation in field " + f + " of class " + obj);
        }

        @NonNull OptionGroupInfo ogi = groupNameToOptionGroup.get(currentGroup);
        ogi.optionList.add(oi);
      } // loop through fields
    } // loop through args

    String prefix = useSingleDash ? "-" : "--";

    // Add each option to the option name map
    for (OptionInfo oi : options) {
      if (oi.shortName != null) {
        if (nameToOption.containsKey("-" + oi.shortName)) {
          throw new Error("short name " + oi + " appears twice");
        }
        nameToOption.put("-" + oi.shortName, oi);
      }
      if (nameToOption.containsKey(prefix + oi.longName)) {
        throw new Error("long name " + oi + " appears twice");
      }
      nameToOption.put(prefix + oi.longName, oi);
      if (useDashes && oi.longName.contains("-")) {
        nameToOption.put(prefix + oi.longName.replace('-', '_'), oi);
      }
      if (oi.aliases.length > 0) {
        for (String alias : oi.aliases) {
          if (nameToOption.containsKey(alias)) {
            throw new Error("alias " + oi + " appears twice");
          }
          nameToOption.put(alias, oi);
        }
      }
    }
  }

  /**
   * Converts a Java field name to a (long) option name. The option name uses '_' to separate words.
   *
   * @param fieldName the name of the field
   * @return the (long) name of the option
   */
  /* package-protected */ static String fieldNameToOptionName(String fieldName) {
    String optionName = fieldName;
    if (optionName.indexOf('_') == -1
        && !optionName.equals(optionName.toLowerCase(Locale.getDefault()))) {
      // optionName contains no underscores, but does contain a capital letter.
      // Insert an underscore before each capital letter, which is downcased.
      StringBuilder lnb = new StringBuilder();
      int optionNamelength = optionName.length();
      for (int i = 0; i < optionNamelength; i++) {
        char ch = optionName.charAt(i);
        if (Character.isUpperCase(ch)) {
          lnb.append('_');
          lnb.append(Character.toLowerCase(ch));
        } else {
          lnb.append(ch);
        }
      }
      optionName = lnb.toString();
    }
    return optionName;
  }

  /**
   * Like {@link Field#getAnnotation}, but returns null (and prints a warning) rather than throwing
   * an exception.
   *
   * @param <T> the type of the annotation to query for and return if present
   * @param f the Field that may contain the annotation
   * @param annotationClass the Class object corresponding to the annotation type, or null
   * @return this element's annotation for the specified annotation type if present on this element,
   *     else null
   */
  private static <T extends Annotation> @Nullable T safeGetAnnotation(
      Field f, Class<T> annotationClass) {
    @Nullable T annotation;
    try {
      // @SuppressWarnings("nullness:initialization.cast")
      @Nullable T cast = f.getAnnotation((Class<@NonNull T>) annotationClass);
      annotation = cast;
    } catch (Exception e) {
      // Can get
      //   java.lang.ArrayStoreException: sun.reflect.annotation.TypeNotPresentExceptionProxy
      // when an annotation is not present at run time (example: @NonNull)
      System.out.printf(
          "Exception in call to f.getAnnotation(%s)%n  for f=%s%n  %s%nClasspath =%n",
          annotationClass, f, e.getMessage());
      for (URI uri : new ClassGraph().getClasspathURIs()) {
        System.out.println("  " + uri);
      }
      annotation = null;
    }

    return annotation;
  }

  /**
   * If true, Options will parse arguments even after a non-option command-line argument. Setting
   * this to true is useful to permit users to write options at the end of a command line. Setting
   * this to false is useful to avoid processing arguments that are actually options/arguments for
   * another program that this one will invoke. The default is true.
   *
   * @param val whether to parse arguments after a non-option command-line argument
   */
  public void setParseAfterArg(boolean val) {
    parseAfterArg = val;
  }

  /**
   * If true, long options (those derived from field names) are expected with a single dash prefix
   * as in <span style="white-space: nowrap;">{@code -long-option}</span> rather than <span
   * style="white-space: nowrap;">{@code --long-option}</span>. The default is false and long
   * options will be parsed with a double dash prefix as in <span style="white-space:
   * nowrap;">{@code --longOption}</span>.
   *
   * @param val whether to parse long options with a single dash, as in <span style="white-space:
   *     nowrap;">{@code -longOption}</span>
   */
  public void setUseSingleDash(boolean val) {
    useSingleDash = val;
  }

  /**
   * Splits the argument string into an array of tokens (command-line flags and arguments),
   * respecting single and double quotes.
   *
   * <p>This method is only appropriate when the {@code String[]} version of the arguments is not
   * available &mdash; for example, for the {@code premain} method of a Java agent.
   *
   * @param args the command line to be tokenized
   * @return a string array analogous to the argument to {@code main}
   */
  // TODO: should this throw some exceptions?
  public static String[] tokenize(String args) {

    // Split the args string on whitespace boundaries accounting for quoted
    // strings.
    args = args.trim();
    List<String> argList = new ArrayList<>();
    String arg = "";
    for (int ii = 0; ii < args.length(); ii++) {
      char ch = args.charAt(ii);
      if ((ch == '\'') || (ch == '"')) {
        arg += ch;
        ii++;
        while ((ii < args.length()) && (args.charAt(ii) != ch)) {
          arg += args.charAt(ii++);
        }
        arg += ch;
      } else if (Character.isWhitespace(ch)) {
        // System.out.printf ("adding argument '%s'%n", arg);
        argList.add(arg);
        arg = "";
        while ((ii < args.length()) && Character.isWhitespace(args.charAt(ii))) {
          ii++;
        }
        if (ii < args.length()) {
          // Encountered a non-whitespace character.
          // Back up to process it on the next loop iteration.
          ii--;
        }
      } else { // must be part of current argument
        arg += ch;
      }
    }
    if (!arg.equals("")) {
      argList.add(arg);
    }

    String[] argsArray = argList.toArray(new String[argList.size()]);
    return argsArray;
  }

  /**
   * Sets option variables from the given command line.
   *
   * @param args the commandline to be parsed
   * @return all non-option arguments
   * @throws ArgException if the command line contains unknown option or misused options
   */
  public String[] parse(String[] args) throws ArgException {

    List<String> nonOptions = new ArrayList<>();
    // If true, then "--" has been seen and any argument starting with "-"
    // is processed as an ordinary argument, not as an option.
    boolean ignoreOptions = false;

    // Loop through each argument
    String tail = "";
    String arg;
    for (int ii = 0; ii < args.length; ) {
      // If there was a ',' separator in previous arg, use the tail as
      // current arg; otherwise, fetch the next arg from args list.
      if (tail.length() > 0) {
        arg = tail;
        tail = "";
      } else {
        arg = args[ii];
      }

      if (arg.equals("--")) {
        ignoreOptions = true;
      } else if ((arg.startsWith("--") || arg.startsWith("-")) && !ignoreOptions) {
        String argName;
        String argValue;

        // Allow ',' as an argument separator to get around
        // some command line quoting problems.  (markro)
        int splitPos = arg.indexOf(",-");
        if (splitPos == 0) {
          // Just discard the ',' if ",-" occurs at begining of string
          arg = arg.substring(1);
          splitPos = arg.indexOf(",-");
        }
        if (splitPos > 0) {
          tail = arg.substring(splitPos + 1);
          arg = arg.substring(0, splitPos);
        }

        int eqPos = arg.indexOf('=');
        if (eqPos == -1) {
          argName = arg;
          argValue = null;
        } else {
          argName = arg.substring(0, eqPos);
          argValue = arg.substring(eqPos + 1);
        }
        OptionInfo oi = nameToOption.get(argName);
        if (oi == null) {
          StringBuilder msg = new StringBuilder();
          msg.append(String.format("unknown option name '%s' in arg '%s'", argName, arg));
          if (false) { // for debugging
            msg.append("; known options:");
            for (String optionName : sortedKeySet(nameToOption)) {
              msg.append(" ");
              msg.append(optionName);
            }
          }
          throw new ArgException(msg.toString());
        }
        if (oi.argumentRequired() && (argValue == null)) {
          ii++;
          if (ii >= args.length) {
            throw new ArgException("option %s requires an argument", arg);
          }
          argValue = args[ii];
        }
        // System.out.printf ("argName = '%s', argValue='%s'%n", argName,
        //                    argValue);
        setArg(oi, argName, argValue);
      } else { // not an option
        if (!parseAfterArg) {
          ignoreOptions = true;
        }
        nonOptions.add(arg);
      }

      // If no ',' tail, advance to next args option
      if (tail.length() == 0) {
        ii++;
      }
    }
    String[] result = nonOptions.toArray(new String[nonOptions.size()]);
    return result;
  }

  /**
   * Sets option variables from the given command line; if any command-line argument is illegal,
   * prints the given message and terminates the program.
   *
   * <p>If an error occurs, prints the exception's message, prints the given message, and then
   * terminates the program. The program is terminated rather than throwing an error to create
   * cleaner output.
   *
   * @param message a message to print, such as "Pass --help for a list of all command-line
   *     arguments."
   * @param args the command line to parse
   * @return all non-option arguments
   * @see #parse(String[])
   */
  public String[] parse(String message, String[] args) {

    String[] nonOptions = null;

    try {
      nonOptions = parse(args);
    } catch (ArgException ae) {
      String exceptionMessage = ae.getMessage();
      if (exceptionMessage != null) {
        System.out.println(exceptionMessage);
      }
      System.out.println(message);
      System.exit(-1);
      // throw new Error ("message error: ", ae);
    }
    return nonOptions;
  }

  /**
   * Sets option variables from the given command line; if any command-line argument is illegal,
   * prints the usage message and terminates the program.
   *
   * <p>If an error occurs and {@code showUsageOnError} is true, prints the exception's message,
   * prints usage inoframtion, and then terminates the program. The program is terminated rather
   * than throwing an error to create cleaner output.
   *
   * @param showUsageOnError if a command-line argument is incorrect, print a usage message
   * @param args the command line to parse
   * @return all non-option arguments
   * @see #parse(String[])
   */
  public String[] parse(boolean showUsageOnError, String[] args) {

    String[] nonOptions = null;

    try {
      nonOptions = parse(args);
    } catch (ArgException ae) {
      String exceptionMessage = ae.getMessage();
      if (exceptionMessage != null) {
        System.out.println(exceptionMessage);
      }
      printUsage();
      System.exit(-1);
      // throw new Error ("usage error: ", ae);
    }
    return nonOptions;
  }

  /**
   * True if some documented option accepts a list as a parameter. Used and set by {code usage()}
   * methods and their callees.
   */
  private boolean hasListOption = false;

  /**
   * Prints usage information to the given PrintStream. Uses the usage synopsis passed into the
   * constructor, if any.
   *
   * @param ps where to print usage information
   */
  public void printUsage(PrintStream ps) {
    hasListOption = false;
    if (usageSynopsis != null) {
      ps.printf("Usage: %s%n", usageSynopsis);
    }
    ps.println(usage());
    if (hasListOption) {
      ps.println();
      ps.println(LIST_HELP);
    }
  }

  /** Prints, to standard output, usage information. */
  public void printUsage() {
    printUsage(System.out);
  }

  /**
   * Returns a usage message for command-line options.
   *
   * @param groupNames the list of option groups to include in the usage message. If empty and
   *     option groups are being used, will return usage for all option groups that are not
   *     unpublicized. If empty and option groups are not being used, will return usage for all
   *     options that are not unpublicized.
   * @return the command-line usage message
   */
  public String usage(String... groupNames) {
    return usage(false, groupNames);
  }

  /**
   * Returns a usage message for command-line options.
   *
   * @param showUnpublicized if true, treat all unpublicized options and option groups as publicized
   * @param groupNames the list of option groups to include in the usage message. If empty and
   *     option groups are being used, will return usage for all option groups that are not
   *     unpublicized. If empty and option groups are not being used, will return usage for all
   *     options that are not unpublicized.
   * @return the command-line usage message
   */
  public String usage(boolean showUnpublicized, String... groupNames) {
    if (!hasGroups) {
      if (groupNames.length > 0) {
        throw new IllegalArgumentException(
            "This instance of Options does not have any option groups defined");
      }
      return formatOptions(options, maxOptionLength(options, showUnpublicized), showUnpublicized);
    }

    List<OptionGroupInfo> groups = new ArrayList<>();
    if (groupNames.length > 0) {
      for (String groupName : groupNames) {
        if (!groupNameToOptionGroup.containsKey(groupName)) {
          throw new IllegalArgumentException("invalid option group: " + groupName);
        }
        OptionGroupInfo gi = groupNameToOptionGroup.get(groupName);
        if (!showUnpublicized && !gi.anyPublicized()) {
          throw new IllegalArgumentException(
              "group does not contain any publicized options: " + groupName);
        } else {
          groups.add(groupNameToOptionGroup.get(groupName));
        }
      }
    } else { // return usage for all groups that are not unpublicized
      for (OptionGroupInfo gi : groupNameToOptionGroup.values()) {
        if ((gi.unpublicized || !gi.anyPublicized()) && !showUnpublicized) {
          continue;
        }
        groups.add(gi);
      }
    }

    List<Integer> lengths = new ArrayList<>();
    for (OptionGroupInfo gi : groups) {
      lengths.add(maxOptionLength(gi.optionList, showUnpublicized));
    }
    int maxLength = Collections.max(lengths);

    StringJoiner buf = new StringJoiner(lineSeparator);
    for (OptionGroupInfo gi : groups) {
      buf.add(String.format("%n%s:", gi.name));
      buf.add(formatOptions(gi.optionList, maxLength, showUnpublicized));
    }

    return buf.toString();
  }

  /**
   * Format a list of options for use in generating usage messages. Also sets {@link #hasListOption}
   * if any option has list type.
   *
   * @param optList the options to format
   * @param maxLength the maximum number of characters in the output
   * @param showUnpublicized if true, include unpublicized options in the output
   * @return the formatted options
   */
  private String formatOptions(List<OptionInfo> optList, int maxLength, boolean showUnpublicized) {
    StringJoiner buf = new StringJoiner(lineSeparator);
    for (OptionInfo oi : optList) {
      if (oi.unpublicized && !showUnpublicized) {
        continue;
      }
      String defaultStr = "";
      if (oi.defaultStr != null) {
        defaultStr = String.format(" [default %s]", oi.defaultStr);
      }

      @SuppressWarnings("formatter") // format string computed from maxLength argument
      String use =
          String.format("  %-" + maxLength + "s - %s%s", oi.synopsis(), oi.description, defaultStr);
      buf.add(use);

      if (oi.list != null) {
        hasListOption = true;
      }
    }
    return buf.toString();
  }

  /**
   * Return the length of the longest synopsis message in a list of options. Useful for aligning
   * options in usage strings.
   *
   * @param optList the options whose synopsis messages to measure
   * @param showUnpublicized if true, include unpublicized options in the computation
   * @return the length of the longest synopsis message in a list of options
   */
  private int maxOptionLength(List<OptionInfo> optList, boolean showUnpublicized) {
    int maxLength = 0;
    for (OptionInfo oi : optList) {
      if (oi.unpublicized && !showUnpublicized) {
        continue;
      }
      int len = oi.synopsis().length();
      if (len > maxLength) {
        maxLength = len;
      }
    }
    return maxLength;
  }

  // Package-private accessors/utility methods that are needed by the OptionsDoclet class to
  // generate HTML documentation.

  /**
   * Return whether option groups are being used.
   *
   * @return true if option groups are being used
   */
  @Pure
  boolean hasGroups() {
    return hasGroups;
  }

  /**
   * Return whether single dashes are being used.
   *
   * @return true if single dashes are being used
   */
  @Pure
  boolean getUseSingleDash() {
    return useSingleDash;
  }

  /**
   * Returns all the defined options.
   *
   * @return all the defined options
   */
  List<OptionInfo> getOptions() {
    return options;
  }

  /**
   * Returns all the option groups.
   *
   * @return all the option groups
   */
  Collection<OptionGroupInfo> getOptionGroups() {
    return groupNameToOptionGroup.values();
  }

  /**
   * Set the specified option to the value specified in argValue.
   *
   * @param oi the option to set
   * @param argName the name of the argument as passed on the command line; used only for debugging
   * @param argValue a string representation of the value
   * @throws ArgException if there are any errors
   */
  @SuppressWarnings({
    "nullness:argument", // object can be null if field is static
    "interning:argument" // interning is not relevant to the call
  })
  private void setArg(OptionInfo oi, String argName, @Nullable String argValue)
      throws ArgException {

    Field f = oi.field;
    Class<?> type = oi.baseType;

    // Keep track of all of the options specified
    if (optionsString.length() > 0) {
      optionsString += " ";
    }
    optionsString += argName;
    if (argValue != null) {
      if (!argValue.contains(" ")) {
        optionsString += "=" + argValue;
      } else if (!argValue.contains("'")) {
        optionsString += "='" + argValue + "'";
      } else if (!argValue.contains("\"")) {
        optionsString += "=\"" + argValue + "\"";
      } else {
        throw new ArgException("Can't quote for internal debugging: " + argValue);
      }
    }
    // Argument values are required for everything but booleans
    if (argValue == null) {
      if ((type != Boolean.TYPE) || (type != Boolean.class)) {
        argValue = "true";
      } else {
        throw new ArgException("Value required for option " + argName);
      }
    }

    try {
      if (type.isPrimitive()) {
        if (type == Boolean.TYPE) {
          boolean val;
          String argValueLowercase = argValue.toLowerCase(Locale.getDefault());
          if (argValueLowercase.equals("true") || argValueLowercase.equals("t")) {
            val = true;
          } else if (argValueLowercase.equals("false") || argValueLowercase.equals("f")) {
            val = false;
          } else {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a boolean", argValue, argName);
          }
          argValue = val ? "true" : "false";
          // System.out.printf ("Setting %s to %s%n", argName, val);
          f.setBoolean(oi.obj, val);
        } else if (type == Byte.TYPE) {
          byte val;
          try {
            val = Byte.decode(argValue);
          } catch (Exception e) {
            throw new ArgException("Value \"%s\" for argument %s is not a byte", argValue, argName);
          }
          f.setByte(oi.obj, val);
        } else if (type == Character.TYPE) {
          if (argValue.length() != 1) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a single character", argValue, argName);
          }
          char val = argValue.charAt(0);
          f.setChar(oi.obj, val);
        } else if (type == Short.TYPE) {
          short val;
          try {
            val = Short.decode(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a short integer", argValue, argName);
          }
          f.setShort(oi.obj, val);
        } else if (type == Integer.TYPE) {
          int val;
          try {
            val = Integer.decode(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not an integer", argValue, argName);
          }
          f.setInt(oi.obj, val);
        } else if (type == Long.TYPE) {
          long val;
          try {
            val = Long.decode(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a long integer", argValue, argName);
          }
          f.setLong(oi.obj, val);
        } else if (type == Float.TYPE) {
          Float val;
          try {
            val = Float.valueOf(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a float", argValue, argName);
          }
          f.setFloat(oi.obj, val);
        } else if (type == Double.TYPE) {
          Double val;
          try {
            val = Double.valueOf(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a double", argValue, argName);
          }
          f.setDouble(oi.obj, val);
        } else { // unexpected type
          throw new Error("Unexpected type " + type);
        }
      } else { // reference type

        // If the argument is a list, add repeated arguments or multiple
        // blank separated arguments to the list, otherwise just set the
        // argument value.
        if (oi.list != null) {
          if (spaceSeparatedLists) {
            String[] aarr = argValue.split(" +");
            for (String aval : aarr) {
              Object val = getRefArg(oi, argName, aval);
              oi.list.add(val); // uncheck cast
            }
          } else {
            Object val = getRefArg(oi, argName, argValue);
            oi.list.add(val);
          }
        } else {
          Object val = getRefArg(oi, argName, argValue);
          f.set(oi.obj, val);
        }
      }
    } catch (ArgException ae) {
      throw ae;
    } catch (Exception e) {
      throw new Error("Unexpected error ", e);
    }
  }

  /**
   * Given a value string supplied on the command line, create an object. The only expected error is
   * some sort of parse error from the constructor.
   *
   * @param oi the option corresponding to {@code argName} and {@code argValue}
   * @param argName the argument name -- used only for diagnostics
   * @param argValue the value supplied on the command line, which this method parses
   * @return a value, whose printed representation is {@code argValue}
   * @throws ArgException if the user supplied an incorrect string (contained in {@code argValue})
   */
  @SuppressWarnings("nullness") // static method, so null first arg is OK: oi.factory
  private @NonNull Object getRefArg(OptionInfo oi, String argName, String argValue)
      throws ArgException {

    Object val;
    try {
      if (oi.constructor != null) {
        @SuppressWarnings("signedness:assignment") // assume command-line numeric args are signed
        @Signed Object signedVal = oi.constructor.newInstance(new Object[] {argValue});
        val = signedVal;
      } else if (oi.baseType.isEnum()) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object tmpVal = getEnumValue((Class<Enum>) oi.baseType, argValue);
        val = tmpVal;
      } else {
        if (oi.factory == null) {
          throw new Error("No constructor or factory for argument " + argName);
        }
        if (oi.factoryArg2 == null) {
          val = oi.factory.invoke(null, argValue);
        } else {
          val = oi.factory.invoke(null, argValue, oi.factoryArg2);
        }
      }
    } catch (Exception e) {
      throw new ArgException("Invalid argument (%s) for argument %s", argValue, argName);
    }

    return val;
  }

  /**
   * Behaves like {@link java.lang.Enum#valueOf}, except that {@code name} is case-insensitive and
   * hyphen-insensitive (hyphens can be used in place of underscores). This allows for greater
   * flexibility when specifying enum types as command-line arguments.
   *
   * @param <T> the enum type whose constant is to be returned
   * @param enumType the Class object of the enum type from which to return a constant
   * @param name the name of the constant to return
   * @return the enum constant of the specified enum type with the specified name
   */
  private <T extends Enum<T>> T getEnumValue(Class<T> enumType, String name) {
    T[] constants = enumType.getEnumConstants();
    if (constants == null) {
      throw new IllegalArgumentException(enumType.getName() + " is not an enum type");
    }
    for (T constant : constants) {
      if (constant.name().equalsIgnoreCase(name.replace('-', '_'))) {
        return constant;
      }
    }
    // same error that's thrown by Enum.valueOf()
    throw new IllegalArgumentException(
        "No enum constant " + enumType.getCanonicalName() + "." + name);
  }

  /**
   * Return a short name for the specified type for use in messages. This is usually the lowercase
   * simple name of the type, but there are special cases (for files, regular expressions, enums,
   * ...).
   *
   * @param type the type whoso short name to return
   * @return a short name for the specified type for use in messages
   */
  private static String typeShortName(Class<?> type) {

    if (type.isPrimitive()) {
      return type.getName();
    } else if (type == File.class || type == Path.class) {
      return "filename";
    } else if (type == Pattern.class) {
      return "regex";
    } else if (type.isEnum()) {
      return "enum";
    } else {
      return type.getSimpleName().toLowerCase(Locale.getDefault());
    }
  }

  /**
   * Returns a string containing all of the options that were set and their arguments. This is
   * essentially the contents of args[] with all non-options removed. It can be used for calling a
   * subprocess or for debugging.
   *
   * @return options, similarly to supplied on the command line
   * @see #settings()
   */
  public String getOptionsString() {
    return optionsString;
  }

  // TODO: document what this is good for.  Debugging?  Invoking other programs?
  /**
   * Returns a string containing the current setting for each option, in command-line format that
   * can be parsed by Options. Contains every known option even if the option was not specified on
   * the command line. Never contains duplicates.
   *
   * @return a command line that can be tokenized with {@link #tokenize}, containing the current
   *     setting for each option
   */
  public String settings() {
    return settings(false);
  }

  // TODO: document what this is good for.  Debugging?  Invoking other programs?
  /**
   * Returns a string containing the current setting for each option, in command-line format that
   * can be parsed by Options. Contains every known option even if the option was not specified on
   * the command line. Never contains duplicates.
   *
   * @param showUnpublicized if true, treat all unpublicized options and option groups as publicized
   * @return a command line that can be tokenized with {@link #tokenize}, containing the current
   *     setting for each option
   */
  public String settings(boolean showUnpublicized) {
    StringJoiner out = new StringJoiner(lineSeparator);

    // Determine the length of the longest name
    int maxLength = maxOptionLength(options, showUnpublicized);

    // Create the settings string
    for (OptionInfo oi : options) {
      @SuppressWarnings("formatter") // format string computed from maxLength
      String use =
          String.format("%-" + maxLength + "s = %s", oi.longName, fieldGet(oi.field, oi.obj));
      out.add(use);
    }

    return out.toString();
  }

  /**
   * Return a description of all of the known options. Each option is described on its own line in
   * the output.
   *
   * @return a description of all of the known options
   */
  @Override
  @SuppressWarnings({
    "allcheckers:purity",
    "lock:method.guarantee.violated" // side effect to local state (string creation)
  })
  @SideEffectFree
  public String toString(@GuardSatisfied Options this) {
    StringJoiner out = new StringJoiner(lineSeparator);

    for (OptionInfo oi : options) {
      out.add(oi.toString());
    }

    return out.toString();
  }

  /**
   * Indicates an exception encountered during argument processing. Contains no information other
   * than the message string.
   */
  public static class ArgException extends Exception {
    /** Unique identifier for serialization. If you add or remove fields, change this number. */
    static final long serialVersionUID = 20051223L;

    /**
     * Create an ArgException with the specified detail message.
     *
     * @param message the detail message for the exception
     */
    public ArgException(String message) {
      super(message);
    }

    /**
     * Create an ArgException whose detail message is formed by formatting the given format string
     * and arguments.
     *
     * @param format the format string
     * @param args the arguments to be formatted by the format string
     */
    @FormatMethod
    public ArgException(String format, @Nullable Object... args) {
      super(String.format(format, args));
    }
  }

  /** The result of parsing the argument to {@code @Option}. */
  private static class ParseResult {
    /** The short name of an option, or null if none. */
    @Nullable String shortName;

    /** The type name of an option, or null if none. */
    @Nullable String typeName;

    /** The description of an option. */
    String description;

    /**
     * Create a new ParseResult.
     *
     * @param shortName the short name of an option, or null if none
     * @param typeName the type name of an option, or null if none
     * @param description the description of an option
     */
    ParseResult(@Nullable String shortName, @Nullable String typeName, String description) {
      this.shortName = shortName;
      this.typeName = typeName;
      this.description = description;
    }
  }

  /**
   * Parse an option value (the argument to {@code @Option}) and return its three components
   * (shortName, typeName, and description). The shortName and typeName are null if they are not
   * specified in the string.
   *
   * @param val the string to parse, which is an argument to {@code @Option}
   * @return a description of the option
   */
  private static ParseResult parseOption(String val) {

    // Get the short name, long name, and description
    String shortName;
    String typeName;
    @NonNull String description;

    // Get the short name (if any)
    if (val.startsWith("-")) {
      if (val.length() < 4 || !val.substring(2, 3).equals(" ")) {
        throw new Error(
            "Malformed @Option argument \""
                + val
                + "\".  An argument that starts with '-' should contain"
                + " a short name, a space, and a description.");
      }
      shortName = val.substring(1, 2);
      description = val.substring(3);
    } else {
      shortName = null;
      description = val;
    }

    // Get the type name (if any)
    if (description.startsWith("<")) {
      typeName = description.substring(1).replaceFirst(">.*", "");
      description = description.replaceFirst("<.*> ", "");
    } else {
      typeName = null;
    }

    // Return the result
    return new ParseResult(shortName, typeName, description);
  }

  /**
   * Returns a sorted version of m.keySet().
   *
   * @param <K> type of the map keys
   * @param <V> type of the map values
   * @param m a map whose keyset will be sorted
   * @return a sorted version of m.keySet()
   */
  private static <K extends Comparable<? super K>, V> Collection<@KeyFor("#1") K> sortedKeySet(
      Map<K, V> m) {
    ArrayList<@KeyFor("#1") K> theKeys = new ArrayList<>(m.keySet());
    Collections.sort(theKeys);
    return theKeys;
  }

  /**
   * Returns the value of the field represented by this Field, on the specified object. Wraps {@code
   * Field.get}, but throws no exceptions other than an informative Error.
   *
   * @param field the field to extract
   * @param obj object from which the field's value is to be extracted; may be null if the field is
   *     static
   * @return the value of the represented field in object obj; primitive values are wrapped in an
   *     appropriate object before being returned
   */
  @SuppressWarnings(
      "nullness" // should not be called with non-static field and null obj, but if so, the
  // exception is caught and handled
  )
  private static Object fieldGet(Field field, @UnknownInitialization @Nullable Object obj) {
    try {
      return field.get(obj);
    } catch (Exception e) {
      throw new Error("Unexpected error reading " + field + " in " + obj, e);
    }
  }
}
