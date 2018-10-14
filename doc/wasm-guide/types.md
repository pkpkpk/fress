# Types

### Naive Fressian Types
| Type       | rust | cljs    | clj  |
|------------|------|---------|------|
| NULL       |  ()/None  | nil     | nil  
| TRUE/FALSE | bool | bool | bool
| INT        | i64  | Number | Long
| FLOAT      | f32  | Number | Float
| DOUBLE     | f64  | Number | Double
| BYTES      | [serde_bytes::ByteBuf][serde_bytes] <sup>[0]</sup> | Int8Array | byte[]
| STRING     | string       | string | string
| UTF8<sup>[1]</sup>      | string       | string | string
| LIST       | Vec&lt;T&gt; | vec    | vec
| MAP        | map          | map    | map
| SET        | types::SET<sup>[0][0]</sup>   | set    | set
| SYM        | types::SYM<sup>[3][3]</sup>   | sym   | sym
| KEY        | types::KEY<sup>[3][3]</sup>   | kw    | kw
| INST       | types::INST<sup>[4][4]</sup>  | #inst | #inst
| UUID       | types::UUID<sup>[5][5]</sup>  | #UUID |#UUID
| REGEX      | types::REGEX<sup>[5][5]</sup> | regex | regex
| URI        | types::URI<sup>[5][5]</sup>   | goog.Uri | URL
| INT_ARRAY     | types::IntArray(Vec&lt;i32&gt;)<sup>[0][0]</sup>      | Int32Array | int[]
| LONG_ARRAY    | types::LongArray(Vec&lt;i64&gt;)<sup>[0][0]</sup>     | Array&lt;Number&gt;<sup>[7][7]</sup> | long[]
| FLOAT_ARRAY   | types::FloatArray(Vec&lt;f32&gt;)<sup>[0][0]</sup>    | Float32Array          | float[]
| DOUBLE_ARRAY  | types::DoubleArray(Vec&lt;f64&gt;)<sup>[0][0]</sup>   | Float64Array          | double[]
| BOOLEAN_ARRAY | types::BooleanArray(Vec&lt;bool&gt;)<sup>[0][0]</sup> | Array&lt;bool&gt;  | bool[]

### Unsupported Fressian Types (WIP)
| Type    | rust | cljs    | clj  
|---------|------|---------|------
| BIGINT  | ???  | ???     | BigInt
| BIGDEC  | ???  | ???     | BigDec
| Records | ???<sup>[8][8]</sup>  | records/TaggedObjects | records/TaggedObjects
| CHAR<sup>[9][9]</sup>    | char (utf8) | string | java char

###   Basic Rust (Serde) Types
| Serde           | Fressian| cljs          | clj  
|-----------------|---------|---------------|------
| unit            |  NULL   | nil           | nil   
| bool            |  T/F    | bool          | bool  
| i8              |  INT    | number        | long  
| i16             |  INT    | number        | long  
| i32             |  INT    | number        | long  
| i64             |  INT    | number<sup>[7][7]</sup> | long  
| u8              |  INT    | number        | long
| u16             |  INT    | number        | long
| u32             |  INT    | number        | long
| u64<sup>[7][7]</sup> |  INT    | number   | number
| f32             |  FLOAT  | number        | float
| f64             |  DOUBLE | number        | double
| char<sup>[9][9]</sup> |  string | string      | char
| string          |  STRING | string        | string
|      \\-->      |  UTF8<sup>[1][1]</sup>  | string  | tag -> string
| [u8]            |  BYTES  | byte-array    | byte-array
| Option<value>   |  value/NIL | value/NIL  | value/NIL
| tuple           |  LIST      | vec        |  vec


### Collections

| Serde           | Fressian| cljs | clj
|-----------------|---------|------|----
| seq             | LIST    | vec  | vec
| map             | MAP     | map  | map

###   Structs
| Serde           | Fressian| cljs          | clj           | Serde JSON          
|-----------------|---------|---------------|---------------|--------------
| unit_struct     |  NULL | nil           |               |`null`
| newtype_struct  |       |               |               |`0`
| tuple_struct    |  rec  | record? list? |               |`[0,0]`
| struct          |  MAP  | MAP           |               |`{"a":0,"b":0}`

### Enums


| Serde           | Fressian| cljs          | clj           | Serde JSON          
|-----------------|---------|---------------|---------------|--------------
| unit_variant    |  KEY    | keyword       |               |`"Z"`
| newtype_variant |         |               |               |`{"Y":0}`
| tuple_variant   |  rec    | record? list? |               |`{"X":[0,0]}`
| struct_variant  |         | struct?       |               |`{"W":{"a":0,"b":0}}`


### Unsupported Rust Types
| Serde           | Fressian| cljs          | clj
|-----------------|---------|---------------|-------
| i128            |  BIGINT | **TODO**      | bigint
| u128            |  BIGINT | **TODO**      | bigint

<hr>

[0]: #complications
[1]: #raw-utf8
[3]: #named-types
[4]: #dates
[5]: #optional-deps
[7]: #integer-safety
[8]: #records
[9]: #chars

### complications

All standard rust types have built-in Serialize/Deserialize impls which prevent fine grain control over serialization. For example, HashSets, BTreeSets, Slices, and Vec<T> are all written as generic sequences and the nuance of each container is lost. This is a known source of friction in serde, and in the future there will be a way to override Serialization impls. Until then, the workaround solution is to create a 'newtype' struct enclosing your target type, which allows you to use custom serialize impls. You can use wrapper types directly, or use serde attributes (see below). This doesn't cost anything, it is just an annoyance.

