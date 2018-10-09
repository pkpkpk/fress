# Orientation
This is an outline of less technical things that will help clarify the big picture motivating the project


### 'Wasm' in this project means compiling *Rust* for  `--target wasm32-unknown-unknown`

Before WebAssembly was a thing, there was [asm.js][asm.js]. Asm.js uses [emscripten][emscripten] to generate a subset of javascript that browsers could compile down to native code.

```
   C/C++  -> LLVM-bytecode ->  emscripten -> asm.js
```

Then the browser vendors all got together and agreed upon the WASM spec

```
   C/C++  -> LLVM-bytecode -> emscripten, binaryen -> LLVM-wasm-backend -> wasm
```

Rust could use the above too via `--target wasm32-unknown-emscripten`

```
   Rust  -> LLVM-bytecode -> emscripten, binaryen -> LLVM-wasm-backend -> wasm
```

In november 2017 [the rust team announced][wasm-target] that rust can compile to a wasm target independently of the emscripten tooling via `wasm32-unknown-unknown`

```
   Rust  -> LLVM-bytecode -> LLVM-wasm-backend -> wasm
```

Emscripten delivers all kinds of runtime support should your program need it, up to and including webgl api bindings and even emulating the file system in js.  A typical emscripten project is a complete program that can ship with large javascript files.

`wasm32-unknown-unknown` on the other hand compiles small dynamic libraries. There is zero javascript produced, just `.wasm` binaries. This is what we are using.

### Wasm Documentation

+ [javascript api at MDN](https://developer.mozilla.org/en-US/docs/WebAssembly)

+ Read the amazing [Lin Clark][Linclark]
  - https://hacks.mozilla.org/2017/02/a-crash-course-in-assembly/
  - https://hacks.mozilla.org/2017/02/creating-and-working-with-webassembly-modules/
  - https://hacks.mozilla.org/2017/02/what-makes-webassembly-fast/
  - https://hacks.mozilla.org/2017/06/a-crash-course-in-memory-management/
  - https://hacks.mozilla.org/2017/07/webassembly-table-imports-what-are-they/

+ [https://webassembly.org/](https://webassembly.org/)


### What is serde

Serde is a serialization framework for rust.

##### Packaging

##### What about Wasm-bindgen, Wasm-pack
+ https://hacks.mozilla.org/2018/04/javascript-to-rust-and-back-again-a-wasm-bindgen-tale/
+ https://hacks.mozilla.org/2018/04/hello-wasm-pack/

##### Further Reading
+ https://rustwasm.github.io/
+ https://kripken.github.io/blog/binaryen/2018/04/18/rust-emscripten.html
+ https://hacks.mozilla.org/2018/01/oxidizing-source-maps-with-rust-and-webassembly/
  - ^ this is a great read




[wasm-target]: https://www.hellorust.com/news/native-wasm-target.html
[asm.js]: https://asmjs.org/
[emscripten]: https://kripken.github.io/emscripten-site/
[linclark]: https://twitter.com/linclark


[serde]: https://github.com/serde-rs/serde
[serde-doc]: https://serde.rs/
[serde-json]: https://github.com/serde-rs/json







