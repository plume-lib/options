# Plume-lib Options change log

## 2.0.3 (??)

- Tested under JDK 19.
- Permit variable names to use either camelCase or snake_case.
  This has no effect on how the user supplies command-line arguments.

## 2.0.2 (2022-10-18)

- Tested under JDK 18.
- Don't use version of Apache Commons Text that has a security vulnerability.

## 2.0.1 (2022-07-15)

- Tested under JDK 17.

## 2.0.0 (2021-07-11)

- Options version 2.x supports Java 11 and higher.
  Options version 1.x supports Java 8 and Java 11.
- OptionsDoclet supports "--" or "-" prefix for long options to OptionsDoclet.
  For instance, you can pass it `-singledash` as before or the new `--singledash`.
  This is independent of the `setUseSingleDash()` method of Options itself.
  This is also independent of the `-singledash` and `--singledash` options to OptionsDoclet.

## 1.0.5 (2021-01-01)

- Add colon after "default" in option descriptions
- Documentation improvements

## 1.0.4 (2020-01-25)

- Documentation and annotation improvements

## 1.0.3 (2019-10-30)

- Bug fixes

## 1.0.2 (2019-07-03)

- Minor improvements

## 1.0.0

- Require Java 8

## 0.3.3

- Support `Path` as an option variable type

## 0.3.2

- Make getOptionsString public
- Build file improvements, such as work around bug in shadowJar task

## 0.3.1

- Document the need to set `docletpath`
- Prefer camelCase to underscore-separated method and field names.
- Introduce `tokenize` method, reduce number of `parse*` methods
