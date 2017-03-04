// The five files
//   Option.java
//   OptionGroup.java
//   Options.java
//   Unpublicized.java
//   OptionsDoclet.java
// together comprise the implementation of command-line processing.

package org.plumelib.options;

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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringJoiner;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

/*>>>
import org.checkerframework.checker.formatter.qual.*;
import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.checker.signature.qual.*;
*/

/**
 * Generates HTML documentation of command-line options. Works with the {@link plume.Options} class.
 *
 * <p><b>Usage</b>
 *
 * <p>This doclet is typically invoked with:
 *
 * <pre>javadoc -quiet -doclet plume.OptionsDoclet [doclet options] [java files]</pre>
 *
 * <p><b>Doclet Options</b>
 *
 * <p>The following doclet options are supported:
 *
 * <ul>
 *   <li> <b>-docfile</b> <i>file</i> When specified, the output of this doclet is the result of
 *       replacing everything between the two lines
 *       <pre>&lt;!-- start options doc (DO NOT EDIT BY HAND) --&gt;</pre>
 *       and
 *       <pre>&lt;!-- end options doc --&gt;</pre>
 *       in <i>file</i> with the options documentation. This can be used for inserting option
 *       documentation into an existing manual. The existing docfile is not modified; output goes to
 *       the <code>-outfile</code> argument, or to standard out.
 *   <li> <b>-outfile</b> <i>file</i> The destination for the output (the default is standard out).
 *       If both <code>-outfile</code> and <code>-docfile</code> are specified, they must be
 *       different. When <code>-d</code> is used, the output is written to a file with the given
 *       name relative to that destination directory.
 *   <li> <b>-d</b> <i>directory</i> The destination directory for the output file. Only used if
 *       <code>-outfile</code> is used, in which case, the file is written in this directory.
 *       Otherwise, this option is ignored.
 *   <li> <b>-i</b> Specifies that the docfile should be edited in-place. This option can only be
 *       used if the <code>-docfile</code> option is used, and may not be used at the same time as
 *       the <code>-outfile</code> option.
 *   <li> <b>-format</b> <i>format</i> This option sets the output format of this doclet. Currently,
 *       the following values for <i>format</i> are supported:
 *       <ul>
 *         <li> <b>javadoc</b> When this format is specified, the output of this doclet is formatted
 *             as a Javadoc comment. This is useful for including option documentation inside Java
 *             source code. When this format is used with the <code>-docfile</code> option, the
 *             generated documentation is inserted between the lines
 *             <pre>* &lt;!-- start options doc (DO NOT EDIT BY HAND) --&gt;</pre>
 *             and
 *             <pre>* &lt;!-- end options doc --&gt;</pre>
 *             using the same indentation. Inline {@code @link} and {@code @see} tags in the Javadoc
 *             input are left untouched.
 *         <li> <b>html</b> This format outputs HTML for general-purpose use, meaning inline
 *             {@code @link} and {@code @see} tags in the Javadoc input are suitably replaced. This
 *             is the default output format and does not need to be specified explicitly.
 *       </ul>
 *
 *   <li> <b>-classdoc</b> When specified, the output of this doclet includes the class
 *       documentation of the first class specified on the command-line.
 *   <li> <b>-singledash</b> When specified, <code>use_single_dash(true)</code> is called on the
 *       underlying instance of Options used to generate documentation. See {@link
 *       plume.Options#use_single_dash(boolean)}.
 * </ul>
 *
 * <p><b>Examples</b>
 *
 * <p>To update the Javarifier HTML manual with option documentation run:
 *
 * <pre>
 * javadoc -quiet -doclet plume.OptionsDoclet -i -docfile javarifier.html src/javarifier/Main.java
 * </pre>
 *
 * <p>To update the class Javadoc for plume.Lookup with option documentation run:
 *
 * <pre>
 * javadoc -quiet -doclet plume.OptionsDoclet -i -docfile Lookup.java -format javadoc Lookup.java
 * </pre>
 *
 * <p>For a more extensive example, see file <code>java/Makefile</code> in plume-lib itself.
 *
 * <p><b>Requirements</b>
 *
 * <p>Classes passed to OptionsDoclet that have <code>@</code>{@link Option} annotations on
 * non-static fields should have a nullary (no-argument) constructor. The nullary constructor may be
 * private or public. This is required because an object instance is needed to get the default value
 * of a non-static field. It is cleaner to require a nullary constructor instead of trying to guess
 * arguments to pass to another constructor.
 *
 * <p><b>Hiding default value strings</b>
 *
 * <p>By default, the documentation generated by OptionsDoclet includes a default value string for
 * each option in square brackets after the option's description, similar to the usage messages
 * generated by {@link plume.Options#usage(String...)}. The {@link plume.Option#noDocDefault} field
 * in the {@code @Option} annotation can be set to <code>true</code> to omit the default value
 * string from the generated documentation for that option.
 *
 * <p>Omitting the generated default value string is useful for options that have system-dependent
 * defaults. Such options are not an issue for usage messages that are generated at runtime.
 * However, system dependent defaults do pose a problem for static documentation, which is rarely
 * regenerated and meant to apply to all users. Consider the following {@code @Option}-annotated
 * field:
 *
 * <pre>
 * &#64;Option(value="&lt;timezone&gt; Set the time zone")
 * public static String timezone = TimeZone.getDefault().getID();</pre>
 *
 * The default value for <code>timezone</code> depends on the system's timezone setting. HTML
 * documentation of this option generated in Chicago would not apply to a user in New York. To work
 * around this problem, the default value should be hidden; instead the Javadoc for this field
 * should indicate a special default as follows.
 *
 * <pre>
 * &#47;**
 *  * &lt;other stuff...&gt;  This option defaults to the system timezone.
 *  *&#47;
 * &#64;Option(value="&lt;timezone&gt; Set the timezone", noDocDefault=true)
 * public static String timezone = TimeZone.getDefault().getID();</pre>
 *
 * This keeps the documentation system-agnostic.
 *
 * <p><b>Caveats</b>
 *
 * <p>The generated HTML documentation includes unpublicized option groups but not
 * {@code @Unpublicized} options. Option groups that contain only {@code @Unpublicized} options are
 * not included in the output at all.
 *
 * <p><b>Troubleshooting</b>
 *
 * <p>If you get an error such as "<code>ARGH! @Option</code>", then you are using a buggy version
 * of gjdoc, the GNU Classpath implementation of Javadoc. To avoid the problem, upgrade or use a
 * different Javadoc implementation.
 *
 * @see plume.Option
 * @see plume.Options
 * @see plume.OptionGroup
 * @see plume.Unpublicized
 */

