package org.plumelib.options;

import static org.plumelib.options.Options.ArgException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOptions {

  /** Test class for Options testing. */
  public static class ClassWithOptions {

    // TODO: test the @Unpublicized annotation and the usage() message.

    @Option("list of patterns")
    public List<Pattern> lp = new ArrayList<>();

    @Option("-a <filename> argument 1")
    public String arg1 = "/tmp/foobar";

    @Option("argument 2")
    public @Nullable String arg2;

    @Option("-d double value")
    public double temperature;

    @Option("-f the input file")
    public @Nullable File input_file;

    @Option("-g the input file")
    public @Nullable Path input_path;

    @Option("-b boolean")
    public boolean bool;

    @Option("-i Integer")
    public @Nullable Integer integer_reference;

    @Option("list of doubles")
    public List<Double> ld = new ArrayList<>();

    @Option("list with no default")
    public @Nullable List<String> ls;
  }

  /**
   * Test command line option parsing (Options).
   *
   * @throws ArgException if there is an illegal argument
   */
  @Test
  public void testOptions() throws ArgException, IOException {

    new File("/tmp/TestOptions1.txt").createNewFile();
    new File("/tmp/TestOptions2.txt").createNewFile();

    ClassWithOptions t = new ClassWithOptions();
    Options options = new Options("test", t);
    try {
      options.parse(
          new String[] {
            "--lp=foo",
            "--lp",
            "bar",
            "-i",
            "24",
            "-d=37.8",
            "-b",
            "-b=false",
            "--ld",
            "34.6",
            "--ld",
            "17.8",
            "--input-file",
            "/tmp/TestOptions1.txt",
            "--input-path",
            "/tmp/TestOptions2.txt",
          });
    } catch (ArgException e) {
      System.out.println(e);
      throw e;
    }

    assert t.lp.get(0).toString().equals("foo");
    assert t.lp.get(1).toString().equals("bar");
    assert t.integer_reference != null && t.integer_reference.intValue() == 24;
    assert t.temperature == 37.8;
    assert t.bool == false;
    assert t.ld.get(0).doubleValue() == 34.6;
    assert t.ld.get(1).doubleValue() == 17.8;

    // Test non-options
    t.bool = false;
    String[] args = options.parse(new String[] {"one", "two", "three", "-b"});
    assert args.length == 3 : "@AssumeAssertion(value)";
    assert args[0].equals("one") : args[0];
    assert args[1].equals("two") : args[1];
    assert args[2].equals("three") : args[2];
    assert t.bool;

    // Test --
    t.bool = false;
    args = options.parse(new String[] {"--", "one", "two", "-b"});
    assert args.length == 3 : "@AssumeAssertion(value)";
    assert args[0].equals("one") : args[0];
    assert args[1].equals("two") : args[1];
    assert args[2].equals("-b") : args[2];
    assert !t.bool;

    // Test spaceSeparatedLists
    t.ld.clear();
    Options.spaceSeparatedLists = true;
    args = options.parse(new String[] {"--ld", "42.1 9.3 10.5", "--ld", "2.7"});
    assert args.length == 0 : "@AssumeAssertion(value)";
    assert t.ld.size() == 4;
    assert t.ld.get(0).doubleValue() == 42.1;
    assert t.ld.get(1).doubleValue() == 9.3;
    assert t.ld.get(2).doubleValue() == 10.5;
    assert t.ld.get(3).doubleValue() == 2.7;

    // Test list with no default
    args = options.parse(new String[] {"--ls", "hello", "--ls", "world"});
    assert args.length == 0 : "@AssumeAssertion(value)";
    assert t.ls != null
        : "@AssumeAssertion(nullness): application invariant: parsed string with --ls just above";
    assert t.ls.size() == 2;
    assert t.ls.get(0).equals("hello");
    assert t.ls.get(1).equals("world");

    // Test files and paths
    assert t.input_file != null
        : "@AssumeAssertion(nullness): application invariant: parsed string with --input-file";
    assert t.input_file.exists();
    assert t.input_file.getName().equals("TestOptions1.txt");
    assert t.input_path != null
        : "@AssumeAssertion(nullness): application invariant: parsed string with --input-path";
    assert t.input_path.toFile().exists();
    assert t.input_path != null
        : "@AssumeAssertion(nullness): annotated JDK doesn't contain toFile() yet";
    assert t.input_path.toString().equals("/tmp/TestOptions2.txt");
    ;
  }

  /** Test class for option alias testing. */
  public static class ClassWithOptionsAliases {
    @Option("-d Set the day")
    public String day = "Friday";

    @Option(
        value = "-t Set the temperature",
        aliases = {"-temp"})
    public double temperature = 42.0;

    @Option(
        value = "Print the program version",
        aliases = {"-v", "-version", "--version"})
    public boolean printVersion = false;
  }

  /**
   * Test option aliases (Options).
   *
   * @throws ArgException if there is an illegal argument
   */
  @Test
  public void testOptionsAliases() throws ArgException {
    ClassWithOptionsAliases t = new ClassWithOptionsAliases();
    Options options = new Options("test", t);

    options.parse(new String[] {"-d", "Monday", "-temp", "-12.3"});
    assert t.day.equals("Monday");
    assert t.temperature == -12.3;
    assert !t.printVersion;

    options.parse(Options.tokenize("-d Monday -temp -12.3"));
    assert t.day.equals("Monday");
    assert t.temperature == -12.3;
    assert !t.printVersion;

    options.parse(new String[] {"-t", "21.7", "-version"});
    assert t.day.equals("Monday");
    assert t.temperature == 21.7;
    assert t.printVersion;

    options.parse(Options.tokenize("-t 21.7 -version"));
    assert t.day.equals("Monday");
    assert t.temperature == 21.7;
    assert t.printVersion;

    t.printVersion = false;
    options.parse(new String[] {"--version", "-temp=-60.1", "--day", "Tuesday"});
    assert t.day.equals("Tuesday");
    assert t.temperature == -60.1;
    assert t.printVersion;

    t.printVersion = false;
    options.parse(Options.tokenize("--version -temp=-60.1 --day Tuesday"));
    assert t.day.equals("Tuesday");
    assert t.temperature == -60.1;
    assert t.printVersion;
  }

  /** Test class for testing option groups. */
  public static class TestOptionGroups1 {
    @Option("-m Set the mass")
    public static int mass;

    @OptionGroup("Coordinate options")
    @Option("-x Set the X coordinate")
    public static int x;

    @Option("-y Set the Y coordinate")
    public static int y;

    @Option("-z Set the Z coordinate")
    public static int z;
  }

  /** Test class for testing option groups. */
  public static class TestOptionGroups2 {
    @OptionGroup("General options")
    @Option(
        value = "-h Display help message",
        aliases = {"-help"})
    public static boolean help = false;

    @OptionGroup(value = "Internal options", unpublicized = true)
    @Option("Set mu")
    public static double mu = 4902.7;

    @Unpublicized
    @Option("Set pi")
    public static double pi = 3.14;

    @OptionGroup("Display options")
    @Option(
        value = "Use colors",
        aliases = {"--colour"})
    public static boolean color = false;
  }

  /** Test class for testing option groups. */
  public static class TestOptionGroups3 {
    @OptionGroup("General options")
    @Option(
        value = "-h Display help message",
        aliases = {"-help"})
    public static boolean help = false;

    @OptionGroup("Internal options")
    @Unpublicized
    @Option("Set mu")
    public static double mu = 4902.7;

    @Unpublicized
    @Option("Set pi")
    public static double pi = 3.14;

    @OptionGroup("Display options")
    @Option(
        value = "Use colors",
        aliases = {"--colour"})
    public static boolean color = false;
  }

  /**
   * Test option groups (Options).
   *
   * @throws ArgException if there is an illegal argument
   */
  @Test
  public void testOptionGroups() throws ArgException {
    // TODO: The following two exception tests are not adequate.  There must be
    // a better way to do these.
    try {
      new Options("test", TestOptionGroups1.class);
    } catch (Error e) {
      assert e.getMessage() != null
          && e.getMessage()
                  .indexOf("missing @OptionGroup annotation on the first @Option-annotated field")
              > -1;
    }

    try {
      new Options("test", TestOptionGroups2.class, TestOptionGroups1.class);
    } catch (Error e) {
      assert e.getMessage() != null
          && e.getMessage().indexOf("missing @OptionGroup annotation in field") > -1;
    }

    Options options = new Options("test", TestOptionGroups2.class);

    assert options.usage().indexOf("General options") > -1;
    assert options.usage().indexOf("Display options") > -1;
    // "Internal options" is unpublicized so it should not occur in the default
    // usage message.
    assert options.usage().indexOf("Internal options") == -1;

    assert options.usage("Internal options").indexOf("Set mu") > -1;
    // "Set pi" should not appear in the usage message for "Internal options"
    // because it is marked with @Unpublicized.
    assert options.usage("Internal options").indexOf("Set pi") == -1;

    options.parse(new String[] {"--colour", "--pi", "3.15"});
    assert TestOptionGroups2.color;
    assert TestOptionGroups2.pi == 3.15;

    options.parse(Options.tokenize("--colour --pi 3.15"));
    assert TestOptionGroups2.color;
    assert TestOptionGroups2.pi == 3.15;

    // Test that an option group that contains only unpublicized options is not
    // included in the usage message.
    Options options2 = new Options("test", TestOptionGroups3.class);
    assert options2.usage().indexOf("Internal options") == -1;
    // ...unless showUnpublicized is true.
    assert options2.usage(true).indexOf("Internal options") > -1;
  }

  public static class ClassWithOptionsEnums {
    enum Compressor {
      RLE,
      SMART_RLE,
      HUFFMAN
    }

    @Option("Set the first compression pass")
    public static @Nullable Compressor firstPass;

    @Option("Set the second compression pass")
    public static @Nullable Compressor secondPass;
  }

  @Test
  public void testOptionsEnums() throws ArgException {
    Options options = new Options("test", ClassWithOptionsEnums.class);

    options.parse(new String[] {"--firstPass", "SMART_RLE"});
    assert ClassWithOptionsEnums.firstPass == ClassWithOptionsEnums.Compressor.SMART_RLE;
    ClassWithOptionsEnums.firstPass = ClassWithOptionsEnums.Compressor.HUFFMAN;

    options.parse(new String[] {"--firstPass", "smart_rle"});
    assert ClassWithOptionsEnums.firstPass == ClassWithOptionsEnums.Compressor.SMART_RLE;
    ClassWithOptionsEnums.firstPass = ClassWithOptionsEnums.Compressor.HUFFMAN;

    options.parse(new String[] {"--firstPass", "smart-rle"});
    assert ClassWithOptionsEnums.firstPass == ClassWithOptionsEnums.Compressor.SMART_RLE;

    options.parse(new String[] {"--firstPass", "rle", "--secondPass", "SMART-RLE"});
    assert ClassWithOptionsEnums.firstPass == ClassWithOptionsEnums.Compressor.RLE;
    assert ClassWithOptionsEnums.secondPass == ClassWithOptionsEnums.Compressor.SMART_RLE;

    options.parse(new String[] {"--secondPass", "Huffman"});
    assert ClassWithOptionsEnums.firstPass == ClassWithOptionsEnums.Compressor.RLE;
    assert ClassWithOptionsEnums.secondPass == ClassWithOptionsEnums.Compressor.HUFFMAN;
  }

  @Test
  public void testOptionsEnumsFail() throws ArgException {
    Options options = new Options("test", ClassWithOptionsEnums.class);
    Assertions.assertThrows(
        ArgException.class,
        () -> {
          // should fail: can not leave out _ or -
          options.parse(new String[] {"--firstPass", "smartrle"});
        });
  }
}
