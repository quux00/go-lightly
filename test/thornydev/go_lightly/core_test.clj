(ns thornydev.go-lightly.core-test
  (:use clojure.test
        thornydev.go-lightly.core))

(declare test-routine test-routine-track-enqueues)

(deftest test-go&
  (testing "go& routines"
    (let [ch1 (channel) ch2 (channel)]
      (go& (.transfer ch1 1))
      (go& (.transfer ch2 2))
      (is (= 1 (.take ch1)))
      (is (= 2 (.take ch2))))))

(deftest test-go-and-stop
  (testing "start and stop go routines"
    (let [ch1 (channel) ch2 (channel)]
      (go (.transfer ch1 1))
      (go (.transfer ch2 2))
      (is (= 1 (.take ch1)))
      (is (= 2 (.take ch2)))
      (stop))))

(deftest test-timeout-channel
  (testing "timeout channel has :go-lightly/timeout enqueued if nothing enqueued"
    (let [ch (timeout-channel 100)]
      (is (= :go-lightly/timeout (.take ch)))))

  (testing "timeout channel has :go-lightly/timeout enqueued after other things enqueued"
    (let [ch (timeout-channel 150)]
      (.put ch :a)
      (.put ch :b)
      (is (= :a (.take ch)))
      (is (= :b (.take ch)))
      (is (= :go-lightly/timeout (.take ch)))))

  (testing "timeout channel with other channels"
    (let [ch1 (channel)
          ch2 (channel)
          tch (timeout-channel 250)
          fnext-msg (partial select ch1 ch2 tch)]
      ;; this basically tests that it ends => not an infinite loop
      (go (test-routine ch1 1))
      (go (test-routine ch1 2))
      (loop [msg (fnext-msg)]
        (when-not (= msg :go-lightly/timeout)
          (is (some #{1 2} [msg]))
          (recur (fnext-msg)))))
    (stop)))


(deftest test-select
  (let [ch1 (channel)
        ch2 (channel)
        ch3 (channel)
        qlog (atom #{})]
    (go (test-routine-track-enqueues ch1 qlog))
    (go (test-routine-track-enqueues ch2 qlog))
    (go (test-routine-track-enqueues ch3 qlog))
    (let [results (doall (for [_ (range 10)]
                           (select ch1 ch2 ch3)))]
      (is (= 10 (count results)))
      (is (= (set results) @qlog)))   
    (stop))
  )

(deftest test-select-timeout
  (let [ch1 (channel)  ch2 (channel)
        timeout 25]
    (go& (test-routine ch1 1))
    (go& (test-routine ch2 2))
    (let [results (set
                   (doall
                    (for [_ (range 150)]
                      (do (Thread/sleep 1)
                          (select-timeout timeout ch1 ch2)))))]
      (is (= 3 (count results)))
      (is (contains? results :go-lightly/timeout))
      (is (contains? results 1))
      (is (contains? results 2)))))

(deftest test-nowait
  (let [ch1 (channel)  ch2 (channel)]
    (let [results (set
                   (doall
                    (for [_ (range 10)]
                      (select-nowait ch1 ch2 :foo-sentinel))))]
      (is (contains? results :foo-sentinel)))

    (go& (test-routine ch1 1))
    (go& (test-routine ch2 2))
    (Thread/sleep 750)

    (let [results (set
                   (doall
                    (for [_ (range 50)]
                      (select-nowait ch1 ch2))))]
      (is (> (count results) 1)))))


(println (run-tests 'thornydev.go-lightly.core-test))

;; ---[ helper fns ]--- ;;

(defn- test-routine [ch n]
  (dotimes [_ 50]
    (Thread/sleep (rand-int 500))
    (.transfer ch n)))


(defn- test-routine-track-enqueues [ch qlog]
  (loop [value (rand-int 6000)]
    (Thread/sleep (rand-int 100))
    (.transfer ch value)
    (swap! qlog conj value)
    (recur (rand-int 6000))))