// This doesn't itself use plume.Options for its command-line option
// processing because a Doclet is required to implement the optionLength
// and validOptions methods.
public class OptionsDoclet {

  private static String eol = System.getProperty("line.separator");

  private static final /*@Format({})*/ String USAGE =
      "Provided by Options doclet:%n"
          + "-docfile <file>        Specify file into which options documentation is inserted%n"
          + "-outfile <file>        Specify destination for resulting output%n"
          + "-d <directory>         Destination directory for -outfile%n"
          + "-i                     Edit the docfile in-place%n"
          + "-format javadoc        Format output as a Javadoc comment%n"
          + "-classdoc              Include 'main' class documentation in output%n"
          + "-singledash            Use single dashes for long options (see plume.Options)%n"
          + "See the OptionsDoclet documentation for more details.%n";

  private static final String LIST_HELP =
      "<code>[+]</code> marked option can be specified multiple times";

  private String startDelim = "<!-- start options doc (DO NOT EDIT BY HAND) -->";
  private String endDelim = "<!-- end options doc -->";

  private /*@Nullable*/ File docFile = null;
  private /*@Nullable*/ File outFile = null;

  /** If true, then edit docFile in place (and docFile is non-null). */
  private boolean inPlace = false;
  /** If true, then output format is Javadoc. */
  private boolean formatJavadoc = false;
  /** If true, then include the class's main Javadoc comment. */
  private boolean includeClassDoc = false;

