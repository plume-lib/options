/**
 * Command-line option processing for Java
 *
 * <p>The Options class:
 *
 * <ul>
 *   <li>parses command-line options and sets fields in your program accordingly,
 *   <li>creates usage messages (such as printed by a `--help` option), and
 *   <li>creates documentation suitable for a manual or manpage.
 * </ul>
 *
 * Thus, the programmer is freed from writing duplicative, boilerplate code. The user documentation
 * is automatically generated and never gets out of sync with the rest of the program.
 *
 * <p>The programmer does not have to write any code, only declare and document variables. For each
 * field that you want to set from a command-line argument, you write Javadoc and an {@code @}{@link
 * Option} annotation. Then, the field is automatically set from a command-line option of the same
 * name, and usage messages and printed documentation are generated automatically.
 */
package org.plumelib.options;
