(ns thornydev.go-lightly.core-test
  (:refer-clojure :exclude [peek take])
  (:use clojure.test
        thornydev.go-lightly.core))

;; ---[ helper fns ]--- ;;

(defn- put-with-sleeps [ch n]
  (dotimes [_ 50]
    (Thread/sleep (rand-int 125))
    (put ch n)))

(defn- put-20
  "puts the provided seconds arg 20 times into the channel
   if no second arg provided, then puts the series 0 .. 19"
  [& args]
  (let [[ch value] args]
    (dotimes [i 20]
      (put ch (or value i)))))

(defn- put-with-sleeps-track-enqueues [ch qlog]
  (loop [value (rand-int 6000)]
    (Thread/sleep (rand-int 100))
    (put ch value)
    (swap! qlog conj value)
    (recur (rand-int 6000))))


;; ---[ tests ]--- ;;

(deftest test-basic-channel-functions-with-go&
  (testing "test take, peek, put, size on Channels"
    (let [ch1 (channel) ch2 (channel)]
      (go& (put ch1 1))
      (go& (put ch2 2))
      (Thread/sleep 50)
      (is (= 0 (size ch1)))
      (is (= 0 (size ch2)))
      (is (= 1 (peek ch1)))
      (is (= 2 (peek ch2)))
      (is (= 1 (take ch1)))
      (is (= 2 (take ch2)))
      (is (= 0 (size ch1)))
      (is (= 0 (size ch2)))
      (is (nil? (peek ch1)))
      (is (nil? (peek ch2)))))

  (testing "test take, peek, put, size on BufferedChannels"
    (let [ch1 (channel 2) ch2 (channel 100)]
      (go& (put-20 ch1))  ;; will block after 2, so go& routine
      (put-20 ch2)
      (with-timeout 200
        (while (not= 2 (size ch1))
          (Thread/sleep 10)))
      (is (= 2 (size ch1)))
      (is (= 20 (size ch2)))
      (is (= 0 (peek ch1)))
      (is (= 0 (peek ch2)))
      (is (= 0 (take ch1)))
      (is (= 0 (take ch2)))
      (Thread/sleep 5)
      (is (= 2 (size ch1)))
      (is (= 19 (size ch2)))
      (is (= 1 (peek ch1)))
      (is (= 1 (peek ch2))))))

(deftest test-go-and-stop
  (testing "start and stop go routines"
    (let [ch1 (channel) ch2 (channel)]
      (go (put ch1 1))
      (go (put ch2 2))
      (is (= 1 (take ch1)))
      (is (= 2 (take ch2)))
      ))
  (stop))