The most common wrapper you'll probably need is the ByteBuf provided by the [serde_bytes][serde_bytes] crate. This let's you write Vec&lt;u8&gt; and `&[u8]` as BYTE arrays rather than generic lists.

```rust
use serde_bytes::{ByteBuf};
use serde_fressian::ser;

let bytes: Vec<u8> = vec![0,1,2];
let output = ser::to_vec(&bytes).unwrap(); //--> serialized as LIST

let bb = serde_bytes::ByteBuf::from(bytes);
let output = ser::to_vec(&bb).unwrap(); //--> serialized as BYTES

```
#### `serde_fressian::set`

This module provides the `set::SET` wrapper for BTreeSets and `set::HASHSET` for HashSets. `serde_fressian` prefers BTreeSets over HashSets because the former implements Hash on the Set container itself. The same is true for BtreeMaps vs HashMaps. Hashing the container itself is required for implementing `serde_fressian::value` (see below) because it enables using the containers themselves as keys (..or set values), which may show up in that crazy clojure data.

```rust
#[macro_use]
extern crate maplit; // provides map & set literals
use std::collections::{BTreeSet,HashSet};
use serde_fressian::set::{SET, HASHSET};
use serde_fressian::ser;

let btreeset: BTreeSet<i64> = btreeset!{0,1,2,3};
let output = ser::to_vec(&btreeset); //--> serialized as LIST; [0 1 2 3]

let wrapped_btreeset: SET<i64> = SET::from(btreeset);
let output = ser::to_vec(&wrapped_btreeset); //--> serialized as SET; #{0 1 2 3}

// SET derives hash from its btreeset, so it can be stored in a hashset if we want
// but we could not do the other way around.
let hashset: HashSet<SET<i64>> = hashset!{wrapped_btreeset};
let output = ser::to_vec(&hashset); //--> serialized as LIST; [#{0 1 2 3}]

let wrapped_hashset: HASHSET<SET<i64>> = HASHSET::from(hashset);
let output = ser::to_vec(&wrapped_hashset); //--> serialized as SET; #{#{0 1 2 3}}
```

#### `serde_fressian::typed_arrays`

This module provides additional wrappers for fressian's typed arrays:

```rust
use serde_fressian::ser;
use serde_fressian::typed_arrays::{DoubleArray};

let v: Vec<f64> = vec![-2.0, -1.0, 0.0, 1.0, 2.0];
let output = ser::to_vec(&v).unwrap(); //--> serialized as LIST

let da: DoubleArray = DoubleArray::from_vec(v);
let output = ser::to_vec(&da).unwrap(); //--> serialized as DOUBLE_ARRAY
```

#### named-types
As you would expect, symbols and keywords do not have analogous rust types. The `serde_fressian::sym::{SYM}` and `serde_fressian::key::{KEY}` types are provided for compatibility. They are both tuple structs composed of an `Option<String>` namespace and a `String` Name. They have no other built-in functionality outside of lossless serialization.


#### optional-deps
With the expectation that they will be less commonly used, the remaining types default to newtypes over primitives in the interest of keeping binaries small. You can use compiler flags to enable extra functionality provided by external crates:

+ `serde_fressian::regex::{REGEX}`
  - by default is just a newtype around `String`
  - compile with the `use_regex_crate` to enable the external [regex crate][reg]
+ `serde_fressian::uri::{URI}`
  - by default is just a newtype around `String`
  - compile with the `use_url_crate` to enable the external [url crate][url]
+ `serde_fressian::uuid::{UUID}`
  - by default is just a newtype around `ByteBuf`
  - compile with the `use_uuid_crate` to enable the external [uuid crate][uuid]
+ `serde_fressian::inst::{INST}`
  - by default is just a newtype around `i64`
  - TODO: support the [chrono crate][chrono]


### raw-utf8

### dates

### integer-safety

### records

### chars


#### Using Serde Attributes

You can use [serde attributes](https://serde.rs/attributes.html) to use custom serialize/deserialize impls produced by serde-derive. For example, this is from [serde_bytes][serde_bytes]

```rust
#[macro_use]
extern crate serde_derive;

extern crate serde;
extern crate serde_bytes;

#[derive(Serialize)]
struct Efficient<'a> {
    #[serde(with = "serde_bytes")]
    bytes: &'a [u8],

    #[serde(with = "serde_bytes")]
    byte_buf: Vec<u8>,
}

#[derive(Serialize, Deserialize)]
struct Packet {
    #[serde(with = "serde_bytes")]
    payload: Vec<u8>,
}
```




<!-- [Url][url] -->
<!-- [Regex][reg] -->
<!-- [Uuid][uuid] -->
<!-- [Datetime&lt;Utc&gt;][chrono] -->
[chrono]: https://github.com/chronotope/chrono
[uuid]: https://github.com/uuid-rs/uuid
[reg]: https://github.com/rust-lang/regex
[url]: https://github.com/servo/rust-url
[serde_bytes]: https://docs.serde.rs/serde_bytes

<hr>

## Value

<hr>

[enum-reps]: https://serde.rs/enum-representations.html
[variant-attr]: https://serde.rs/variant-attrs.html