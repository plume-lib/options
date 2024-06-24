# Plume-lib Options:  Command-line option processing for Java

The Options class:

 * parses command-line options and sets fields in your program accordingly,
 * creates usage messages (such as printed by a `--help` option), and
 * (at build time) creates documentation suitable for a manual or manpage.

Thus, the programmer is freed from writing duplicative, boilerplate code.
The user documentation is automatically generated and never gets out
of sync with the rest of the program.

The programmer does not have to write any code, only declare and document
variables. For each field that you want to set from a command-line
argument, you write Javadoc and an
[`@Option`](http://plumelib.org/options/api/org/plumelib/options/Option.html)
annotation. Then, the field is
automatically set from a command-line option of the same name, and usage
messages and printed documentation are generated automatically.

For more details, see the [API documentation for the `Options`
class](http://plumelib.org/options/api/org/plumelib/options/Options.html).

The options package provides two types of functionality:
 * It reads command-line options and creates usage messages when your program runs.
   Both version 1.x and version 2.x work with all versions of Java.
 * It generates documentation at build time.
   Version 1.x works with Java 8-12.
   Version 2.x works with Java 9 and later.


## Editing your buildfile ##

In a Gradle buildfile, write

```
dependencies {
  implementation 'org.plumelib:options:2.0.3'
}
```

Other build systems are [similar](https://search.maven.org/artifact/org.plumelib/options/2.0.3/jar).

To update user documentation after an option or its documentation has changed,
use a rule like this:

```
task updateUserOptions(type: Javadoc, dependsOn: 'assemble') {
  group "Documentation"
  description "Updates printed documentation of command-line arguments."
  source = sourceSets.main.allJava.files.sort()
  classpath = project.sourceSets.main.compileClasspath
  options.memberLevel = JavadocMemberLevel.PRIVATE
  options.docletpath = project.sourceSets.main.runtimeClasspath as List
  options.doclet = "org.plumelib.options.OptionsDoclet"
  options.addStringOption("docfile", "${projectDir}/path/to/Main.java")
  options.addStringOption("format", "javadoc")
  options.addStringOption("i", "-quiet")
  options.noTimestamp(false)
  title = ""
}
```

Other build systems are [similar](https://search.maven.org/artifact/org.plumelib/options/2.0.3/jar).


## Alternatives ##

The popular JCommander library was written later than Plume-lib Options and
takes a similar approach, having many of the same features.  One difference
is that JCommander does not generate usage messages nor documentation for a
manual.

Another similar library is picocli.  It generates usage messages, and also
man pages.  Its syntax is more verbose than that of plume-lib Options.  It
does not support having multiple different programs share command lines
arguments; if you want to do that, it suggests making them subcommands of a
single program.
