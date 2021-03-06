(ns thornydev.go-lightly.util)

;; right now this is for use only with the lamina channels which can be closed
;; copied and modified from with-open from clojure.core
(defmacro with-channel-open
  "bindings => [name init ...]

  Evaluates body in a try expression with names bound to the values
  of the inits, and a finally clause that calls (close name) on each
  name in reverse order."
  [bindings & body]
  (assert (vector? bindings) "a vector for its binding")
  (assert (even? (count bindings)) "an even number of forms in binding vector")
  (cond
   (= (count bindings) 0) `(do ~@body)
   (symbol? (bindings 0)) `(let ~(subvec bindings 0 2)
                             (try
                               (with-channel-open ~(subvec bindings 2) ~@body)
                               (finally
                                 (~'close ~(bindings 0)))))
   :else (throw (IllegalArgumentException.
                 "with-open only allows Symbols in bindings"))))
