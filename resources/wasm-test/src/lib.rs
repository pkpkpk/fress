#[macro_use]
extern crate serde_derive;
extern crate serde;
extern crate serde_fressian;

use std::mem;
use std::os::raw::{c_void};
use serde::Serialize;
use serde_fressian::ser::{self,Serializer};
use serde_fressian::de::{self};
use serde_fressian::wasm::{self};
use serde_fressian::imp::error::{Error, Result};
use serde_fressian::value::{self, Value};

#[no_mangle]
pub extern "C" fn hello() -> *mut c_void {
    let data = vec![["hello", "from", "wasm!"], ["isn't", "this", "exciting?!"]];
    wasm::to_js(data)
}


#[no_mangle]
pub extern "C" fn echo(ptr: *mut u8, cap: usize) -> *mut c_void
{
    let bytes: Vec<u8> = wasm::ptr_to_vec(ptr, cap);
    let val: Result<Value> = de::from_vec(&bytes);

    match val {
        Ok(val) => wasm::to_js(val),
        Err(msg) => wasm::to_js("got an error!")
    }

    // wasm::to_js( format!("bytes: {:?}, ptr: {:?}, cap: {:?}", bytes, ptr, cap))
}