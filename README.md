# Plume-lib Options:  Command-line option processing for Java #

The Options class:

 * parses command-line options and sets fields in your program accordingly,
 * creates usage messages (such as printed by a `--help` option), and
 * creates documentation suitable for a manual or manpage.

Thus, the programmer is freed from writing duplicative, boilerplate code
and documentation that could get out of sync with the rest of the program.

The programmer does not have to write any code, only declare and document
variables. For each field that you want to set from a command-line
argument, you write Javadoc and an `@Option` annotation. Then, the field is
automatically set from a command-line option of the same name, and usage
messages and printed documentation are generated automatically.

For more details, see the Javadoc for the [`Options`
class](https://github.com/plume-lib/options/blob/master/src/main/java/org/plumelib/options/Options.java)..
