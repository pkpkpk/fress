(ns fress.macros)

(defmacro >>> [a b]
  `(~'.floor ~'js/Math (/ ~a (~'.pow ~'js/Math 2 ~b))))

(defmacro << [a b]
  `(bit-shift-left ~a ~b))
