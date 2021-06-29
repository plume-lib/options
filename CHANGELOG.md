# Plume-lib Options change log

## 2.0.0

- Options version 2.x supports Java 11 and higher.
  Options version 1.x supports Java 8 and Java 11.
- OptionsDoclet supports "--" or "-" prefix for long options to OptionsDoclet.
  For instance, you can pass it `-singledash` as before or the new `--singledash`.
  This is independent of the `setUseSingleDash()` method of Options itself.
  This is also independent of the `-singledash` and `--singledash` options to OptionsDoclet.

## 1.0.5

- Add colon after "default" in option descriptions
- Documentation improvements

## 1.0.4

- Documentation and annotation improvements

## 1.0.3

- Bug fixes

## 1.0.2

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
