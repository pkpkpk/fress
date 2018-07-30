(ns fress.runner
  (:require [cljs.test :refer-macros [run-tests]]
            [fress.roundtrip-test]
            [fress.impl.hopmap-test]
            [fress.impl.raw-test]))

(run-tests
 'fress.roundtrip-test
 'fress.impl.hopmap-test
 'fress.impl.raw-test)