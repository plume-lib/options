// The five files
//   Option.java
//   OptionGroup.java
//   Options.java
//   Unpublicized.java
//   OptionsDoclet.java
// together comprise the implementation of command-line processing.

package org.plumelib.options;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.Tag;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringJoiner;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.checkerframework.checker.formatter.qual.Format;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.common.value.qual.MinLen;

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
// because a Doclet is
// required to implement the optionLength() and validOptions() methods.
@SuppressWarnings("deprecation") // JDK 9 deprecates com.sun.javadoc package
public class OptionsDoclet {

  /** The system-specific line separator. */
  private static String lineSep = System.lineSeparator();

  /** How to use the Options doclet. */
  @SuppressWarnings("InlineFormatString")
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

  /** Help message about options that can be specified multiple times. */
  private static final String LIST_HELP =
      "<code>[+]</code> marked option can be specified multiple times";

  /** Marker for start of options documentation. */
  private String startDelim = "<!-- start options doc (DO NOT EDIT BY HAND) -->";
  /** Marker for end of options documentation. */
  private String endDelim = "<!-- end options doc -->";

  /** The file into which options documentation is inserted. */
  private @Nullable File docFile = null;
  /** Destination for output. */
  private @Nullable File outFile = null;

  /** If true, then edit docFile in place (and docFile is non-null). */
  private boolean inPlace = false;
  /** If true, then output format is Javadoc. */
  private boolean formatJavadoc = false;
  /** If true, then include the class's main Javadoc comment. */
  private boolean includeClassDoc = false;

  /** The document root. */
  private RootDoc root;
  /** The command-line options. */
  private Options options;

  /**
   * Create an OptionsDoclet that documents the given options.
   *
   * @param root the document root
   * @param options the command-line options
   */
  public OptionsDoclet(RootDoc root, Options options) {
    this.root = root;
    this.options = options;
  }

  // Doclet-specific methods