(deftest test-timeout-channel
  (testing "timeout channel has :go-lightly/timeout enqueued if nothing enqueued"
    (let [ch (timeout-channel 100)]
      (is (= :go-lightly/timeout (take ch)))))

  (testing "timeout channel with other channels"
    (let [ch1 (channel)
          ch2 (channel)
          tch (timeout-channel 200)]
      (go (put-with-sleeps ch1 1))
      (go (put-with-sleeps ch1 2))
      (loop [cnt 1000 selected #{}]
        (cond
         (zero? cnt) (is false "After 1000 did not see entry of each channel")
         (= #{1 2 :go-lightly/timeout} selected) (is true)
         :else (recur (dec cnt)
                      (conj selected (select ch1 ch2 tch)))))
      )
    (stop)))

(deftest test-select-basic
  (testing "pre-enqueue values on 3 sync channels and make sure get all"
    (let [ch1 (channel)
          ch2 (channel)
          ch3 (channel)]
      (go (put ch1 1))
      (go (put ch1 2))
      (go (put ch2 10))
      (go (put ch2 20))
      (go (put ch3 100))
      (go (put ch3 200))
      (is (= #{1 2 10 20 100 200}
             (set (for [_ (range 6)]
                    (select ch1 ch2 ch3)))))))
  (testing "test with go routines adding more onto the sync channels"
    (let [ch1 (channel)
          ch2 (channel)
          ch3 (channel)
          qlog (atom #{})]
      (go (put-with-sleeps-track-enqueues ch1 qlog))
      (go (put-with-sleeps-track-enqueues ch2 qlog))
      (go (put-with-sleeps-track-enqueues ch3 qlog))
      (let [results (for [i (range 10)]
                      (select ch1 ch2 ch3))]
        (is (= 10 (count results)))
        (with-timeout 100
          (while (not= 10 (count @qlog))))
        (is (= (set results) @qlog)))))
  
  (testing "test with mix of buffered and sync channels and go routines"
    (let [ch1 (channel 2)
          ch2 (channel 10)
          ch3 (channel 1000)
          ch4 (channel)
          qlog (atom #{})]
      (go (put-with-sleeps ch1 1))
      (go (put-with-sleeps ch2 2))
      (go (put-with-sleeps ch3 3))
      (go (put-with-sleeps ch4 4))
      (loop [cnt 1000 selected #{}]
        (cond
         (zero? cnt) (is false "After 1000 did not see entry of each channel")
         (= #{1 2 3 4} selected) (is true)
         :else (recur (dec cnt)
                      (conj selected (select ch1 ch2 ch3 ch4)))))))
  (stop))

(deftest test-select-timeout
  (testing "single channel"
    (let [ch (channel)]
      (is (nil? (select-timeout 50 ch)))
      (go (put ch :foo))
      (with-timeout 50 ;; wait for element to be on the channel
        (while (nil? (peek ch))))
      (is (= :foo (select-timeout 50 ch)))))
  
  (testing "is OK to put false on a channel, but not nil"
    (let [ch (channel 10)]
      (is (nil? (select-timeout 50 ch)))
      (put ch false)
      (is (= false (select-timeout 50 ch)))
      ;; you can't put nil on a LinkedBlockingQueue (or LinkedTransferQueue)
      ;; the Java class throws an NPE
      (is (thrown? NullPointerException (put ch nil)))))
  
  (testing "multiple channels"
    (let [ch1 (channel) ch2 (channel) timeout 25]
      (go (put-with-sleeps ch1 1))
      (go (put-with-sleeps ch2 2))
      (loop [cnt 1000 selected #{}]
        (cond
         (zero? cnt) (is false "After 1000 did not see entry of each channel")
         (= #{1 2 nil} selected) (is true)
         :else (recur (dec cnt)
                      (conj selected (select-timeout timeout ch1 ch2)))))))
  (stop))


(deftest test-select-nowait
  (testing "single channel"
    (let [ch (channel)]
      (is (nil? (select-nowait ch)))
      (is (= :nada (select-nowait ch :nada)))
      (go (put ch :foo))
      (with-timeout 50 ;; wait for element to be on the channel
        (while (nil? (peek ch))))
      (is (= :foo (select-nowait ch :nada)))))

  (testing "single buffered channel"
    (let [ch (channel 5)]
      (is (nil? (select-nowait ch)))
      (is (= :nada (select-nowait ch :nada)))
      (put ch :foo)
      (is (= :foo (select-nowait ch :nada)))
      (is (nil? (select-nowait ch)))
      (is (= :nada (select-nowait ch :nada)))))

  (testing "multiple channels (mixed sync and buffered)"
    (let [ch1 (channel) ch2 (channel) ch3 (channel) ch4 (channel 10)]
      (let [results (set
                     (doall
                      (for [_ (range 10)]
                        (select-nowait ch1 ch2 :foo-sentinel))))]
        (is (contains? results :foo-sentinel)))

      (go (put-with-sleeps ch1 1))
      (go (put-with-sleeps ch2 2))
      (go (put-with-sleeps ch3 3))
      (go (put-with-sleeps ch4 4))

      (loop [cnt 5000 selected #{}]
        (Thread/sleep 20)
        (cond
         (zero? cnt) (is false "After 1000 did not see entry of each channel")
         (= #{1 2 3 4} selected)     (is true)
         (= #{1 2 3 4 nil} selected) (is true)
         :else (recur (dec cnt)
                      (conj selected (select-nowait ch1 ch2 ch3 ch4)))))
      ))
  (stop))


(deftest test-selectf-basic
  (testing "pre-enqueue values on 3 sync channels and make sure get all"
    (let [ch1 (channel)
          ch2 (channel)
          ch3 (channel)
          acc (atom 0)]
      (go (put ch1 1))
      (go (put ch1 2))
      (go (put ch2 10))
      (go (put ch2 20))
      (go (put ch3 100))
      (go (put ch3 200))

      (let [results (set
                     (for [_ (range 6)]
                       (selectf
                        ch3 #(swap! acc + %)
                        ch2 #(swap! acc + %)
                        ch1 #(swap! acc + %))))]
        (= #{1 3 13 113 133 333}))
      (is (= 333 @acc))))

  (testing "test with go routines adding more onto the sync channels"
    (let [ch1 (channel)
          ch2 (channel)
          ch3 (channel)
          qlog (atom #{})]
      (go (put-with-sleeps-track-enqueues ch1 qlog))
      (go (put-with-sleeps-track-enqueues ch2 qlog))
      (go (put-with-sleeps-track-enqueues ch3 qlog))
      (let [results (for [i (range 10)]
                      (selectf ch1 #(identity %)
                               ch2 #(identity %)
                               ch3 #(identity %)))]
        (is (= 10 (count results)))
        (with-timeout 100
          (while (not= 10 (count @qlog))))
        (is (= (set results) @qlog)))))
  (stop))


(deftest test-selectf-with-default
  (testing "single channel"
    (let [ch (channel)]
      (is (= :nada (selectf ch #(identity %)
                            :default #(identity :nada))))
      (go (put ch :foo))
      (with-timeout 50 ;; wait for element to be on the channel
        (while (nil? (peek ch))))
      (is (= :foo (selectf ch #(identity %)
                           :default #(identity :nada))))))

  (testing "multiple channels (mixed sync and buffered)"
    (let [ch1 (channel) ch2 (channel) ch3 (channel) ch4 (channel 10)
          result-ch (channel 3000)]
      (let [results (set
                     (doall
                      (for [_ (range 10)]
                        (select-nowait ch1 ch2 :foo-sentinel))))]
        (is (contains? results :foo-sentinel)))

      (go (put-with-sleeps ch1 1))
      (go (put-with-sleeps ch2 2))
      (go (put-with-sleeps ch3 3))
      (go (put-with-sleeps ch4 4))

      (loop [cnt 2000]
        (Thread/sleep 10)
        (selectf ch1 #(put result-ch %)
                 ch2 #(put result-ch %)
                 ch3 #(put result-ch %)
                 ch4 #(put result-ch %)
                 :default #(put result-ch :default))
        (cond
         (zero? cnt) (is false "No match seen after 2000 cycles")
         (= #{1 2 3 4 :default} (set (channel->seq result-ch))) (is true)
         :else (recur (dec cnt))))
      ))
  (stop))


(deftest test-selecf-with-timeout-channel
  (testing "timeout channel with other channels"
    (let [ch1 (channel)
          ch2 (channel)
          tch (timeout-channel 200)
          result-ch (channel 2000)]
      (go (put-with-sleeps ch1 1))
      (go (put-with-sleeps ch1 2))
      (loop [cnt 1500]
        (Thread/sleep 2)
        (selectf ch1 #(put result-ch %)
                 ch2 #(put result-ch %)
                 tch #(put result-ch %))
        (cond
         (zero? cnt) (is false "After 1500 did not see entry of each channel")
         (= #{1 2 :go-lightly/timeout} (set (channel->vec result-ch))) (is true)
         :else (recur (dec cnt))))
      )
    (stop)))

;; TODO: convert this to use selectf
(deftest test-selectf-with-preferred-channel
  (testing "prefered channels are always read from first if values present"
    (let [ch1 (channel)
          ch2 (preferred-channel 100)
          ch3 (preferred-channel 100)
          ch4 (channel 100)
          result-ch (channel 100)]
      (go (put-20 ch1 :foo))
      (put-20 ch2)  ;; values 0 .. 19
      (put-20 ch3 :baz)  
      (put-20 ch4 :quux)

      (dotimes [_ 40]
        (selectf ch1 #(throw (RuntimeException. (str "should not have selected " %)))
                 ch2 #(put result-ch %)
                 ch3 #(put result-ch %)
                 ch4 #(throw (RuntimeException. (str "should not have selected " %))))
        )
      (is (= (into #{:baz} (range 20))
             (set (drain result-ch))))

      ;; now should read from unpreferred
      (selectf ch1 #(put result-ch %)
              ch2 #(throw (RuntimeException. (str "should not have selected " %)))
              ch3 #(throw (RuntimeException. (str "should not have selected " %))))
      (is (= :foo (take result-ch)))
      
      (selectf ch2 #(throw (RuntimeException. (str "should not have selected " %)))
              ch3 #(throw (RuntimeException. (str "should not have selected " %)))
              ch4 #(put result-ch %))
      (is (= :quux (take result-ch)))

      (testing "make channel unpreferred - should no longer be preferentially read"
        (put-20 ch2 :bar)  
        (put-20 ch3 :baz)  
        (unprefer! ch3)
        (dotimes [_ 20]
          (selectf ch1 #(throw (RuntimeException. (str "should not have selected " %)))
                   ch2 #(put result-ch %)
                   ch3 #(throw (RuntimeException. (str "should not have selected " %)))
                   ch4 #(throw (RuntimeException. (str "should not have selected " %)))))
        (is (= #{:bar} (set (drain result-ch)))))))

  (stop))


(deftest test-close-channel
  (testing "closing a buffered channel should let existing items be removed"
    (let [ch (channel 50)]
      (put-20 ch)
      (is (not (closed? ch)))
      (is (= 0 (take ch)))
      ;; now close
      (close! ch)
      (is (closed? ch))
      (is (= 1 (take ch)))
      (is (= 2 (take ch)))
      (is (= 17 (size ch)))
      (is (thrown? IllegalStateException (put ch :nono)))
      (is (= 3 (take ch)))
      (is (= 16 (size ch)))))

  (testing "closing a (sync) channel should let existing item to be removed"
    (let [ch (channel)]
      (go (put ch 1))
      (is (not (closed? ch)))
      (is (= 1 (peek ch)))
      ;; now close ch -> will throw (silent) exception in go routine
      (close! ch)
      (is (closed? ch))
      (is (= 1 (peek ch)))
      (is (= 1 (take ch)))))
  (stop))


(deftest test-preferred-channel
  (testing "prefered channels are always read from first if values present"
    (let [ch1 (channel)
          ch2 (preferred-channel 100)
          ch3 (channel 100)]
      (is (not (preferred? ch1)))
      (is (preferred? ch2))
      (is (not (preferred? ch3)))
      (go (put-20 ch1 :foo))
      (put-20 ch2)  ;; values 0 .. 19
      (put-20 ch3 :quux)
      
      (testing "order of channels in select doesn't matter"
        (dotimes [i 20]
          (is (= i (select ch1 ch2 ch3))))

        (put-20 ch2)
        (dotimes [i 20]
          (is (= i (select ch2 ch3 ch1)))))

      ;; now preferred channel is empty, so unpreferred is read from
      (loop [cnt 1000 selected #{nil}]
        (cond
         (zero? cnt) (is false "After 1000 checks did not see entry of each channel")
         (= #{:foo :quux nil} selected) (is true)
         :else (recur (dec cnt)
                      (conj selected (select-timeout 20 ch3 ch1 ch2)))))
      
      (testing "make a channel a prefered after creating"
        ;; prefer! modifies the channel in place
        (prefer! ch3)
        (is (preferred? ch3))
        (dotimes [i 10]
          (is (= :quux (select ch1 ch2 ch3)))))

      (testing "make channel unpreferred should now allow equal
                choice between remaining non-empty non-preferred channels"
        ;; unprefer! modifies the channel in place
        (unprefer! ch3)
        (is (not (preferred? ch3)))

        (loop [cnt 1000 selected #{:go-lightly/timeout}]
          (cond
           (zero? cnt) (is false "After 1000 checks did not see entry of each channel")
           (= #{:foo :quux :go-lightly/timeout} selected) (is true)
           :else (recur (dec cnt)
                        (conj selected (select-timeout 20 ch2 ch1 ch3))))))))
  (stop))


(deftest test-channel->seq-and-channel-vec
  (testing "buffered channels"
    (testing "channel->seq returns seq of all elements on channel without removing them"
      (let [ch1 (channel 30)]
        (put-20 ch1) ;; puts 0 .. 19
        (is (= 20 (size ch1)))
        (let [chseq (channel->seq ch1)]
          (is (seq? chseq))
          (is (= 20 (count chseq)))
          (is (= #{1 3 5 7 9 11 13 15 17 19}
                 (set (filter odd? chseq))))
          )
        ;; test not removed from channel
        (is (= 20 (size ch1)))))

    (testing "channel->vec returns vector of all elements on channel without removing them"
      (let [ch1 (channel 30)]
        (put-20 ch1) ;; puts 0 .. 19
        (is (= 20 (size ch1)))
        (let [chvec (channel->vec ch1)]
          (is (vector? chvec))
          (is (= 20 (count chvec)))
          (is (= #{1 3 5 7 9 11 13 15 17 19}
                 (set (filter odd? chvec))))
          )
        ;; test not removed from channel
        (is (= 20 (size ch1)))))

    (testing "channel->vec and channel->seq return the same values"
      (let [ch1 (channel 30)]
        (put-20 ch1) ;; puts 0 .. 19
        (is (= 20 (size ch1)))
        (let [chvec (channel->vec ch1)
              chseq (channel->seq ch1)]
          (is (= (seq chvec) chseq)))))

    (testing "channel->vec and channel->seq return empty coll if channel is empty"
      (let [ch (channel 4)]
        (is (= 0 (size ch)))
        (is (empty? (channel->vec ch)))
        (is (empty? (channel->seq ch)))))

    (testing "channel->vec and channel->seq return all elements on a closed channel"
      (let [ch (channel 30)]
        (put-20 ch) ;; puts 0 .. 19
        (close! ch)
        (is (= 20 (size ch)))
        (let [chvec (channel->vec ch)
              chseq (channel->seq ch)]
          (is (= 20 (count chseq)))
          (is (= #{1 3 5 7 9 11 13 15 17 19}
                 (set (filter odd? chseq))))
          (is (= (seq chvec) chseq)))
        ;; test not removed from channel
        (is (= 20 (size ch))))))

  (testing "synchronous channels"
    (testing "channel->seq and channel->vec return the first/only element pending on the queue"
      (let [ch (channel)]
        (go (put-20 ch)) ;; puts 0 .. 19
        (with-timeout 50 ;; wait for first element to be on the channel
          (while (nil? (peek ch))))

        (let [chseq (channel->seq ch)]
          (is (= 1 (count chseq)))
          (is (= 0 (first chseq))))
        
        (let [chvec (channel->vec ch)]
          (is (= 1 (count chvec)))
          (is (= 0 (first chvec))))

        (is (= 0 (peek ch)))))
    (testing "channel->seq and channel->vec return seq/vec with one value when sync channel is closed"
      (let [ch (channel)]
        (go (put-20 ch)) ;; puts 0 .. 19
        (with-timeout 50 ;; wait for first element to be on the channel
          (while (nil? (peek ch))))
        (close! ch)
        (let [chseq (channel->seq ch)
              chvec (channel->vec ch)]
          (is (= 0 (first chseq)))
          (is (= 0 (first chvec))))
        ;; channel->xxx methods do not remove the value from the channel
        (is (= 0 (peek ch)))))))


(deftest test-drain
  (testing "buffered channel"
    (testing "drain removes and returns all enqueued values"
      (let [ch (channel 50)]
        (put-20 ch :foo)
        (is (= 20 (size ch)))
        (let [seqch (drain ch)]
          (is (= 20 (count seqch)))
          (is (= :foo (first seqch) (last seqch))))
        (is (= (zero? (size ch)))))))

  (testing "draininng an empty channel returns empty seq"
    (is (empty? (drain (channel 2)))))

  (testing "synchronous channel"
    (testing "drain removes first value"
      (let [ch (channel)]
        (go (put-20 ch))
        (with-timeout 50
          (while (nil? (peek ch))))

        (let [seqch (drain ch)]
          ;; the behavior of drain on a synchronous channel is undefined
          ;; on some systems it only returns 1 value, on others all 20
          ;; only guarantee is that it returns at least 1 value if there
          ;; is anything pending on the channel
          (is (> (count seqch)) 0)
          (is (= 0 (first seqch))))
        )
      )
    (testing "draining a sync channel with no pending put returns empty seq"
      (is (empty? (drain (channel)))))
    )
  (stop))


(deftest test-lazy-drain
  (testing "buffered channel"
    (testing "lazy-drain removes and returns all enqueued values"
      (let [ch (channel 50)]
        (put-20 ch :foo)
        (is (= 20 (size ch)))
        (let [seqch (lazy-drain ch)]
          (is (= 20 (count seqch)))
          (is (= :foo (first seqch) (last seqch))))
        (is (= (zero? (size ch)))))))

  (testing "lazy-draining an empty channel returns empty seq"
    (is (empty? (drain (channel 2)))))

  (testing "synchronous channel"
    (testing "behaving is partially undefined: returns at least one element if channel is not empty"
      (let [ch (channel)]
        (go (put-20 ch))
        (with-timeout 50
          (while (nil? (peek ch))))

        (let [seqch (lazy-drain ch)]
          (is (<= 1 (count seqch)))
          (is (= 0 (first seqch))))))

    (testing "draining a sync channel with no pending put returns empty seq"
      (is (empty? (lazy-drain (channel)))))
    )
  (testing "lazy-drain with closed buffered channel"
    (testing "lazy-drain removes and returns all enqueued values from closed channel"
      (let [ch (channel 50)]
        (put-20 ch :foo)
        (close! ch)
        (is (= 20 (size ch)))
        (let [seqch (lazy-drain ch)]
          (is (= 20 (count seqch)))
          (is (= :foo (first seqch) (last seqch))))
        (is (= (zero? (size ch)))))))
  (stop))

;; (println (run-tests 'thornydev.go-lightly.core-test))
