(ns thornydev.go-lightly.sleeping-barber.barber
  (:refer-clojure :exclude [peek take])
  (:require [thornydev.go-lightly.core :refer :all]))

;; Implementation based on Go routine implementation
;; by Alexey Kacayev: https://gist.github.com/4688906

;; This version uses two threads (Go routines) for:
;;  - one to bring in clients to the shop one at a time
;;  - one to select between handling a client just entered
;;        or to handle a barber becoming available
;; And then one thread/routine per barber while s/he
;; is cutting hair

(def ^:dynamic *num-barbers* 3)
(def ^:dynamic *num-hall-seats* 5)
(def ^:dynamic *cutting-time* 100)

;; ---[ print helper fns ]--- ;;

(defn prf [& vals]
  (let [s (apply str (interpose " " (map #(if (nil? %) "nil" %) vals)))]
    (print (str s "\n")) (flush)
    s))

(defn prv [v]
  (print (str ">>" v "<<\n")) (flush)
  v)

;; ---[ barber shop fns ]--- ;;

(defn client-producer
  "producer routine/thread that simulates clients coming
  to the barber shop"
  [clients-ch]
  (loop [i 0]
    (Thread/sleep (rand-int (/ *cutting-time* 2)))
    (put clients-ch (keyword (str "c" i)))
    (recur (inc i))))

(defn init-barber-vector []
  (vec (for [i (range *num-barbers*)]
         (keyword (str "b" i)))))

(defn cut-hair [barber client finished-ch]
  (prf barber "cuts hair of" client)
  (Thread/sleep *cutting-time*)
  (prf "!" barber "finished cutting hair of" client)
  (put finished-ch barber))


(defn client-walked-in [client barber-ch shop-state]
  (if (zero? (count (:free-barbers shop-state)))
    (if (< (count (:waiting-clients shop-state)) *num-hall-seats*)
      (do (prf
           (format "Client %s is waiting in the hall (tot: %d)",
                   client, (inc (count (:waiting-clients shop-state)))))
          (update-in shop-state [:waiting-clients] conj client))
      (do (prf "No free space for client" client " ... leaving")
          shop-state))

    (let [barber (first (:free-barbers shop-state))]
      (prf "Client" client "goes to barber" barber)
      (go (cut-hair barber client barber-ch))
      (update-in shop-state [:free-barbers] #(vec (rest %))))))


(defn barber-available [barber barber-ch shop-state]
  (if (seq (:waiting-clients shop-state))
    (let [client (first (:waiting-clients shop-state))]
      (prf
       (format "%s takes client %s from room (remaining clients: %d)",
               barber, client, (dec (count (:waiting-clients shop-state)))))
      (go (cut-hair barber client barber-ch))
      (update-in shop-state [:waiting-clients] #(vec (rest %))))

    (do (prf "Barber" barber "takes a nap.")
        (update-in shop-state [:free-barbers] conj barber))))

(defn barber-shop [clients-ch]
  (let [barber-ch (channel)]
    (loop [shop-state {:free-barbers (init-barber-vector)
                       :waiting-clients []}]
      (-> (selectf
           clients-ch #(client-walked-in % barber-ch shop-state)
           barber-ch  #(barber-available % barber-ch shop-state))
          ;; prv  ;; uncomment to see the shop-state after each select
          (recur)))))

(defn -main
  "This version has one thread/routine for continuously bringing
  clients into the shop and another thread that waits for either
  clients to come in or barbers to become available, handles that
  scenario and repeats. Each barber cutting hair has its own thread."
  [& args]
  (with-timeout (Integer/valueOf (or (first args) 2000))
    (let [clients-ch (channel)]
      (go (client-producer clients-ch))
      (barber-shop clients-ch)))
  (prf "done")
  (stop))
