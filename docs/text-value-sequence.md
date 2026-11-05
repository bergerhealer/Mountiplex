# Version-Aware Text Comparison (`TextValueSequence`)

`TextValueSequence` (`src/main/java/com/bergerkiller/mountiplex/logic/TextValueSequence.java`) compares strings that contain numbers
the way people compare version numbers: `1.9` is lower than `1.10`, where plain string comparison says the opposite. It is dependency-free
and used wherever Mountiplex compares versions, most importantly the `variable <operator> value` comparisons in template
[conditions](template-syntax.md#conditions-expressions) (`ClassResolver.evaluateExpression`). Projects can use it directly to compare
their own version strings.

## How It Works

The text is split into alternating tokens: a run of digits (a number) and a run of non-digits (text). `1.20.5-pre1` becomes
`1` `.` `20` `.` `5` `-pre` `1`. Tokens are compared pairwise from the start:

- Two number tokens compare numerically (`9 < 10`). Leading zeros do not matter (`1.8` equals `1.08`).
- Otherwise the tokens compare as plain text (`-pre` and `-rc`: alphabetical).
- If all shared tokens are equal, the **longer** sequence is greater.
- A number too large for an `int` is treated as text.

The result is cached in the object, so parse once when comparing many times.

## API

All static methods unless noted:

| Method | Use |
|---|---|
| `TextValueSequence.parse(String)` | Parse into a comparable (`Comparable<TextValueSequence>`) object. |
| `compareText(a, b)` | Parse and compare in one call. Returns negative, zero or positive. |
| `evaluateText(a, operator, b)` | Parse and evaluate a comparison. Operators: `>`, `>=`, `<`, `<=`, `==`, `!=`. Returns `false` for any other operator. |
| `evaluate(seqA, operator, seqB)` | Same, for already-parsed values. |
| `STRING_COMPARATOR` | A `Comparator<String>` for sorting version strings. Does not support `null`. |

```java
TextValueSequence.compareText("1.9", "1.10");              // negative: 1.9 is older
TextValueSequence.evaluateText("1.20.5", ">=", "1.20");     // true
TextValueSequence.evaluateText("1.9", "==", "1.9");         // true
versions.sort(TextValueSequence.STRING_COMPARATOR);         // [1.8, 1.9, 1.9.1, 1.10]
```

## Results to Know About

Checked by running it:

| Comparison | Result | Note |
|---|---|---|
| `1.9` vs `1.10` | `1.9` is lower | Numeric. |
| `1.20.5` vs `1.20` | `1.20.5` is higher | Longer wins when the start is equal. |
| `1.20` vs `1.20.0` | `1.20` is **lower** | Missing tokens are not treated as zero. |
| `1.21-pre1` vs `1.21` | `1.21-pre1` is **higher** | A suffix makes the sequence longer. Pre-releases do not sort before the release. |
| `1.21-pre1` vs `1.21-pre2`, `-rc1` | `pre1` < `pre2` < ... | Numeric within a suffix, alphabetical between suffixes. |
| `1.8` vs `1.08` | Equal | Leading zeros are ignored. |
| `26.1` vs `1.21.11` | `26.1` is higher | Works for year-style versions too. |

Because of the pre-release behavior, a project that needs `1.21-pre1 < 1.21` must handle it itself, for instance with a separate
variable (BKCommonLib sets a `pre` variable next to `version`, see [examples/project-setup.md](examples/project-setup.md)).

## Gotchas

- The equality operator is `==`. A single `=` is not an operator and evaluates to `false`.
- `STRING_COMPARATOR` returns 0 only for identical strings (it short-circuits on `equals`), so `1.8` and `1.08` are ordered, not equal. Use `compareText` for numeric equality.
- Operators are checked as text, so a typo does not throw. It silently evaluates to `false`.
