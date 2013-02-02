(ns thornydev.go-lightly.examples.sleeping-barber.barber
  (:refer-clojure :exclude [peek take])
  (:require [thornydev.go-lightly.core :refer :all]))


(def ^:dynamic *num-barbers* 5)
(def ^:dynamic *num-hall-seats* 5)
(def ^:dynamic *cutting-time* 250)


(defn prf [& vals]
  (let [s (apply str (interpose " " (map #(if (nil? %) "nil" %) vals)))]
    (print (str s "\n")) (flush)
    s))

(defn prv [v]
  (print (str ">>" v "<<\n")) (flush)
  v)

(defn client-producer [clients-ch]
  (loop [i 0]
    (Thread/sleep (rand-int (/ *cutting-time* 1.0)))
    (put clients-ch (keyword (str "c" i)))
    (recur (inc i))))

(defn init-barber-vector []
  (vec
   (for [i (range *num-barbers*)]
     (keyword (str "b" i)))))

(defn cut-hair [barber client finished-ch]
  (prf barber "cuts hair of" client)
  (Thread/sleep *cutting-time*)
  (prf "!finished cutting hair of" client)
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
      (gox (cut-hair barber client barber-ch))
      (update-in shop-state [:free-barbers] #(vec (rest %))))))

(defn barber-available [barber barber-ch shop-state]
  (if (> (count (:waiting-clients shop-state)) 0)  ;; (if (seq (:wc ss))
    (let [client (first (:waiting-clients shop-state))]
      (prf
       (format "Take client %s from room (remaining clients: %d)",
               client, (dec (count (:waiting-clients shop-state)))))
      (gox (cut-hair barber client barber-ch))
      (update-in shop-state [:waiting-clients] #(vec (rest %))))

    (do (prf "Barber" barber "takes a nap.")
        (update-in shop-state [:free-barbers] conj barber))))

(defn barber-shop [clients-ch]
  (let [barber-ch (channel)]    
    (loop [shop-state {:free-barbers (init-barber-vector)
                       :waiting-clients []}]  ;; TODO: should be PersistentQueue
      (-> (selectf
           clients-ch #(client-walked-in % barber-ch shop-state)
           barber-ch  #(barber-available % barber-ch shop-state))
          prv
          (recur)))))

;; TODO: why does this with-timeout not work?
;;       Does it not compose with go or gox?
(defn -main2 [& args]
  (with-timeout (Integer/valueOf (or (first args) 2000))
    (let [clients-ch (channel)]
      (gox (client-producer clients-ch))
      (gox (barber-shop clients-ch))))
  (prf "done")
  (stop))

(defn -main [& args]
  (let [clients-ch (channel)]
    (gox (client-producer clients-ch))
    (gox (barber-shop clients-ch)))
  (Thread/sleep (Integer/valueOf (or (first args) 2000)))
  (prf "done")
  (stop))
