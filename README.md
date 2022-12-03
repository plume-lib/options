# Plume-lib Options:  Command-line option processing for Java

The Options class:

 * parses command-line options and sets fields in your program accordingly,
 * creates usage messages (such as printed by a `--help` option), and
 * creates documentation suitable for a manual or manpage.

Thus, the programmer is freed from writing duplicative, boilerplate code.
The user documentation is automatically generated and never gets out
of sync with the rest of the program.

The programmer does not have to write any code, only declare and document
variables. For each field that you want to set from a command-line
argument, you write Javadoc and an `@Option` annotation. Then, the field is
automatically set from a command-line option of the same name, and usage
messages and printed documentation are generated automatically.

For more details, see the [API documentation for the `Options`
class](http://plumelib.org/options/api/org/plumelib/options/Options.html).

Version 1.x works with Java 8-12.
Version 2.x works with Java 9 and later.


## Editing your buildfile ##

In a Gradle buildfile, write

```
dependencies {
  if (JavaVersion.current() == JavaVersion.VERSION_1_8) {
    implementation 'org.plumelib:options:1.0.6'
  } else {
    implementation 'org.plumelib:options:2.0.2'
  }
}
```

That's it!

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

Other build systems are [similar](https://search.maven.org/artifact/org.plumelib/options/2.0.2/jar).


## Alternatives ##

The popular JCommander library was written later than Plume-lib Options and
takes a similar approach, having many of the same features.  One difference
is that JCommander does not generate usage messages nor documentation for a
manual.
