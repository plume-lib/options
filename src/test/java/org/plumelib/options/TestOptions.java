package org.plumelib.options;

import static org.junit.Assert.*;
import static org.plumelib.options.Options.ArgException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;

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

    assertEquals("foo", t.lp.get(0).toString());
    assertEquals("bar", t.lp.get(1).toString());
    assertNotNull(t.integer_reference);
    assertEquals(24, t.integer_reference.intValue());
    assertEquals(37.8, t.temperature, 0);
    assertFalse(t.bool);
    assertEquals(34.6, t.ld.get(0).doubleValue(), 0);
    assertEquals(17.8, t.ld.get(1).doubleValue(), 0);

    // Test non-options
    t.bool = false;
    String[] args = options.parse(new String[] {"one", "two", "three", "-b"});
    assert args.length == 3 : "@AssumeAssertion(value)";
    assertEquals("one", args[0]);
    assertEquals("two", args[1]);
    assertEquals("three", args[2]);
    assertTrue(t.bool);

    // Test --
    t.bool = false;
    args = options.parse(new String[] {"--", "one", "two", "-b"});
    assert args.length == 3 : "@AssumeAssertion(value)";
    assertEquals("one", args[0]);
    assertEquals("two", args[1]);
    assertEquals("-b", args[2]);
    assertFalse(t.bool);

    // Test spaceSeparatedLists
    t.ld.clear();
    Options.spaceSeparatedLists = true;
    args = options.parse(new String[] {"--ld", "42.1 9.3 10.5", "--ld", "2.7"});
    assert args.length == 0 : "@AssumeAssertion(value)";
    assertEquals(4, t.ld.size());
    assertEquals(42.1, t.ld.get(0).doubleValue(), 0);
    assertEquals(9.3, t.ld.get(1).doubleValue(), 0);
    assertEquals(10.5, t.ld.get(2).doubleValue(), 0);
    assertEquals(2.7, t.ld.get(3).doubleValue(), 0);

    // Test list with no default
    args = options.parse(new String[] {"--ls", "hello", "--ls", "world"});
    assert args.length == 0 : "@AssumeAssertion(value)";
    assert t.ls != null
        : "@AssumeAssertion(nullness): application invariant: parsed string with --ls just above";
    assertEquals(2, t.ls.size());
    assertEquals("hello", t.ls.get(0));
    assertEquals("world", t.ls.get(1));

    // Test files and paths
    assert t.input_file != null
        : "@AssumeAssertion(nullness): application invariant: parsed string with --input-file just above";
    assertTrue(t.input_file.exists());
    assertEquals("TestOptions1.txt", t.input_file.getName());
    assert t.input_path != null
        : "@AssumeAssertion(nullness): application invariant: parsed string with --input-path just above";
    assertTrue(t.input_path.toFile().exists());
    assert t.input_path != null
        : "@AssumeAssertion(nullness): annotated JDK doesn't contain toFile() yet";
    assertEquals("/tmp/TestOptions2.txt", t.input_path.toString());
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
    assertEquals("Monday", t.day);
    assertEquals(-12.3, t.temperature, 0);
    assertFalse(t.printVersion);

    options.parse(Options.tokenize("-d Monday -temp -12.3"));
    assertEquals("Monday", t.day);
    assertEquals(-12.3, t.temperature, 0);
    assertFalse(t.printVersion);

    options.parse(new String[] {"-t", "21.7", "-version"});
    assertEquals("Monday", t.day);
    assertEquals(21.7, t.temperature, 0);
    assertTrue(t.printVersion);

    options.parse(Options.tokenize("-t 21.7 -version"));
    assertEquals("Monday", t.day);
    assertEquals(21.7, t.temperature, 0);
    assertTrue(t.printVersion);

    t.printVersion = false;
    options.parse(new String[] {"--version", "-temp=-60.1", "--day", "Tuesday"});
    assertEquals("Tuesday", t.day);
    assertEquals(-60.1, t.temperature, 0);
    assertTrue(t.printVersion);

    t.printVersion = false;
    options.parse(Options.tokenize("--version -temp=-60.1 --day Tuesday"));
    assertEquals("Tuesday", t.day);
    assertEquals(-60.1, t.temperature, 0);
    assertTrue(t.printVersion);
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
      fail();
    } catch (Error e) {
      assertNotNull(e.getMessage());
      assertTrue(
          e.getMessage()
              .contains("missing @OptionGroup annotation on the first @Option-annotated field"));
    }

    try {
      new Options("test", TestOptionGroups2.class, TestOptionGroups1.class);
      fail();
    } catch (Error e) {
      assertNotNull(e.getMessage());
      assertTrue(e.getMessage().contains("missing @OptionGroup annotation in field"));
    }

    Options options = new Options("test", TestOptionGroups2.class);

    assertNotEquals(-1, options.usage().indexOf("General options"));
    assertNotEquals(-1, options.usage().indexOf("Display options"));
    // "Internal options" is unpublicized so it should not occur in the default
    // usage message.
    assertEquals(-1, options.usage().indexOf("Internal options"));

    assertNotEquals(-1, options.usage("Internal options").indexOf("Set mu"));
    // "Set pi" should not appear in the usage message for "Internal options"
    // because it is marked with @Unpublicized.
    assertEquals(-1, options.usage("Internal options").indexOf("Set pi"));

    options.parse(new String[] {"--colour", "--pi", "3.15"});
    assertTrue(TestOptionGroups2.color);
    assertEquals(3.15, TestOptionGroups2.pi, 0);

    options.parse(Options.tokenize("--colour --pi 3.15"));
    assertTrue(TestOptionGroups2.color);
    assertEquals(3.15, TestOptionGroups2.pi, 0);

    // Test that an option group that contains only unpublicized options is not
    // included in the usage message.
    Options options2 = new Options("test", TestOptionGroups3.class);
    assertEquals(-1, options2.usage().indexOf("Internal options"));
    // ...unless showUnpublicized is true.
    assertNotEquals(-1, options2.usage(true).indexOf("Internal options"));
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
    assertEquals(ClassWithOptionsEnums.Compressor.SMART_RLE, ClassWithOptionsEnums.firstPass);
    ClassWithOptionsEnums.firstPass = ClassWithOptionsEnums.Compressor.HUFFMAN;

    options.parse(new String[] {"--firstPass", "smart_rle"});
    assertEquals(ClassWithOptionsEnums.Compressor.SMART_RLE, ClassWithOptionsEnums.firstPass);
    ClassWithOptionsEnums.firstPass = ClassWithOptionsEnums.Compressor.HUFFMAN;

    options.parse(new String[] {"--firstPass", "smart-rle"});
    assertEquals(ClassWithOptionsEnums.Compressor.SMART_RLE, ClassWithOptionsEnums.firstPass);

    options.parse(new String[] {"--firstPass", "rle", "--secondPass", "SMART-RLE"});
    assertEquals(ClassWithOptionsEnums.Compressor.RLE, ClassWithOptionsEnums.firstPass);
    assertEquals(ClassWithOptionsEnums.Compressor.SMART_RLE, ClassWithOptionsEnums.secondPass);

    options.parse(new String[] {"--secondPass", "Huffman"});
    assertEquals(ClassWithOptionsEnums.Compressor.RLE, ClassWithOptionsEnums.firstPass);
    assertEquals(ClassWithOptionsEnums.Compressor.HUFFMAN, ClassWithOptionsEnums.secondPass);
  }

  @Test
  public void testOptionsEnumsFail() {
    Options options = new Options("test", ClassWithOptionsEnums.class);
    try {
      // should fail: can not leave out _ or -
      options.parse(new String[] {"--firstPass", "smartrle"});
      org.junit.Assert.fail("Didn't throw ArgException as expected");
    } catch (ArgException e) {
    }
  }
}
