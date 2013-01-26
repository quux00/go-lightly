(ns thornydev.go-lightly.examples.load-balancer.ldbal
  (:refer-clojure :exclude [peek take])
  (:require [thornydev.go-lightly.core :refer :all]))

(def continue? (atom true))

(def ^:dynamic *nworkers* 1)

(declare load-or-id init-workers)

;; ---[ data structures ]--- ;;

(def balancer (delay {:pool (apply sorted-set-by load-or-id (init-workers))
                      :done-ch (channel)}))


;; ---[ fns ]--- ;;

(defn prf [& vals]
  (let [s (apply str (interpose " " (map #(if (nil? %) "nil" %) vals)))]
    (print (str s "\n")) (flush)
    s))

(defn calc-tau []
  (Thread/sleep (min 1000 (rand-int 20000)))
  (* 2 Math/PI))

(defn load-or-id
  [w1 w2]
  (if (= (:pending w1) (:pending w2))
    (< (:index w1) (:index w2))
    (< (:pending w1) (:pending w2))))

(defn init-workers []
  (for [i (range *nworkers*)]
    {:index i, :pending 0, :completed 0, :requests-ch (channel 50000)}))

(defn further-process [result]
  (print (str "request processed: " result "\n")) (flush))

(defn requester
  "A single requester looping infinitely to put work requests on the work channel"
  [work-ch]
  (try
    (let [ch (channel)]
      (loop []
                        (prf "DEBUG 1: requester")
        (Thread/sleep (rand-int 300))
                        (prf "DEBUG 2 requester")
        (put work-ch {:operation calc-tau, :result-ch ch})  ;; send request
                        (prf "DEBUG 3 req")
        (-> (take ch)
            further-process)
                        (prf "LALALA")
        (recur)))
    (catch Exception e (prf "ERROR in requester:" (.getMessage e) "\n"))))

(defn work-orig [worker done-ch]
  (try
    (loop []
      (print (str "worker: " (:index worker) "\n"))
      (let [req (take (:requests-ch worker))
            f (:operation req)]
        (put (:result-ch req) (f)) ;; sync channel
        (put done-ch worker))      ;; sync channel
      (recur))
    (catch Exception e (prf "ERROR in work:" (.getMessage e) "\n"))))

(defn work [worker done-ch]
  (try
    (print (str "worker: " (:index worker) "\n"))
    (let [req (take (:requests-ch worker))
          f (:operation req)]
      (put (:result-ch req) (f)) ;; sync channel
      (put done-ch worker))      ;; sync channel
    (catch Exception e (prf "ERROR in work:" (.getMessage e) "\n"))))

(defn completed-orig [balancer worker]
  (prf "completed called with worker:" worker)
  (assoc-in balancer [:pool]
            (-> (:pool balancer)
                (disj worker)
                (conj (-> worker
                          (prf worker)
                          (update-in [:pending] dec)
                          (update-in [:completed] inc))))))

(defn completed [balancer worker]
  (prf "completed called with worker:" worker)
  (let [upwkr (-> worker
                  (update-in [:pending] dec)
                  (update-in [:completed] inc))]
    (assoc-in balancer [:pool]
              (-> (:pool balancer)
                  (disj worker)
                  (conj upwkr)))))

(defn dispatch [balancer request]
  (prf "dispatch called")
  (let [worker (first (:pool balancer))
        pool   (disj (:pool balancer) worker)]
    (put (:requests-ch worker) request)
    (->> (update-in worker [:pending] inc)
         (conj pool)
         (assoc-in balancer [:pool]))))

(defn balance [balcr work-ch]
  (try
    (loop [bal balcr]
      (clojure.pprint/pprint bal)
      (prf "--------------------------")
      (if @continue?
        (do (->
             (selectf
              work-ch        (fn [req] (dispatch bal req))
              (:done-ch bal) (fn [wrkr] (completed bal wrkr))
              (timeout-channel 800) (fn [_] bal))
             (recur)))
        (do (println "balancer shutting down:") (clojure.pprint/pprint bal) (println))))
    (catch Exception e (println e))))

(defn start-all-workers [bal]
  (doseq [w (:pool bal)]
    (go (work w (:done-ch bal)))))

(defn -main [& args]
  (println "Starting ...")
  (let [work-ch (channel 10000)]
    (go (do (Thread/sleep 1665) (reset! continue? false)))
    (go (balance @balancer work-ch))
    (start-all-workers @balancer)
    (go (requester work-ch))
    ;; (Thread/sleep 222)
    ;; (put (:done-ch @balancer) (first (:pool @balancer)))
    ;; (put (:done-ch @balancer) (second (:pool @balancer)))
    (Thread/sleep 2800)
    (print "work chan:")
    (clojure.pprint/pprint work-ch) (println)
    )
  )
