(ns thornydev.go-lightly.examples.load-balancer.balancer
  (:refer-clojure :exclude [peek take])
  (:require [thornydev.go-lightly.core :refer :all]))

(def continue? (atom true))

;; ---[ Requestors ]--- ;;

;; Request => run this fn and when done send me the answer on the channel I give you

(defn calc-tau []
  (Thread/sleep (min 1000 (rand-int 20000)))
  (* 2 Math/PI))

;; Request
{:operation calc-tau     ;; op to perform
 :channel (channel)}  ;; channel to return result on

(defn further-process [result-data]
  (println "result:" result-data))

;; requester func
(defn requester [work-ch]
  (let [ch (channel)]
    ;; send request
    (put work-ch {:operation calc-tau
                  :channel ch})
    (further-process (take ch)) ;; wait for answer and do more with it
    ))

(defn create-requests
  "Main loop for creating requests onto the work channel"
  [work-ch]
  (while @continue?
    (Thread/sleep (rand-int 2000))
    (go (requester work-ch))))




;; ---[ Workers ]--- ;;

(def ^:dynamic *nworkers* 4)

;; pool of workers
(def pool (atom nil))

;; Note that the worker is just data -> functions do the work, not objects
;; in this way Go is much like Clojure or a FP style, but not in others
(defn init-workers []
  (for [i (range *nworkers*)]
    {:index i, :pending 0, :requests-ch (channel 50000)}))

(defn load-or-id
  [w1 w2]
  (if (= (:pending w1) (:pending w2))
    (< (:index w1) (:index w2))
    (< (:pending w1) (:pending w2))))

(defn init-worker-pool []
  (reset! pool (apply sorted-set-by load-or-id (init-workers))))

;; Example Worker
{:requests-ch (channel 1000) ;; buffered channel of work to do
 :pending  22                ;; count of pending tasks
 :index    3}                ;; index in the heap

(defn work [worker done-chan]
  
  )


;; ---[ Balancer ]--- ;;

;; will this only be called on one thread at a time or
;; do we need to worry about thread safety?
(defn dispatch [balancer request]
  (let [pool (:pool balancer)
        worker (disj pool (first pool))
        (put (:requests-ch worker) request)
        worker2 (update-in worker [:pending] inc)]
    (assoc-in balancer [:pool] (conj pool worker2))))

(defn completed [balancer worker]
  (assoc-in balancer [:pool]
            (-> (:pool balancer)
                (disj worker)
                (conj (update-in worker [:pending] dec))))
  )

;; (defn balance
;;   "@param balancer - ?
;;    @param work-chan - channel of requests"
;;   [balancer work-ch]
;;   (let [bal (selectf
;;              work-ch             (fn [req]) (dispatch balancer req)
;;              (:done-ch balancer) (fn [wrkr] (completed balancer wrkr)))]
;;     (recur bal work-ch)))

;; TODO: can more than one thread can this simultaneously?
;;       if so, may need to have balancer be an atom or even a ref?
(defn balance
  "@param balancer - ?
   @param work-chan - channel of requests"
  [balancer work-ch]
  (-> (selectf
       work-ch             (fn [req]) (dispatch balancer req)
       (:done-ch balancer) (fn [wrkr] (completed balancer wrkr)))
    (recur work-ch)))

;; Notes
;; load balancer has a single channel for all the requests to come in on
;; load balancer has a single "done" for all the workers to signal on

(defn report []
  ;; printout state of pool
  ;; printout results (first 10 or something?)
  )

(defn -main [& args]
  (println "Starting ...")
  (init-worker-pool)

  ;; let the games begin
  (let [work-ch (channel 10000)
        balancer {:pool (init-workers)
                  :done-ch (channel)}]
    (go (create-requests work-ch))
    (go (balance balancer work-ch))
    )
  
  ;; let the games run
  (Thread/sleep (* 60 1000))

  ;; announce the games have ended
  (reset! continue? false)
  (Thread/sleep (* 2 1000))
  (report)
  (shutdown))
