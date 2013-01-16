(ns thornydev.go-lightly.core-test
  (:refer-clojure :exclude [peek take])
  (:use clojure.test
        thornydev.go-lightly.core))

;; ---[ helper fns ]--- ;;

(defn- test-routine [ch n]
  (dotimes [_ 50]
    (Thread/sleep (rand-int 125))
    (put ch n)))

(defn- test-routine-track-enqueues [ch qlog]
  (loop [value (rand-int 6000)]
    (Thread/sleep (rand-int 100))
    (put ch value)
    (swap! qlog conj value)
    (recur (rand-int 6000))))


;; ---[ tests ]--- ;;

(deftest test-go&
  (testing "go& routines"
    (let [ch1 (channel) ch2 (channel)]
      (go& (put ch1 1))
      (go& (put ch2 2))
      (is (= 1 (take ch1)))
      (is (= 2 (take ch2))))))

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

  ;; (testing "timeout channel with other channels"
  ;;   (let [ch1 (channel)
  ;;         ch2 (channel)
  ;;         tch (timeout-channel 250)
  ;;         fnext-msg (partial select ch1 ch2 tch)]
  ;;     ;; this basically tests that it ends => not an infinite loop
  ;;     (go (test-routine ch1 1))
  ;;     (go (test-routine ch1 2))
  ;;     (loop [msg (fnext-msg)]
  ;;       (when-not (= msg :go-lightly/timeout)
  ;;         (is (some #{1 2} [msg]))
  ;;         (recur (fnext-msg)))))
  ;;   (stop))
  )

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
      (go& (test-routine-track-enqueues ch1 qlog))
      (go& (test-routine-track-enqueues ch2 qlog))
      (go& (test-routine-track-enqueues ch3 qlog))
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
      (go& (test-routine ch1 1))
      (go& (test-routine ch2 2))
      (go& (test-routine ch3 3))
      (go& (test-routine ch4 4))
      (loop [cnt 1000 selected #{}]
        (cond
         (zero? cnt) (is false "After 1000 did not see entry of each channel")
         (= #{1 2 3 4} selected) (is true)
         :else (recur (dec cnt)
                      (conj selected (select ch1 ch2 ch3 ch4)))))))
  (stop))

(deftest test-select-timeout
  (let [ch1 (channel) ch2 (channel) timeout 25]
    (go& (test-routine ch1 1))
    (go& (test-routine ch2 2))
    (loop [cnt 1000 selected #{}]
        (cond
         (zero? cnt) (is false "After 1000 did not see entry of each channel")
         (= #{1 2 :go-lightly/timeout} selected) (is true)
         :else (recur (dec cnt)
                      (conj selected (select-timeout timeout ch1 ch2)))))))


(deftest test-select-nowait
  (let [ch1 (channel) ch2 (channel) ch3 (channel) ch4 (channel 10)]
    (let [results (set
                   (doall
                    (for [_ (range 10)]
                      (select-nowait ch1 ch2 :foo-sentinel))))]
      (is (contains? results :foo-sentinel)))

    (go (test-routine ch1 1))
    (go (test-routine ch2 2))
    (go (test-routine ch3 3))
    (go (test-routine ch4 4))

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


(println (run-tests 'thornydev.go-lightly.core-test))

