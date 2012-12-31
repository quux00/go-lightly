(ns thornydev.go-lightly.util
  (:import (java.util.concurrent SynchronousQueue)))

(defn sync-channel [] (SynchronousQueue.))

;; go: build own daemon thread pool
(defn goda [func]
  (doto (Thread. func) (.setDaemon true) (.start)))

;; go: future cancel version (needs stop or shutdown)
(def inventory (atom []))

(defn go [func]
  (let [fut (future (func))]
    (swap! inventory conj fut)))

(defn stop []
  (doseq [f @inventory] (future-cancel f)))

(defn shutdown []
  (stop)
  (shutdown-agents))



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
