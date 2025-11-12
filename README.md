# 🧩 C0-Compiler

[![Status](https://img.shields.io/badge/status-in%20development-orange)](#)

A **work-in-progress compiler** for the **C0** programming language — built as part of a compiler design project.
It translates C0 source code into assembly and then uses **GCC** to produce machine executables.

---

## 🚀 Overview

The goal of this project is to implement a compiler that:

* Parses **C0** source code
* Generates **assembly** output
* Uses **GCC** to assemble and link into an executable

This repository provides two helper scripts to streamline building and running the compiler.

---

## ⚙️ Usage

### 1. Building the project

Run:

```bash
./build.sh
```

This script:

* Ensures it runs from the project root
* Invokes Gradle to build and install the compiler under `build/install/compiler`

After a successful build, the compiled binaries will be available in:

```
build/install/compiler/bin/
```

---

### 2. Running the compiler

Invoke the compiler using:

```bash
./run.sh <source-file> <target-executable>
```

**Arguments:**

1. `<source-file>` — Path to the input C0 source file
2. `<target-executable>` — Path where the compiled executable should be written

**Example:**

```bash
./run.sh examples/hello.c0 out/hello
```

This will:

1. Compile `examples/hello.c0` to assembly
2. Store the generated assembly as `out/hello.s`
3. Call `gcc` to assemble and link it into the final executable `out/hello`

---

## 🧰 Requirements

* **Java 17+** (for Gradle)
* **GCC** (for assembling and linking)
* **Unix-like environment** (Linux, macOS, or WSL)

---

## 📚 Acknowledgements

This project is based on a **template developed for a Compiler Design course**, created and maintained by other contributors.
Their foundational work provided the build setup, initial structure, and Gradle configuration used here.

---

## 🧩 Status

This compiler is currently **under active development** — features, optimizations, and error handling are incomplete and subject to change.
