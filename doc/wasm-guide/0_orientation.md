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

### What is serde

Serde is a serialization framework for rust.

##### Packaging

##### What about Wasm-bindgen, Wasm-pack

##### People to follow

##### Further Reading
 + https://kripken.github.io/blog/binaryen/2018/04/18/rust-emscripten.html
 + https://hacks.mozilla.org/2017/03/why-webassembly-is-faster-than-asm-js/



[wasm-target]: https://www.hellorust.com/news/native-wasm-target.html
[asm.js]: https://asmjs.org/
[emscripten]: https://kripken.github.io/emscripten-site/

[serde-json]: https://github.com/serde-rs/json