  /**
   * Entry point for the doclet.
   *
   * @param root the root document
   * @return true if processing completed without an error
   */
  public static boolean start(RootDoc root) {
    List<Object> objs = new ArrayList<>();
    for (ClassDoc doc : root.specifiedClasses()) {
      // TODO: Class.forName() expects a binary name but doc.qualifiedName()
      // returns a fully qualified name.  I do not know a good way to convert
      // between these two name formats.  For now, we simply ignore inner
      // classes.  This limitation can be removed when we figure out a better
      // way to go from ClassDoc to Class<?>.
      if (doc.containingClass() != null) {
        continue;
      }

      Class<?> clazz;
      try {
        @SuppressWarnings("signature") // Javadoc source code is not yet annotated
        @BinaryName String className = doc.qualifiedName();
        clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        Options.printClassPath();
        return false;
      }

      if (needsInstantiation(clazz)) {
        try {
          Constructor<?> c = clazz.getDeclaredConstructor();
          c.setAccessible(true);
          objs.add(c.newInstance(new Object[0]));
        } catch (Exception e) {
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
    Options options = new Options(objarray);
    if (options.getOptions().size() < 1) {
      System.out.println("Error: no @Option-annotated fields found");
      return false;
    }

    OptionsDoclet o = new OptionsDoclet(root, options);
    String[] @MinLen(1) [] rootOptions = root.options();
    o.setOptions(rootOptions);
    o.processJavadoc();
    try {
      o.write();
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }

    return true;
  }

  /**
   * Given a command-line option of this doclet, returns the number of arguments you must specify on
   * the command line for the given option. Returns 0 if the argument is not recognized. This method
   * is automatically invoked by Javadoc.
   *
   * @param option the command-line option
   * @return the number of command-line arguments needed when using the option
   * @see <a
   *     href="https://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/doclet/overview.html">Doclet
   *     overview</a>
   */
  public static int optionLength(String option) {
    if (option.equals("-help")) {
      System.out.printf(USAGE);
      return 1;
    }
    if (option.equals("-i") || option.equals("-classdoc") || option.equals("-singledash")) {
      return 1;
    }
    if (option.equals("-docfile")
        || option.equals("-outfile")
        || option.equals("-format")
        || option.equals("-d")) {
      return 2;
    }
    return 0;
  }

  /**
   * Tests the validity of command-line arguments passed to this doclet. Returns true if the option
   * usage is valid, and false otherwise. This method is automatically invoked by Javadoc.
   *
   * <p>Also sets fields from the command-line arguments.
   *
   * @param options the command-line options to be checked: an array of 1- or 2-element arrays,
   *     where the length depends on {@link #optionLength} applied to the first element
   * @param reporter where to report errors
   * @return true iff the command-line options are valid
   * @see <a
   *     href="https://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/doclet/overview.html">Doclet
   *     overview</a>
   */
  // @SuppressWarnings("index") // dependent: os[1] is legal when optionLength(os[0])==2
  public static boolean validOptions(String[] @MinLen(1) [] options, DocErrorReporter reporter) {
    boolean hasDocFile = false;
    boolean hasOutFile = false;
    boolean hasDestDir = false;
    boolean hasFormat = false;
    boolean inPlace = false;
    String docFile = null;
    String outFile = null;
    for (int oi = 0; oi < options.length; oi++) {
      String[] os = options[oi];
      String opt = os[0].toLowerCase();
      if (opt.equals("-docfile")) {
        if (hasDocFile) {
          reporter.printError("-docfile option specified twice");
          return false;
        }
        assert os.length == 2 : "@AssumeAssertion(value): dependent: optionLength(\"docfile\")==2";
        docFile = os[1];
        File f = new File(docFile);
        if (!f.exists()) {
          reporter.printError("-docfile file not found: " + docFile);
          return false;
        }
        hasDocFile = true;
      }
      if (opt.equals("-outfile")) {
        if (hasOutFile) {
          reporter.printError("-outfile option specified twice");
          return false;
        }
        if (inPlace) {
          reporter.printError("-i and -outfile can not be used at the same time");
          return false;
        }
        assert os.length == 2 : "@AssumeAssertion(value): dependent: optionLength(\"outfile\")==2";
        outFile = os[1];
        hasOutFile = true;
      }
      if (opt.equals("-i")) {
        if (hasOutFile) {
          reporter.printError("-i and -outfile can not be used at the same time");
          return false;
        }
        inPlace = true;
      }
      if (opt.equals("-format")) {
        if (hasFormat) {
          reporter.printError("-format option specified twice");
          return false;
        }
        assert os.length == 2 : "@AssumeAssertion(value): dependent: optionLength(\"format\")==2";
        String format = os[1];
        if (!format.equals("javadoc") && !format.equals("html")) {
          reporter.printError("unrecognized output format: " + format);
          return false;
        }
        hasFormat = true;
      }
      if (opt.equals("-d")) {
        if (hasDestDir) {
          reporter.printError("-d specified twice");
          return false;
        }
        hasDestDir = true;
      }
    }
    if (docFile != null && outFile != null && outFile.equals(docFile)) {
      reporter.printError("docfile must be different from outfile");
      return false;
    }
    if (inPlace && docFile == null) {
      reporter.printError("-i supplied but -docfile was not");
      return false;
    }
    return true;
  }

  /**
   * Set the underlying Options instance for this class based on command-line arguments given by
   * RootDoc.options().
   *
   * @param options the command-line options to parse: a list of 1- or 2-element arrays
   */
  // @SuppressWarnings("index") // dependent: os[1] is legal when optionLength(os[0])==2
  public void setOptions(String[] @MinLen(1) [] options) {
    String outFilename = null;
    File destDir = null;
    for (int oi = 0; oi < options.length; oi++) {
      String[] os = options[oi];
      String opt = os[0].toLowerCase();
      if (opt.equals("-docfile")) {
        assert os.length == 2 : "@AssumeAssertion(value): dependent: optionLength(\"docfile\")==2";
        this.docFile = new File(os[1]);
      } else if (opt.equals("-d")) {
        assert os.length == 2 : "@AssumeAssertion(value): dependent: optionLength(\"d\")==2";
        destDir = new File(os[1]);
      } else if (opt.equals("-outfile")) {
        assert os.length == 2 : "@AssumeAssertion(value): dependent: optionLength(\"outfile\")==2";
        outFilename = os[1];
      } else if (opt.equals("-i")) {
        this.inPlace = true;
      } else if (opt.equals("-format")) {
        assert os.length == 2 : "@AssumeAssertion(value): dependent: optionLength(\"format\")==2";
        if (os[1].equals("javadoc")) {
          setFormatJavadoc(true);
        }
      } else if (opt.equals("-classdoc")) {
        this.includeClassDoc = true;
      } else if (opt.equals("-singledash")) {
        setUseSingleDash(true);
      }
    }
    if (outFilename != null) {
      if (destDir != null) {
        this.outFile = new File(destDir, outFilename);
      } else {
        this.outFile = new File(outFilename);
      }
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
      if (f.isAnnotationPresent(Option.class) && !Modifier.isStatic(f.getModifiers())) {
        return true;
      }
    }
    return false;
  }

  // File IO methods

  /**
   * Write the output of this doclet to the correct file.
   *
   * @throws Exception if there is trouble
   */
  public void write() throws Exception {
    PrintWriter out;
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
   * Get the final output of this doclet. The string returned by this method is the output seen by
   * the user.
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
   * Get the result of inserting the options documentation into the docfile.
   *
   * @return the docfile, but with the command-line argument documentation updated
   * @throws Exception if there is trouble reading files
   */
  @RequiresNonNull("docFile")
  private String newDocFileText() throws Exception {
    StringJoiner b = new StringJoiner(lineSep);
    BufferedReader doc = Files.newBufferedReader(docFile.toPath(), UTF_8);
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

    doc.close();
    return b.toString();
  }

  // HTML and Javadoc processing methods

  /** Adds Javadoc info to each option in {@code options.getOptions()}. */
  public void processJavadoc() {
    for (Options.OptionInfo oi : options.getOptions()) {
      ClassDoc optDoc = root.classNamed(oi.getDeclaringClass().getName());
      if (optDoc != null) {
        String nameWithUnderscores = oi.longName.replace('-', '_');
        for (FieldDoc fd : optDoc.fields()) {
          if (fd.name().equals(nameWithUnderscores)) {
            // If Javadoc for field is unavailable, then use the @Option
            // description in the documentation.
            if (fd.getRawCommentText().length() == 0) {
              // Input is a string rather than a Javadoc (HTML) comment so we
              // must escape it.
              oi.jdoc = StringEscapeUtils.escapeHtml4(oi.description);
            } else if (formatJavadoc) {
              oi.jdoc = fd.commentText();
            } else {
              oi.jdoc = javadocToHtml(fd);
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
   * mapping from enum constants to their Javadoc
   *
   * @param oi the enum option whose Javadoc to read
   */
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

    ClassDoc enumDoc = root.classNamed(oi.baseType.getName());
    if (enumDoc == null) {
      return;
    }

    assert oi.enumJdoc != null : "@AssumeAssertion(nullness): bug in flow?";
    for (String name : oi.enumJdoc.keySet()) {
      for (FieldDoc fd : enumDoc.fields()) {
        if (fd.name().equals(name)) {
          if (formatJavadoc) {
            oi.enumJdoc.put(name, fd.commentText());
          } else {
            oi.enumJdoc.put(name, javadocToHtml(fd));
          }
          break;
        }
      }
    }
  }

  /**
   * Get the HTML documentation for the underlying Options instance.
   *
   * @param refillWidth the number of columns to fit the text into, by breaking lines
   * @return the HTML documentation for the underlying Options instance
   */
  public String optionsToHtml(int refillWidth) {
    StringJoiner b = new StringJoiner(lineSep);

    if (includeClassDoc && root.classes().length > 0) {
      b.add(OptionsDoclet.javadocToHtml(root.classes()[0]));
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
   * Get the HTML documentation for the underlying Options instance, formatted as a Javadoc comment.
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
   * Get the HTML describing many options, formatted as an HTML list.
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
          "index:argument.type.incompatible") // https://github.com/panacekcz/checker-framework/issues/23
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
   * Get the line of HTML describing one Option.
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
      b.append(" <code>[+]</code>");
    }
    f.format(".%n ");
    f.format("%s", StringUtils.repeat(" ", padding));

    String jdoc = ((oi.jdoc == null) ? "" : oi.jdoc);
    if (oi.noDocDefault || oi.defaultStr == null) {
      f.format("%s", jdoc);
    } else {
      String defaultStr = "default " + oi.defaultStr;
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
   * @param doc a Javadoc comment to convert to HTML
   * @return HTML version of doc
   */
  public static String javadocToHtml(Doc doc) {
    StringBuilder b = new StringBuilder();
    Tag[] tags = doc.inlineTags();
    for (Tag tag : tags) {
      String kind = tag.kind();
      String text = tag.text();
      if (tag instanceof SeeTag) {
        b.append("<code>" + text.replace('#', '.') + "</code>");
      } else {
        if (kind.equals("@code")) {
          b.append("<code>" + StringEscapeUtils.escapeHtml4(text) + "</code>");
        } else {
          b.append(text);
        }
      }
    }
    SeeTag[] seetags = doc.seeTags();
    if (seetags.length > 0) {
      b.append(" See: ");
      {
        StringJoiner bb = new StringJoiner(", ");
        for (SeeTag tag : seetags) {
          bb.add("<code>" + tag.text() + "</code>");
        }
        b.append(bb);
      }
      b.append(".");
    }
    return b.toString();
  }

  // Getters and Setters

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
      startDelim = StringUtils.removeStart("* ", startDelim);
      endDelim = StringUtils.removeStart("* ", endDelim);
    }
    this.formatJavadoc = val;
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
}
