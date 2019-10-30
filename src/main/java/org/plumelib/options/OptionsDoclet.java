// The five files
//   Option.java
//   OptionGroup.java
//   Options.java
//   Unpublicized.java
//   OptionsDoclet.java
// together comprise the implementation of command-line processing.

package org.plumelib.options;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import org.checkerframework.checker.formatter.qual.Format;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Generates HTML documentation of command-line options, for use in a manual or in a Javadoc class
 * comment. Works with the {@link org.plumelib.options.Options} class.
 *
 * <p><b>Usage</b>
 *
 * <p>This doclet is typically invoked with:
 *
 * <pre>javadoc -quiet -doclet org.plumelib.options.OptionsDoclet [doclet options] [java files]
 * </pre>
 *
 * You must specify a docletpath to Javadoc, and it needs to include the class files for the
 * documented classes.
 *
 * <p><b>Doclet Options</b>
 *
 * <p>The following doclet options are supported:
 *
 * <ul>
 *   <li><b>-docfile</b> <i>file</i> When specified, this doclet reads <i>file</i> and replaces
 *       everything between the two lines
 *       <pre>&lt;!-- start options doc (DO NOT EDIT BY HAND) --&gt;</pre>
 *       and
 *       <pre>&lt;!-- end options doc --&gt;</pre>
 *       in <i>file</i> with the options documentation. By default, produces output to standard out
 *       without modifying <i>file</i>; see <span style="white-space: nowrap;">{@code
 *       -outfile}</span> and <span style="white-space: nowrap;">{@code -i}</span> to output
 *       elsewhere or to edit <i>file</i> in place.
 *   <li><b>-outfile</b> <i>file</i> The destination for the output (the default is standard out).
 *       If both <span style="white-space: nowrap;">{@code -outfile}</span> and <span
 *       style="white-space: nowrap;">{@code -docfile}</span> are specified, they must be different
 *       (but see the <span style="white-space: nowrap;">{@code -i}</span> option). When <span
 *       style="white-space: nowrap;">{@code -d}</span> is used, the output is written to a file
 *       with the given name relative to that destination directory.
 *   <li><b>-d</b> <i>directory</i> The destination directory for the output file. Only used if
 *       <span style="white-space: nowrap;">{@code -outfile}</span> is used, in which case, the file
 *       is written in this directory.
 *   <li><b>-i</b> Specifies that the docfile should be edited in-place. This option can only be
 *       used if the <span style="white-space: nowrap;">{@code -docfile}</span> option is used, and
 *       may not be used at the same time as the <span style="white-space: nowrap;">{@code
 *       -outfile}</span> option.
 *   <li><b>-format</b> <i>format</i> This option sets the output format of this doclet. Currently,
 *       the following values for <i>format</i> are supported:
 *       <ul>
 *         <li><b>javadoc</b> When this format is specified, the output of this doclet is formatted
 *             as a Javadoc comment, with "*" at the beginning of each line and (when used with the
 *             <span style="white-space: nowrap;">{@code -docfile}</span> option) using appropriate
 *             indentation. Inline {@code @link} and {@code @see} tags in the Javadoc input are left
 *             untouched.
 *         <li><b>html</b> This format outputs HTML for general-purpose use, meaning inline
 *             {@code @link} and {@code @see} tags in the Javadoc input are suitably replaced by
 *             HTML links. This is the default output format and need not be specified explicitly.
 *       </ul>
 *   <li><b>-classdoc</b> When specified, the output of this doclet includes the class documentation
 *       of the first class specified on the command-line.
 *   <li><b>-singledash</b> When specified, {@link
 *       org.plumelib.options.Options#setUseSingleDash(boolean) setUseSingleDash(true)} is called on
 *       the underlying instance of Options used to generate documentation.
 * </ul>
 *
 * <p><b>Examples</b>
 *
 * <p>Search for "OptionsDoclet" in the buildfiles for <a
 * href="https://types.cs.washington.edu/plume-lib/api/plume/Lookup.html#command-line-options">Lookup</a>,
 * <a href="https://randoop.github.io/randoop/manual/#command-line-options">Randoop</a>, and <a
 * href="https://types.cs.washington.edu/javari/javarifier/#command-line-opts">Javarifier</a>.
 *
 * <p><b>Requirements</b>
 *
 * <p>Classes passed to OptionsDoclet that have {@code @}{@link Option} annotations on non-static
 * fields should have a nullary (no-argument) constructor. The nullary constructor may be private or
 * public. This is required because an object instance is needed to get the default value of a
 * non-static field. It is cleaner to require a nullary constructor instead of trying to guess
 * arguments to pass to another constructor.
 *
 * <p><b>Hiding default value strings</b>
 *
 * <p>By default, the documentation generated by OptionsDoclet includes a default value string for
 * each option, in square brackets after the option's description, similar to the usage messages
 * generated by {@link org.plumelib.options.Options#usage(String...)}. To omit the default value for
 * an option that has a system-dependent default, set the {@link
 * org.plumelib.options.Option#noDocDefault} field to {@code true}:
 *
 * <pre>
 * &#47;**
 *  * &lt;other stuff...&gt;  This option defaults to the system timezone.
 *  *&#47;
 * &#64;Option(value="&lt;timezone&gt; Set the timezone", noDocDefault=true)
 * public static String timezone = TimeZone.getDefault().getID();</pre>
 *
 * Suppose that HTML documentation is generated in Chicago. Without {@code noDocDefault}, the HTML
 * documentation would incorrectly state that the default time zone is {@code "America/Chicago"},
 * which is incorrect for users elsewhere. Using {@code noDocDefault} keeps the HTML documentation
 * system-agnostic.
 *
 * <p><b>Uppublicized options</b>
 *
 * <p>The generated HTML documentation omits {@code @}{@link Unpublicized} options. It includes
 * unpublicized option groups if they contain any publicized options.
 *
 * <p><b>Troubleshooting</b>
 *
 * <p>If you get an error such as "{@code ARGH! @Option}", then you are using a buggy version of
 * gjdoc, the GNU Classpath implementation of Javadoc. To avoid the problem, upgrade or use a
 * different Javadoc implementation.
 *
 * @see org.plumelib.options.Option
 * @see org.plumelib.options.Options
 * @see org.plumelib.options.OptionGroup
 * @see org.plumelib.options.Unpublicized
 */

// This doesn't itself use org.plumelib.options.Options for its command-line option processing
// because a Doclet is required to implement the optionLength() and validOptions() methods.
public class OptionsDoclet implements Doclet {

  /** Return this to indicate that the doclet completed successfully. */
  private static final boolean OK = true;

  private static String lineSep = System.lineSeparator();

  private static final String LIST_HELP =
      "<code>[+]</code> marked option can be specified multiple times";

  private static final String startDelim = "<!-- start options doc (DO NOT EDIT BY HAND) -->";
  private static final String endDelim = "<!-- end options doc -->";

  // TODO: Is this needed?
  private static final @Format({}) String USAGE =
      "Provided by Options doclet:%n"
          + "-docfile <file>        Specify file into which options documentation is inserted%n"
          + "-outfile <file>        Specify destination for resulting output%n"
          + "-d <directory>         Destination directory for -outfile%n"
          + "-i                     Edit the docfile in-place%n"
          + "-format javadoc        Format output as a Javadoc comment%n"
          + "-classdoc              Include 'main' class documentation in output%n"
          + "-singledash            Use single dashes for long options (see org.plumelib.options.Options)%n"
          + "See the OptionsDoclet documentation for more details.%n";

  @SuppressWarnings("nullness:assignment.type.incompatible") // set by init()
  Locale locale = null;

  @SuppressWarnings("nullness:assignment.type.incompatible") // set by init()
  Reporter reporter = null;

  @SuppressWarnings("nullness:assignment.type.incompatible") // set by start or something??
  private Options options = null;;

  /** File into which options documentation is inserted. */
  private @Nullable File docFile = null;

  /** Destination for output, as a string. */
  private @Nullable String outFilename = null;
  /** Destination for output. */
  private @Nullable File outFile = null;
  /** Destination directory for output. */
  private @Nullable File destDir = null;

  /** If true, then edit docFile in place (and docFile is non-null). */
  private boolean inPlace = false;
  /** If true, then output format is Javadoc. */
  private boolean formatJavadoc = false;
  /** If true, then include the class's main Javadoc comment. */
  private boolean includeClassDoc = false;

  // /**
  //  * Tests the validity of command-line arguments passed to this doclet. Returns true if the
  // option
  //  * usage is valid, and false otherwise. This method is automatically invoked by Javadoc.
  //  *
  //  * @param reporter where to report errors
  //  */
  // public static boolean validOptions(String[] @MinLen(1) [] options, DocErrorReporter reporter) {
  //   for (int oi = 0; oi < options.length; oi++) {
  //   if (inPlace && outFile != null)
  //         reporter.print(Diagnostic.Kind.ERROR, "-i and -outfile can not be used at the same
  // time");
  //       }
  //     }
  //   }
  //   if (inPlace && docFile == null) {
  //     reporter.print(Diagnostic.Kind.ERROR, "-i supplied but -docfile was not");
  //     return false;
  //   }

  // if (outFilename != null) {
  //   if (destDir != null) {
  //     this.outFile = new File(destDir, outFilename);
  //   } else {
  //     this.outFile = new File(outFilename);
  //   }
  // }

  //   if (docFile != null && outFile != null && outFile.equals(docFile)) {
  //     reporter.print(Diagnostic.Kind.ERROR, "docfile must be different from outfile");
  //     return false;
  //   }
  // }

  private final Set<DocletOption> docletOptions =
      Set.of(
          new DocletOption(
              "--docfile", true, "file into which options documentation is inserted", "<file>") {
            @Override
            public boolean process(String option, List<String> arguments) {
              docFile = new File(arguments.get(0));
              if (!docFile.exists()) {
                reporter.print(Diagnostic.Kind.ERROR, "--docfile file not found: " + docFile);
                return false;
              }
              return OK;
            }
          },
          new DocletOption("--outfile", true, "destination for output", "<file>") {
            @Override
            public boolean process(String option, List<String> arguments) {
              outFilename = arguments.get(0);
              return OK;
            }
          },
          new DocletOption(
              "--dest-dir", true, "destination directory for --outfile", "<directory>") {
            @Override
            public boolean process(String option, List<String> arguments) {
              destDir = new File(arguments.get(0));
              return OK;
            }
          },
          new DocletOption("--in-place", false, "edit the docfile in-place", null) {
            @Override
            public boolean process(String option, List<String> arguments) {
              inPlace = true;
              return OK;
            }
          },
          new DocletOption("--format", false, "format output as a Javadoc comment", "javadoc") {
            @Override
            public boolean process(String option, List<String> arguments) {
              String format = arguments.get(0);
              if (!format.equals("javadoc") && !format.equals("html")) {
                reporter.print(Diagnostic.Kind.ERROR, "unrecognized output format: " + format);
                return false;
              }
              if (format.equals("javadoc")) {
                formatJavadoc = true;
              }
              return OK;
            }
          },
          new DocletOption(
              "--classdoc", false, "include 'main' class documentation in output", null) {
            @Override
            public boolean process(String option, List<String> arguments) {
              includeClassDoc = true;
              return OK;
            }
          },
          new DocletOption("--singledash", false, "use single dashes for long options", null) {
            @Override
            public boolean process(String option, List<String> arguments) {
              setUseSingleDash(true);
              return OK;
            }
          });

  @Override
  public void init(Locale locale, Reporter reporter) {
    this.locale = locale;
    this.reporter = reporter;
  }

  @Override
  public String getName() {
    // For this doclet, the name of the doclet is just the
    // simple name of the class. The name may be used in
    // messages related to this doclet, such as in command-line
    // help when doclet-specific options are provided.
    return getClass().getSimpleName();
  }

  @Override
  public Set<? extends Doclet.Option> getSupportedOptions() {
    return docletOptions;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public boolean run(DocletEnvironment environment) {
    // This method is called to perform the work of the doclet.
    // In this case, it just prints out the names of the
    // elements specified on the command line.
    // environment.getSpecifiedElements().forEach(System.out::println);
    return OK;
  }

  /**
   * Return true if using a single dash (as opposed to a double dash) for command-line options.
   *
   * @return whether to use a single dash (as opposed to a double dash) for command-line options
   */
  public boolean getUseSingleDash() {
    return options.getUseSingleDash();
  }

  /**
   * See {@link Options#setUseSingleDash(boolean)}.
   *
   * @param val whether to use a single dash (as opposed to a double dash) for command-line options
   */
  public void setUseSingleDash(boolean val) {
    options.setUseSingleDash(true);
  }

  /**
   * A base class for declaring options. Subtypes for specific options should implement the {@link
   * #process(String,List) process} method to handle instances of the option found on the command
   * line.
   */
  abstract class DocletOption implements Doclet.Option {
    private final String name;
    private final boolean hasArg;
    private final String description;
    private final @Nullable String parameters;

    DocletOption(String name, boolean hasArg, String description, @Nullable String parameters) {
      this.name = name;
      this.hasArg = hasArg;
      this.description = description;
      this.parameters = parameters;
    }

    @Override
    public int getArgumentCount() {
      return hasArg ? 1 : 0;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public Kind getKind() {
      return Kind.STANDARD;
    }

    @Override
    public List<String> getNames() {
      return List.of(name);
    }

    @Override
    public String getParameters() {
      return hasArg && parameters != null ? parameters : "";
    }
  }
}