  private RootDoc root;
  private Options options;

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
    List<Object> objs = new ArrayList<Object>();
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
        /*@BinaryNameForNonArray*/ String className = doc.qualifiedName();
        clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
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
    o.setOptions(root.options());
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
   * is automatically invoked.
   *
   * @param option the command-line option
   * @return the number of command-line arguments needed when using the option
   * @see <a
   *     href="http://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/doclet/overview.html">Doclet
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
   * usage is valid, and false otherwise. This method is automatically invoked.
   *
   * @param options the command-line options to be checked: an array of 1- or 2-element arrays
   * @param reporter where to report errors
   * @return true iff the command-line options are valid
   * @see <a
   *     href="http://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/doclet/overview.html">Doclet
   *     overview</a>
   */
  public static boolean validOptions(String[][] options, DocErrorReporter reporter) {
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
   * Set the options for this class based on command-line arguments given by RootDoc.options().
   *
   * @param options the command-line options to parse: a list of 1- or 2-element arrays
   */
  public void setOptions(String[][] options) {
    String outFilename = null;
    File destDir = null;
    for (int oi = 0; oi < options.length; oi++) {
      String[] os = options[oi];
      String opt = os[0].toLowerCase();
      if (opt.equals("-docfile")) {
        this.docFile = new File(os[1]);
      } else if (opt.equals("-d")) {
        destDir = new File(os[1]);
      } else if (opt.equals("-outfile")) {
        outFilename = os[1];
      } else if (opt.equals("-i")) {
        this.inPlace = true;
      } else if (opt.equals("-format")) {
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
      out = new PrintWriter(new BufferedWriter(new FileWriter(outFile)));
    } else if (inPlace) {
      assert docFile != null
          : "@AssumeAssertion(nullness): dependent: docFile is non-null if inPlace is true";
      out = new PrintWriter(new BufferedWriter(new FileWriter(docFile)));
    } else {
      out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));
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

  /** Get the result of inserting the options documentation into the docfile. */
  /*@RequiresNonNull("docFile")*/
  private String newDocFileText() throws Exception {
    StringJoiner b = new StringJoiner(eol);
    BufferedReader doc = new BufferedReader(new FileReader(docFile));
    String docline;
    boolean replacing = false;
    boolean replaced_once = false;
    String prefix = null;

    while ((docline = doc.readLine()) != null) {
      if (replacing) {
        if (docline.trim().equals(endDelim)) {
          replacing = false;
        } else {
          continue;
        }
      }

      b.add(docline);

      if (!replaced_once && docline.trim().equals(startDelim)) {
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
        replaced_once = true;
        replacing = true;
      }
    }

    doc.close();
    return b.toString();
  }

  // HTML and Javadoc processing methods

  /** Side-effects each option in {@code options.getOptions()}. Adds Javadoc info to it. */
  public void processJavadoc() {
    for (Options.OptionInfo oi : options.getOptions()) {
      ClassDoc opt_doc = root.classNamed(oi.get_declaring_class().getName());
      if (opt_doc != null) {
        String nameWithUnderscores = oi.long_name.replace('-', '_');
        for (FieldDoc fd : opt_doc.fields()) {
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
      if (oi.base_type.isEnum()) {
        processEnumJavadoc(oi);
      }
    }
  }

  /** Initializes {@link Options.OptionInfo.enum_jdoc} for the given <code>OptionInfo</code>. */
  private void processEnumJavadoc(Options.OptionInfo oi) {
    Enum<?>[] constants = (Enum<?>[]) oi.base_type.getEnumConstants();
    if (constants == null) {
      return;
    }

    oi.enum_jdoc = new LinkedHashMap<String, String>();

    for (Enum<?> constant : constants) {
      assert oi.enum_jdoc != null : "@AssumeAssertion(nullness): bug in flow?";
      oi.enum_jdoc.put(constant.name(), "");
    }

    ClassDoc enum_doc = root.classNamed(oi.base_type.getName());
    if (enum_doc == null) {
      return;
    }

    assert oi.enum_jdoc != null : "@AssumeAssertion(nullness): bug in flow?";
    for (String name : oi.enum_jdoc.keySet()) {
      for (FieldDoc fd : enum_doc.fields()) {
        if (fd.name().equals(name)) {
          if (formatJavadoc) {
            oi.enum_jdoc.put(name, fd.commentText());
          } else {
            oi.enum_jdoc.put(name, javadocToHtml(fd));
          }
          break;
        }
      }
    }
  }

  /**
   * Get the HTML documentation for the underlying options instance.
   *
   * @param refillWidth the number of columns to fit the text into, by breaking lines
   * @return the HTML documentation for the underlying options instance
   */
  public String optionsToHtml(int refillWidth) {
    StringJoiner b = new StringJoiner(eol);

    if (includeClassDoc && root.classes().length > 0) {
      b.add(OptionsDoclet.javadocToHtml(root.classes()[0]));
      b.add("<p>Command line options:</p>");
    }

    b.add("<ul>");
    if (!options.isUsingGroups()) {
      b.add(optionListToHtml(options.getOptions(), 6, 2, refillWidth));
    } else {
      for (Options.OptionGroupInfo gi : options.getOptionGroups()) {
        // Do not include groups without publicized options in output
        if (!gi.any_publicized()) {
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
        b.add("");
        // b.add("  </li>");
      }
    }
    b.add("</ul>");
    b.add("");

    for (Options.OptionInfo oi : options.getOptions()) {
      if (oi.list != null && !oi.unpublicized) {
        b.add(LIST_HELP);
        break;
      }
    }

    return b.toString();
  }

  /**
   * Get the HTML documentation for the underlying options instance, formatted as a Javadoc comment.
   *
   * @param padding the number of leading spaces to add in the Javadoc output, before "* "
   * @param refillWidth the number of columns to fit the text into, by breaking lines
   * @return the HTML documentation for the underlying options instance
   */
  public String optionsToJavadoc(int padding, int refillWidth) {
    StringJoiner b = new StringJoiner(eol);
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

  /** Get the HTML describing many options, formatted as an HTML list. */
  private String optionListToHtml(
      List<Options.OptionInfo> opt_list, int padding, int firstLinePadding, int refillWidth) {
    StringJoiner b = new StringJoiner(eol);
    for (Options.OptionInfo oi : opt_list) {
      if (oi.unpublicized) {
        continue;
      }
      StringBuilder bb = new StringBuilder();
      String optHtml = optionToHtml(oi, padding);
      bb.append(StringUtils.repeat(" ", padding));
      bb.append("<li id=\"option:" + oi.long_name + "\">").append(optHtml);
      // .append("</li>");
      if (refillWidth <= 0) {
        b.add(bb);
      } else {
        b.add(refill(bb.toString(), padding, firstLinePadding, refillWidth));
      }
    }
    return b.toString();
  }

  /** refillWidth includes the padding. */
  private String refill(String in, int padding, int firstLinePadding, int refillWidth) {
    if (refillWidth <= 0) {
      return in;
    }

    // suffix is text *not* to refill.
    String suffix = null;
    int ulPos = in.indexOf(eol + "<ul>" + eol);
    if (ulPos != -1) {
      suffix = in.substring(ulPos + eol.length());
      in = in.substring(0, ulPos);
    }

    String compressedSpaces = in.replaceAll("[ \n\r]+", " ");
    // google-java-format bug: https://github.com/google/google-java-format/issues/84
    compressedSpaces = compressedSpaces.replaceAll("<code> ", "<code>");
    if (compressedSpaces.startsWith(" ")) {
      compressedSpaces = compressedSpaces.substring(1);
    }
    String oneLine = StringUtils.repeat(" ", firstLinePadding) + compressedSpaces;
    StringJoiner multiLine = new StringJoiner(eol);
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
   * Get the line of HTML describing an Option.
   *
   * @param oi the option to describe
   * @param padding the number of spaces to add at the begginning of the detail line (after the line
   *     with the option itself)
   * @return HTML describing oi
   */
  public String optionToHtml(Options.OptionInfo oi, int padding) {
    StringBuilder b = new StringBuilder();
    Formatter f = new Formatter(b);
    if (oi.short_name != null) {
      f.format("<b>-%s</b> ", oi.short_name);
    }
    for (String a : oi.aliases) {
      f.format("<b>%s</b> ", a);
    }
    String prefix = getUseSingleDash() ? "-" : "--";
    f.format("<b>%s%s=</b><i>%s</i>", prefix, oi.long_name, oi.type_name);
    if (oi.list != null) {
      b.append(" <code>[+]</code>");
    }
    f.format(".%n ");
    f.format("%s", StringUtils.repeat(" ", padding));

    String jdoc = ((oi.jdoc == null) ? "" : oi.jdoc);
    if (oi.no_doc_default || oi.default_str == null) {
      f.format("%s", jdoc);
    } else {
      String default_str = "default " + oi.default_str;
      // The default string must be HTML-escaped since it comes from a string
      // rather than a Javadoc comment.
      String suffix = "";
      if (jdoc.endsWith("</p>")) {
        suffix = "</p>";
        jdoc = jdoc.substring(0, jdoc.length() - suffix.length());
      }
      f.format("%s [%s]%s", jdoc, StringEscapeUtils.escapeHtml4(default_str), suffix);
    }
    if (oi.base_type.isEnum()) {
      b.append(eol).append("<ul>").append(eol);
      assert oi.enum_jdoc != null
          : "@AssumeAssertion(nullness): dependent: non-null if oi.base_type is an enum";
      for (Map.Entry<String, String> entry : oi.enum_jdoc.entrySet()) {
        b.append("  <li><b>").append(entry.getKey()).append("</b>");
        if (entry.getValue().length() != 0) {
          b.append(" ").append(entry.getValue());
        }
        // b.append("</li>");
        b.append(eol);
      }
      b.append("</ul>").append(eol).append(eol);
    }
    return b.toString();
  }

  /**
   * Replace the @link tags and block @see tags in a Javadoc comment with sensible, non-hyperlinked
   * HTML. This keeps most of the information in the comment while still being presentable.
   *
   * <p>This is only a temporary solution. Ideally, @link/@see tags would be converted to HTML links
   * that point to actual documentation.
   *
   * @param doc a Javadoc comment to convert to HTML
   * @return HTML version of doc
   */
  public static String javadocToHtml(Doc doc) {
    StringBuilder b = new StringBuilder();
    Tag[] tags = doc.inlineTags();
    for (Tag tag : tags) {
      String text = tag.text().replace('#', '.'); // is this replacement always desirable?
      if (tag instanceof SeeTag) {
        b.append("<code>" + text + "</code>");
      } else {
        b.append(text);
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

  public boolean getFormatJavadoc() {
    return formatJavadoc;
  }

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

  public boolean getUseSingleDash() {
    return options.isUsingSingleDash();
  }

  public void setUseSingleDash(boolean val) {
    options.use_single_dash(true);
  }
}
