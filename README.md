# Plume-lib Options:  Command-line option processing for Java #

The Options class:

 * parses command-line options and sets fields in your program accordingly,
 * creates usage messages (such as printed by a `--help` option), and
 * creates documentation suitable for a manual or manpage.

Thus, the programmer is freed from writing duplicative, boilerplate code.
The user documentation is automatically generated and never gets out
of sync with the rest of the program.

The programmer does not have to write any code, only declare and document
variables. For each field that you want to set from a command-line
argument, you write Javadoc and an
[`@Option`](http://plumelib.org/options/api/org/plumelib/options/Option.html)
annotation. Then, the field is automatically set from a command-line option
of the same name, and usage messages and printed documentation are
generated automatically.

For more details, see the [Javadoc for the `Options`
class](http://plumelib.org/options/api/org/plumelib/options/Options.html).


## Editing your buildfile ##

You can obtain the Options class from [Maven Central](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.plumelib%22%20a%3A%22options%22).

In a gradle buildfile, write

```
dependencies {
  implementation 'org.plumelib:options:0.3'
}
```

Other build systems are similar.
