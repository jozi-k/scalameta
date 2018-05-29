# SemanticDB Guide

SemanticDB is a data model for semantic information such as symbols and types
about programs in Scala and other languages. SemanticDB decouples production
and consumption of semantic information, establishing documented means for
communication between tools.

In this document, we introduce practical aspects of working with SemanticDB.
We describe the tools that can be used to produce SemanticDB payloads, the tools
can be used to consume SemanticDB payloads and useful tips & tricks for working
with SemanticDB. If you're looking for a comprehensive reference of SemanticDB
features, check out [the specification](semanticdb3.md).

* [Installation](#installation)
* [Example](#example)
* [What is SemanticDB good for?](#what-is-semanticdb-good-for)
* [Producing SemanticDB](#producing-semanticdb)
  * [Scalac compiler plugin](#scalac-compiler-plugin)
  * [Metac](#metac)
  * [sbt](#sbt)
  * [Metacp](#metacp)
* [Consuming SemanticDB](#consuming-semanticdb)
  * [Scala bindings](#scala-bindings)
  * [Metap](#metap)
  * [Protoc](#protoc)
* [SemanticDB-based tools](#semanticdb-based-tools)
  * [Scalafix](#scalafix)
  * [Metadoc](#metadoc)
  * [Metals](#metals)

## Install

This guide covers several non-standard command-line tools: `metac`, `metacp`,
`metap`. To install these tools on your computer, you can do the following:

1. Install the `coursier` command-line tool by following the
[instructions here](https://github.com/coursier/coursier/#command-line).
Make sure you are using the latest coursier version (1.1.0-M4 or newer).
2. Add the following aliases to your shell:

```bash
alias metac="coursier launch org.scalameta:metac_2.11:3.7.4 -- -cp $(coursier fetch -p org.scala-lang:scala-library:2.11.12)"
alias metacp="coursier launch org.scalameta:metacp_2.11:3.7.4 --"
alias metap="coursier launch org.scalameta:metap_2.11:3.7.4 --"
```

(Optional) Instead of running `metap` on the JVM, you can build a native binary
on macOS or Linux. Thanks to [Scala Native](https://scala-native.readthedocs.io/en/latest/),
native Metap works much faster than regular Metap (on a personal laptop of
one of the authors of this guide, a simple Metap invocation takes 500+ ms on JVM
and 10 ms on native).

1. Setup the [development environment for Scala Native](http://www.scala-native.org/en/latest/user/setup.html#installing-clang-and-runtime-dependencies).
2. Link a native `metap` binary.

```bash
coursier bootstrap org.scalameta:metap_native0.3_2.11:3.7.4 -o metap -f --native --main scala.meta.cli.Metap
```

## Example

Let's generate SemanticDB for a simple Scala program. (At the moment,
our SemanticDB producers provide full Scala support and partial Java support.
Theoretically, [the SemanticDB protobuf schema](semanticdb3.proto)
can accommodate other languages as well, but we haven't attempted to do that yet).

```scala
object Test {
  def main(args: Array[String]): Unit = {
    println("hello world")
  }
}
```

In order to obtain a SemanticDB corresponding to this program, let's use
the Metac command-line tool. For more information on other tools that can
produce SemanticDB, [see below](#producing-semanticdb).

```
$ metac Test.scala
```

`metac` is a thin wrapper over the Scala compiler. It supports the same
command-line arguments as `scalac` supports, but instead of generating .class
files it generates .semanticdb files. Newer versions of Metac may also generate
an accompanying .semanticidx file, but it's an experimental feature, so we won't
won't be discussing it in this document.

```
$ tree
.
├── META-INF
│   └── semanticdb
│       └── Test.scala.semanticdb
└── Test.scala
```

If we take a look inside Test.scala.semanticdb, we'll see a weird mix of
legible-looking text and special characters. That's because .semanticdb
files store protobuf payloads.

```
$ xxd META-INF/semanticdb/Test.scala.semanticdb
00000000: 0ae4 0408 0312 0a54 6573 742e 7363 616c  .......Test.scal
00000010: 611a 596f 626a 6563 7420 5465 7374 207b  a.Yobject Test {
00000020: 0a20 2064 6566 206d 6169 6e28 6172 6773  .  def main(args
00000030: 3a20 4172 7261 795b 5374 7269 6e67 5d29  : Array[String])
00000040: 3a20 556e 6974 203d 207b 0a20 2020 2070  : Unit = {.    p
00000050: 7269 6e74 6c6e 2822 6865 6c6c 6f20 776f  rintln("hello wo
00000060: 726c 6422 290a 2020 7d0a 7d0a 2a5b 0a1a  rld").  }.}.*[..
00000070: 5f65 6d70 7479 5f2e 5465 7374 2e6d 6169  _empty_.Test.mai
00000080: 6e28 292e 2861 7267 7329 1808 2a04 6172  n().(args)..*.ar
...
```

In order to make sense of .semanticdb files, we can use the Metap
command-line tool. For more information on other tools that can
consume SemanticDB, [see below](#consuming-semanticdb).

```
$ metap .
Test.scala
----------

Summary:
Schema => SemanticDB v3
Uri => Test.scala
Text => non-empty
Language => Scala
Symbols => 3 entries
Occurrences => 7 entries

Symbols:
_empty_.Test. => final object Test.{+1 decls}
  extends AnyRef
_empty_.Test.main(). => method main: (args: Array[String]): Unit
  args => _empty_.Test.main().(args)
  Array => scala.Array#
  String => scala.Predef.String#
  Unit => scala.Unit#
_empty_.Test.main().(args) => param args: Array[String]
  Array => scala.Array#
  String => scala.Predef.String#

Occurrences:
[0:7..0:11): Test <= _empty_.Test.
[1:6..1:10): main <= _empty_.Test.main().
[1:11..1:15): args <= _empty_.Test.main().(args)
[1:17..1:22): Array => scala.Array#
[1:23..1:29): String => scala.Predef.String#
[1:33..1:37): Unit => scala.Unit#
[2:4..2:11): println => scala.Predef.println(+1).
```

Metap prettyprints various parts of the SemanticDB payload in correspondence
with [the SemanticDB specification](semanticdb3.md). Here are the most
important parts:
  * `Uri` stores the URI of the source file relative to
    the directory where the SemanticDB producer was invoked.
  * `Symbols` contains information about definitions in the source
    file, including modifiers, signatures, etc.

    For example, `_empty_.Test.main(). => method main: (args: Array[String]): Unit`
    says that `main` is a method with one parameter of type `Array[String]`.
    Further lines of the printout establish that `Array` in that type
    refers to `scala.Array#`, etc.
  * `Occurrences` contains a list of identifiers from the source file with
    their line/column-based positions and unique identifiers pointing to
    corresponding definitions resolved by the compiler.

    For example, `[2:4..2:11): println => scala.Predef.println(+1).` says that
    the identifier `println` on line 3 (zero-based numbering scheme!) refers
    to the second overload of `println` from `scala.Predef`.

## What is SemanticDB good for?

SemanticDB decouples producers and consumers of semantic information about
programs and establishes [a rigorous specification](semanticdb3.md) of the
interchange format.

Thanks to that, SemanticDB-based tools like [Scalafix](#scalafix),
[Metadoc](#metadoc) and [Metals](#metals) don't need to know about compiler
internals and can work with any compiler that supports SemanticDB.
This demonstrably improves developer experience, portability and scalability.
Next-generation semantic tools at Twitter are based on SemanticDB.

For more information about the SemanticDB vision, check out our talks:
  * [Semantic Tooling at Twitter](https://www.youtube.com/watch?v=4yqDFsdKciA)
    (June 2017) by Eugene Burmako & Stu Hood.
  * [SemanticDB for Scala developer tools](https://t.co/Z080F6JxyQ?amp=1)
    (April 2018) by Ólafur Páll Geirsson.
  * [How We Built Tools That Scale to Millions of Lines of Code](https://na.scaladays.org/schedule/how-we-built-tools-that-scale-to-millions-of-lines-of-code)
    (June 2018) by Eugene Burmako.

## Producing SemanticDB

### Scalac compiler plugin

The `semanticdb-scalac` compiler plugin injects itself immediately after the
`typer` phase of the Scala compiler and then harvests and dumps semantic
information from Scalac in SemanticDB format.

```
scalac -Xplugin:path/to.jar -Yrangepos [<pluginOption> ...] [<scalacOption> ...] [<sourceFile> ...]
```

The compiler plugin supports the following options that can
be passed through Scalac in the form of `-P:semanticdb:<option>:<value>`

<table>
  <tr>
    <td width="310px">Option</td>
    <td>Value</td>
    <td>Explanation</td>
    <td>Default</td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:sourceroot:&lt;value&gt;</code></td>
    <td>Absolute or relative path</td>
    <td>
      Used to relativize source file paths into
      <code>TextDocument.uri</code>.
    </td>
    <td>Current working directory</td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:mode:&lt;value&gt;</code></td>
    <td>
        <code>fat</code>,<br/>
        <code>slim</code>
    </td>
    <td>
      Specifies whether to include source code in
      <code>TextDocument.text</code> (<code>fat</code> for yes,
      <code>slim</code> for no).
    </td>
    <td><code>fat</code></td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:failures:&lt;value&gt;</code></td>
    <td>
      <code>error</code>,<br/>
      <code>warning</code>,<br/>
      <code>info</code>,<br/>
      <code>ignore</code></td>
    <td>
      The level at which the Scala compiler should report failures arise during
      SemanticDB generation.
    </td>
    <td><code>warning</code></td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:symbols:&lt;value&gt;</code></td>
    <td>
      <code>all</code>,<br/>
      <code>definitions</code>,<br/>
      <code>none</code><br/>
    </td>
    <td>
      Specifies what symbols will appear <code>TextDocument.symbols</code>
      (<code>none</code> for none,
      <code>definitions</code> for just symbols defined in the current program,
      <code>all</code> for symbols either defined or referenced from the current
      program).
    </td>
    <td><code>definitions</code></td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:types:&lt;value&gt;</code></td>
    <td>
      <code>all</code>,<br/>
      <code>none</code><br/>
    </td>
    <td>
      Says whether to save type signatures in
      <code>SymbolInformation.tpe</code> (<code>all</code> for yes,
      <code>none</code> for no).
    </td>
    <td><code>all</code></td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:profiling:&lt;value&gt;</code></td>
    <td>
      <code>console</code>,<br/>
      <code>off</code><br/>
    </td>
    <td>
      Controls basic profiling functionality that computes the overhead of
      SemanticDB generation relative to regular compilation time
      (<code>console</code> for dumping profiling information to console,
      <code>off</code> for disabling profiling).
    </td>
    <td><code>off</code></td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:include:&lt;value&gt;</code></td>
    <td>Java regex</td>
    <td>Which source files to include in SemanticDB generation?</td>
    <td><code>.*</code></td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:exclude:&lt;value&gt;</code></td>
    <td>Java regex</td>
    <td>Which source files to exclude from SemanticDB generation?</td>
    <td><code>^$</code></td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:diagnostics:&lt;value&gt;</code></td>
    <td>
      <code>all</code>,<br/>
      <code>none</code><br/>
    </td>
    <td>
      Says whether to save compiler messages in
      <code>TextDocument.diagnostics</code> (<code>all</code> for yes,
      <code>none</code> for no).
    </td>
    <td><code>all</code></td>
  </tr>
  <tr>
    <td><code>-P:semanticdb:synthetics:&lt;value&gt;</code></td>
    <td>
      <code>all</code>,<br/>
      <code>none</code><br/>
    </td>
    <td>
      Specifies whether to save compiler-generated code in the unspecified
      `Synthetics` section of SemanticDB (<code>all</code> for yes,
      <code>none</code> for no).
    </td>
    <td><code>all</code></td>
  </tr>
</table>

`semanticdb-scalac` can be hooked into Scala builds in a number of ways.
Read below for more information on command-line tools as well as integration
into Scala build tools.

### Metac

Metac is a command-line tool that serves as a drop-in replacement for `scalac`
and produces `*.semanticdb` files instead of `*.class` files. It supports the
same command-line arguments as `scalac`, including the compiler plugin options
[described above](#scalac-compiler-plugin).

```
metac [<pluginOption> ...] [<scalacOption> ...] [<sourceFile> ...]
```

With metac, it is not necessary to provide the flags
`-Xplugin:/path/to.jar` and `-Yrangepos`, which makes it ideal
for quick experiments with SemanticDB. For an example of using Metac,
check out [Example](#example).

### sbt

In order to enable `semanticdb-scalac` for your sbt project, add the following
to your build. Note that the compiler plugin requires the `-Yrangepos` compiler
option to be enabled.

```scala
addCompilerPlugin("org.scalameta" % "semanticdb-scalac" % "3.7.4" cross CrossVersion.full)
scalacOptions += "-Yrangepos"
```

### Metacp

Metacp is a command-line tool that takes a classpath, generates SemanticDB files
for all classfiles and returns a new classpath that contains the SemanticDB files.
Advanced command-line options control caching, parallelization and interaction
with some quirks of the Scala standard library.

```
metacp [options] <classpath>
```

<table>
  <tr>
    <td width="200px">Option</td>
    <td width="100px">Value</td>
    <td>Explanation</td>
    <td>Default</td>
  </tr>
  <tr>
    <td><code>&lt;classpath&gt;</code></td>
    <td>Java classpath</td>
    <td>
      Specifies classpath to be converted to SemanticDB.
    </td>
    <td></td>
  </tr>
  <tr>
    <td><code>--cache-dir &lt;value&gt</code></td>
    <td>Absolute or relative path</td>
    <td>
      Says where Metacp should cache conversion results.
    </td>
    <td>
      See below
    </td>
  </tr>
  <tr>
    <td>
      <code>--exclude-scala-library-synthetics</code>,<br/>
      <code>--include-scala-library-synthetics</code>
    </td>
    <td></td>
    <td>
      Specifies whether the output classpath should include a jar that contains
      SemanticDB files corresponding to definitions missing from
      <code>scala-library.jar</code>, e.g. <code>scala.Any</code>,
      <code>scala.AnyRef</code> and others.
    </td>
    <td>
      <code>--exclude-scala-library-synthetics</code>
    </td>
  </tr>
  <tr>
    <td><code>--par</code>,<br/><code>--no-par</code></td>
    <td></td>
    <td>
      Toggles parallel processing. If enabled, classpath entries
      will be converted in parallel.
      <br/>
      <br/>
      NOTE: Some of our users <a href="https://github.com/scalameta/scalameta/issues/1398">have reported deadlocks</a>
      supposedly caused by enabling <code>--par</code>. Proceed at your own risk.
    </td>
    <td>
      <code>--no-par</code>
    </td>
  </tr>
</table>

Metacp understands classfiles produced by both the Scala and Java compiler.
Since Metacp is a standalone application independent from the Scala compiler,
the [compiler plugin](#scalac-compiler-plugin) is not required when using Metacp.

Because Metacp only works with classfiles and not sources, SemanticDB files
that it produces only contain the `Symbols` section. Neither `Occurrences`
nor `Diagnostics` sections are present, because they both require source
information. For more information about the SemanticDB format, check out
[the specification](#semanticdb3.md).

In order to improve performance, Metacp sometimes caches conversion results.
Every classpath entry is converted and cached individually according to the
following scheme:
  * Jar files. Always cached in the OS cache directory keyed by the application
    name `semanticdb`, Metacp version and the fingerprint of the jar.
    The default cache location depends on the OS and is computed using
    [directories-jvm](https://github.com/soc/directories-jvm).
    For example, on macOS the location for scala-library would be
    `$HOME/Library/Caches/semanticdb/3.7.4/scala-library-FINGERPRINT.jar`.
  * Class directories. Never cached to avoid overflowing the cache with
    transient results.

As an example of using Metacp, let's compile Test.scala from
[Example](#example) using Scalac and then convert
the resulting classfiles to SemanticDB. Note that newer versions of Metac
may also generate an accompanying .semanticidx file, but it's an experimental
feature, so we won't be discussing it in this document.

```
$ scalac Test.scala
<success>

$ tree
.
├── Test$.class
├── Test.class
└── Test.scala

$ metacp .
/var/folders/pg/v06175sd2_qb9jbg7c28xr040000gn/T/semanticdb8798662205463788559

$ tree /var/folders/pg/v06175sd2_qb9jbg7c28xr040000gn/T/semanticdb8798662205463788559
/var/folders/pg/v06175sd2_qb9jbg7c28xr040000gn/T/semanticdb8798662205463788559
└── META-INF
    └── semanticdb
        └── Test.class.semanticdb

$ metap /var/folders/pg/v06175sd2_qb9jbg7c28xr040000gn/T/semanticdb8798662205463788559
Test.class
----------

Summary:
Schema => SemanticDB v3
Uri => Test.class
Text => empty
Language => Scala
Symbols => 5 entries

Symbols:
_empty_. => package _empty_
_empty_.Test. => final object Test.{+1 decls}
  extends AnyRef
_empty_.Test.main(). => method main: (args: Array[String]): Unit
  args => _empty_.Test.main().(args)
  Array => scala.Array#
  String => scala.Predef.String#
  Unit => scala.Unit#
_empty_.Test.main().(args) => param args: Array[String]
  Array => scala.Array#
  String => scala.Predef.String#
_root_. => package _root_
```

## Consuming SemanticDB

### Scala bindings

The `semanticdb3` library contains [ScalaPB](https://scalapb.github.io/)
bindings to [the SemanticDB protobuf schema](semanticdb3.proto). Using this
library, one can model SemanticDB entities as Scala case classes and
serialize/deserialize them into bytes and streams.

```
libraryDependencies += "org.scalameta" %% "semanticdb3" % "3.7.4"
```

`semanticdb3` is available for all supported Scala platforms - JVM, Scala.js
and Scala Native. For more information, check out autogenerated documentation for
[Scala 2.11](https://static.javadoc.io/org.scalameta/semanticdb3_2.11/3.7.4/scala/meta/internal/semanticdb3/TextDocuments.html)
and [Scala 2.12](https://static.javadoc.io/org.scalameta/semanticdb3_2.12/3.7.4/scala/meta/internal/semanticdb3/TextDocuments.html).

Caveats:
  * At the moment, there are no compatibility guarantees for Scala bindings to the SemanticDB
    schema. The current package of the schema (`scala.meta.internal.semanticdb3`)
    is considered internal, so we do not provide any guarantees about
    compatibility across different versions of the `semanticdb3` library.
    We are planning to [improve the situation](https://github.com/scalameta/scalameta/issues/1300)
    in the future.
  * At the moment, SemanticDB-based tools are responsible for implementing discovery of
    SemanticDB payloads on their own. For example, the non-trivial logic that
    Metap uses to traverse its inputs and detect SemanticDB files must be
    reproduced by Scalafix, Metadoc and others. We are planning to
    [improve the situation](https://github.com/scalameta/scalameta/issues/1566)
    in the future.

### Metap

Metap is a command-line tool that takes a list of paths and then prettyprints
all .semanticdb files that it finds in these paths. Advanced options control
prettyprinting format.

```
metap [options] <classpath>
```

<table>
  <tr>
    <td width="125px">Option</td>
    <td>Value</td>
    <td>Explanation</td>
    <td width="125px">Default</td>
  </tr>
  <tr>
    <td><code>&lt;classpath&gt;</code></td>
    <td>Pseudo classpath</td>
    <td>
      Supported classpath entries:
      <ul>
        <li>
          .semanticdb files (prettyprinted directly)
        </li>
        <li>
          Directories (traversed recursively, all found .semanticdb files
          prettyprinted)
        </li>
        <li>
          .jar files (traversed recursively, all found .semanticdb files
          uncompressed and prettyprinted)
        </li>
      </ul>
    </td>
    <td></td>
  </tr>
  <tr>
    <td><code>--pretty</code>,<br/><code>--proto</code></td>
    <td></td>
    <td>
      Specifies prettyprinting format, which can be either <code>--pretty</code>
      (prints the most important parts of the payload in a condensed fashion)
      or <code>--proto</code> (prints the same output as <code>protoc</code>
      would print, <a href="#protoc">see above</a>).
    </td>
    <td>
      <code>--pretty</code>
    </td>
  </tr>
</table>

For an example of using Metap, check out [Example](#example).

### Protoc

The Protocol Compiler tool (`protoc`) can inspect protobuf payloads in
`--decode` (takes a schema) and `--decode_raw` (doesn't need a schema) modes.
For the reference, here's [the SemanticDB protobuf schema](semanticdb3.proto).

```
$ tree
.
├── META-INF
│   └── semanticdb
│       └── Test.scala.semanticdb
└── Test.scala

$ protoc --proto_path <directory with the .proto file>\
--decode scala.meta.internal.semanticdb3.TextDocuments\
semanticdb3.proto < META-INF/semanticdb/Test.scala.semanticdb

documents {
  schema: SEMANTICDB3
  uri: "Test.scala"
  text: "object Test {\n  def main(args: Array[String]): Unit = {\n    println(\"hello world\")\n  }\n}\n"
  symbols {
    symbol: "_empty_.Test.main().(args)"
    kind: PARAMETER
    name: "args"
    tpe {
      tag: TYPE_REF
      typeRef {
        symbol: "scala.Array#"
        type_arguments {
          tag: TYPE_REF
          typeRef {
            symbol: "scala.Predef.String#"
          }
        }
      }
    }
    accessibility {
      tag: PUBLIC
    }
    language: SCALA
  }
  ...
}
```

`protoc` was useful for getting things done in the early days of SemanticDB,
but nowadays it's a bit too low-level. It is recommended to use `metap`
instead of `protoc`.

# SemanticDB-based tools

## Scalafix

[Scalafix](https://github.com/scalacenter/scalafix) is a rewrite and linting
tool for Scala developed at the Scala Center with the goal to help automate
migration between different Scala compiler and library versions.

Scalafix provides syntactic and semantic APIs that tool developers can use
to write custom rewrite and linting rules. Syntactic information is obtained from
[the Scalameta parser](https://github.com/scalameta/scalameta), and semantic
information is loaded from SemanticDB files produced by
[the Scalac compiler plugin](#scalac-compiler-plugin) and [Metacp](#metacp).

Thanks to SemanticDB, Scalafix is:
  * **Accessible**: Scalafix enables novices to implement advanced rules without
    learning compiler internals.
  * **Portable**: Scalafix is not tied to compiler internals, which means that it
    can seamlessly work with any compiler / compiler version that supports
    [the SemanticDB compiler plugin](#scalac-compiler-plugin).
  * **Scalable**: Scalafix does not need a running Scala compiler, so it can perform
    rewrites and lints in parallel. (Unlike compiler plugin-based linters
    that are limited by the single-threaded architecture of Scalac).

## Metadoc

[Metadoc](https://github.com/scalameta/metadoc) is an experiment with SemanticDB
to build online code browser with IDE-like features. Check out
[the demo](http://scalameta.org/metadoc/) for more information.

Metadoc takes Scala sources and corresponding SemanticDB files generated by
[the Scalac compiler plugin](#scalac-compiler-plugin). It then generates a
static site that is possible to serve via GitHub pages, supporting jump to
definition, find usages and search by symbol.

Thanks to SemanticDB, Metadoc is:
  * **Cross-platform**: [Scala bindings to SemanticDB](#scala-bindings) are
    cross-compiled to JVM and Scala.js, which means that the site generator
    and the online code browser can reuse the same logic to work with
    SemanticDB payloads.
  * **Portable**: Just like Scalafix, Metadoc is not tied to compiler internals,
    which means that it can seamlessly work with any compiler / compiler version
    that supports [the SemanticDB compiler plugin](#scalac-compiler-plugin).

## Metals

[Metals](https://github.com/scalameta/metals) is an experiment to implement a
[language server](https://github.com/Microsoft/language-server-protocol) for Scala
using [Scalameta](https://github.com/scalameta) projects such as Scalafmt,
Scalafix and SemanticDB. Check out
[the presentation](https://geirsson.com/assets/metals/) for more information.

Metals uses SemanticDB to:
  * Index project dependencies for intelligent jump to definition
    and quick lookups of project symbols.
  * Communicate with the Scala compiler regarding semantic information
    about the opened project.
  * Feed semantic information into [Scalafix](#scalafix)-based refactorings.

Thanks to SemanticDB, Metals is:
  * **Mostly portable**: Unlike Scalafix and Metadoc, Metals has modules that
    interface directly with compiler internals, but the majority its
    functionality is based on SemanticDB, so it can work with any compiler /
    compiler version that supports
    [the SemanticDB compiler plugin](#scalac-compiler-plugin).
  * **Surprisingly fast**: A well-defined schema for semantic information
    that can come from multiple locations (dependency classpath, uncompiled
    files, compiled files, etc) allows for a robust implementation of indexing,
    which reliably speeds up operations like jump to definition and find usages.
    We have even experimented with [a relational index for SemanticDB data](https://github.com/scalameta/metals/pull/94),
    which further improves performance characteristics.
  * **Resilient**: Reification of semantic information makes it possible to
    consult results of previous typechecks and accommodate certain edits by simply
    shifting offsets in old SemanticDB snapshots. This technique is surprisingly
    effective for supporting minor edits that result in temporarily invalid code.