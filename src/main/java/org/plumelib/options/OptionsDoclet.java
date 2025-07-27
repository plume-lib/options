// The five files
//   Option.java
//   OptionGroup.java
//   Options.java
//   Unpublicized.java
//   OptionsDoclet.java
// together comprise the implementation of command-line processing.

package org.plumelib.options;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.SimpleDocTreeVisitor;
import io.github.classgraph.ClassGraph;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.StringJoiner;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.text.StringEscapeUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.checker.signature.qual.FullyQualifiedName;
import org.checkerframework.checker.signedness.qual.Signed;
import org.plumelib.reflection.Signatures;

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
 * <p>You must specify a docletpath to Javadoc, and it needs to include the class files for the
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
 *       <span style="white-space: nowrap;">{@code -outfile}</span> is used, in which case the file
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
 * <p>Classes passed to OptionsDoclet that have {@code @}{@link org.plumelib.options.Option}
 * annotations on non-static fields should have a nullary (no-argument) constructor. The nullary
 * constructor may be private or public. This is required because an object instance is needed to
 * get the default value of a non-static field. It is cleaner to require a nullary constructor
 * instead of trying to guess arguments to pass to another constructor.
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
 * <p>Suppose that HTML documentation is generated in Chicago. Without {@code noDocDefault}, the
 * HTML documentation would incorrectly state that the default time zone is {@code
 * "America/Chicago"}, which is incorrect for users elsewhere. Using {@code noDocDefault} keeps the
 * HTML documentation system-agnostic.
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
// because Doclet has a different way of processing command-line options.
public class OptionsDoclet implements Doclet {

  /** The system-specific line separator. */
  private static String lineSep = System.lineSeparator();

  /** How to use the Options doclet. */
  private static final String USAGE =
      String.join(
          System.lineSeparator(),
          "Provided by Options doclet:",
          "-docfile <file>        Specify file into which options documentation is inserted",
          "-outfile <file>        Specify destination for resulting output",
          "-d <directory>         Destination directory for -outfile",
          "-i                     Edit the docfile in-place",
          "-format javadoc        Format output as a Javadoc comment",
          "-classdoc              Include 'main' class documentation in output",
          "-singledash            Use single dashes for long options (see"
              + " org.plumelib.options.Options)",
          "See the OptionsDoclet documentation for more details.");

  /** Help message about options that can be specified multiple times. */
  private static final String LIST_HELP =
      "{@code [+]} means option can be specified multiple times";

  /** Marker for start of options documentation. */
  private String startDelim = "<!-- start options doc (DO NOT EDIT BY HAND) -->";

  /** Marker for end of options documentation. */
  private String endDelim = "<!-- end options doc -->";

  /** The file into which options documentation is inserted. */
  private @Nullable File docFile = null;

  /** File name of destination for output. */
  private @Nullable String outFileName = null;

  /** Name of destination directory. */
  private @Nullable String destDir = null;

  /** Destination for output; is set from destDir and outFileName. */
  private @Nullable File outFile = null;

  /** If true, then edit docFile in place (and docFile is non-null). */
  private boolean inPlace = false;

  /** If true, then output format is Javadoc. */
  private boolean formatJavadoc = false;

  /** If true, then include the class's main Javadoc comment. */
  private boolean includeClassDoc = false;

  /** The doclet environment. */
  private DocletEnvironment denv;

  /** The command-line options. */
  private Options options;

  /** The DocTrees instance assocated with {@link #denv}. */
  private DocTrees docTrees;

  /** Used to report errors. */
  private Reporter reporter;

  /** Create an OptionsDoclet. */
  @SuppressWarnings({
    "nullness:initialization.fields.uninitialized", // init() sets reporter, run() sets denv
    "initializedfields:contracts.postcondition" // init() sets reporter, run() sets denv
  })
  public OptionsDoclet() {
    // this.options = options;
  }

  // //////////////////////////////////////////////////////////////////////
  // Doclet-specific methods
  //

  @Override
  public void init(Locale locale, Reporter reporter) {
    this.reporter = reporter;
  }

