## 1.13

- Add support for setting a custom name for a `@TestParameter` value given via a provider: 

```java
private static final class FruitProvider implements TestParameterValuesProvider {
  @Override
  public List<?> provideValues() {
    return ImmutableList.of(
        value(new Apple()).withName("apple"),
        value(new Banana()).withName("banana"));
  }
}
```

- Add support for `BigInteger` and `UnsignedLong`
- JUnit4: Fix for interrupted test cases causing random failures with thread
  reuse (porting [the earlier fix in
  JUnit4](https://github.com/junit-team/junit4/issues/1365)) 

## 1.12

- Tweak to the test name generation: Show the parameter name if its value is potentially
  ambiguous (e.g. null, "" or "123").
- Made `TestParametersValues.name()` optional. If missing, a name will be generated.

## 1.11

- Replaced deprecated call to org.yaml.snakeyaml.constructor.SafeConstructor

## 1.10

- Removed dependency on `protobuf-javalite` (see
  [issue #24](https://github.com/google/TestParameterInjector/issues/24))

## 1.9

- Bugfix: Support explicit ordering by the JUnit4 `@Rule`. For example: `@Rule(ordering=3)`.
- Potential test name change: Test names are no longer dependent on the locale of the machine
  running it (e.g. doubles with integer values are always formatted with a trailing `.0`)

## 1.8

- Add support for JUnit5 (Jupiter)

## 1.7

- Remove `TestParameterInjector` support for `org.junit.runners.Parameterized`,
  which was undocumented and thus unlikely to be used.

## 1.6

- Bugfixes
- Better documentation

## 1.5

- `@TestParameters` can now also be used as a repeated annotation:

```java
// Newly added and recommended for new code
@Test
@TestParameters("{age: 17, expectIsAdult: false}")
@TestParameters("{age: 22, expectIsAdult: true}")
public void withRepeatedAnnotation(int age, boolean expectIsAdult){...}

// The old way of using @TestParameters is still supported
@Test
@TestParameters({
    "{age: 17, expectIsAdult: false}",
    "{age: 22, expectIsAdult: true}",
})
public void withSingleAnnotation(int age, boolean expectIsAdult){...}
```

- `@TestParameters` supports setting a custom test name:

```java
@Test
@TestParameters(customName = "teenager", value = "{age: 17, expectIsAdult: false}")
@TestParameters(customName = "young adult", value = "{age: 22, expectIsAdult: true}")
public void personIsAdult(int age, boolean expectIsAdult){...}
```

- Test names with very long parameter strings are abbreviated differentily: In
  some cases, more characters are allowed.

## 1.4

- Bugfix: Run test methods declared in a base class (instead of throwing an
  exception)
- Test names with very long parameter strings are now abbreviated with a snippet
  of the shortened parameter
- Duplicate test names are given a suffix for deduplication
- Replaced dependency on `protobuf-java` by a dependency on `protobuf-javalite`

## 1.3

- Treat 'null' as a magic string that results in a null value

## 1.2

- Don't use the parameter name if it's not explicitly provided by the compiler
- Add support for older Android SDK versions by removing the dependency on
  `j.l.r.Parameter`. The minimum Android SDK version is now 24.

## 1.1

- Add support for `ByteString` and `byte[]`
