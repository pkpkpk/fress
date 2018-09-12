// #[macro_use]
// extern crate serde_derive;
extern crate serde;
extern crate serde_fressian;

use std::os::raw::{c_void};
// use serde::Serialize;

use serde_fressian::de::{self};
use serde_fressian::ser::{self};
use serde_fressian::error::{Error, ErrorCode, Result};
use serde_fressian::value::{self, Value};
use serde_fressian::wasm::{self};

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
    wasm::to_js(val)
}

#[no_mangle]
pub extern "C" fn single_error() -> *mut c_void
{
    let err: Error = Error::msg("a single error.".to_string());
    wasm::to_js(err)
}

#[no_mangle]
pub extern "C" fn get_errors() -> *mut c_void
{
    let msg: Error = Error::msg("some message".to_string());
    let unmatched_code: Error = Error::unmatched_code(42, 43);
    let unsupported = Error::syntax(ErrorCode::UnsupportedCacheType, 99);

    let errors: Vec<Error> = vec![msg, unmatched_code, unsupported];

    wasm::to_js(errors)
}