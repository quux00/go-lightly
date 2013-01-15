(ns thornydev.go-lightly.core
  (:refer-clojure :exclude [peek take])
  (:import (java.io Closeable)
           (java.util ArrayList)
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

(defn shutdown
  "Stop (cancel) all futures started via the go macro and
   then call shutdown-agents to close down the entire Clojure
   agent/future thread pool."
  []
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

(declare closed?)

(defprotocol GoChannel
  (put [this val] "Put a value on a channel")
  (take [this] "Take the first value from a channel")
  (size [this] "Returns the number of values on the channel")
  (peek [this] "Retrieve, but don't remove, the first element on the channel"))

(deftype Channel [^LinkedTransferQueue q open? prefer?]
  GoChannel
  (put [this val]
    (if @(.open? this)
      (.transfer q val)
      (throw (IllegalStateException. "Channel is closed. Cannot 'put'."))))

  (take [this] (.take q))
  (peek [this] (.peek q))
  (size [this] 0)

  Object
  (toString [this]
    (let [stat-str (when-not @(.open? this) ":closed ")]
      (if-let [sq (seq (.toArray q))]
        (str stat-str "<=[ ..." sq "] ")
        (str stat-str "<=[] "))))

  Closeable
  (close [this]
    (reset! (.open? this) false)
    nil))

(deftype BufferedChannel [^LinkedBlockingQueue q open? prefer?]
  GoChannel
  (put [this val]
    (if @(.open? this)
      (.put q val)
      (throw (IllegalStateException. "Channel is closed. Cannot 'put'."))))
  (take [this] (.take q))
  (peek [this] (.peek q))
  (size [this] (.size q))

  Object
  (toString [this]
    (let [stat-str (when-not @(.open? this) ":closed ")]
      (str stat-str "<=[" (apply str (interpose " " (seq (.toArray q)))) "] ")))

  Closeable
  (close [this]
    (reset! (.open? this) false)
    nil))

(deftype TimeoutChannel [^LinkedBlockingQueue q open? prefer?]
  GoChannel
  (put [this val] (throw (UnsupportedOperationException.
                          "Cannot put values onto a TimeoutChannel")))
  (take [this] (.take q))
  (peek [this] (.peek q))
  (size [this] (.size q))

  Object
  (toString [this]
    (if (closed? this)
      ":closed <=[:go-lightly/timeout] "
      "<=[] "))

  Closeable
  (close [this]
    (reset! (.open? this) false)
    nil))

;; TODO: doesn't work - why not?
;; (defmethod print-method BufferedChannel
;;   [ch w]
;;   (print-method '<- w) (print-method (seq (.q ch) w) (print-method '-< w)))

(defn close [channel] (.close channel))

(defn closed? [channel]
  (not @(.open? channel)))

(defn prefer [channel]
  (reset! (.prefer? channel) true)
  channel)

(defn unprefer [channel]
  (reset! (.prefer? channel) false)
  channel)

(defn preferred? [channel]
  @(.prefer? channel))

(defn channel
  "If no size is specifies, returns a synchronous blocking channel.
   If a size is passed is in, returns a bounded asynchronous channel."
  ([] (->Channel (LinkedTransferQueue.) (atom true) (atom false)))
  ([^long capacity]
     (->BufferedChannel (LinkedBlockingQueue. capacity)
                        (atom true) (atom false))))

(defn preferred-channel
  ([] (prefer (channel)))
  ([capacity] (prefer (channel capacity))))

(defn timeout-channel
  "Create a channel that after the specified duration (in
   millis) will have the :go-lightly/timeout sentinel value"
  [duration-ms]
  (let [ch (->TimeoutChannel (LinkedBlockingQueue. 1) (atom true) (atom true))]
    (go& (do (Thread/sleep duration-ms)
             (.put (.q ch) :go-lightly/timeout)
             (close ch)))
    ch))


;; ---[ select and helper fns ]--- ;;

(defn- now [] (System/currentTimeMillis))

(defn- timed-out? [start duration]
  (when duration
    (> (now) (+ start duration))))

(defn- choose [ready-chans]
  (take (nth ready-chans (rand-int (count ready-chans)))))

(defn- filter-ready [chans]
  (seq (doall (filter #(not (nil? (peek %))) chans))))

(defn- attempt-select [pref-chans reg-chans]
  (if-let [ready-list (filter-ready pref-chans)]
    (choose ready-list)
    (when-let [ready-list (filter-ready reg-chans)]
      (choose ready-list))))

(defn probe-til-ready [pref-chans reg-chans timeout]
  (let [start (now)]
    (loop [ready-chan nil mcsec 200]
      (cond
       ready-chan ready-chan
       (timed-out? start timeout) :go-lightly/timeout
       :else (do (Thread/sleep 0 mcsec)
                 (recur (attempt-select pref-chans reg-chans)
                        (min 1500 (+ mcsec 25))))))))

(defn separate-preferred [channels]
  (loop [chans channels pref [] reg []]
    (if (seq chans)
      (if (preferred? (first chans))
        (recur (rest chans) (conj pref (first chans)) reg)
        (recur (rest chans) pref (conj reg (first chans))))
      [pref reg])))

(defn doselect [channels timeout nowait]
  (let [[pref-chans reg-chans] (separate-preferred channels)]
    (if-let [ready (attempt-select pref-chans reg-chans)]
      ready
      (when-not nowait
        (probe-til-ready pref-chans reg-chans timeout)))))

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
  "Like select, selects one message from the channels passed in
   with the same behavior except that if no channel has a message
   ready, it immediately returns nil or the sentinel keyword value
   passed in as the last argument."
  [& channels]
  (let [[chans sentinel] (parse-nowait-args channels)
        result (doselect chans nil :nowait)]
    (if (and (nil? result) (seq? sentinel))
      (first sentinel)
      result)))


;; ---[ channels to collection/sequence conversions ]--- ;;

(defn channel->seq
  "Takes a snapshot of all values on a channel *without* removing
   the values from the channel. Returns a (non-lazy) seq of the values.
   Generally recommended for use with a buffered channel, but will return
   return a single value if a producer is waiting to put one on."
  [ch]
  (seq (.toArray (.q ch))))

(defn channel->vec
  "Takes a snapshot of all values on a channel *without* removing
   the values from the channel. Returns a vector of the values.
   Generally recommended for use with a buffered channel, but will return
   return a single value if a producer is waiting to put one on."
  [ch]
  (vec (.toArray (.q ch))))

(defn drain
  "Removes all the values on a channel and returns them as a non-lazy seq.
   Generally recommended for use with a buffered channel, but will return
   a pending transfer value if a producer is waiting to put one on."
  [ch]
  (let [al (ArrayList.)]
    (.drainTo (.q ch) al)
    (seq al)))

(defn lazy-drain
  "Lazily removes values from a channel. Returns a Cons lazy-seq until
   it reaches the end of the channel.
   Generally recommended for use with a buffered channel, but will return
   on or more values one or more producer(s) is waiting to put a one or
   more values on.  There is a race condition with producers when using."
  [ch]
  (if-let [v (.poll (.q ch))]
    (cons v (lazy-seq (lazy-drain ch)))
    nil))

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
