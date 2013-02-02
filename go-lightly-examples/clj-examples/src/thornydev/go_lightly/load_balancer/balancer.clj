(ns thornydev.go-lightly.load-balancer.balancer
  (:refer-clojure :exclude [peek take])
  (:require [thornydev.go-lightly.core :refer :all]))

;; Clojure version of a load balancer using go-lightly routines and channels
;; Based on Go version presented by Rob Pike in 2010 Google IO Conf and
;; 2012 Heroku conf:
;;  Concurrency is not Parallelism: http://vimeo.com/49718712
;;  slides here: https://rspace.googlecode.com/hg/slide/concur.html#landing-slide
;;
;;  https://twitter.com/nedbat/status/194452404794691584

(def ^:dynamic *nworkers* 5)
(def ^:dynamic *requests-to-process* 100)
(def ^:dynamic *requesters-to-workers-ratio* 3)

;; place to keep all the results
;; (def results (atom []))
(def nresults (atom 0))

;; ---[ fns ]--- ;;

(defn prf [& vals]
  (let [s (apply str (interpose " " (map #(if (nil? %) "nil" %) vals)))]
    (print (str s "\n")) (flush)
    s))

(defn load-or-id
  "Comparator function for sorted-map-by"
  [w1 w2]
  (if (= (:pending @w1) (:pending @w2))
    (< (:index @w1) (:index @w2))
    (< (:pending @w1) (:pending @w2))))


;; ---[ requester fns ]--- ;;

(defn calc-tau []
  (Thread/sleep (min 500 (rand-int 5000)))
  (* 2 Math/PI))

(defn further-process [result]
  (->> (swap! nresults inc)
       (prf "request processed: " result ": ")))

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
    (catch InterruptedException ie)  ;; ignore => thrown when cancelled at end
    (catch Exception e (prf "ERROR in requester:" (.getMessage e) "\n"))))

(defn start-all-requesters [work-ch]
  (doseq [_ (range (* *requesters-to-workers-ratio* *nworkers*))]
    (go (requester work-ch))))


;; ---[ worker fns ]--- ;;

(defn init-workers []
  (for [i (range *nworkers*)]
    (atom {:index i, :pending 0, :completed 0, :requests-ch (channel 5000)})))

(defn work [worker done-ch]
  (loop []
    (let [req (take (:requests-ch @worker))
          f (:operation req)]
        (put (:result-ch req) (f)) ;; sync channel => requester waits for result
        (put done-ch worker))      ;; buffered channel => worker mvs onto next task
    (recur)))

(defn start-all-workers [bal]
  (doseq [w (:pool bal)]
    (go (work w (:done-ch bal)))))

;; ---[ balancer fns ]--- ;;

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
      (if (< @nresults *requests-to-process*)
        (do (-> (selectf
                 work-ch        (fn [req] (dispatch bal req))
                 (:done-ch bal) (fn [wrkr] (completed bal wrkr))
                 (timeout-channel 200) (fn [_] bal))
                recur))
        (do (prf ">>> balancer shutting down:"))))
    (catch Exception e (println e))))

(defn- report [bal work-ch]
  (clojure.pprint/pprint bal)
  (println)
  (when (< (size work-ch) 20)
    (print "work chan:")
    (clojure.pprint/pprint work-ch))
  (println "num results:" @nresults))

(defn- init []
  (reset! nresults 0))

(defn- create-balancer []
  {:pool (apply sorted-set-by load-or-id (init-workers))
   :done-ch (channel 5000)})

(defn -main [& args]
  (init)
  (binding [*nworkers* (Integer/valueOf (or (first args) 5))
            *requests-to-process* (Integer/valueOf (or (second args) 100))]
    (println (format "Starting with ... %d workers, %d requesters, run until %d requests processed",
                     *nworkers*, (* *requesters-to-workers-ratio* *nworkers*), *requests-to-process*))
    (let [balancer (create-balancer)
          work-ch (channel 5000)]
      (start-all-workers balancer)
      (start-all-requesters work-ch)
      (balance balancer work-ch)  ;; run balancer in main thread (blocks until done)
      (stop)
      (Thread/sleep 100)
      (report balancer work-ch))))
