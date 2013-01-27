(ns thornydev.go-lightly.examples.load-balancer.ldbal
  (:refer-clojure :exclude [peek take])
  (:require [thornydev.go-lightly.core :refer :all]))

(def ^:dynamic *nworkers* 10)

(def results (atom []))

(declare load-or-id init-workers)

;; ---[ data structures ]--- ;;

(def balancer (delay {:pool (apply sorted-set-by load-or-id (init-workers))
                      :done-ch (channel 10000)}))

;; ---[ fns ]--- ;;

(defn prf [& vals]
  (let [s (apply str (interpose " " (map #(if (nil? %) "nil" %) vals)))]
    (print (str s "\n")) (flush)
    s))

(defn calc-tau []
  (Thread/sleep (min 800 (rand-int 20000)))
  (* 2 Math/PI))

(defn load-or-id [w1 w2]
  (if (= (:pending @w1) (:pending @w2))
    (< (:index @w1) (:index @w2))
    (< (:pending @w1) (:pending @w2))))

(defn init-workers []
  (for [i (range *nworkers*)]
    (atom {:index i, :pending 0, :completed 0, :requests-ch (channel 50000)})))

(defn further-process [result]
  (swap! results conj result)
  (print (str "request processed: " result "\n")) (flush))

(defn requester
  "A single requester looping infinitely to put work requests on the work channel"
  [work-ch]
  (try
    (let [ch (channel)]
      (loop []
        (Thread/sleep (rand-int 250))
        (put work-ch {:operation calc-tau, :result-ch ch})  ;; send request
        (-> (take ch)
            further-process)
        (recur)))
    (catch InterruptedException ie)  ;; ignore => called when cancelled at end
    (catch Exception e (prf "ERROR in requester:" (.getMessage e) "\n"))))

(defn start-all-requesters [work-ch]
  (doseq [_ (range (* 4 *nworkers*))]
    (go (requester work-ch))))

(defn work [worker done-ch]
  (loop []
    (let [req (take (:requests-ch @worker))
          f (:operation req)]
        (put (:result-ch req) (f)) ;; sync channel => requester waits for result
        (put done-ch worker))      ;; buffered channel => worker mvs onto next task
    (recur)))

(defn completed [balancer worker]
  (let [pool (disj (:pool balancer) worker)]
    (swap! worker #(-> %
                       (update-in [:pending] dec)
                       (update-in [:completed] inc)))    
    (assoc-in balancer [:pool] (conj pool worker))))

(defn dispatch [balancer request]
  (let [worker (first (:pool balancer))
        pool   (disj (:pool balancer) worker)]
    (put (:requests-ch @worker) request)
    (swap! worker update-in [:pending] inc)
    (->> worker
         (conj pool)
         (assoc-in balancer [:pool]))))

(defn balance [balcr work-ch]
  (try
    (loop [bal balcr]
      (if (< (count @results) 200)
        (do (-> (selectf
                 work-ch        (fn [req] (dispatch bal req))
                 (:done-ch bal) (fn [wrkr] (completed bal wrkr))
                 (timeout-channel 200) (fn [_] bal))
                recur))
        (do (prf "> balancer shutting down:") (clojure.pprint/pprint bal) (println))))
    (catch Exception e (println e))))

(defn start-all-workers [bal]
  (doseq [w (:pool bal)]
    (go (work w (:done-ch bal)))))

(defn -main [& args]
  (println "Starting ...")
  (let [work-ch (channel 10000)]
    (start-all-workers @balancer)
    (start-all-requesters work-ch)
    (balance @balancer work-ch)  ;; do balancer in main thread
    (print "work chan:")
    (clojure.pprint/pprint work-ch)
    (println "sz results:" (count @results)))
  (stop))