  @Override
  public String getName() {
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
  public boolean run(DocletEnvironment denv) {
    this.denv = denv;
    postprocessOptions();
    docTrees = denv.getDocTrees();

    List<Object> objs = new ArrayList<>();
    for (Element doc : denv.getSpecifiedElements()) {
      if (!isTypeElement(doc)) {
        throw new Error(
            String.format("Unexpected specified element of kind %s: %s", doc.getKind(), doc));
      }

      Class<?> clazz;
      try {
        @BinaryName String className = getBinaryName((TypeElement) doc);
        // Note: Thread.currentThread().getContextClassLoader() lacks the needed classes.
        clazz = Class.forName(className, true, OptionsDoclet.class.getClassLoader());
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        for (URI uri : new ClassGraph().getClasspathURIs()) {
          System.out.println(uri);
        }
        return false;
      }

      // It would be possible for OptionsDoclet to do all its processing without instantiating any
      // classes, by parsing source files.  However, it is simpler to re-use the existing Options
      // class, which requires instantiation, rather than re-implementing functionality.
      if (needsInstantiation(clazz)) {
        try {
          Constructor<?> c = clazz.getDeclaredConstructor();
          c.setAccessible(true);
          @SuppressWarnings("signedness:assignment")
          @Signed Object signedObj = c.newInstance(new Object[0]);
          objs.add(signedObj);
        } catch (Exception e) {
          System.out.println("Classpath:");
          for (URI uri : new ClassGraph().getClasspathURIs()) {
            System.out.println("  " + uri);
          }
          e.printStackTrace();
          return false;
        }
      } else {
        objs.add(clazz);
      }
    }

    if (objs.isEmpty()) {
      System.out.println("Error: no classes found");
      return false;
    }

    Object[] objarray = objs.toArray();
    options = new Options(objarray);
    if (options.getOptions().size() < 1) {
      System.out.println("Error: no @Option-annotated fields found");
      return false;
    }

    processJavadoc();
    try {
      write();
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }

    return OK;
  }

  // //////////////////////////////////////////////////////////////////////
  // Javadoc command-line options
  //

  // The doclet cannot use the Options class itself because  Javadoc specifies its own way of
  // handling command-line arguments.

  /** A value that indicates that a method completed successfully. */
  private static final boolean OK = true;

  /**
   * A command-line option to the Javadoc doclet; implements the {@link Doclet.Option} interface.
   *
   * <p>Is abstract because it does not provide an implementation of the {@code process} method.
   */
  abstract static class DocletOption implements Doclet.Option {
    /** The number of arguments this option will consume. */
    private int argumentCount;

    /** The user-friendly description of the option. */
    private String description;

    /**
     * The list of names and aliases that may be used to identify the option. Each starts with "-"
     * or "--".
     */
    private List<String> names;

    /**
     * A user-friendly string description of the option's parameters, or the empty string if this
     * option has no parameters.
     */
    private String parameters;

    /**
     * Creates a new DocletOption.
     *
     * @param name the option's name, starting with "-" or "--"
     * @param parameters a user-friendly string description of the option's parameters, or the empty
     *     string if this option has no parameters
     * @param argumentCount the number of arguments this option will consume
     * @param description the user-friendly description of the option
     */
    DocletOption(String name, String parameters, int argumentCount, String description) {
      this.argumentCount = argumentCount;
      this.description = description;
      this.names = List.of(name);
      this.parameters = parameters;
    }

    /**
     * Creates a new DocletOption with an aliased name.
     *
     * @param name the option's name, starting with "-" or "--"
     * @param alias the option's alias, starting with "-" or "--"
     * @param parameters a user-friendly string description of the option's parameters, or the empty
     *     string if this option has no parameters
     * @param argumentCount the number of arguments this option will consume
     * @param description the user-friendly description of the option
     */
    DocletOption(
        String name, String alias, String parameters, int argumentCount, String description) {
      this.argumentCount = argumentCount;
      this.description = description;
      this.names = List.of(name, alias);
      this.parameters = parameters;
    }

    @Override
    public int getArgumentCount() {
      return argumentCount;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public Doclet.Option.Kind getKind() {
      return Doclet.Option.Kind.STANDARD;
    }

    @Override
    public List<String> getNames() {
      return names;
    }

    @Override
    public String getParameters() {
      return parameters;
    }
  }

  /** The command-line options for OptionsDoclet. */
  @SuppressWarnings(
      "nullness:method.invocation" // when methods such as printError() are called, the receiver
  // (an OptionsDoclet) is initialized
  )
  private final Set<DocletOption> docletOptions =
      Set.of(
          new DocletOption(
              "--docfile",
              "-docfile",
              "file",
              1,
              "the output is the contents of this file, with some text replaced") {
            @Override
            public boolean process(String option, List<String> arguments) {
              assert arguments.size() == 1;
              String docFileName = arguments.get(0);
              docFile = new File(docFileName);
              if (!docFile.exists()) {
                printError("-docfile file not found: " + docFile);
                return false;
              }
              if (docFile != null && outFile != null && outFile.equals(docFile)) {
                printError("docfile must be different from outfile");
                return false;
              }
              return OK;
            }
          },
          new DocletOption("--outfile", "-outfile", "file", 1, "the destination for the output") {
            @Override
            public boolean process(String option, List<String> arguments) {
              assert arguments.size() == 1;
              outFileName = arguments.get(0);
              // TODO: move to later and centralize.
              if (docFile != null && outFileName != null && new File(outFileName).equals(docFile)) {
                printError("docfile must be different from outfile");
                return false;
              }
              return OK;
            }
          },
          new DocletOption("-d", "directory", 1, "the destination directory for the output file") {
            @Override
            public boolean process(String option, List<String> arguments) {
              assert arguments.size() == 1;
              destDir = arguments.get(0);
              return OK;
            }
          },
          new DocletOption("-i", "", 0, "the docfile should be edited in place") {
            @Override
            public boolean process(String option, List<String> arguments) {
              assert arguments.size() == 0;
              inPlace = true;
              return OK;
            }
          },
          new DocletOption(
              "--format", "-format", "formatname", 1, "the output format: javadoc or html") {
            @Override
            public boolean process(String option, List<String> arguments) {
              assert arguments.size() == 1;
              String format = arguments.get(0);
              if (!format.equals("javadoc") && !format.equals("html")) {
                printError("unrecognized output format: " + format);
                return false;
              }
              setFormatJavadoc(format.equals("javadoc"));
              return OK;
            }
          },
          new DocletOption(
              "--classdoc", "-classdoc", "", 0, "the docfile should be edited in place") {
            @Override
            public boolean process(String option, List<String> arguments) {
              assert arguments.size() == 0;
              includeClassDoc = true;
              return OK;
            }
          },
          new DocletOption(
              "--singledash",
              "-singledash",
              "",
              0,
              "show long options with leading \"-\" instead of \"--\"") {
            @Override
            public boolean process(String option, List<String> arguments) {
              assert arguments.size() == 0;
              setUseSingleDash(true);
              return OK;
            }
          });

  /**
   * Sets variables that can only be set after all command-line options have been processed. Isuses
   * errors and halts if any command-line options are incompatible with one another.
   */
  private void postprocessOptions() {
    if (outFileName != null) {
      if (destDir != null) {
        this.outFile = new File(destDir, outFileName);
      } else {
        this.outFile = new File(outFileName);
      }
    }

    boolean hasError = false;
    if (inPlace && outFile != null) {
      printError("-i and -outfile can not be used at the same time");
      hasError = true;
    }
    if (inPlace && docFile == null) {
      printError("-i supplied but -docfile was not");
      hasError = true;
    }
    if (docFile != null && outFile != null && outFile.equals(docFile)) {
      printError("--docfile must be different from --outfile");
      hasError = true;
    }
    if (inPlace && docFile == null) {
      printError("-i supplied but --docfile was not");
      hasError = true;
    }
    if (hasError) {
      System.err.println(USAGE);
      System.exit(1);
    }
  }

  /**
   * Determine if a class needs to be instantiated in order to work properly with {@link Options}.
   *
   * @param clazz the class whose values will be created by command-line arguments
   * @return true if the class needs to be instantiated before command-line arguments are parsed
   */
  private static boolean needsInstantiation(Class<?> clazz) {
    for (Field f : clazz.getDeclaredFields()) {
      if (f.isAnnotationPresent(org.plumelib.options.Option.class)
          && !Modifier.isStatic(f.getModifiers())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Print error message via delegation to {@link #reporter}.
   *
   * @param msg message to print
   */
  private void printError(String msg) {
    reporter.print(Diagnostic.Kind.ERROR, msg);
  }

  // //////////////////////////////////////////////////////////////////////
  // File IO methods
  //

  /**
   * Write the output of this doclet to the correct file.
   *
   * @throws Exception if there is trouble
   */
  public void write() throws Exception {
    PrintWriter out;
    // `output()` is called here because it might throw an exception; if called after `out` is set,
    // that exception might prevent `out` from being closed.
    String output = output();

    if (outFile != null) {
      out = new PrintWriter(Files.newBufferedWriter(outFile.toPath(), UTF_8));
    } else if (inPlace) {
      assert docFile != null
          : "@AssumeAssertion(nullness): dependent: docFile is non-null if inPlace is true";
      out = new PrintWriter(Files.newBufferedWriter(docFile.toPath(), UTF_8));
    } else {
      out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8)));
    }

    out.println(output);
    out.flush();
    out.close();
  }

  /**
   * Returns the final output of this doclet. The string returned by this method is the output seen
   * by the user.
   *
   * @return the user-visible doclet output
   * @throws Exception if there is trouble
   */
  public String output() throws Exception {
    if (docFile == null) {
      if (formatJavadoc) {
        return optionsToJavadoc(0, 99);
      } else {
        return optionsToHtml(0);
      }
    }

    return newDocFileText();
  }

  /**
   * Returns the result of inserting the options documentation into the docfile.
   *
   * @return the docfile, but with the command-line argument documentation updated
   * @throws Exception if there is trouble reading files
   */
  @RequiresNonNull("docFile")
  private String newDocFileText() throws Exception {
    StringJoiner b = new StringJoiner(lineSep);
    try (BufferedReader doc = Files.newBufferedReader(docFile.toPath(), UTF_8)) {
      String docline;
      boolean replacing = false;
      boolean replacedOnce = false;

      while ((docline = doc.readLine()) != null) {
        if (replacing) {
          if (docline.trim().equals(endDelim)) {
            replacing = false;
          } else {
            continue;
          }
        }

        b.add(docline);

        if (!replacedOnce && docline.trim().equals(startDelim)) {
          if (formatJavadoc) {
            int starIndex = docline.indexOf('*');
            b.add(docline.substring(0, starIndex + 1));
            String jdoc = optionsToJavadoc(starIndex, 100);
            b.add(jdoc);
            if (jdoc.endsWith("</ul>")) {
              b.add(docline.substring(0, starIndex + 1));
            }
          } else {
            b.add(optionsToHtml(0));
          }
          replacedOnce = true;
          replacing = true;
        }
      }
      if (!replacedOnce) {
        System.err.println("Did not find start delimiter: " + startDelim);
      }
    }
    return b.toString();
  }

  // //////////////////////////////////////////////////////////////////////
  // HTML and Javadoc processing methods
  //

  /**
   * Returns the fields defined by the given type.
   *
   * @param type a type
   * @return the fields defined by the given type
   */
  private List<VariableElement> fields(TypeElement type) {
    List<VariableElement> result = new ArrayList<>();
    for (Element ee : type.getEnclosedElements()) {
      if (ee.getKind() == ElementKind.FIELD) {
        result.add((VariableElement) ee);
      }
    }
    return result;
  }

  /**
   * Returns the enum constants defined by the given type.
   *
   * @param type a type
   * @return the enum constants defined by the given type
   */
  private List<VariableElement> enumConstants(TypeElement type) {
    List<VariableElement> result = new ArrayList<>();
    for (Element ee : type.getEnclosedElements()) {
      if (ee.getKind() == ElementKind.ENUM_CONSTANT) {
        result.add((VariableElement) ee);
      }
    }
    return result;
  }

  /** Adds Javadoc info to each option in {@code options.getOptions()}. */
  public void processJavadoc() {
    for (Options.OptionInfo oi : options.getOptions()) {
      @SuppressWarnings("signature") // non-array non-primitive => Class.getName(): @BinaryName
      @FullyQualifiedName String className = Signatures.binaryNameToFullyQualified(oi.getDeclaringClass().getName());
      TypeElement optDoc = denv.getElementUtils().getTypeElement(className);
      if (optDoc != null) {
        String nameWithUnderscores = oi.longName.replace('-', '_');
        for (VariableElement fd : fields(optDoc)) {
          if (nameWithUnderscores.equals(
              Options.fieldNameToOptionName(fd.getSimpleName().toString()))) {
            // If Javadoc for field is unavailable, then use the @Option
            // description in the documentation.
            DocCommentTree fieldComment = docTrees.getDocCommentTree(fd);
            if (fieldComment == null) {
              // oi.description is a string rather than a Javadoc (HTML) comment so we
              // must escape it.
              oi.jdoc = StringEscapeUtils.escapeHtml4(oi.description);
            } else if (formatJavadoc) {
              // TODO: Need to remove tags from this text?
              oi.jdoc = fieldComment.toString();
            } else {
              oi.jdoc = docCommentToHtml(fieldComment);
            }
            break;
          }
        }
      }
      if (oi.baseType.isEnum()) {
        processEnumJavadoc(oi);
      }
    }
  }

  /**
   * Initializes {@link Options.OptionInfo#enumJdoc} for the given {@code OptionInfo}: creates a
   * mapping from enum constants to their Javadoc.
   *
   * @param oi the enum option whose Javadoc to read
   */
  @SuppressWarnings("ModifyCollectionInEnhancedForLoop")
  private void processEnumJavadoc(Options.OptionInfo oi) {
    Enum<?>[] constants = (Enum<?>[]) oi.baseType.getEnumConstants();
    if (constants == null) {
      return;
    }

    oi.enumJdoc = new LinkedHashMap<>();

    for (Enum<?> constant : constants) {
      assert oi.enumJdoc != null : "@AssumeAssertion(nullness): bug in flow?";
      oi.enumJdoc.put(constant.name(), "");
    }

    @SuppressWarnings("signature") // non-array non-primitive => Class.getName(): @BinaryName
    @FullyQualifiedName String className = Signatures.binaryNameToFullyQualified(oi.baseType.getName());
    TypeElement enumDoc = denv.getElementUtils().getTypeElement(className);
    if (enumDoc == null) {
      return;
    }

    assert oi.enumJdoc != null : "@AssumeAssertion(nullness): bug in flow?";
    for (String name : oi.enumJdoc.keySet()) {
      for (VariableElement fd : enumConstants(enumDoc)) {
        if (fd.getSimpleName().toString().equals(name)) {
          if (formatJavadoc) {
            oi.enumJdoc.put(name, fd.toString());
          } else {
            oi.enumJdoc.put(name, docCommentToHtml(docTrees.getDocCommentTree(fd)));
          }
          break;
        }
      }
    }
  }

  /**
   * Returns the HTML documentation for the underlying Options instance.
   *
   * @param refillWidth the number of columns to fit the text into, by breaking lines
   * @return the HTML documentation for the underlying Options instance
   */
  public String optionsToHtml(int refillWidth) {
    StringJoiner b = new StringJoiner(lineSep);

    Set<? extends Element> classes = denv.getSpecifiedElements();
    if (includeClassDoc && classes.size() > 0) {
      Element firstElement = classes.iterator().next();
      b.add(OptionsDoclet.docCommentToHtml(docTrees.getDocCommentTree(firstElement)));
      b.add("<p>Command line options:</p>");
    }

    b.add("<ul>");
    if (!options.hasGroups()) {
      b.add(optionListToHtml(options.getOptions(), 6, 2, refillWidth));
    } else {
      for (Options.OptionGroupInfo gi : options.getOptionGroups()) {
        // Do not include groups without publicized options in output
        if (!gi.anyPublicized()) {
          continue;
        }

        String ogroupHeader =
            "  <li id=\"optiongroup:"
                + gi.name.replace(" ", "-").replace("/", "-")
                + "\">"
                + gi.name;
        b.add(refill(ogroupHeader, 6, 2, refillWidth));
        b.add("      <ul>");
        b.add(optionListToHtml(gi.optionList, 12, 8, refillWidth));
        b.add("      </ul>");
        // b.add("  </li>");
      }
    }
    b.add("</ul>");

    for (Options.OptionInfo oi : options.getOptions()) {
      if (oi.list != null && !oi.unpublicized) {
        b.add("");
        b.add(LIST_HELP);
        break;
      }
    }

    return b.toString();
  }

  /**
   * Returns the HTML documentation for the underlying Options instance, formatted as a Javadoc
   * comment.
   *
   * @param padding the number of leading spaces to add in the Javadoc output, before "* "
   * @param refillWidth the number of columns to fit the text into, by breaking lines
   * @return the HTML documentation for the underlying Options instance
   */
  public String optionsToJavadoc(int padding, int refillWidth) {
    StringJoiner b = new StringJoiner(lineSep);
    Scanner s = new Scanner(optionsToHtml(refillWidth - padding - 2));

    while (s.hasNextLine()) {
      String line = s.nextLine();
      StringBuilder bb = new StringBuilder();
      bb.append(StringUtils.repeat(" ", padding));
      if (line.trim().equals("")) {
        bb.append("*");
      } else {
        bb.append("* ").append(line);
      }
      b.add(bb);
    }

    return b.toString();
  }

  /**
   * Returns the HTML describing many options, formatted as an HTML list.
   *
   * @param optList the options to document
   * @param padding the number of leading spaces to add before each line of HTML output, except the
   *     first one
   * @param firstLinePadding the number of leading spaces to add before the first line of HTML
   *     output
   * @param refillWidth the number of columns to fit the text into, by breaking lines
   * @return the options documented in HTML format
   */
  private String optionListToHtml(
      List<Options.OptionInfo> optList, int padding, int firstLinePadding, int refillWidth) {
    StringJoiner b = new StringJoiner(lineSep);
    for (Options.OptionInfo oi : optList) {
      if (oi.unpublicized) {
        continue;
      }
      StringBuilder bb = new StringBuilder();
      String optHtml = optionToHtml(oi, padding);
      bb.append(StringUtils.repeat(" ", padding));
      bb.append("<li id=\"option:" + oi.longName + "\">").append(optHtml);
      // .append("</li>");
      if (refillWidth <= 0) {
        b.add(bb);
      } else {
        b.add(refill(bb.toString(), padding, firstLinePadding, refillWidth));
      }
    }
    return b.toString();
  }

  /**
   * Refill the string so that each line is {@code refillWidth} characters long.
   *
   * @param in the string to refill
   * @param padding each line, other than the first, starts with this many spaces
   * @param firstLinePadding the first line starts with this many spaces
   * @param refillWidth the maximum width of each line in the output, including the padding
   * @return a string in which no more than {@code refillWidth} characters appear between any two
   *     end-of-line character sequences
   */
  private String refill(String in, int padding, int firstLinePadding, int refillWidth) {
    if (refillWidth <= 0) {
      return in;
    }

    // suffix is text *not* to refill.
    String suffix = null;
    int ulPos = in.indexOf(lineSep + "<ul>" + lineSep);
    if (ulPos != -1) {
      @SuppressWarnings(
          "index:argument") // https://github.com/panacekcz/checker-framework/issues/23
      String suffixTemp = in.substring(ulPos + lineSep.length());
      suffix = suffixTemp;
      in = in.substring(0, ulPos);
    }

    String compressedSpaces = in.replaceAll("[ \n\r]+", " ");
    // Accommodate google-java-format bug: https://github.com/google/google-java-format/issues/84 .
    // In general, prefer {@code ...} to <code>...</code>.
    compressedSpaces = compressedSpaces.replaceAll("<code> ", "<code>");
    if (compressedSpaces.startsWith(" ")) {
      compressedSpaces = compressedSpaces.substring(1);
    }
    String oneLine = StringUtils.repeat(" ", firstLinePadding) + compressedSpaces;
    StringJoiner multiLine = new StringJoiner(lineSep);
    while (oneLine.length() > refillWidth) {
      int breakLoc = oneLine.lastIndexOf(' ', refillWidth);
      if (breakLoc == -1) {
        break;
      }
      String firstPart = oneLine.substring(0, breakLoc);
      if (firstPart.trim().isEmpty()) {
        break;
      }
      multiLine.add(firstPart);
      oneLine = StringUtils.repeat(" ", padding) + oneLine.substring(breakLoc + 1);
    }
    multiLine.add(oneLine);
    if (suffix != null) {
      Scanner s = new Scanner(suffix);
      while (s.hasNextLine()) {
        multiLine.add(StringUtils.repeat(" ", padding) + s.nextLine());
      }
    }
    return multiLine.toString();
  }

  /**
   * Returns the line of HTML describing one Option.
   *
   * @param oi the option to describe
   * @param padding the number of spaces to add at the begginning of the detail line (after the line
   *     with the option itself)
   * @return HTML describing oi
   */
  public String optionToHtml(Options.OptionInfo oi, int padding) {
    StringBuilder b = new StringBuilder();
    Formatter f = new Formatter(b);
    if (oi.shortName != null) {
      f.format("<b>-%s</b> ", oi.shortName);
    }
    for (String a : oi.aliases) {
      f.format("<b>%s</b> ", a);
    }
    String prefix = getUseSingleDash() ? "-" : "--";
    f.format("<b>%s%s=</b><i>%s</i>", prefix, oi.longName, oi.typeName);
    if (oi.list != null) {
      b.append(" {@code [+]}");
    }
    f.format(".%n ");
    f.format("%s", StringUtils.repeat(" ", padding));

    String jdoc = ((oi.jdoc == null) ? "" : oi.jdoc);
    if (oi.noDocDefault || oi.defaultStr == null) {
      f.format("%s", jdoc);
    } else {
      String defaultStr = "default: " + oi.defaultStr;
      // The default string must be HTML-escaped since it comes from a string
      // rather than a Javadoc comment.
      String suffix = "";
      if (jdoc.endsWith("</p>")) {
        suffix = "</p>";
        jdoc = jdoc.substring(0, jdoc.length() - suffix.length());
      }
      f.format("%s [%s]%s", jdoc, StringEscapeUtils.escapeHtml4(defaultStr), suffix);
    }
    if (oi.baseType.isEnum()) {
      b.append(lineSep).append("<ul>").append(lineSep);
      assert oi.enumJdoc != null
          : "@AssumeAssertion(nullness): dependent: non-null if oi.baseType is an enum";
      for (Map.Entry<String, String> entry : oi.enumJdoc.entrySet()) {
        b.append("  <li><b>").append(entry.getKey()).append("</b>");
        if (entry.getValue().length() != 0) {
          b.append(" ").append(entry.getValue());
        }
        // b.append("</li>");
        b.append(lineSep);
      }
      b.append("</ul>").append(lineSep);
    }
    return b.toString();
  }

  /**
   * Replace the @link tags and block @see tags in a Javadoc comment with HTML.
   *
   * <p>Currently, the output is non-hyperlinked HTML. This keeps most of the information in the
   * comment while still being presentable. Ideally, @link/@see tags would be converted to HTML
   * links that point to actual documentation.
   *
   * @param docCommentTree a Javadoc comment to convert to HTML
   * @return HTML version of doc
   */
  public static String docCommentToHtml(DocCommentTree docCommentTree) {
    StringBuilder result = new StringBuilder();

    new DocCommentToHtmlVisitor().visitDocComment(docCommentTree, result);
    return result.toString();
  }

  /** Converts DocTree to a HTML string. . */
  static class DocCommentToHtmlVisitor extends SimpleDocTreeVisitor<Void, StringBuilder> {

    /** Create a new DocCommentToHtmlVisitor. */
    public DocCommentToHtmlVisitor() {}

    @Override
    protected Void defaultAction(DocTree node, StringBuilder sb) {
      // The default action does not recurse.  It needs to be overridden for any DocTree whose
      // elements should be investigated (which is most of them!).
      sb.append(node.toString());
      return null;
    }

    /**
     * Visit each element of a list in turn.
     *
     * @param list a list of DocTrees
     * @param sb where to produce output
     */
    void visitList(List<? extends DocTree> list, StringBuilder sb) {
      for (DocTree dt : list) {
        visit(dt, sb);
      }
    }

    // public Void visit(DocTree node, StringBuilder sb)

    // public Void visitAttribute(AttributeTree node, StringBuilder sb)
    // public Void visitAuthor(AuthorTree node, StringBuilder sb)
    // public Void visitComment(CommentTree node, StringBuilder sb)
    // public Void visitDeprecated(DeprecatedTree node, StringBuilder sb)

    @Override
    public Void visitDocComment(DocCommentTree node, StringBuilder sb) {
      visitList(node.getFullBody(), sb);
      return null;
    }

    // public Void visitDocRoot(DocRootTree node, StringBuilder sb)
    // public Void visitDocType(DocTypeTree node, StringBuilder sb)
    // public Void visitEndElement(EndElementTree node, StringBuilder sb)
    // public Void visitEntity(EntityTree node, StringBuilder sb)
    // public Void visitErroneous(ErroneousTree node, StringBuilder sb)
    // public Void visitHidden(HiddenTree node, StringBuilder sb)
    // public Void visitIdentifier(IdentifierTree node, StringBuilder sb)
    // public Void visitIndex(IndexTree node, StringBuilder sb)
    // public Void visitInheritDoc(InheritDocTree node, StringBuilder sb)

    @Override
    public Void visitLink(LinkTree node, StringBuilder sb) {
      List<? extends DocTree> label = node.getLabel();
      if (label.size() > 0) {
        visitList(label, sb);
      } else {
        sb.append("{@code ");
        sb.append(node.getReference().getSignature());
        sb.append("}");
      }
      return null;
    }

    // LiteralTree is for {@code ...} and {@literal ...}.
    @Override
    public Void visitLiteral(LiteralTree node, StringBuilder sb) {
      sb.append("{@code ");
      visitText(node.getBody(), sb);
      sb.append("}");
      return null;
    }

    // public Void visitOther(DocTree node, StringBuilder sb)
    // public Void visitParam(ParamTree node, StringBuilder sb)
    // public Void visitProvides(ProvidesTree node, StringBuilder sb)
    // public Void visitReference(ReferenceTree node, StringBuilder sb)
    // public Void visitReturn(ReturnTree node, StringBuilder sb)

    @Override
    public Void visitSee(SeeTree node, StringBuilder sb) {
      List<? extends DocTree> references = node.getReference();
      for (int i = 0; i < references.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append("<code>");
        visit(references.get(i), sb);
        sb.append("</code>");
      }
      return null;
    }

    // public Void visitSerial(SerialTree node, StringBuilder sb)
    // public Void visitSerialData(SerialDataTree node, StringBuilder sb)
    // public Void visitSerialField(SerialFieldTree node, StringBuilder sb)
    // public Void visitSince(SinceTree node, StringBuilder sb)
    // public Void visitStartElement(StartElementTree node, StringBuilder sb)
    // public Void visitSummary(SummaryTree node, StringBuilder sb)
    // public Void visitText(TextTree node, StringBuilder sb)
    // public Void visitThrows(ThrowsTree node, StringBuilder sb)
    // public Void visitUnknownBlockTag(UnknownBlockTagTree node, StringBuilder sb)
    // public Void visitUnknownInlineTag(UnknownInlineTagTree node, StringBuilder sb)
    // public Void visitUses(UsesTree node, StringBuilder sb)
    // public Void visitValue(ValueTree node, StringBuilder sb)
    // public Void visitVersion(VersionTree node, StringBuilder sb)

  }

  // //////////////////////////////////////////////////////////////////////
  // Signature string methods
  //

  /**
   * Returns true if the given element kind is a type, i.e., a class, enum, interface, or annotation
   * type.
   *
   * @param element the element to test
   * @return true, iff the given kind is a type kind
   */
  public static boolean isTypeElement(Element element) {
    ElementKind elementKind = element.getKind();
    return elementKind.isClass() || elementKind.isInterface();
  }

  // This method is copied from the Checker Framework's ElementUtils class.

  /**
   * Returns the binary name of the given type.
   *
   * @param te a type
   * @return the binary name of the type
   */
  @SuppressWarnings("signature:return") // string manipulation
  public static @BinaryName String getBinaryName(TypeElement te) {
    Element enclosing = te.getEnclosingElement();
    String simpleName = te.getSimpleName().toString();
    if (enclosing == null) { // is this possible?
      return simpleName;
    }
    if (isTypeElement(enclosing)) {
      return getBinaryName((TypeElement) enclosing) + "$" + simpleName;
    } else if (enclosing.getKind() == ElementKind.PACKAGE) {
      PackageElement pe = (PackageElement) enclosing;
      if (pe.isUnnamed()) {
        return simpleName;
      } else {
        return pe.getQualifiedName() + "." + simpleName;
      }
    } else {
      // This case occurs for anonymous inner classes. Fall back to the flatname method.
      // return ((ClassSymbol) te).flatName().toString();
      throw new Error();
    }
  }

  // //////////////////////////////////////////////////////////////////////
  // Getters and Setters
  //

  /**
   * Returns true if the output format is Javadoc, false if the output format is HTML.
   *
   * @return true if the output format is Javadoc, false if the output format is HTML
   */
  public boolean getFormatJavadoc() {
    return formatJavadoc;
  }

  /**
   * Supply true to set the output format to Javadoc, false to set the output format to HTML.
   *
   * @param val true to set the output format to Javadoc, false to set the output format to HTML
   */
  public void setFormatJavadoc(boolean val) {
    if (val && !formatJavadoc) {
      startDelim = "* " + startDelim;
      endDelim = "* " + endDelim;
    } else if (!val && formatJavadoc) {
      startDelim = Strings.CS.removeStart("* ", startDelim);
      endDelim = Strings.CS.removeStart("* ", endDelim);
    }
    this.formatJavadoc = val;
  }

  /**
   * Returns true if using a single dash (as opposed to a double dash) for command-line options.
   *
   * @return true if using a single dash (as opposed to a double dash) for command-line options
   */
  public boolean getUseSingleDash() {
    return options.getUseSingleDash();
  }

  /**
   * See {@link Options#setUseSingleDash(boolean)}.
   *
   * @param val if true, use a single dash (as opposed to a double dash) for command-line options
   */
  public void setUseSingleDash(boolean val) {
    options.setUseSingleDash(true);
  }
}
