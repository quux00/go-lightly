(ns thornydev.go-lightly.core-test
  (:refer-clojure :exclude [peek take])
  (:use clojure.test
        thornydev.go-lightly.core))

;; ---[ helper fns ]--- ;;

(defn- put-with-sleeps [ch n]
  (dotimes [_ 50]
    (Thread/sleep (rand-int 125))
    (put ch n)))

(defn- put-20 [& args]
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
      (stop))))

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
      (Thread/sleep 10)
      (let [results (for [i (range 10)]
                      (select ch1 ch2 ch3))]
        (is (= 10 (count results)))
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
  (let [ch1 (channel) ch2 (channel) timeout 25]
    (go (put-with-sleeps ch1 1))
    (go (put-with-sleeps ch2 2))
    (loop [cnt 1000 selected #{}]
        (cond
         (zero? cnt) (is false "After 1000 did not see entry of each channel")
         (= #{1 2 :go-lightly/timeout} selected) (is true)
         :else (recur (dec cnt)
                      (conj selected (select-timeout timeout ch1 ch2))))))
  (stop))


(deftest test-select-nowait
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
    )
  (stop))


(deftest test-close-channel
  (testing "closing a buffered channel should let existing items be removed"
    (let [ch (channel 50)]
      (put-20 ch)
      (is (not (closed? ch)))
      (is (= 0 (take ch)))
      ;; now close
      (close ch)
      (is (closed? ch))
      (is (= 1 (take ch)))
      (is (= 2 (take ch)))
      (is (= 17 (size ch)))
      (is (thrown? IllegalStateException (put ch :nono)))
      (is (= 3 (take ch)))
      (is (= 16 (size ch)))))

  (testing "closing a (sync) channel will cause it to be empty"
    (let [ch (channel)]
      (go (put-with-sleeps ch 1))
      (is (not (closed? ch)))
      (is (= 1 (take ch)))
      ;; now close ch -> will throw (silent) exception in go routine
      (close ch)
      (is (closed? ch))
      (is (= nil (peek ch)))))
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

      ;; TODO: this doesn't actually test what we want => want to make sure
      ;; both values are seen 
      (dotimes [_ 10]
        (let [sval (select ch3 ch1 ch2)]
          (is (or (= :foo sval)
                  (= :quux sval)))))
      (testing "make a channel a prefered after creating"
        ;; prefer modifies the channel in place
        (prefer ch3)
        (is (preferred? ch3))
        (dotimes [i 10]
          (is (= :quux (select ch1 ch2 ch3))))
        )
      (testing "make channel unpreferred should not allow equal choice between remaining non-empty channels"
        (unprefer ch3)
        (is (not (preferred? ch3)))

        ;; TODO: this doesn't actually test what we want => want to make sure
        ;; both values are seen 
        (dotimes [_ 10]
          (let [sval (select ch3 ch1 ch2)]
            (is (or (= :foo sval)
                    (= :quux sval)))))
        )
      )
    )
  )

(println (run-tests 'thornydev.go-lightly.core-test))
