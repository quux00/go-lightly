(ns thornydev.go-lightly.core
  (:import (java.util ArrayList)
           (java.util.concurrent LinkedTransferQueue TimeUnit
                                 LinkedBlockingQueue TimeoutException)))

;; ---[ go routines ]--- ;;

(def inventory (atom []))

(defmacro go
  "Launches a Clojure future as a 'go-routine' and returns the future.
   It is not necessary to keep a reference to this future, however.
   Instead, you can call the accompanying stop function to
   shutdown (cancel) all futures created by this function."
  [& body]
  `(let [fut# (future ~@body)]
     (swap! inventory conj fut#)
     fut#))

(defn stop
  "Stop (cancel) all futures started via the go macro.
   This should only be called when you are finished with
   all go routines running in your app, ideally at the end
   of the program.  It can be reused on a new set of go
   routines, as long as they were started after this stop
   fn returned, as it clears an cached of remembered go
   routines that could be subject to a race condition."
  []
  (doseq [f @inventory] (future-cancel f))
  (reset! inventory [])
  nil)

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

   It is intended to be used with channels for communication
   between threads.  This thread is not part of a managed Thread 
   pool so cannot be directly shutdown.  It will stop either when 
   all non-daemon threads cease or when you stop it some ad-hoc way."
  [& body]
  `(doto (Thread. (fn [] (do ~@body))) (.setDaemon true) (.start)))


;; ---[ channels and channel fn ]--- ;;

(defn channel
  "If no size is specifies, returns a TransferQueue as a channel.
   If a size is passed is in, returns a bounded BlockingQueue."
  ([] (LinkedTransferQueue.))
  ([size] (LinkedBlockingQueue. size)))

(defn timeout-channel
  "Create a channel that after the specified duration (in
   millis) will have the :go-lightly/timeout sentinel value"
  [duration]
  (let [ch (channel)]
    (go& (do (Thread/sleep duration)
             (.put ch :go-lightly/timeout)))
    ch))


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


;; ---[ select and helper fns ]--- ;;

(defn- now [] (System/currentTimeMillis))

(defn- timed-out? [start duration]
  (when duration
    (> (now) (+ start duration))))

(defn- choose [ready-chans]
  (.take (nth ready-chans (rand-int (count ready-chans)))))

(defn- peek-channels [channels]
  (let [ready (doall (keep #(when-not (nil? (.peek %)) %) channels))]
    (if (seq ready)
      (nth ready (rand-int (count ready)))  ;; pick at random if >1 ready
      (Thread/sleep 0 500))))

;; TODO: if any of these channels are timeout channels, they
;; need to be read preferentially, so we would need to add
;; some polymorphism or flags to detect which are timeout
;; channels => can do this by creating our own protocols for
;; the channel and have a defrecord type of TimerChannel
;; vs. regular GoChannel
(defn- probe-til-ready [channels timeout]
  (let [start (now)]
    (loop [chans channels ready-chan nil]
      (cond
       ready-chan (.take ready-chan)
       (timed-out? start timeout) :go-lightly/timeout
       :else (recur channels (peek-channels channels))))))

(defn- doselect [channels timeout nowait]
  (let [ready (doall (filterv #(not (nil? (.peek %))) channels))]
    (if (seq ready)
      (choose ready)
      (when-not nowait
        (probe-til-ready channels timeout)))))

(defn- parse-nowait-args [channels]
  (if (keyword? (last channels))
    (split-at (dec (count channels)) channels)
    [channels nil]))

;; public select fns

(defn select
  "Select one message from the channels passed in."
  [& channels]
  (doselect channels nil nil))

(defn select-timeout
  "Like select, selects one message from the channels passed in
   with the same behavior except that a timeout is in place that
   if no message becomes available before the timeout expires, a
   :go-lightly/timeout sentinel message will be returned."
  [timeout & channels]
  (doselect channels timeout nil))

(defn select-nowait
  [& channels]
  "Like select, selects one message from the channels passed in
   with the same behavior except that if no channel has a message
   ready, it immediately returns nil or the sentinel keyword value
   passed in as the last argument."
  (let [[chans sentinel] (parse-nowait-args channels)
        result (doselect chans nil :nowait)]
    (if (and (nil? result) (seq? sentinel))
      (first sentinel)
      result)))



;; ---[ channels to collection/sequence conversions ]--- ;;

;; TODO: make these polymorphic based on channel type?

(defn channel->vec [ch]
  (vec (.toArray ch)))

(defn drain-to-vec [ch]
  (let [al (ArrayList.)]
    (.drainTo ch al)
    (vec al)))

(defn channel->seq [ch]
  (seq (.toArray ch)))

;; TODO: need channel->lazy-seq


;; ---[ helper macros ]--- ;;

;; Credit to mikera
;; from: http://stackoverflow.com/a/6697469/871012
;; TODO: is there a way to do this where the future can
;;       return something or do something before being
;;       cancelled?  Would require an abstraction around
;;       future ...
(defmacro with-timeout [millis & body]
  `(let [fut# (future ~@body)]
     (try
       (.get fut# ~millis TimeUnit/MILLISECONDS)
       (catch TimeoutException x# 
         (do
           (future-cancel fut#)
           nil)))))


