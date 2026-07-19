# Cascara JSON

*Cascara JSON* is a low‑level JSON/JSON5 tokenizer and parser engineered for speed, correctness, and integration into language tooling. It supports both character‑based and byte‑buffer‑based input, enabling fast paths for UTF‑8 data and efficient processing of large documents. The tokenizer implements strict numeric grammar, full JSON5 extensions (including hexadecimal numbers, single‑quoted strings, Infinity/NaN, and Unicode identifiers), and robust escape‑sequence handling. *Cascara JSON* is designed for predictable performance: structural tokens, strings, identifiers, and numbers are scanned using specialized routines that minimize branching and avoid unnecessary conversions. The result is a parser that is easy to embed, easy to reason about, and capable of handling demanding workloads.

## Features

- **High‑performance tokenizer** — Optimized ASCII and byte‑buffer scanning paths with minimal branching and predictable performance across all input sizes.

- **Full JSON5 support** — Single‑quoted strings, hexadecimal numbers, Infinity/NaN, Unicode identifiers, and relaxed whitespace rules.

- **Strict numeric grammar** — Accurate handling of integers, fractions, exponents, signs, and JSON5 numeric extensions.

- **Fast string scanning** — Efficient escape processing, surrogate correctness checks, and SIMD‑accelerated ASCII scanning when enabled.

- **Byte‑buffer optimized path** — Direct UTF‑8 processing without widening, ideal for large documents and streaming scenarios.

- **Unicode‑aware parsing** — Correct handling of Unicode escapes, identifiers, and BOM behavior when enabled.

- **Predictable structural scanning** — Fast detection of braces, brackets, commas, colons, and string delimiters.

- **Clean integration with Cascara language tooling** — Designed to plug directly into Cascara’s [AST](https://qishr.github.io/javadoc/cascara.common/lang/ast/), [Schema](https://qishr.github.io/cascara/schema/), and [diagnostic](https://qishr.github.io/cascara/diagnostics/) systems.

- **Configurable behavior** — Toggle JSON5 features, Unicode validation, comment handling, and strictness levels via [JsonOptions](https://qishr.github.io/javadoc/cascara.lang.json/json/util/JsonOptions/).

- **Lightweight and embeddable** — No external dependencies, small footprint, and easy to integrate into editors, compilers, and configuration systems.

- **Serialization** - Convert between POJOs, text, and AST nodes.

- **Fluent API** - Easily configure JSON processors or construct AST nodes.

## Examples

For examples, see the [Cascara Language Processors Collection](http://127.0.0.1:8000/cascara/language-processors/) page.
## Javadoc

- [CascaraJson](https://qishr.github.io/javadoc/cascara.lang.json/json/CascaraJson/)
- [JsonSerializer](https://qishr.github.io/javadoc/cascara.lang.json/json/processor/JsonSerializer/)
- [JsonAstParser](https://qishr.github.io/javadoc/cascara.lang.json/json/processor/JsonAstParser/)
- [JsonTokenizer](https://qishr.github.io/javadoc/cascara.lang.json/json/processor/JsonTokenizer/)
- [JsonEmitter](https://qishr.github.io/javadoc/cascara.lang.json/json/processor/JsonEmitter/)
- [JsonConverter](https://qishr.github.io/javadoc/cascara.lang.json/json/processor/JsonConverter/)

## Parity

![](https://qishr.github.io/cascara/language-processors/json/images/parity-json-parser.svg)
