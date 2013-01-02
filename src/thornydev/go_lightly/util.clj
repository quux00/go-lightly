(ns thornydev.go-lightly.util
  (:import (java.util.concurrent SynchronousQueue LinkedTransferQueue)))

;; producers should use .transfer
;; consumers should use .peek (check if anything on the queue)
;; and .take or .poll
(defn go-channel [] (LinkedTransferQueue.))

(defn sync-channel [] (SynchronousQueue.))

;; (def ^{:private true} inventory (atom []))

(def inventory (atom []))

(defmacro go
  "Launches a Clojure future as a 'go-routine' and returns the future.
   It is not necessary to keep a reference to this future, however.
   Instead, you can call the accompanying stop function to
   shutdown all futures created by this function."
  [& body]
  `(let [fut# (future ~@body)]
     (swap! inventory conj fut#)
     fut#))

(defn stop
  "Stop (cancel) all futures started via the go macro."
  []
  (doseq [f @inventory] (future-cancel f)))

(defn shutdown []
  "Stop (cancel) all futures started via the go macro and
   then call shutdown-agents to close down the entire Clojure
   agent/future thread pool."
  (stop)
  (shutdown-agents))


(defmacro go&
  "Launch a 'go-routine' like deamon Thread to execute the body. 
   This macro does not yield a future so it cannot be dereferenced.
   Instead it returns the Java Thread itself.

   It is intended to be used with go-channels for communication
   between threads.  This thread is not part of a managed Thread 
   pool so cannot be directly shutdown.  It will stop either when 
   all non-daemon threads cease or when you stop it some ad-hoc way."
  [& body]
  `(doto (Thread. (fn [] (do ~@body))) (.setDaemon true) (.start)))


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
